package systemoncat.core

import chisel3._
import chisel3.util._
import systemoncat.devices._
import systemoncat.sysbus._

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
}

class SysBusBundle extends Bundle {
    val res = new SysBusResponse
    val req = new SysBusRequest
}

class SysBusConnectorIO extends Bundle {
    val imem = new SysBusBundle()
    val dmem = new SysBusBundle()
}

class SysBusConnector() extends Module {
    val io = IO(new SysBusConnectorIO())

    val bus = Module(new RAMSlave())
    // val ram_slave = Module(new RAMSlave())
    // val bus_map = Seq(
    //     BitPat.dontCare(32) -> 0.U(1.W)
    // )
    // val bus_slaves = Seq(
    //     ram_slave
    // )

    // val bus = Module(new SysBusTranslator(bus_map, bus_slaves))
    // TODO: use SysBusTranslator


    val imem_en = io.imem.req.wen || io.imem.req.ren
    val dmem_en = io.dmem.req.wen || io.dmem.req.ren
    bus.io.dat_i := Mux(io.dmem.req.wen, io.dmem.req.data_wr, 0.U(32.W))
    bus.io.adr_i := Mux(dmem_en, io.dmem.req.addr, // data memory first
        Mux(imem_en, io.imem.req.addr, 0.U(32.W)))
    bus.io.stb_i := dmem_en || imem_en
    bus.io.sel_i := Mux(dmem_en, io.dmem.req.sel,
        Mux(imem_en, io.imem.req.sel, 0.U(32.W)))
    bus.io.cyc_i := true.B
    bus.io.we_i := io.dmem.req.wen

    io.dmem.res.data_rd := 0.U(32.W)
    io.imem.res.data_rd := 0.U(32.W)
    io.dmem.res.locked := false.B
    io.imem.res.locked := false.B

    when (dmem_en && io.dmem.req.ren) {
        io.dmem.res.data_rd := bus.io.dat_o
        io.imem.res.data_rd := 0.U(32.W)
        io.dmem.res.locked := false.B
        io.imem.res.locked := true.B
    }

    when (imem_en && !dmem_en) {
        io.dmem.res.data_rd := 0.U(32.W)
        io.imem.res.data_rd := bus.io.dat_o
        io.dmem.res.locked := false.B
        io.imem.res.locked := false.B
    }

// class SysBusSlaveBundle extends Bundle{
//     val dat_i = Input(UInt(32.W))
//     val dat_o = Output(UInt(32.W))
//     val ack_o = Output(Bool())
//     val adr_i = Input(UInt(32.W))
//     val cyc_i = Input(Bool())
//     val err_o = Output(Bool())
//     val rty_o = Output(Bool())
//     val sel_i = Input(UInt(4.W))
//     val stb_i = Input(Bool())
//     val we_i = Input(Bool())
// }

}
