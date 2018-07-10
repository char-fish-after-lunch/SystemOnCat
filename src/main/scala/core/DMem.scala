package systemoncat.core

import chisel3._
import chisel3.util._

class DMemIO extends Bundle {
    val addr = Input(UInt(32.W))
    val wr_data = Input(UInt(32.W))
    val rd_data = Output(UInt(32.W))
    val wr_en = Input(Bool())
    val rd_en = Input(Bool())
    val mem_type = Input(Bits(MEM_X.getWidth.W))
}

class DMem extends Module {
    // TODO: implement me!
    val io = IO(new DMemIO)
    io.rd_data := 0.U(32.W)
}