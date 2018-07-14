package systemoncat.core

import chisel3._
import chisel3.util._

class DMemCoreIO extends Bundle {
    val addr = Input(UInt(32.W))
    val wr_data = Input(UInt(32.W))
    val rd_data = Output(UInt(32.W))
    val wr_en = Input(Bool())
    val rd_en = Input(Bool())
    val mem_type = Input(Bits(MEM_X.getWidth.W))
}

class DMemIO extends Bundle {
    val core = new DMemCoreIO
    val bus = Flipped(new SysBusBundle)
}

class DMem extends Module {
    // TODO: implement me!
    val io = IO(new DMemIO)
    io.bus.req.addr := io.core.addr
    io.bus.req.data_wr := io.core.wr_data

    val mem_type_reg = Reg(Bits())
    val rd_reg = Reg(Bool())
    io.bus.req.sel := MuxLookup(io.core.mem_type, 0.U(4.W), Seq(
        MEM_B -> 1.U(4.W), // 0001
        MEM_BU -> 1.U(4.W),
        MEM_H -> 3.U(4.W), // 0011
        MEM_HU -> 3.U(4.W),
        MEM_W -> 15.U(4.W) // 1111
    ))
    io.bus.req.wen := io.core.wr_en
    io.bus.req.ren := io.core.rd_en
    mem_type_reg := io.core.mem_type
    rd_reg := io.core.rd_en


    val bus_data = io.bus.res.data_rd
    val ext_data = MuxLookup(mem_type_reg, bus_data, Seq(
        MEM_B -> Cat(Fill(24, bus_data(7)), bus_data(7, 0)),
        MEM_BU -> Cat(Fill(24, 0.U(1.W)), bus_data(7, 0)),
        MEM_H -> Cat(Fill(16, bus_data(15)), bus_data(15, 0)),
        MEM_HU -> Cat(Fill(16, 0.U(1.W)), bus_data(15, 0))
    ))
    io.core.rd_data := Mux(rd_reg, ext_data, 0.U(32.W))
}