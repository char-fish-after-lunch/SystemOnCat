package systemoncat.core

import chisel3._
import chisel3.util._
import systemoncat.sysbus._

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
    val ifetch = Module(new IFetch)
    val dmem = Module(new DMem)
    val bus_conn = Module(new SysBusConnector)

    dpath.io.ctrl <> ctrl.io
    dpath.io.debug_devs <> io.devs
    dpath.io.imem <> ifetch.io.core
    dpath.io.dmem <> dmem.io.core

    ifetch.io.bus <> bus_conn.io.imem
    dmem.io.bus <> bus_conn.io.dmem
}
