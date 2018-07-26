package systemoncat

import chisel3._
import chisel3.util._
import systemoncat.core._

class SystemOnCat extends Module {
    val io = IO(new CoreIO)
    val core = Module(new Core())
    core.io.devs <> io.devs
    core.io.ram <> io.ram
    core.io.ram2 <> io.ram2
    core.io.serial <> io.serial
    core.io.plic_interface <> io.plic_interface
    core.io.flash <> io.flash
}
