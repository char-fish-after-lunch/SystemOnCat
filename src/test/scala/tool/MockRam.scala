package test.tool

import chisel3._
import chisel3.util._
import core_._

class RAMOp extends Bundle {
  val addr  = Output(UInt(32.W))
  val mode  = Output(UInt(4.W))   // Consts.scalaRAMMode.XX
  val wdata = Output(UInt(32.W))

  val rdata = Input(UInt(32.W))
  val ok    = Input(Bool())
}

class MockRam() extends Module {
  val io = IO(Flipped(new RAMOp))

  // With init data
  def this(mem_data: Seq[UInt]) {
    this()

    // Don't use `RegInit(VecInit(mem_data))`
    // because it's too slow
    val init_data = VecInit(mem_data)

    // Load init data at first cycle
    val uninit = RegInit(true.B)
    when(uninit) {
      uninit := false.B
      for(i <- 0 until init_data.length)
        mem(i) := init_data(i)
    }
  }

  private val mem = Mem(0x800000, UInt(8.W))

  io.ok := io.mode =/= RAMMode.NOP
  val data = Cat((0 until 4).reverse.map(i => mem(io.addr + i.U)))

  switch(io.mode) {
    is(RAMMode.SW) {
      for (i <- 0 until 4)
        mem(io.addr + i.U) := io.wdata(i * 8 + 7, i * 8)
      printf("[SimRAM] SW: [%x]=%x\n", io.addr, io.wdata)
    }
    is(RAMMode.SH) {
      mem(io.addr + 1.U) := io.wdata(15, 8)
      mem(io.addr) := io.wdata(7, 0)
      printf("[SimRAM] SH: [%x]=%x\n", io.addr, io.wdata)
    }
    is(RAMMode.SB) {
      mem(io.addr) := io.wdata(7, 0)
      printf("[SimRAM] SB: [%x]=%x\n", io.addr, io.wdata)
    }
  }

  io.rdata := 0.U
  switch(io.mode) {
    is(RAMMode.LW) {
      io.rdata := data
      printf("[SimRAM] LW: [%x]->%x\n", io.addr, io.rdata)
    }
    is(RAMMode.LH) {
      io.rdata := Cat(Mux(data(15), 0xff.U, 0.U), data(15, 0))
      printf("[SimRAM] LH: [%x]->%x\n", io.addr, io.rdata)
    }
    is(RAMMode.LB) {
      io.rdata := Cat(Mux(data(7), 0xfff.U, 0.U), data(7, 0))
      printf("[SimRAM] LB: [%x]->%x\n", io.addr, io.rdata)
    }
    is(RAMMode.LHU) {
      io.rdata := data(15, 0).zext.asUInt
      printf("[SimRAM] LHU: [%x]->%x\n", io.addr, io.rdata)
    }
    is(RAMMode.LBU) {
      io.rdata := data(7, 0).zext.asUInt
      printf("[SimRAM] LBU: [%x]->%x\n", io.addr, io.rdata)
    }
  }
}