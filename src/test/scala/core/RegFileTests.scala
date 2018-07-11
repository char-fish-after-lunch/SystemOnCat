package systemoncat.core

import chisel3._
import chisel3.util._
import chisel3.testers._

class RegFileTester(rf: => RegFile) extends BasicTester {
    val regfile = Module(rf)
    // TODO: implement me!
    regfile.io.raddr1 := 0.U(5.W)
    regfile.io.raddr2 := 0.U(5.W)
    regfile.io.wen := false.B
    regfile.io.waddr := 0.U(5.W)
    regfile.io.wdata := 0.U(32.W)
    
    assert(1 == 1)
    stop()
}


class RegFileTests extends org.scalatest.FlatSpec {
  "RegFileTests" should "pass" in {
    assert(TesterDriver execute (() => new RegFileTester(new RegFile)))
  }
}