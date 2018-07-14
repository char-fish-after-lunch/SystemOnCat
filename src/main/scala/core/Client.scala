package systemoncat.core

import systemoncat.sysbus.SysBusSlaveBundle

import chisel3._
import chisel3.util._
import chisel3.testers._


object CliAddr{
	val msip_addr = "h2000000".U(32.W)
	val cmph_addr = "h2004004".U(32.W)
	val cmpl_addr = "h2004000".U(32.W)
	val tmeh_addr = "h200bffc".U(32.W)
	val tmel_addr = "h200bff8".U(32.W)
}

class ClientIO() extends Bundle{
	// bus interface
	val bus = new SysBusSlaveBundle()

	//Interrupt
	val sft_irq_r = Output(Bool())
	val tmr_irq_r = Output(Bool())
}

class Client() extends Module{
	val io = IO(new ClientIO)

	//Software Interrupt Register
	val msip = Reg(UInt(32.W))

	//Time Interrupt Register
	val cmpl = Reg(UInt(32.W))
	val cmph = Reg(UInt(32.W))
	val tmel = Reg(UInt(32.W))
	val tmeh = Reg(UInt(32.W))
	val state = RegInit(Bool(), false.B)
	val ans = Reg(UInt(32.W))

	val req = io.bus.cyc_i && io.bus.stb_i
	val we = req && io.bus.we_i

	state := req

	cmpl := Mux(we && io.bus.adr_i(4,2) === 0.U, io.bus.dat_i, cmpl)
	cmph := Mux(we && io.bus.adr_i(4,2) === 1.U, io.bus.dat_i, cmph)
	tmel := Mux(we && io.bus.adr_i(4,2) === 2.U, io.bus.dat_i, tmel + 1.U)
	tmeh := Mux(we && io.bus.adr_i(4,2) === 3.U, io.bus.dat_i, Mux(tmel.andR, tmeh + 1.U, tmeh))
	msip := Mux(we && io.bus.adr_i(4,2) === 4.U, io.bus.dat_i, msip)

	ans := MuxLookup(io.bus.adr_i(4,2), 0.U, Seq(
		0.U -> cmpl,
		1.U -> cmph,
		2.U -> tmel,
		3.U -> tmeh,
		4.U -> msip
	))

	//Time Interrupt Generate
	val cmp = Cat(cmph, cmpl)
	val tme = Cat(tmeh, tmel)
	when(tme >= cmp){ io.tmr_irq_r := true.B } .otherwise { io.tmr_irq_r := false.B }

	//Software Interrupt Generate
	when(msip === 1.U(32.W)){ io.sft_irq_r := true.B } .otherwise { io.sft_irq_r := false.B }

	io.bus.stall_o := false.B
	io.bus.ack_o := state
	io.bus.err_o := false.B
	io.bus.rty_o := false.B
	io.bus.dat_o := ans
}