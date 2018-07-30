package systemoncat.cache

import systemoncat.sysbus.SysBusFilterBundle

import chisel3._
import chisel3.core.Module
import chisel3.util._

class CacheSnooperBundle extends Bundle {
    val gg = Input(Bool())
}

class CacheSnooper extends Module{
    val io = IO(new CacheSnooperBundle)
}
