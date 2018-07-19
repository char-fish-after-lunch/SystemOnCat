package systemoncat.cache

import chisel3._
import chisel3.util._
import chisel3.testers._

class CacheTester(c: => Cache) extends BasicTester {
    val cache = Module(c)

    // adr_i, we_i, cyc_i, stb_i
    val inputSeq = Seq(
        (0x2000.U(32.W), false.B, true.B, true.B),
        (0.U(32.W), false.B, true.B, false.B),
        (0.U(32.W), false.B, true.B, false.B),
        (0.U(32.W), false.B, true.B, false.B),
        (0.U(32.W), false.B, true.B, false.B),
        (0.U(32.W), false.B, true.B, false.B),
        (0.U(32.W), false.B, true.B, false.B),
        (0.U(32.W), false.B, true.B, false.B)
    )

    val test_adr_i = VecInit(inputSeq.map(s => s._1))
    val test_we_i = VecInit(inputSeq.map(s => s._2))
    val test_cyc_i = VecInit(inputSeq.map(s => s._3))
    val test_stb_i = VecInit(inputSeq.map(s => s._4))

    // val outputSeq = Seq(
    //     (false.B)
    // )

    val (cntr, done) = Counter(true.B, inputSeq.size)

    cache.io.bus.slave.adr_i := test_adr_i(cntr)
    cache.io.bus.slave.we_i := test_we_i(cntr)
    cache.io.bus.slave.cyc_i := test_cyc_i(cntr)
    cache.io.bus.slave.stb_i := test_stb_i(cntr)
    cache.io.bus.master.stall_i := false.B
    cache.io.bus.master.rty_i := false.B
    cache.io.bus.master.err_i := false.B
    cache.io.bus.master.ack_i := true.B
    cache.io.bus.slave.dat_i := 0.U(32.W)
    cache.io.bus.slave.sel_i := 15.U(4.W)
    cache.io.bus.master.dat_i := 0.U(32.W)


    when(done) {
        stop()
        stop()
    }
}


class CacheTests extends org.scalatest.FlatSpec {
  "CacheTests" should "pass" in {
    assert(TesterDriver execute (() => new CacheTester(new Cache(3, 1, 8))))
  }
}