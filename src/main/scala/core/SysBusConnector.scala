package systemoncat.core

import chisel3._
import chisel3.util._
import systemoncat.devices._
import systemoncat.sysbus._
import systemoncat.mmu._

class SysBusRequest extends Bundle {
    val addr = Input(UInt(32.W))
    val data_wr = Input(UInt(32.W))
    val sel = Input(UInt(4.W))
    val wen = Input(Bool())
    val ren = Input(Bool())
}

class SysBusResponse extends Bundle {
    val data_rd = Output(UInt(32.W))
    val locked = Output(Bool())
    val err = Output(Bool())
}

class SysBusBundle extends Bundle {
    val res = new SysBusResponse
    val req = new SysBusRequest
}

class SysBusConnectorIO extends Bundle {
    val imem = new SysBusBundle()
    val dmem = new SysBusBundle()
    val mmu_csr_info = new CSRInfo()
    val mmu_expt = new MMUException()
    val imem_pending = Output(Bool())
    val bus_request = Flipped(new SysBusSlaveBundle)
}

class SysBusConnector extends Module {
    val io = IO(new SysBusConnectorIO())

    val mmu = Module(new MMUWrapper())
    mmu.io.csr_info <> io.mmu_csr_info
    mmu.io.expt <> io.mmu_expt
    mmu.io.bus_request <> io.bus_request

    val imem_en = io.imem.req.wen || io.imem.req.ren
    val dmem_en = io.dmem.req.wen || io.dmem.req.ren
    mmu.io.req.addr := Mux(dmem_en, io.dmem.req.addr, // data memory first
        Mux(imem_en, io.imem.req.addr, 0.U(32.W)))
    mmu.io.req.data_wr := Mux(io.dmem.req.wen, io.dmem.req.data_wr, 0.U(32.W))
    mmu.io.req.sel := Mux(dmem_en, io.dmem.req.sel,
        Mux(imem_en, io.imem.req.sel, 0.U(32.W)))
    mmu.io.req.wen := io.dmem.req.wen
    mmu.io.req.ren := io.dmem.req.ren || (!io.dmem.req.wen && io.imem.req.ren)
    mmu.io.req.cmd := Mux(io.dmem.req.wen, MemoryConsts.Store,
        Mux(io.dmem.req.ren, MemoryConsts.Load, MemoryConsts.PC))

    val dmem_reg_en = RegInit(false.B)
    val imem_reg_en = RegInit(false.B)

    when (!mmu.io.res.locked) {
        dmem_reg_en := dmem_en
        imem_reg_en := imem_en
    }

    io.dmem.res.data_rd := 0.U(32.W)
    io.imem.res.data_rd := 0.U(32.W)
    io.dmem.res.locked := dmem_reg_en && mmu.io.res.locked

    io.imem.res.locked := !dmem_reg_en && imem_reg_en && mmu.io.res.locked
    io.imem_pending := dmem_reg_en
    io.dmem.res.err := false.B
    io.imem.res.err := false.B

    when (dmem_reg_en) {
        io.dmem.res.data_rd := mmu.io.res.data_rd
        io.imem.res.data_rd := 0.U(32.W)
        io.dmem.res.err := mmu.io.res.err
        io.imem.res.err := false.B
    }

    when (imem_reg_en && !dmem_reg_en) {
        io.dmem.res.data_rd := 0.U(32.W)
        io.imem.res.data_rd := mmu.io.res.data_rd
        io.dmem.res.err := false.B
        io.imem.res.err := mmu.io.res.err
    }
}
