package systemoncat.core

import chisel3._
import chisel3.util._
import chisel3.testers._

class IFetchTester(ifetch: => IFetch) extends BasicTester {
    val fetch = Module(ifetch)
    // TODO: implement me!
    fetch.io.pc := 0.U(32.W)
    assert(1 == 1)
    stop()
}


class IFetchTests extends org.scalatest.FlatSpec {
  "IFetchTests" should "pass" in {
    assert(TesterDriver execute (() => new IFetchTester(new IFetch)))
  }
}