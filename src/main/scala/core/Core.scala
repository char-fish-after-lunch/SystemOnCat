package systemoncat.core

import chisel3._
import chisel3.util._
import systemoncat.sysbus._
import systemoncat.devices.PLICInterface

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
    val ram = Flipped(new SysBusSlaveBundle)
    val serial = Flipped(new SysBusSlaveBundle)
    val plic_interface = Flipped(new PLICInterface)
}

class Core() extends Module {
    val io = IO(new CoreIO)
    val dpath = Module(new Datapath) 
    val ctrl  = Module(new Control)
    val ifetch = Module(new IFetch)
    val dmem = Module(new DMem)
    val irq_client = Module(new Client)
    val plic = Module(new PLIC)
    val bus_conn = Module(new SysBusConnector(irq_client, plic))

    bus_conn.io.external.ram <> io.ram
    bus_conn.io.external.serial <> io.serial
    bus_conn.io.external.irq_client <> irq_client.io.out
    bus_conn.io.external.plic <> plic.io.out

    dpath.io.ctrl <> ctrl.io
    dpath.io.debug_devs <> io.devs
    dpath.io.imem <> ifetch.io.core
    dpath.io.dmem <> dmem.io.core
    dpath.io.irq_client <> irq_client.io.in

    ifetch.io.bus <> bus_conn.io.imem
    dmem.io.bus <> bus_conn.io.dmem

    // temporarily, no such devices

    val bridge = Wire(new PLICIO)
    bridge <> plic.io.in
    io.plic_interface <> bridge.external
    bridge.core1_ext_irq_r <> dpath.io.core1_ext_irq_r
}
