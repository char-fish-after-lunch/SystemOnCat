package systemoncat.core

import chisel3._
import chisel3.util._

class DMemRequest extends Bundle {
    val addr = Input(UInt(32.W))
    val wr_data = Input(UInt(32.W))
    val wr_en = Input(Bool())
    val rd_en = Input(Bool())
    val lr_en = Input(Bool())
    val sc_en = Input(Bool())
    val amo_en = Input(Bool())
    val mem_type = Input(Bits(MEM_X.getWidth.W))
    val amo_op = Input(Bits(AMO_OP.AMO_X.getWidth.W))
}

class DMemExceptions extends Bundle {
    val wr_addr_invalid_expt = Output(Bool())
    val rd_addr_invalid_expt = Output(Bool())
    val wr_access_err_expt = Output(Bool())
    val rd_access_err_expt = Output(Bool())
}

class DMemResponse extends Bundle {
    val rd_data = Output(UInt(32.W))
    val expt = new DMemExceptions
    val locked = Output(Bool())
}

class DMemCoreIO extends Bundle {
    val req = new DMemRequest
    val res = new DMemResponse
}

class DMemIO extends Bundle {
    val core = new DMemCoreIO
    val bus = Flipped(new SysBusBundle)
}

object AtomicConsts {
    def LoadReservationCycles = 126.U(7.W) 
    // The static code for the LR/SC sequence plus the code to retry the sequence in case
    // of failure must comprise at most 16 integer instructions placed sequentially in memory. -- riscv-spec-v2.2, p41
    // However, TLB refill & cache refill costs taken into consideration, a much larger limit is needed.

}

import AtomicConsts._

class DMem extends Module {
    val io = IO(new DMemIO)

    // -------- LR & SC --------
    val lr_valid_counter = RegInit(UInt(), 0.U(LoadReservationCycles.getWidth.W))
    // lr_valid_counter == 0: no addr is reserved
    // else: a LR has been waiting for x cycles
    // when reserved addr is writen, this is reset to 0.

    val lr_valid = (lr_valid_counter =/= 0.U)
    val lr_reserved_addr = RegInit(UInt(), 0.U(32.W))

    when (io.core.req.lr_en) {
        lr_reserved_addr := io.core.req.addr
    }

    lr_valid_counter := Mux(io.core.req.wr_en && io.core.req.addr === lr_reserved_addr, 0.U,
        Mux(io.core.req.lr_en, 1.U,
        Mux(lr_valid_counter === LoadReservationCycles, 0.U,
        Mux(lr_valid_counter === 0.U, 0.U, lr_valid_counter + 1.U))))

    val sc_valid = io.core.req.sc_en && lr_valid && lr_reserved_addr === io.core.req.addr
    // reservation is valid, store conditional succeeded

    // -------- Sysbus --------
    val en = io.core.req.sc_en || io.core.req.rd_en || io.core.req.wr_en

    val prev_wr_data = RegInit(0.U(32.W))
    val prev_addr = RegInit(0.U(32.W))
    val prev_en = RegInit(false.B)
    val prev_wr_en = RegInit(false.B)
    val prev_rd_en = RegInit(false.B)
    val prev_sc_en = RegInit(false.B)
    val prev_sc_valid = RegInit(false.B)
    val prev_mem_type = RegInit(MEM_X)
    val prev_mask = RegInit(0.U(4.W))
    val prev_addr_err = RegInit(false.B)
    val prev_amo_en = RegInit(false.B)
    val prev_amo_op = RegInit(AMO_OP.AMO_X)

    val amo_locked = Wire(Bool())
    val cur_locked = io.bus.res.locked || amo_locked
    when (!cur_locked) {
        prev_wr_data := io.core.req.wr_data
        prev_addr := io.core.req.addr
        prev_wr_en := io.core.req.wr_en || sc_valid
        prev_rd_en := io.core.req.rd_en
        prev_sc_en := io.core.req.sc_en
        prev_en := en
        prev_sc_valid := sc_valid
        prev_mem_type := io.core.req.mem_type
        prev_amo_en := io.core.req.amo_en
        prev_amo_op := io.core.req.amo_op
    }

    val cur_wr_data = Mux(cur_locked, prev_wr_data, io.core.req.wr_data)
    val cur_addr = Mux(cur_locked, prev_addr, io.core.req.addr)
    val cur_en = Mux(cur_locked, prev_en, en)
    val cur_wr_en = Mux(cur_locked, prev_wr_en, io.core.req.wr_en || sc_valid)
    val cur_rd_en = Mux(cur_locked, prev_rd_en, io.core.req.rd_en)
    val cur_mem_type = Mux(cur_locked, prev_mem_type, io.core.req.mem_type)
    val cur_amo_en = Mux(cur_locked, prev_amo_en, io.core.req.amo_en)
    val cur_amo_op = Mux(cur_locked, prev_amo_op, io.core.req.amo_op)

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

    // ---------- AMO ----------
    val amo = Module(new AMO)
    amo.io.request := cur_amo_en
    amo.io.req.addr := cur_addr
    amo.io.req.rs2_data := cur_wr_data
    amo.io.req.amo_op := cur_amo_op
    amo.io.bus.res := io.bus.res

    amo_locked := amo.io.res.locked
    // TODO: link AMO module into DMem

    when (!cur_amo_en) {
        io.bus.req.sel := mask
        io.bus.req.wen := cur_wr_en && (!addr_err)
        io.bus.req.ren := cur_rd_en && (!addr_err)
        io.bus.req.en := cur_en && (!addr_err)
        io.bus.req.addr := cur_addr
        io.bus.req.data_wr := wr_data
    }
    .otherwise {
        io.bus.req := amo.io.bus.req
    }

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

    when (!prev_amo_en) {
        io.core.res.rd_data := Mux(prev_sc_en, Mux(prev_sc_valid, 0.U(32.W), 1.U(32.W)),
            Mux(prev_rd_en, ext_data, 0.U(32.W)))
    }
    .otherwise {
        io.core.res.rd_data := amo.io.res.data
    }

    io.core.res.locked := cur_locked
    io.core.res.expt.wr_addr_invalid_expt := (prev_wr_en || prev_amo_en) && prev_addr_err
    io.core.res.expt.wr_access_err_expt := (prev_wr_en || prev_amo_en) && (!prev_addr_err) && io.bus.res.err
    io.core.res.expt.rd_addr_invalid_expt := prev_rd_en && prev_addr_err
    io.core.res.expt.rd_access_err_expt := prev_rd_en && (!prev_addr_err) && io.bus.res.err
}