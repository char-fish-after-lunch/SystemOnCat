package systemoncat.core

import chisel3._
import chisel3.util._

class IFetchCoreIO extends Bundle {
    val pc = Input(UInt(32.W))
    val inst = Output(UInt(32.W))
    val locked = Output(Bool())
    val pc_invalid_expt = Output(Bool())
    val pc_err_expt = Output(Bool())
}

class IFetchIO extends Bundle {
    val core = new IFetchCoreIO
    val bus = Flipped(new SysBusBundle)
}

class IFetch extends Module {
    val io = IO(new IFetchIO)
    val pc_invalid = io.core.pc(1, 0) =/= 0.U(2.W)
    io.bus.req.addr := io.core.pc
    io.bus.req.data_wr := 0.U(32.W)
    io.bus.req.sel := 15.U(4.W)
    io.bus.req.wen := false.B
    io.bus.req.ren := !pc_invalid

    val pc_reg_invalid = RegInit(Bool(), false.B)    

    pc_reg_invalid := pc_invalid
    io.core.inst := Mux(pc_reg_invalid, NOP, io.bus.res.data_rd)
    io.core.locked := Mux(pc_reg_invalid, true.B, io.bus.res.locked)
    io.core.pc_invalid_expt := pc_reg_invalid
    io.core.pc_err_expt := Mux(pc_reg_invalid, false.B, io.bus.res.err) // if pc addr is invalid, access err is ignored
}