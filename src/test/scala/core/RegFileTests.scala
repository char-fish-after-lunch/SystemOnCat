package systemoncat.core

import chisel3._
import chisel3.util._
import chisel3.testers._

class RegFileTester(rf: => RegFile) extends BasicTester {
    val regfile = Module(rf)
    // TODO: implement me!


    val test_seq = Seq(
        (0.U(5.W), 1.U(5.W), true.B, 7.U(5.W), 0xbeef.U(32.W)),
        (0.U(5.W), 1.U(5.W), true.B, 17.U(5.W), 0xeaddead.U(32.W)),
        (17.U(5.W), 7.U(5.W), false.B, 7.U(5.W), 0xaaaa.U(32.W)),
        (17.U(5.W), 7.U(5.W), true.B, 7.U(5.W), 0xaaaa.U(32.W)),
        (7.U(5.W), 17.U(5.W), true.B, 7.U(5.W), 0xaaaa.U(32.W))
    )

    val test_raddr1 = VecInit(test_seq.map(s => s._1))
    val test_raddr2 = VecInit(test_seq.map(s => s._2))
    val test_wen = VecInit(test_seq.map(s => s._3))
    val test_waddr = VecInit(test_seq.map(s => s._4))
    val test_wdata = VecInit(test_seq.map(s => s._5))

    val out_seq = Seq(
        (0.U, 0.U),
        (0.U, 0.U),
        (0xeaddead.U, 0xbeef.U),
        (0xeaddead.U, 0xbeef.U),
        (0xaaaa.U, 0xeaddead.U)
    )

    val test_rdata1 = VecInit(out_seq.map(s => s._1))
    val test_rdata2 = VecInit(out_seq.map(s => s._2))

    val (cntr, done) = Counter(true.B, test_seq.size)

    regfile.io.raddr1 := test_raddr1(cntr)
    regfile.io.raddr2 := test_raddr2(cntr)
    regfile.io.wen := test_wen(cntr)
    regfile.io.waddr := test_waddr(cntr)
    regfile.io.wdata := test_wdata(cntr)
    
    assert(regfile.io.rdata1 === test_rdata1(cntr))
    assert(regfile.io.rdata2 === test_rdata2(cntr))
    
    when(done) { stop(); stop() } // from VendingMachine example...

}


class RegFileTests extends org.scalatest.FlatSpec {
  "RegFileTests" should "pass" in {
    assert(TesterDriver execute (() => new RegFileTester(new RegFile)))
  }
}