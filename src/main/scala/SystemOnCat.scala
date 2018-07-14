package systemoncat

import chisel3._
import chisel3.util._
import systemoncat.core._

class SystemOnCat extends Module {
    val io = IO(new CoreIO)
    val core = Module(new Core())
    core.io.devs <> io.devs
    core.io.ram <> io.ram
    core.io.serial <> io.serial
}
