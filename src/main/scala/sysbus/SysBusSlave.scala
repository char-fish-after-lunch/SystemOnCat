package systemoncat.sysbus

import chisel3._
import chisel3.core.BaseModule

class SysBusSlaveBundle extends Bundle {
    val dat_i = Input(UInt(32.W))
    val dat_o = Output(UInt(32.W))
    val ack_o = Output(Bool())
    val adr_i = Input(UInt(32.W))
    val cyc_i = Input(Bool())
    val err_o = Output(Bool())
    val rty_o = Output(Bool())
    val sel_i = Input(UInt(4.W))
    val stb_i = Input(Bool())
    val we_i = Input(Bool())
    val stall_o = Output(Bool())
}

class SysBusSlaveIO extends Bundle {
    val out = new SysBusSlaveBundle
    val in = Flipped(new SysBusSlaveBundle)
}

trait SysBusSlave extends BaseModule {
    val io = IO(new SysBusSlaveIO)
}

