package systemoncat.core

import chisel3._
import chisel3.util._

class IFetchCoreIO extends Bundle {
    val pc = Input(UInt(32.W))
    val inst = Output(UInt(32.W))
    val locked = Output(Bool())
}

class IFetchIO extends Bundle {
    val core = new IFetchCoreIO
    val bus = Flipped(new SysBusBundle)
}

class IFetch extends Module {
    val io = IO(new IFetchIO)
    io.bus.req.addr := io.core.pc
    io.bus.req.data_wr := 0.U(32.W)
    io.bus.req.sel := 15.U(4.W)
    io.bus.req.wen := false.B
    io.bus.req.ren := true.B
    io.core.inst := io.bus.res.data_rd
    io.core.locked := io.bus.res.locked
}