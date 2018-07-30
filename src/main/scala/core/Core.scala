package systemoncat.core

import chisel3._
import chisel3.util._
import systemoncat.sysbus._
import systemoncat.mmu._
import systemoncat.cache.Cache
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
    val ext_irq_r = Input(Bool())
    val irq_client = Flipped(new ClientIrqIO)
    val bus_request = Flipped(new SysBusSlaveBundle)
}

class Core(not_to_cache : Seq[(BitPat, Bool)]) extends Module {
    val io = IO(new CoreIO)
    val dpath = Module(new Datapath) 
    val ctrl  = Module(new Control)
    val ifetch = Module(new IFetch)
    val dmem = Module(new DMem)

    val bus_conn = Module(new SysBusConnector())
    val mmu = Module(new MMUWrapper())
    val cache = Module(new Cache(3, 2, 4, not_to_cache))

    mmu.io.csr_info <> dpath.io.mmu_csr_info
    mmu.io.expt <> dpath.io.mmu_expt
    bus_conn.io.req <> mmu.io.req
    bus_conn.io.res <> mmu.io.res

    dpath.io.ctrl <> ctrl.io
    dpath.io.debug_devs <> io.devs
    dpath.io.imem <> ifetch.io.core
    dpath.io.dmem <> dmem.io.core
    dpath.io.irq_client <> io.irq_client

    ifetch.io.bus <> bus_conn.io.imem
    ifetch.io.pending <> bus_conn.io.imem_pending
    dmem.io.bus <> bus_conn.io.dmem

    io.ext_irq_r <> dpath.io.ext_irq_r

    mmu.io.bus_request <> cache.io.bus.slave
    cache.io.bus.master <> io.bus_request
}
