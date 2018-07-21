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
    val wr_addr_invalid_expt = Output(Bool())
    val rd_addr_invalid_expt = Output(Bool())
    val wr_access_err_expt = Output(Bool())
    val rd_access_err_expt = Output(Bool())
    val locked = Output(Bool())
}

class DMemIO extends Bundle {
    val core = new DMemCoreIO
    val bus = Flipped(new SysBusBundle)
}

class DMem extends Module {
    // TODO: implement me!
    val io = IO(new DMemIO)

    val prev_wr_data = RegInit(0.U(32.W))
    val prev_addr = RegInit(0.U(32.W))
    val prev_wr_en = RegInit(false.B)
    val prev_rd_en = RegInit(false.B)
    val prev_mem_type = RegInit(MEM_X)
    val prev_mask = RegInit(0.U(4.W))
    val prev_addr_err = RegInit(false.B)

    when (!io.bus.res.locked) {
        prev_wr_data := io.core.wr_data
        prev_addr := io.core.addr
        prev_wr_en := io.core.wr_en
        prev_rd_en := io.core.rd_en
        prev_mem_type := io.core.mem_type
    }

    val cur_wr_data = Mux(io.bus.res.locked, prev_wr_data, io.core.wr_data)
    val cur_addr = Mux(io.bus.res.locked, prev_addr, io.core.addr)
    val cur_wr_en = Mux(io.bus.res.locked, prev_wr_en, io.core.wr_en)
    val cur_rd_en = Mux(io.bus.res.locked, prev_rd_en, io.core.rd_en)
    val cur_mem_type = Mux(io.bus.res.locked, prev_mem_type, io.core.mem_type)

    val byte_masks = Seq(
        0.U(4.W) -> "b0001".U(4.W),
        1.U(4.W) -> "b0010".U(4.W),
        2.U(4.W) -> "b0100".U(4.W),
        3.U(4.W) -> "b1000".U(4.W),
    )
    val hword_masks = Seq(
        0.U(4.W) -> "b0011".U(4.W),
        2.U(4.W) -> "b1100".U(4.W),
    )

    val byte_wr_datas = Seq(
        0.U(4.W) -> Cat(0.U(24.W), cur_wr_data(7, 0)),
        1.U(4.W) -> Cat(0.U(16.W), cur_wr_data(7, 0), 0.U(8.W)),
        2.U(4.W) -> Cat(0.U(8.W), cur_wr_data(7, 0), 0.U(16.W)),
        3.U(4.W) -> Cat(cur_wr_data(7, 0), 0.U(24.W))
    )
    val hword_wr_datas = Seq(
        0.U(4.W) -> Cat(0.U(16.W), cur_wr_data(15, 0)),
        2.U(4.W) -> Cat(cur_wr_data(15, 0), 0.U(16.W))
    )

    val mask = MuxLookup(cur_mem_type, 0.U(4.W), Seq(
        MEM_B -> MuxLookup(cur_addr(1, 0), 0.U(4.W), byte_masks),
        MEM_BU -> MuxLookup(cur_addr(1, 0), 0.U(4.W), byte_masks),
        MEM_H -> MuxLookup(cur_addr(1, 0), 0.U(4.W), hword_masks),
        MEM_HU -> MuxLookup(cur_addr(1, 0), 0.U(4.W), hword_masks),
        MEM_W -> 15.U(4.W) // 1111
    ))
    prev_mask := mask

    val wr_data = MuxLookup(cur_mem_type, 0.U(4.W), Seq(
        MEM_B -> MuxLookup(cur_addr(1, 0), 0.U(4.W), byte_wr_datas),
        MEM_BU -> MuxLookup(cur_addr(1, 0), 0.U(4.W), byte_wr_datas),
        MEM_H -> MuxLookup(cur_addr(1, 0), 0.U(4.W), hword_wr_datas),
        MEM_HU -> MuxLookup(cur_addr(1, 0), 0.U(4.W), hword_wr_datas),
        MEM_W -> cur_wr_data // 1111
    ))

    val addr_err = MuxLookup(cur_mem_type, false.B, Seq(
        MEM_H -> (cur_addr(0) =/= 0.U(1.W)), // half word r/w: must be 2-aligned
        MEM_HU -> (cur_addr(0) =/= 0.U(1.W)),
        MEM_W -> (cur_addr(1, 0) =/= 0.U(2.W)) // full word r/w: must be 4-aligned
    ))
    prev_addr_err := addr_err

    io.bus.req.sel := mask
    io.bus.req.wen := cur_wr_en && (!addr_err)
    io.bus.req.ren := cur_rd_en && (!addr_err)
    io.bus.req.addr := cur_addr
    io.bus.req.data_wr := wr_data

    val bus_data = io.bus.res.data_rd
    val byte_data = MuxLookup(prev_mask, 0.U(8.W), Seq(
        "b0001".U -> bus_data(7, 0),
        "b0010".U -> bus_data(15, 8),
        "b0100".U -> bus_data(23, 16),
        "b1000".U -> bus_data(31, 24)
    ))
    val hword_data = MuxLookup(prev_mask, 0.U(16.W), Seq(
        "b0011".U -> bus_data(15, 0),
        "b1100".U -> bus_data(31, 16)
    ))

    val ext_data = MuxLookup(prev_mem_type, bus_data, Seq(
        MEM_B -> Cat(Fill(24, byte_data(7)), byte_data(7, 0)),
        MEM_BU -> Cat(Fill(24, 0.U(1.W)), byte_data(7, 0)),
        MEM_H -> Cat(Fill(16, hword_data(15)), hword_data(15, 0)),
        MEM_HU -> Cat(Fill(16, 0.U(1.W)), hword_data(15, 0))
    ))
    io.core.rd_data := Mux(prev_rd_en, ext_data, 0.U(32.W))
    io.core.locked := io.bus.res.locked
    io.core.wr_addr_invalid_expt := prev_wr_en && prev_addr_err
    io.core.wr_access_err_expt := prev_wr_en && (!prev_addr_err) && io.bus.res.err
    io.core.rd_addr_invalid_expt := prev_rd_en && prev_addr_err
    io.core.rd_access_err_expt := prev_rd_en && (!prev_addr_err) && io.bus.res.err
}