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

    val mem_type_reg = RegInit(MEM_X)
    val rd_reg = RegInit(false.B)
    val mask_reg = RegInit("b0000".U(4.W))

    val byte_masks = Seq(
        0.U(4.W) -> "b0001".U(4.W),
        1.U(4.W) -> "b0010".U(4.W),
        2.U(4.W) -> "b0100".U(4.W),
        3.U(4.W) -> "b1000".U(4.W),
    )
    val hword_maskts = Seq(
        0.U(4.W) -> "b0011".U(4.W),
        2.U(4.W) -> "b1100".U(4.W),
    )

    val mask = MuxLookup(io.core.mem_type, 0.U(4.W), Seq(
        MEM_B -> MuxLookup(io.core.addr(1, 0), 0.U(4.W), byte_masks),
        MEM_BU -> MuxLookup(io.core.addr(1, 0), 0.U(4.W), byte_masks),
        MEM_H -> MuxLookup(io.core.addr(1, 0), 0.U(4.W), hword_maskts),
        MEM_HU -> MuxLookup(io.core.addr(1, 0), 0.U(4.W), hword_maskts),
        MEM_W -> 15.U(4.W) // 1111
    ))

    io.bus.req.sel := mask
    io.bus.req.wen := io.core.wr_en
    io.bus.req.ren := io.core.rd_en
    mem_type_reg := io.core.mem_type
    rd_reg := io.core.rd_en
    mask_reg := mask

    val bus_data = io.bus.res.data_rd
    val byte_data = MuxLookup(mask_reg, 0.U(8.W), Seq(
        "b0001".U -> bus_data(7, 0),
        "b0010".U -> bus_data(15, 8),
        "b0100".U -> bus_data(23, 16),
        "b1000".U -> bus_data(31, 24)
    ))
    val hword_data = MuxLookup(mask_reg, 0.U(16.W), Seq(
        "b0011".U -> bus_data(15, 0),
        "b1100".U -> bus_data(31, 16)
    ))

    val ext_data = MuxLookup(mem_type_reg, bus_data, Seq(
        MEM_B -> Cat(Fill(24, byte_data(7)), byte_data(7, 0)),
        MEM_BU -> Cat(Fill(24, 0.U(1.W)), byte_data(7, 0)),
        MEM_H -> Cat(Fill(16, hword_data(15)), hword_data(15, 0)),
        MEM_HU -> Cat(Fill(16, 0.U(1.W)), hword_data(15, 0))
    ))
    io.core.rd_data := Mux(rd_reg, ext_data, 0.U(32.W))
}