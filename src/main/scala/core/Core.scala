package systemoncat.core

import chisel3._
import chisel3.util._
import systemoncat.sysbus._
import systemoncat.devices.ROM
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
    val ram2 = Flipped(new SysBusSlaveBundle)
    val serial = Flipped(new SysBusSlaveBundle)
    val flash = Flipped(new SysBusSlaveBundle)
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
    val rom = Module(new ROM("prog/firmware/mastercat.bin"))
    val bus_conn = Module(new SysBusConnector(irq_client, plic, rom))

    bus_conn.io.external.ram <> io.ram
    bus_conn.io.external.ram2 <> io.ram2
    bus_conn.io.external.serial <> io.serial
    bus_conn.io.external.irq_client <> irq_client.io.out
    bus_conn.io.external.plic <> plic.io.out
    bus_conn.io.external.flash <> io.flash
    bus_conn.io.external.rom <> rom.io.out

    bus_conn.io.mmu_csr_info <> dpath.io.mmu_csr_info
    bus_conn.io.mmu_expt <> dpath.io.mmu_expt

    dpath.io.ctrl <> ctrl.io
    dpath.io.debug_devs <> io.devs
    dpath.io.imem <> ifetch.io.core
    dpath.io.dmem <> dmem.io.core
    dpath.io.irq_client <> irq_client.io.in

    ifetch.io.bus <> bus_conn.io.imem
    ifetch.io.pending <> bus_conn.io.imem_pending
    dmem.io.bus <> bus_conn.io.dmem

    // temporarily, no such devices

    val bridge = Wire(new PLICIO)
    bridge <> plic.io.in
    io.plic_interface <> bridge.external
    bridge.core1_ext_irq_r <> dpath.io.core1_ext_irq_r
}
