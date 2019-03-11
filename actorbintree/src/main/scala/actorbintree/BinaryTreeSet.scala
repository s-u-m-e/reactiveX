/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package actorbintree

import akka.actor._
import scala.collection.immutable.Queue

object BinaryTreeSet {

  trait Operation {
    def requester: ActorRef
    def id: Int
    def elem: Int
  }

  trait OperationReply {
    def id: Int
  }

  /** Request with identifier `id` to insert an element `elem` into the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Insert(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to check whether an element `elem` is present
    * in the tree. The actor at reference `requester` should be notified when
    * this operation is completed.
    */
  case class Contains(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to remove the element `elem` from the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Remove(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request to perform garbage collection*/
  case object GC

  /** Holds the answer to the Contains request with identifier `id`.
    * `result` is true if and only if the element is present in the tree.
    */
  case class ContainsResult(id: Int, result: Boolean) extends OperationReply
  
  /** Message to signal successful completion of an insert or remove operation. */
  case class OperationFinished(id: Int) extends OperationReply

}


class BinaryTreeSet extends Actor {
  import BinaryTreeSet._
  import BinaryTreeNode._

  def createRoot: ActorRef = context.actorOf(props(0, initiallyRemoved = true))

  var root = createRoot

  // optional
  var pendingQueue = Queue.empty[Operation]

  // optional
  def receive = normal

  // optional
  /** Accepts `Operation` and `GC` messages. */
  val normal: Receive = {
    case op: Operation => root ! op

    case GC => {
      val newRoot = createRoot
      context.become(garbageCollecting(newRoot))
      root ! CopyTo(newRoot)
    }
  }

  // optional
  /** Handles messages while garbage collection is performed.
    * `newRoot` is the root of the new binary tree where we want to copy
    * all non-removed elements into.
    */
  def garbageCollecting(newRoot: ActorRef): Receive = {
    case op: Operation => pendingQueue :+ op
    case GC =>

  }

}

object BinaryTreeNode {
  trait Position

  case object Left extends Position
  case object Right extends Position

  case class CopyTo(treeNode: ActorRef)
  case object CopyFinished

  def props(elem: Int, initiallyRemoved: Boolean) = Props(classOf[BinaryTreeNode],  elem, initiallyRemoved)
}

class BinaryTreeNode(val elem: Int, initiallyRemoved: Boolean) extends Actor {
  import BinaryTreeNode._
  import BinaryTreeSet._

  var subtrees = Map[Position, ActorRef]()
  var removed = initiallyRemoved

  // optional
  def receive = normal

  // optional
  /** Handles `Operation` messages and `CopyTo` requests. */
  val normal: Receive = {
    case i @ Insert(_,_,_) => insertHandler(i)
    case c @ Contains(_,_,_) => containsHandler(c)
    case r @ Remove(_,_,_) => removeHandler(r)
    case CopyTo(node) => {
      context.become(copying(subtrees.values.toSet, true))
      if(!removed) node ! Insert(self, elem, elem)


      subtrees.foreach(_._2 ! CopyTo(context.actorOf(props = )) )
      context.stop(self)
    }
    }

  // optional
  /** `expected` is the set of ActorRefs whose replies we are waiting for,
    * `insertConfirmed` tracks whether the copy of this node to the new tree has been confirmed.
    */
  def copying(expected: Set[ActorRef], insertConfirmed: Boolean): Receive = ???

  def insertAt(position: Position)(insert: Insert): Unit = {
    subtrees.get(position) match {
      case None => {
        subtrees =  subtrees + (position -> context.actorOf(props(insert.elem, false)))
        insert.requester ! OperationFinished(insert.id)
      }
      case Some(node) => node ! insert
    }
  }

  def containsAt(position: Position)(contains: Contains): Unit = {
    subtrees.get(position) match {
      case None => contains.requester ! ContainsResult(contains.id, false)
      case Some(node) => node ! contains
    }
  }

  def removeAt(position: Position)(remove: Remove): Unit = {
    subtrees.get(position) match {
      case None => remove.requester ! OperationFinished(remove.id)
      case Some(node) => node ! remove
    }
  }
  val insertHandler = (message: Insert) => messageHandler(message)
    { message => this.removed = false; message.requester ! OperationFinished (message.id)}
    { message => insertAt(Right)(message)}
    { message => insertAt (Left)(message)}


  val containsHandler = (message: Contains) => messageHandler(message)
    { message => message.requester ! ContainsResult(message.id, !removed) }
    { message => containsAt(Right)(message) }
    { message => containsAt(Left)(message) }


  val removeHandler = (message: Remove) => messageHandler(message)
  { message => this.removed = true; message.requester ! OperationFinished(message.id)}
  { message =>  removeAt(Right)(message)}
  { message => removeAt(Left)(message)}



  def messageHandler[T <: Operation](message: T)(ifEqual: T => Unit)
                    (ifGreater: T => Unit)
                    (ifLess: T =>  Unit) = message.elem match {
    case _ if elem == message.elem => ifEqual(message)
    case _ if message.elem > elem => ifGreater(message)
    case _ => ifLess(message)

  }

}
