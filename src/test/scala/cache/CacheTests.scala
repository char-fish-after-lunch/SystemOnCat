package systemoncat.cache

import chisel3._
import chisel3.util._
import chisel3.testers._

class CacheTester(c1 : => Cache, c2 : => Cache, ar : => CacheArbiter) extends BasicTester {
    val cache_1 = Module(c1)
    val cache_2 = Module(c2)
    val arbiter = Module(ar)

    val inputSeq_1 = Seq(
        (0x0.U(32.W), true.B, true.B, true.B),
        (0x0.U(32.W), false.B, true.B, true.B),
        (0x0.U(32.W), false.B, true.B, true.B),
        (0x0.U(32.W), false.B, true.B, true.B),
        (0x4.U(32.W), false.B, true.B, true.B),
        (0x0.U(32.W), false.B, true.B, true.B),
        (0x4.U(32.W), false.B, true.B, true.B),
        (0x10.U(32.W), false.B, true.B, true.B),
        (0x10.U(32.W), false.B, true.B, true.B),
        (0x10.U(32.W), false.B, true.B, true.B),
        (0x4.U(32.W), false.B, true.B, true.B),
        (0x0.U(32.W), false.B, true.B, true.B),
        (0x1c.U(32.W), false.B, true.B, true.B),
        (0x1c.U(32.W), true.B, true.B, true.B),
        (0x1c.U(32.W), true.B, true.B, true.B),
        (0x1c.U(32.W), false.B, true.B, true.B),
        (0x8.U(32.W), false.B, true.B, true.B),
        (0x1c.U(32.W), false.B, true.B, true.B),
        (0x1c.U(32.W), false.B, true.B, true.B),
        (0x1c.U(32.W), false.B, true.B, true.B),
        (0x18.U(32.W), true.B, true.B, true.B),
        (0x18.U(32.W), true.B, true.B, true.B),
        (0x18.U(32.W), false.B, true.B, true.B),
        (0x18.U(32.W), false.B, true.B, true.B),
        (0x18.U(32.W), true.B, true.B, true.B),
        (0x18.U(32.W), false.B, true.B, true.B)
    )

    // adr_i, we_i, cyc_i, stb_i
    val inputSeq_2 = Seq(
        (0x0.U(32.W), false.B, true.B, true.B),
        (0x0.U(32.W), false.B, true.B, true.B),
        (0x0.U(32.W), false.B, true.B, true.B),
        (0x0.U(32.W), false.B, true.B, true.B),
        (0x0.U(32.W), false.B, true.B, true.B),
        (0x0.U(32.W), false.B, true.B, true.B),
        (0x0.U(32.W), false.B, true.B, true.B),
        (0x0.U(32.W), false.B, true.B, true.B),
        (0x0.U(32.W), true.B, true.B, true.B),
        (0x0.U(32.W), false.B, true.B, true.B),
        (0x0.U(32.W), false.B, true.B, true.B),
        (0x18.U(32.W), false.B, true.B, true.B),
        (0x18.U(32.W), false.B, true.B, true.B),
        (0x18.U(32.W), false.B, true.B, true.B),
        (0x4.U(32.W), false.B, true.B, true.B),
        (0x1c.U(32.W), false.B, true.B, true.B),
        (0x1c.U(32.W), true.B, true.B, true.B),
        (0x1c.U(32.W), true.B, true.B, true.B),
        (0x1c.U(32.W), false.B, true.B, true.B),
        (0x8.U(32.W), false.B, true.B, true.B),
        (0x1c.U(32.W), false.B, true.B, true.B),
        (0x1c.U(32.W), false.B, true.B, true.B),
        (0x1c.U(32.W), false.B, true.B, true.B),
        (0x18.U(32.W), true.B, true.B, true.B),
        (0x18.U(32.W), true.B, true.B, true.B),
        (0x18.U(32.W), false.B, true.B, true.B)
    )

    val test_adr_i_1 = VecInit(inputSeq_1.map(s => s._1))
    val test_we_i_1 = VecInit(inputSeq_1.map(s => s._2))
    val test_cyc_i_1 = VecInit(inputSeq_1.map(s => s._3))
    val test_stb_i_1 = VecInit(inputSeq_1.map(s => s._4))

    val test_adr_i_2 = VecInit(inputSeq_2.map(s => s._1))
    val test_we_i_2 = VecInit(inputSeq_2.map(s => s._2))
    val test_cyc_i_2 = VecInit(inputSeq_2.map(s => s._3))
    val test_stb_i_2 = VecInit(inputSeq_2.map(s => s._4))

    // val outputSeq = Seq(
    //     (false.B)
    // )

    val (cntr, done) = Counter(true.B, inputSeq_1.size)

    val last_adr = RegInit(UInt(32.W), 0.U)
    val last_we = RegInit(Bool(), false.B)


    // cache_1.io.snooper.broadcast_adr := 0.U
    // cache_1.io.snooper.broadcast_dat := 0.U
    // cache_1.io.snooper.broadcast_sel := 0.U
    // cache_1.io.snooper.broadcast_type := CacheCoherence.BR_NO_MSG
    // cache_1.io.broadcaster.response_type := CacheCoherence.RE_NO_MSG
    // cache_1.io.broadcaster.response_dat := 0.U
    cache_1.io.my_turn := arbiter.io.cache1_turn
    cache_2.io.my_turn := arbiter.io.cache2_turn
    arbiter.io.cache1_response := cache_1.io.snooper.response_type
    arbiter.io.cache2_response := cache_2.io.snooper.response_type

    cache_1.io.snooper <> cache_2.io.broadcaster
    cache_2.io.snooper <> cache_1.io.broadcaster

    val last_adr_1 = RegInit(UInt(32.W), 0.U)
    val last_adr_2 = RegInit(UInt(32.W), 0.U)

    last_adr_1 := cache_1.io.bus.master.adr_i
    last_adr_2 := cache_2.io.bus.master.adr_i

    cache_1.io.bus.slave.adr_i := test_adr_i_1(cntr)
    cache_1.io.bus.slave.we_i := test_we_i_1(cntr)
    cache_1.io.bus.slave.cyc_i := test_cyc_i_1(cntr)
    cache_1.io.bus.slave.stb_i := test_stb_i_1(cntr)
    cache_1.io.bus.master.stall_o := false.B
    cache_1.io.bus.master.rty_o := false.B
    cache_1.io.bus.master.err_o := false.B
    cache_1.io.bus.master.ack_o := true.B
    cache_1.io.bus.slave.dat_i := cache_1.io.bus.master.adr_i + 2.U
    cache_1.io.bus.slave.sel_i := "b1111".U(4.W)
    cache_1.io.bus.master.dat_o := last_adr_1 + 1.U

    cache_2.io.bus.slave.adr_i := test_adr_i_2(cntr)
    cache_2.io.bus.slave.we_i := test_we_i_2(cntr)
    cache_2.io.bus.slave.cyc_i := test_cyc_i_2(cntr)
    cache_2.io.bus.slave.stb_i := test_stb_i_2(cntr)
    cache_2.io.bus.master.stall_o := false.B
    cache_2.io.bus.master.rty_o := false.B
    cache_2.io.bus.master.err_o := false.B
    cache_2.io.bus.master.ack_o := true.B
    cache_2.io.bus.slave.dat_i := cache_2.io.bus.master.adr_i + 2.U
    cache_2.io.bus.slave.sel_i := "b0011".U(4.W)
    cache_2.io.bus.master.dat_o := last_adr_2 + 1.U

    // last_adr := cache.io.bus.master.adr_i
    // last_we := cache.io.bus.master.we_i

    printf("ADR1 = %x, WE1 = %d, DATA1 = %d, IDATA1 = %d, ADR2 = %x, WE2 = %d, DATA2 = %d, IDATA2 = %d\n", 
        cache_1.io.bus.slave.adr_i,
        cache_1.io.bus.slave.we_i,
        cache_1.io.bus.slave.dat_i,
        cache_1.io.bus.master.dat_o,
        cache_2.io.bus.slave.adr_i,
        cache_2.io.bus.slave.we_i,
        cache_2.io.bus.slave.dat_i,
        cache_2.io.bus.master.dat_o
        )
    printf("Cache 1: dat_o = %d, ack = %d, adr = %x, we = %d, dat = %d, stb = %d, sel = %d, broadcast = %d, response = %d, ba = %x, rd = %x\n", cache_1.io.bus.slave.dat_o, 
        cache_1.io.bus.slave.ack_o, cache_1.io.bus.master.adr_i,
        cache_1.io.bus.master.we_i, cache_1.io.bus.master.dat_i,
        cache_1.io.bus.master.stb_i, cache_1.io.bus.master.sel_i, cache_1.io.broadcaster.broadcast_type, cache_1.io.snooper.response_type,
        cache_1.io.broadcaster.broadcast_adr, cache_1.io.snooper.response_dat)

    printf("Cache 2: dat_o = %d, ack = %d, adr = %x, we = %d, dat = %d, stb = %d, sel = %d, broadcast = %d, response = %d, ba = %x, rd = %x\n", cache_2.io.bus.slave.dat_o, 
        cache_2.io.bus.slave.ack_o, cache_2.io.bus.master.adr_i,
        cache_2.io.bus.master.we_i, cache_2.io.bus.master.dat_i,
        cache_2.io.bus.master.stb_i, cache_2.io.bus.master.sel_i, cache_2.io.broadcaster.broadcast_type, cache_2.io.snooper.response_type,
        cache_2.io.broadcaster.broadcast_adr, cache_2.io.snooper.response_dat)
    when(done) {
        stop()
        stop()
    }
}

class CacheTests extends org.scalatest.FlatSpec {
  "CacheTests" should "pass" in {
    assert(TesterDriver execute (() => 
        new CacheTester(new Cache(3, 1, 1, Seq()),
            new Cache(3, 1, 1, Seq()),
            new CacheArbiter)))
  }
}