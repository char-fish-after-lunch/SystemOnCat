package systemoncat.core

import chisel3._
import chisel3.util._

object AMO_OP {
    def AMO_X      = 0.U(4.W)
    def AMO_SWAP   = 1.U(4.W)
    def AMO_ADD    = 2.U(4.W)
    def AMO_AND    = 3.U(4.W)
    def AMO_OR     = 4.U(4.W)
    def AMO_XOR    = 5.U(4.W)
    def AMO_MAX    = 6.U(4.W)
    def AMO_MIN    = 7.U(4.W)
    def AMO_MAXU   = 8.U(4.W)
    def AMO_MINU   = 9.U(4.W)
}

import AMO_OP._

class AMORequest extends Bundle {
    val request = Bool()
    val addr = UInt(32.W)
    val rs2_data = UInt(32.W)
}

class AMOResponse extends Bundle {
    val done = Bool()
    val data = UInt(32.W)
}

class AMOBusIO extends Bundle {
    val res = Flipped(new SysBusResponse)
    val req = Flipped(new SysBusRequest)
}

class AMOIO extends Bundle {
    val req = Input(new AMORequest)
    val res = Output(new AMOResponse)
    val bus = new AMOBusIO
}

class AMO extends Module {
    val io = IO(new AMOIO)
    val amoalu = Module(new AMOALU)

    private def STATE_IDLE = 0.U
    private def STATE_LOAD = 1.U
    private def STATE_SAVE = 2.U

    val state = RegInit(UInt(2.W), STATE_IDLE)
    // val ack = 
    // TODO: implement me!
}

class AMOALUIO extends Bundle {
    val cmd = Input(UInt(AMO_X.getWidth.W))
    val op1 = Input(UInt(32.W)) // (rs1)
    val op2 = Input(UInt(32.W)) // rs2
    val out = Output(UInt(32.W))
}

class AMOALU extends Module {
    val io = IO(new AMOALUIO)

    val op1_lt_op2 = op1.asUInt < op2.asUInt
    val op1_ltu_op2 = op1.asSInt < op2.asSInt

    io.out = MuxLookup(io.cmd, 0.U(32.W), Seq(
        AMO_SWAP -> op2,
        AMO_ADD  -> op1 + op2,
        AMO_AND  -> op1 & op2,
        AMO_OR   -> op1 | op2,
        AMO_XOR  -> op1 ^ op2,
        AMO_MAX  -> Mux(op1_lt_op2, op2, op1),
        AMO_MIN  -> Mux(op1_lt_op2, op1, op2),
        AMO_MAXU -> Mux(op1_ltu_op2, op2, op1),
        AMO_MINU -> Mux(op1_ltu_op2, op1, op2),
    ))
}