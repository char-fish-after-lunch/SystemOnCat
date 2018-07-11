package systemoncat.core

import chisel3._
import chisel3.util._
import chisel3.testers._

class DatapathTester(dp: => Datapath) extends BasicTester {
    val dpath = Module(dp)
    // TODO: implement me!
    val ctrl = Module(new Control)
    dpath.io.ctrl <> ctrl.io
    assert(1 == 1)
    stop()
}


class DatapathTests extends org.scalatest.FlatSpec {
  "DatapathTests" should "pass" in {
    assert(TesterDriver execute (() => new DatapathTester(new Datapath)))
  }
}