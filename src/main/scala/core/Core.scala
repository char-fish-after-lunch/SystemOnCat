package systemoncat.core

import chisel3._
import chisel3.util._

class DebugDevicesIO() extends Bundle {
    // unimportant devices, e.g: leds, buttons
    val touch_btn = Input(Bits(4.W))
    val dip_sw = Input(UInt(32.W))
    val leds = Output(UInt(16.W))
    val dpy0 = Output(Bits(8.W))
    val dpy1 = Output(Bits(8.W))
}

class CoreIO() extends Bundle {
    val devs = new DebugDevicesIO
}

class Core() extends Module {
  val io = IO(new CoreIO)
  val dpath = Module(new Datapath) 
  val ctrl  = Module(new Control)
  dpath.io.ctrl <> ctrl.io

  // temporary init
  io.devs.leds := "b0000111100001111".U
  io.devs.dpy0 := 0.U(8.W)
  io.devs.dpy1 := 0.U(8.W)
}
