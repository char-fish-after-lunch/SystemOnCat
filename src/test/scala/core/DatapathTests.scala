package systemoncat.core

import chisel3._
import chisel3.util._
import chisel3.testers._

trait DatapathTest
object BasicTest extends DatapathTest {
  override def toString: String = "basic test"
}
object BypassTest extends DatapathTest {
  override def toString: String = "bypass test"
}
object BranchTest extends DatapathTest {
  override def toString: String = "branch test"
}
object FibonacciTest extends DatapathTest {
  override def toString: String = "fibonacci test"
}

object DatapathTestSpecs extends TestUtils {
    val basic_test_insts = Seq(
        I(Funct3.ADD, 1, 0, 3),  // ADDI x1, x0, 3   # x1 <- 3
        I(Funct3.ADD, 2, 0, 8),  // ADDI x2, x0, 8  # x2 <- 8
        I(Funct3.ADD, 3, 0, 4), // ADDI  x3, x0, 4  # x3 <- 4
        NOP, NOP, NOP,
        RU(Funct3.ADD, 4, 1, 2), // ADD  x4, x1, x2  # x4 <- x1 + x2 = 11
        RU(Funct3.ADD, 5, 3, 3), // ADD  x5, x3, x3  # x5 <- x3 + x3 = 8
        RS(Funct3.ADD, 6, 2, 1), // SUB  x6, x2, x1  # x6 <- x2 - x1 = 5
        RU(Funct3.SLL, 7, 1, 2), // SLL  x7, x1, x2  # x7 <- (3<<8)
        NOP,NOP,NOP,NOP,NOP // finish the pipeline
    )

    val basic_test_alu_results = Seq(
        0.U, 0.U, 3.U, 8.U, 4.U, 0.U, 0.U, 0.U, 11.U, 8.U, 5.U, 0x300.U, 0.U, 0.U, 0.U, 0.U, 0.U
    )

    val bypass_test_insts = Seq(
        I(Funct3.ADD, 1, 0, 3),  // ADDI x1, x0, 3    # x1 <- 3
        I(Funct3.ADD, 2, 0, 8),  // ADDI x2, x0, 8    # x2 <- 8
        RU(Funct3.ADD, 3, 1, 2), // ADD  x3, x1, x2   # x3 <- x1 + x2 = 11
        I(Funct3.SLT, 4, 3, 12), // SLTI  x4, x3, 12  # x4 <- x3 < 12 = 1
        RS(Funct3.SR, 3, 3, 4),  // SRA  x3, x4, x3   # x3 <- (x3 >> x4) = 5
        I(Funct3.SLL, 3, 3, 2),  // SRA  x3, x3, 2   # x3 <- (x3 << 2) = 20
        NOP, NOP, NOP, NOP, NOP
    )

    val bypass_test_alu_results = Seq(
        0.U, 0.U, 3.U, 8.U, 11.U, 1.U, 5.U, 20.U, 0.U, 0.U, 0.U
    )

    val branch_test_insts = Seq(
        I(Funct3.ADD, 1, 0, 1),  // ADDI x1, x0, 1   # x1 <- 1
        I(Funct3.ADD, 2, 0, 1),  // ADDI x2, x0, 1   # x2 <- 1
        I(Funct3.ADD, 1, 0, 2),  // ADDI x1, x0, 2   # x1 <- 2
        B(Funct3.BNE, 1, 2, 32), // BNE  x1, x2, 32   # go to the BNE branch
        RU(Funct3.ADD, 1, 1, 1), // ADD  x1, x1, x1  # x1 <- x1 + x1
        RU(Funct3.ADD, 1, 1, 1), // ADD  x1, x1, x1  # x1 <- x1 + x1
        RU(Funct3.ADD, 1, 1, 1), // ADD  x1, x1, x1  # x1 <- x1 + x1
        RU(Funct3.ADD, 1, 1, 1), // ADD  x1, x1, x1  # x1 <- x1 + x1
        RU(Funct3.ADD, 1, 1, 1), // ADD  x1, x1, x1  # x1 <- x1 + x1
        RU(Funct3.ADD, 1, 1, 1), // ADD  x1, x1, x1  # x1 <- x1 + x1
        RU(Funct3.ADD, 1, 1, 1), // ADD  x1, x1, x1  # x1 <- x1 + x1
        RU(Funct3.ADD, 1, 1, 1), // ADD  x1, x1, x1  # x1 <- x1 + x1
        RU(Funct3.ADD, 1, 1, 1), // ADD  x1, x1, x1  # x1 <- x1 + x1
        NOP,NOP,NOP,NOP,NOP
    )

    val branch_test_alu_results = Seq(
        0.U
    )

    val fibonacci_test_insts = Seq(
        I(Funct3.ADD, 10, 0, 1), // ADDI x10, x0, 1  # x10 <- 1
        I(Funct3.ADD, 1, 0, 1),  // ADDI x1, x0, 1   # x1 <- 1
        I(Funct3.ADD, 2, 0, 1),  // ADDI x2, x0, 1   # x2 <- 1
        I(Funct3.ADD, 3, 0, 15), // ADDI x3, x0, 15  # x3 <- 15
        RU(Funct3.ADD, 1, 1, 2), // ADD  x1, x1, x2  # x1 <- x1 + x2 :loop
        RU(Funct3.ADD, 2, 1, 2), // ADD  x2, x1, x2  # x2 <- x1 + x2
        I(Funct3.ADD, 10, 10, 1), // ADDI x10, x10, 1  # x10 += 1
        B(Funct3.BNE, 10, 3, -12), // BNE  x10, x3, 32   # if (x3 != x10) goto :loop
        NOP,NOP,NOP,NOP,NOP
    )

    val fibonacci_test_alu_results = Seq(
        0.U
    )

    val test_insts = Map(
        BasicTest -> basic_test_insts,
        BypassTest -> bypass_test_insts,
        BranchTest -> branch_test_insts,
        FibonacciTest -> fibonacci_test_insts
    )

    val test_alu_results = Map(
        BasicTest -> basic_test_alu_results,
        BypassTest -> bypass_test_alu_results,
        BranchTest -> branch_test_alu_results,
        FibonacciTest -> fibonacci_test_alu_results
    )
}

class TestIFetch(testType: => DatapathTest) extends Module with TestUtils {
    val io = IO(new IFetchIO)
    val test_insts = DatapathTestSpecs.test_insts(testType)
    io.inst := VecInit(test_insts)(io.pc(31, 2))
}

class DatapathTester(dp: => Datapath, testType: => DatapathTest) extends BasicTester {
    val dpath = Module(dp)
    // TODO: implement me!
    val ctrl = Module(new Control)
    val ifetch = Module(new TestIFetch(testType))
    val dmem = Module(new DMem)
    dpath.io.ctrl <> ctrl.io
    dpath.io.debug_devs.touch_btn := 0.U(4.W)
    dpath.io.debug_devs.dip_sw := 0.U(32.W)
    dpath.io.imem <> ifetch.io
    dpath.io.dmem <> dmem.io
    dpath.io.ctrl <> ctrl.io

    val test_insts = DatapathTestSpecs.test_insts(testType)
    val test_alu_results = VecInit(DatapathTestSpecs.test_alu_results(testType))

    val (cntr, done) = Counter(true.B, 100)

    printf(s"Clk: \n")
    printf(s"INST[%x] => %x\n", ifetch.io.pc, ifetch.io.inst)
    printf(s"alu_out: %x, reg_write: %x\n", dpath.io.debug_devs.leds, dpath.io.debug_devs.dpy1)

    if (testType == BasicTest || testType == BypassTest) {
        assert(dpath.io.debug_devs.leds === test_alu_results(cntr))
    }
    when (ifetch.io.pc(31, 2) > test_insts.size.U || done) { stop(); stop() } // from VendingMachine example...
}


class DatapathTests extends org.scalatest.FlatSpec {
  Seq(BasicTest, BypassTest, BranchTest, FibonacciTest) foreach { test =>
    "Datapath" should s"pass $test" in {
      assert(TesterDriver execute (() => new DatapathTester(new Datapath, test)))
    }
  }
}