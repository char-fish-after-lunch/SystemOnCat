package systemoncat.cache


import chisel3._
import chisel3.core.Module
import chisel3.util._

class CacheArbiterBundle extends Bundle{
    val cache1_response = Input(UInt(3.W))
    val cache2_response = Input(UInt(3.W))

    val cache1_turn = Output(Bool())
    val cache2_turn = Output(Bool())
}

class CacheArbiter extends Module{
    val io = IO(new CacheArbiterBundle)

    val token = RegInit(Bool(), true.B)
    
    io.cache1_turn := token
    io.cache2_turn := !token

    when(io.cache1_response === CacheCoherence.RE_STALL){
        token := false.B
    }.elsewhen(io.cache2_response === CacheCoherence.RE_STALL){
        token := true.B
    }

    assert(io.cache1_response =/= CacheCoherence.RE_STALL ||
        io.cache2_response =/= CacheCoherence.RE_STALL)
}
