package systemoncat.cache


import chisel3._
import chisel3.core.Module
import chisel3.util._

class CacheArbiter extends Module{
    val io = IO(new Bundle{
        val cache1_priv = Output(Bool())
        val cache2_priv = Output(Bool())
    })

    val token = RegInit(Bool(), true.B)
    token := !token
    
    io.cache1_priv := token
    io.cache2_priv := !token
}
