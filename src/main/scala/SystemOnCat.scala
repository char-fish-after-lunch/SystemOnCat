package systemoncat

import chisel3._
import chisel3.util._
import systemoncat.core._
import systemoncat.sysbus._
import systemoncat.devices._

class SoCIO extends Bundle {
    val devs = new DebugDevicesIO
    val ram = Flipped(new SysBusSlaveBundle)
    val ram2 = Flipped(new SysBusSlaveBundle)
    val serial = Flipped(new SysBusSlaveBundle)
    val flash = Flipped(new SysBusSlaveBundle)
    val plic_interface = Flipped(new PLICInterface)
}

class SystemOnCat extends Module {
    val io = IO(new SoCIO)

    
    val not_to_cache = Seq(
        BitPat("b00000000011111111111111111111???") -> true.B,
        BitPat("b000000000111111111111111110?????") -> true.B,
        BitPat("b00000000011111111111111111110???") -> true.B,
        BitPat("b0000000001111111111111111110????") -> true.B,
        BitPat("b11111111111111111111111111??????") -> true.B
        // BitPat("b????????????????????????????????") -> true.B
    )


    // --------- Cores ----------
    val core1 = Module(new Core(not_to_cache))
    io.devs <> core1.io.devs

    // -------- Device ----------
    val ram_slave = Module(new RAMSlaveReflector())
    val ram2_slave = Module(new RAMSlaveReflector())
    val serial_slave = Module(new SerialPortSlaveReflector())
    val flash_slave = Module(new FlashSlaveReflector())
    val irq_client1 = Module(new Client)
    val plic = Module(new PLIC)
    val rom = Module(new ROM("prog/firmware/mastercat.bin"))

    io.ram <> ram_slave.io.in
    io.ram2 <> ram2_slave.io.in
    io.serial <> serial_slave.io.in
    io.flash <> flash_slave.io.in

    val bridge = Wire(new PLICIO)
    bridge <> plic.io.in
    io.plic_interface <> bridge.external
    bridge.core1_ext_irq_r <> core1.io.ext_irq_r

    core1.io.irq_client <> irq_client1.io.in

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
        irq_client1,
        plic,
        flash_slave,
        rom
    )

    // ------- Connector --------
    val arbiter = Module(new SysBusArbiter(1))
    arbiter.io.in(0) <> core1.io.bus_request

    val translator = Module(new SysBusTranslator(bus_map, bus_slaves))

    ram_slave.io.out <> translator.io.in(0)
    ram2_slave.io.out <> translator.io.in(1)
    serial_slave.io.out <> translator.io.in(2)
    irq_client1.io.out <> translator.io.in(3)
    plic.io.out <> translator.io.in(4)
    flash_slave.io.out <> translator.io.in(5)
    rom.io.out <> translator.io.in(6)

    arbiter.io.out <> translator.io.out

}
