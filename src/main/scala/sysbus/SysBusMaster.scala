package systemoncat.sysbus

// import chisel3._
import chisel3._
import chisel3.core.BaseModule

class SysBusMasterBundle extends Bundle{
    val dat_i = Input(UInt(32.W))
    val dat_o = Output(UInt(32.W))
    val ack_i = Input(Bool())
    val adr_o = Output(UInt(32.W))
    val cyc_o = Output(Bool())
    val err_i = Input(Bool())
    val rty_i = Input(Bool())
    val sel_o = Output(UInt(4.W))
    val stb_o = Output(Bool())
    val we_o = Output(Bool())
    val stall_i = Input(Bool())
}

trait SysBusMaster extends BaseModule{
    val io = IO(new SysBusMasterBundle)
}