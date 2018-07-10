package systemoncat.core

import chisel3._
import chisel3.util._
import chisel3.testers._

class ALUTester(alu: => ALU) extends BasicTester {
    val testalu = Module(alu)
    // TODO: implement me!
}


class ALUTests extends org.scalatest.FlatSpec {
  "ALUTests" should "pass" in {
    assert(TesterDriver execute (() => new ALUTester(new ALU)))
  }
}