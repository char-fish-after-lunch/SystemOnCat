package systemoncat.core

import chisel3._
import chisel3.util._

object ALU {
    def ALU_OP_X      = 0.U(4.W)
    def ALU_OP_ADD    = 0.U(4.W)
    def ALU_OP_AND    = 2.U(4.W)
    def ALU_OP_OR     = 3.U(4.W)
    def ALU_OP_SL     = 4.U(4.W)
    def ALU_OP_XOR    = 5.U(4.W)
    def ALU_OP_SRL    = 6.U(4.W)
    def ALU_OP_SRA    = 7.U(4.W)
    def ALU_OP_SEQ    = 8.U(4.W)
    def ALU_OP_SNE    = 9.U(4.W)
    def ALU_OP_SLT    = 10.U(4.W)
    def ALU_OP_SGE    = 11.U(4.W)
    def ALU_OP_SLTU   = 12.U(4.W)
    def ALU_OP_SGEU   = 13.U(4.W)
    def ALU_OP_SUB    = 15.U(4.W)
    def isCmp(fn: UInt) = fn(3) && (fn =/= ALU_OP_SUB)
    def isCmpUnsigned(fn: UInt) = fn(2)
    def isCmpInverted(fn: UInt) = fn(0)
    def isCmpEq(fn: UInt) = !fn(1) && !fn(2)
    def isShiftR(fn: UInt) = fn(1)
    def isSRA(fn: UInt) = fn(0)
    def needSub(fn: UInt) = fn(3)
}

import ALU._

class ALUIO extends Bundle {
    val fn = Input(Bits(ALU_OP_X.getWidth.W))
    val in1 = Input(UInt(32.W))
    val in2 = Input(UInt(32.W))
    val out = Output(UInt(32.W)) // used in arith & bitshift operation
    val cmp_out = Output(Bool()) // used in comparison, has less latency
}

class ALU extends Module {
    //  TODO: implement ALU!
    val io = IO(new ALUIO)
    io.out := (io.in1 + io.in2).asUInt

    val in2_inv = Mux(needSub(io.fn), ~io.in2, io.in2)
    val xor_result = io.in1 ^ in2_inv
    val add_result = io.in1 + in2_inv + needSub(io.fn)

    val slt =
        Mux(io.in1(31) === io.in2(31), add_result(31),
        Mux(isCmpUnsigned(io.fn), io.in2(31), io.in1(31)))
    
    io.cmp_out := isCmpInverted(io.fn) ^ Mux(isCmpEq(io.fn), xor_result === 0.U, slt)

    val shin = Mux(isShiftR(io.fn), io.in1, Reverse(io.in1.asUInt)) // if shift left, then reverse -> shift right -> reverse
    val shout_r = (Cat(isSRA(io.fn) & shin(31), shin).asSInt >> io.in2(4,0))(31,0)
    val shout_l = Reverse(shout_r)
    val shout = Mux(io.fn === ALU_OP_SRL || io.fn === ALU_OP_SRA, shout_r, 0.U) |
                Mux(io.fn === ALU_OP_SL,                     shout_l, 0.U)
    
    // AND, OR, XOR
    val logic = Mux(io.fn === ALU_OP_XOR || io.fn === ALU_OP_OR, xor_result.asUInt, 0.U) |
                Mux(io.fn === ALU_OP_OR || io.fn === ALU_OP_AND, (io.in1 & io.in2).asUInt, 0.U)
    val shift_logic = (isCmp(io.fn) && slt) | logic | shout
    val out = Mux(io.fn === ALU_OP_ADD || io.fn === ALU_OP_SUB, add_result, shift_logic)

    io.out := out
}