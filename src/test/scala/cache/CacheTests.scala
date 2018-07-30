package systemoncat.cache

import chisel3._
import chisel3.util._
import chisel3.testers._

class CacheTester(c: => Cache) extends BasicTester {
    val cache = Module(c)

    // adr_i, we_i, cyc_i, stb_i
    val inputSeq = Seq(
        (0x0.U(32.W), false.B, true.B, true.B),
        (0x0.U(32.W), false.B, true.B, true.B),
        (0x0.U(32.W), false.B, true.B, true.B),
        (0x0.U(32.W), false.B, true.B, true.B),
        (0x0.U(32.W), false.B, true.B, true.B),
        (0x4.U(32.W), false.B, true.B, true.B),
        (0x4.U(32.W), false.B, true.B, true.B),
        (0x4.U(32.W), false.B, true.B, true.B),
        (0x4.U(32.W), false.B, true.B, true.B),
        (0x4.U(32.W), false.B, true.B, true.B),
        (0x1c.U(32.W), false.B, true.B, true.B),
        (0x1c.U(32.W), true.B, true.B, true.B),
        (0x1c.U(32.W), true.B, true.B, true.B),
        (0x1c.U(32.W), false.B, true.B, true.B),
        (0x8.U(32.W), false.B, true.B, true.B),
        (0x1c.U(32.W), false.B, true.B, true.B),
        (0x1c.U(32.W), false.B, true.B, true.B),
        (0x1c.U(32.W), false.B, true.B, true.B),
        (0x18.U(32.W), false.B, true.B, true.B),
        (0x18.U(32.W), false.B, true.B, true.B),
        (0x18.U(32.W), false.B, true.B, true.B),
        (0x18.U(32.W), false.B, true.B, true.B),
        (0x18.U(32.W), false.B, true.B, true.B),
        (0x18.U(32.W), false.B, true.B, true.B)
    )

    val test_adr_i = VecInit(inputSeq.map(s => s._1))
    val test_we_i = VecInit(inputSeq.map(s => s._2))
    val test_cyc_i = VecInit(inputSeq.map(s => s._3))
    val test_stb_i = VecInit(inputSeq.map(s => s._4))

    // val outputSeq = Seq(
    //     (false.B)
    // )

    val (cntr, done) = Counter(true.B, inputSeq.size)

    val last_adr = RegInit(UInt(32.W), 0.U)
    val last_we = RegInit(Bool(), false.B)

    cache.io.bus.slave.adr_i := test_adr_i(cntr)
    cache.io.bus.slave.we_i := test_we_i(cntr)
    cache.io.bus.slave.cyc_i := test_cyc_i(cntr)
    cache.io.bus.slave.stb_i := test_stb_i(cntr)
    cache.io.bus.master.stall_o := false.B
    cache.io.bus.master.rty_o := false.B
    cache.io.bus.master.err_o := false.B
    cache.io.bus.master.ack_o := true.B
    cache.io.bus.slave.dat_i := cache.io.bus.master.adr_i + 2.U
    cache.io.bus.slave.sel_i := "b0010".U(4.W)
    cache.io.bus.master.dat_o := cache.io.bus.master.adr_i + 1.U
    last_adr := cache.io.bus.master.adr_i
    last_we := cache.io.bus.master.we_i

    printf("dat_o = %d, dat_i = %d, ack = %d, stall = %d, adr = %x, we = %d, dat = %d, stb = %d, sel = %d\n", cache.io.bus.slave.dat_o, 
        cache.io.bus.slave.dat_i,
        cache.io.bus.slave.ack_o, 
        cache.io.bus.slave.stall_o, cache.io.bus.master.adr_i, cache.io.bus.master.we_i, cache.io.bus.master.dat_i,
        cache.io.bus.master.stb_i, cache.io.bus.master.sel_i)

    when(done) {
        stop()
        stop()
    }
}


class CacheTests extends org.scalatest.FlatSpec {
  "CacheTests" should "pass" in {
    assert(TesterDriver execute (() => 
        new CacheTester(new Cache(3, 1, 1, Seq()))))
  }
}