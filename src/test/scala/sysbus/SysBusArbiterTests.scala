package systemoncat.sysbus

import chisel3._
import chisel3.util._
import chisel3.testers._

class SysBusArbiterTester(br: => SysBusArbiter) extends BasicTester {
    val arbiter = Module(br)
    assert(1 == 1)
    stop()
}


class SysBusArbiterTests extends org.scalatest.FlatSpec {
  "SysBusArbiterTests" should "pass" in {
    assert(TesterDriver execute (() => new SysBusArbiterTester(new SysBusArbiter)))
  }
}