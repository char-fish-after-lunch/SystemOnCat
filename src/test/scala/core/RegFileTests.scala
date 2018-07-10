package systemoncat.core

import chisel3._
import chisel3.util._
import chisel3.testers._

class RegFileTester(rf: => RegFile) extends BasicTester {
    val regfile = Module(rf)
    // TODO: implement me!
}


class RegFileTests extends org.scalatest.FlatSpec {
  "RegFileTests" should "pass" in {
    assert(TesterDriver execute (() => new RegFileTester(new RegFile)))
  }
}