trait A {
  def blah = "A"
}

trait B {
  def blah = "B"
}

class C extends A with B {

}

val tt = new C()

tt.blah
