package systemoncat.core

import chisel3._
import chisel3.util._

class IFetchIO extends Bundle {
    val pc = Input(UInt(32.W))
    val inst = Output(UInt(32.W))
}

class IFetch extends Module {
    val io = IO(new IFetchIO)
    io.inst := NOP
}