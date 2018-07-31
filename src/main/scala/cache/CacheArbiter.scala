package systemoncat.cache


import chisel3._
import chisel3.core.Module
import chisel3.util._

class CacheArbiterBundle extends Bundle{
    val cache1_turn = Output(Bool())
    val cache2_turn = Output(Bool())
}

class CacheArbiter extends Module{
    val io = IO(new CacheArbiterBundle)

    val token = RegInit(Bool(), true.B)
    token := !token
    
    io.cache1_turn := token
    io.cache2_turn := !token
}
