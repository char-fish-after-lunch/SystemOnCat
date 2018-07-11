package systemoncat.core

import chisel3._
import chisel3.util._
import chisel3.testers._

object TestInsts extends TestUtils {
    val test_insts = Seq(
        I(Funct3.ADD, 1, 0, 3),  // ADDI x1, x0, 1   # x1 <- 3
        I(Funct3.ADD, 2, 0, 8),  // ADDI x2, x0, 8  # x2 <- 8
        I(Funct3.ADD, 3, 0, 4), // ADDI  x3, x0, 4  # x3 <- 4
        I(Funct3.ADD, 3, 0, 4), // ADDI  x3, x0, 4  # x3 <- 4
        I(Funct3.ADD, 3, 0, 4), // ADDI  x3, x0, 4  # x3 <- 4
        I(Funct3.ADD, 3, 0, 4), // ADDI  x3, x0, 4  # x3 <- 4
        RU(Funct3.ADD, 4, 1, 2), // ADD  x4, x1, x2  # x4 <- x1 + x2 = 11
        RU(Funct3.ADD, 5, 3, 3), // ADD  x5, x3, x3  # x5 <- x3 + x3 = 8
        RS(Funct3.ADD, 6, 2, 1), // SUB  x6, x2, x1  # x6 <- x2 - x1 = 5
        RU(Funct3.SLL, 7, 1, 2), // SLL  x7, x1, x2  # x7 <- (3<<8)
        NOP,NOP,NOP,NOP,NOP // finish the pipeline
    )
}

class TestIFetch extends Module with TestUtils {
    val io = IO(new IFetchIO)

    io.inst := VecInit(TestInsts.test_insts)(io.pc(31, 2))
}

class DatapathTester(dp: => Datapath) extends BasicTester {
    val dpath = Module(dp)
    // TODO: implement me!
    val ctrl = Module(new Control)
    val ifetch = Module(new TestIFetch)
    val dmem = Module(new DMem)
    dpath.io.ctrl <> ctrl.io
    dpath.io.debug_devs.touch_btn := 0.U(4.W)
    dpath.io.debug_devs.dip_sw := 0.U(32.W)
    dpath.io.imem <> ifetch.io
    dpath.io.dmem <> dmem.io
    dpath.io.ctrl <> ctrl.io


    val (cntr, done) = Counter(true.B, TestInsts.test_insts.size)

    printf(s"Clk: \n")
    printf(s"INST[%x] => %x\n", ifetch.io.pc, ifetch.io.inst)
    printf(s"reg_write: %x, alu_out: %x\n", dpath.io.debug_devs.leds, dpath.io.debug_devs.dpy1)
    when(done) { stop(); stop() } // from VendingMachine example...
}


class DatapathTests extends org.scalatest.FlatSpec {
  "DatapathTests" should "pass" in {
    assert(TesterDriver execute (() => new DatapathTester(new Datapath)))
  }
}