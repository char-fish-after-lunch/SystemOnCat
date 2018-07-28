package systemoncat.core

import chisel3._
import chisel3.util._
import systemoncat.devices._
import systemoncat.sysbus._
import systemoncat.mmu._

class SysBusRequest extends Bundle {
    val addr = Input(UInt(32.W))
    val data_wr = Input(UInt(32.W))
    val sel = Input(UInt(4.W))
    val wen = Input(Bool())
    val ren = Input(Bool())
}

class SysBusResponse extends Bundle {
    val data_rd = Output(UInt(32.W))
    val locked = Output(Bool())
    val err = Output(Bool())
}

class SysBusBundle extends Bundle {
    val res = new SysBusResponse
    val req = new SysBusRequest
}

class SysBusExternal extends Bundle {
    val ram = Flipped(new SysBusSlaveBundle)
    val ram2 = Flipped(new SysBusSlaveBundle)
    val serial = Flipped(new SysBusSlaveBundle)
    val irq_client = Flipped(new SysBusSlaveBundle)
    val plic = Flipped(new SysBusSlaveBundle)
    val flash = Flipped(new SysBusSlaveBundle)
    val rom = Flipped(new SysBusSlaveBundle)
}

class SysBusConnectorIO extends Bundle {
    val imem = new SysBusBundle()
    val dmem = new SysBusBundle()
    val external = new SysBusExternal()
    val mmu_csr_info = new CSRInfo()
    val mmu_expt = new MMUException()
    val imem_pending = Output(Bool())
}

class SysBusConnector(irq_client: Client, plic: PLIC, rom: ROM) extends Module {
    val io = IO(new SysBusConnectorIO())

    // val bus = Module(new RAMSlaveReflector())
    val ram_slave = Module(new RAMSlaveReflector())
    val ram2_slave = Module(new RAMSlaveReflector())
    val serial_slave = Module(new SerialPortSlaveReflector())
    val flash_slave = Module(new FlashSlaveReflector())
    ram_slave.io.in <> io.external.ram
    ram2_slave.io.in <> io.external.ram2
    serial_slave.io.in <> io.external.serial
    flash_slave.io.in <> io.external.flash

    val bus_map = Seq(
        BitPat("b0000000000??????????????????????") -> 1.U(3.W),
        BitPat("b00000000011111111111111111111???") -> 2.U(3.W),
        BitPat("b000000000111111111111111110?????") -> 3.U(3.W),
        BitPat("b00000000011111111111111111110???") -> 4.U(3.W),
        BitPat("b0000000001111111111111111110????") -> 5.U(3.W),
        BitPat("b11111111111111111111111111??????") -> 6.U(3.W)
    )
    val bus_slaves: Seq[SysBusSlave] = Array(
        ram_slave,
        ram2_slave,
        serial_slave,
        irq_client,
        plic,
        flash_slave,
        rom
    )

    val mmu = Module(new MMUWrapper(bus_map, bus_slaves))
    mmu.io.external.ram <> ram_slave.io.out
    mmu.io.external.ram2 <> ram2_slave.io.out
    mmu.io.external.serial <> serial_slave.io.out
    mmu.io.external.irq_client <> io.external.irq_client
    mmu.io.external.plic <> io.external.plic
    mmu.io.external.flash <> flash_slave.io.out
    mmu.io.external.rom <> io.external.rom
    mmu.io.csr_info <> io.mmu_csr_info
    mmu.io.expt <> io.mmu_expt

    val imem_en = io.imem.req.wen || io.imem.req.ren
    val dmem_en = io.dmem.req.wen || io.dmem.req.ren
    mmu.io.req.addr := Mux(dmem_en, io.dmem.req.addr, // data memory first
        Mux(imem_en, io.imem.req.addr, 0.U(32.W)))
    mmu.io.req.data_wr := Mux(io.dmem.req.wen, io.dmem.req.data_wr, 0.U(32.W))
    mmu.io.req.sel := Mux(dmem_en, io.dmem.req.sel,
        Mux(imem_en, io.imem.req.sel, 0.U(32.W)))
    mmu.io.req.wen := io.dmem.req.wen
    mmu.io.req.ren := io.dmem.req.ren || (!io.dmem.req.wen && io.imem.req.ren)
    mmu.io.req.cmd := Mux(io.dmem.req.wen, MemoryConsts.Store,
        Mux(io.dmem.req.ren, MemoryConsts.Load, MemoryConsts.PC))

    val dmem_reg_en = RegInit(false.B)
    val imem_reg_en = RegInit(false.B)

    when (!mmu.io.res.locked) {
        dmem_reg_en := dmem_en
        imem_reg_en := imem_en
    }

    io.dmem.res.data_rd := 0.U(32.W)
    io.imem.res.data_rd := 0.U(32.W)
    io.dmem.res.locked := dmem_reg_en && mmu.io.res.locked

    io.imem.res.locked := !dmem_reg_en && imem_reg_en && mmu.io.res.locked
    io.imem_pending := dmem_reg_en
    io.dmem.res.err := false.B
    io.imem.res.err := false.B

    when (dmem_reg_en) {
        io.dmem.res.data_rd := mmu.io.res.data_rd
        io.imem.res.data_rd := 0.U(32.W)
        io.dmem.res.err := mmu.io.res.err
        io.imem.res.err := false.B
    }

    when (imem_reg_en && !dmem_reg_en) {
        io.dmem.res.data_rd := 0.U(32.W)
        io.imem.res.data_rd := mmu.io.res.data_rd
        io.dmem.res.err := false.B
        io.imem.res.err := mmu.io.res.err
    }

// class SysBusSlaveBundle extends Bundle{
//     val dat_i = Input(UInt(32.W))
//     val dat_o = Output(UInt(32.W))
//     val ack_o = Output(Bool())
//     val adr_i = Input(UInt(32.W))
//     val cyc_i = Input(Bool())
//     val err_o = Output(Bool())
//     val rty_o = Output(Bool())
//     val sel_i = Input(UInt(4.W))
//     val stb_i = Input(Bool())
//     val we_i = Input(Bool())
// }

}
