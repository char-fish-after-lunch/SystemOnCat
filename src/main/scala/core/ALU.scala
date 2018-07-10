package systemoncat.core

import chisel3._
import chisel3.util._

object ALU {
    def ALU_OP_X      = 0.U(4.W)
    def ALU_OP_ADD    = 0.U(4.W)
    def ALU_OP_SUB    = 1.U(4.W)
    def ALU_OP_AND    = 2.U(4.W)
    def ALU_OP_OR     = 3.U(4.W)
    def ALU_OP_XOR    = 4.U(4.W)
    def ALU_OP_SLT    = 5.U(4.W)
    def ALU_OP_SGE    = 6.U(4.W)
    def ALU_OP_SLTU   = 7.U(4.W)
    def ALU_OP_SGEU   = 8.U(4.W)
    def ALU_OP_SL     = 9.U(4.W)
    def ALU_OP_SRL    = 10.U(4.W)
    def ALU_OP_SRA    = 11.U(4.W)
    def ALU_OP_SEQ    = 12.U(4.W)
    def ALU_OP_SNE    = 13.U(4.W)
}

import ALU._

class ALUIo extends Bundle {
    val fn = Input(Bits(ALU_OP_X.getWidth.W))
    val in1 = Input(SInt(32.W))
    val in2 = Input(SInt(32.W))
    val out = Output(UInt(32.W))
    val cmp_out = Output(Bool())
}

class ALU extends Module {
    //  TODO: implement ALU!
    val io = IO(new ALUIo)
    io.cmp_out := false.B
    io.out := (io.in1 + io.in2).asUInt
}