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
    val en = Input(Bool())
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
    val imem_pending = Output(Bool())
    val req = Output(new MMURequest)
    val res = Flipped(new MMUResponse)
}

class SysBusConnector extends Module {
    val io = IO(new SysBusConnectorIO())

    val imem_en = io.imem.req.en
    val dmem_en = io.dmem.req.en

    io.req.addr := Mux(dmem_en, io.dmem.req.addr, // data memory first
        Mux(imem_en, io.imem.req.addr, 0.U(32.W)))
    io.req.data_wr := Mux(io.dmem.req.wen, io.dmem.req.data_wr, 0.U(32.W))
    io.req.sel := Mux(dmem_en, io.dmem.req.sel,
        Mux(imem_en, io.imem.req.sel, 0.U(32.W)))
    io.req.wen := io.dmem.req.wen
    io.req.ren := io.dmem.req.ren || (!io.dmem.req.en && io.imem.req.ren)
    io.req.cmd := Mux(io.dmem.req.wen, MemoryConsts.Store,
        Mux(io.dmem.req.ren, MemoryConsts.Load, MemoryConsts.PC))

    val dmem_reg_en = RegInit(false.B)
    val imem_reg_en = RegInit(false.B)
    val dmem_reg_wen = RegInit(false.B)
    val dmem_reg_ren = RegInit(false.B)

    when (!io.res.locked) {
        dmem_reg_en := dmem_en
        imem_reg_en := imem_en
        dmem_reg_wen := io.dmem.req.wen
        dmem_reg_ren := io.dmem.req.ren
    }

    io.dmem.res.data_rd := 0.U(32.W)
    io.imem.res.data_rd := 0.U(32.W)
    io.dmem.res.locked := (dmem_reg_wen || dmem_reg_ren) && io.res.locked

    io.imem.res.locked := !dmem_reg_en && imem_reg_en && io.res.locked
    io.imem_pending := dmem_reg_en
    io.dmem.res.err := false.B
    io.imem.res.err := false.B

    when (dmem_reg_en) {
        io.dmem.res.data_rd := io.res.data_rd
        io.imem.res.data_rd := 0.U(32.W)
        io.dmem.res.err := io.res.err
        io.imem.res.err := false.B
    }

    when (imem_reg_en && !dmem_reg_en) {
        io.dmem.res.data_rd := 0.U(32.W)
        io.imem.res.data_rd := io.res.data_rd
        io.dmem.res.err := false.B
        io.imem.res.err := io.res.err
    }
}
