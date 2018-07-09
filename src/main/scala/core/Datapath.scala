package systemoncat.core

import chisel3._
import chisel3.util._

class DatapathIO() extends Bundle {
    val ctrl = Flipped(new DecoderIO)
}

class Datapath() extends Module {
    val io = IO(new DatapathIO())

    val inst_reg = RegInit(NOP)
    io.ctrl.inst := inst_reg
}