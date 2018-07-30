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
    val addr = UInt(32.W)
    val rs2_data = UInt(32.W)
    val amo_op = UInt(AMO_X.getWidth.W)
}

class AMOResponse extends Bundle {
    val locked = Bool()
    val data = UInt(32.W)
}

class AMOIO extends Bundle {
    val request = Input(Bool())
    val req = Input(new AMORequest)
    val res = Output(new AMOResponse)
    val bus = Flipped(new SysBusBundle)
}

class AMO extends Module {
    val io = IO(new AMOIO)
    val amoalu = Module(new AMOALU)

    val sIDLE :: sLOAD :: sLOAD_WAIT :: sSTORE :: sSTORE_WAIT :: Nil = Enum(5)
    // sIDLE: All operation finished, ready for next request.
    // sLOAD: Sending a load request to sysbus (our sysbus requires data before a rising clk)
    // sLOAD_WAIT: Waiting for load request. 
    // sSTORE: When the data is loaded, send it to AMOALU & send a store request to sysbus.
    // sSTORE_WAIT: Waiting for the store request.

    val state = RegInit(sIDLE)
    val locked = !(state === sIDLE)

    val cur_request = RegInit(0.U.asTypeOf(new AMORequest()))
    val cur_loaded_data = RegInit(UInt(), 0.U(32.W))

    io.bus.req.addr := cur_request.addr
    io.bus.req.data_wr := amoalu.io.out
    io.bus.req.sel := 15.U(4.W)
    io.bus.req.ren := state === sLOAD
    io.bus.req.wen := state === sSTORE
    io.bus.req.en := locked || io.request

    amoalu.io.cmd := cur_request.amo_op
    amoalu.io.op1 := cur_loaded_data
    amoalu.io.op2 := cur_request.rs2_data
    // amoalu is fully combinatoral

    switch (state) {
        is(sIDLE) {
            when (io.request) {
                state := sLOAD
                cur_request := io.req
            }
        }
        is(sLOAD) {
            state := sLOAD_WAIT
        }
        is(sLOAD_WAIT) {
            when (io.bus.res.err) {
                state := sIDLE
            }
            .elsewhen (!io.bus.res.locked) {
                cur_loaded_data := io.bus.res.data_rd
                state := sSTORE
            }
        }
        is(sSTORE) {
            state := sSTORE_WAIT
        }
        is(sSTORE_WAIT) {
            when (io.bus.res.err) {
                state := sIDLE
            }
            .elsewhen (!io.bus.res.locked) {
                state := sIDLE
            }
        }
    }
    io.res.locked := locked
    io.res.data := cur_loaded_data
    printf("----------------------------- state: %d\n", state)
    printf("[request]  addr: %x, data_wr: %x, ren: %d, wen: %d, en: %d\n", io.bus.req.addr, io.bus.req.data_wr, io.bus.req.ren, io.bus.req.wen, io.bus.req.en)
    printf("[response] locked: %d, data_rd: %x\n", io.bus.res.locked, io.bus.res.data_rd)
    printf("[input]    request: %d, addr: %x, rs2_data: %x\n", io.request, io.req.addr, io.req.rs2_data)
    printf("[output]   locked: %d, data: %x\n", io.res.locked, io.res.data)
}

class AMOALUIO extends Bundle {
    val cmd = Input(UInt(AMO_X.getWidth.W))
    val op1 = Input(UInt(32.W)) // (rs1)
    val op2 = Input(UInt(32.W)) // rs2
    val out = Output(UInt(32.W))
}

class AMOALU extends Module {
    val io = IO(new AMOALUIO)

    val op1_lt_op2 = io.op1.asUInt < io.op2.asUInt
    val op1_ltu_op2 = io.op1.asSInt < io.op2.asSInt

    io.out := MuxLookup(io.cmd, 0.U(32.W), Seq(
        AMO_SWAP -> (io.op2),
        AMO_ADD  -> (io.op1 + io.op2),
        AMO_AND  -> (io.op1 & io.op2),
        AMO_OR   -> (io.op1 | io.op2),
        AMO_XOR  -> (io.op1 ^ io.op2),
        AMO_MAX  -> Mux(op1_lt_op2, io.op2, io.op1),
        AMO_MIN  -> Mux(op1_lt_op2, io.op1, io.op2),
        AMO_MAXU -> Mux(op1_ltu_op2, io.op2, io.op1),
        AMO_MINU -> Mux(op1_ltu_op2, io.op1, io.op2),
    ))
}