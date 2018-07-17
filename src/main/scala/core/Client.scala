package systemoncat.core

import systemoncat.sysbus.{SysBusSlaveBundle, SysBusSlave}

import chisel3._
import chisel3.util._
import chisel3.testers._


object CliAddr{
	val msip_addr = "h2004010".U(32.W)
	val cmph_addr = "h2004004".U(32.W)
	val cmpl_addr = "h2004000".U(32.W)
	val tmeh_addr = "h200400c".U(32.W)
	val tmel_addr = "h2004008".U(32.W)
}

class ClientIrqIO extends Bundle {
	//Interrupt
	val sft_irq_r = Output(Bool())
	val tmr_irq_r = Output(Bool())
}

class Client() extends SysBusSlave(new ClientIrqIO()){
	//Software Interrupt Register
	val irq = Wire(new ClientIrqIO())
	io.in <> irq // as scala cannot infer that io.in.tmr_irq_r exists, wire type has to be specified again

	val msip = RegInit(0.U(32.W))

	//Time Interrupt Register
	// val cmpl = Reg(UInt(32.W))
	val cmpl = RegInit(0xfffffff.U(32.W))
	val cmph = RegInit(0.U(32.W))
	val tmel = RegInit(0.U(32.W))
	val tmeh = RegInit(0.U(32.W))
	val state = RegInit(Bool(), false.B)
	val ans = RegInit(0.U(32.W))

	val req = io.out.cyc_i && io.out.stb_i
	val we = req && io.out.we_i

	state := req

	cmpl := Mux(we && io.out.adr_i(4,2) === 0.U, io.out.dat_i, cmpl)
	cmph := Mux(we && io.out.adr_i(4,2) === 1.U, io.out.dat_i, cmph)
	tmel := Mux(we && io.out.adr_i(4,2) === 2.U, io.out.dat_i, tmel + 1.U)
	tmeh := Mux(we && io.out.adr_i(4,2) === 3.U, io.out.dat_i, Mux(tmel.andR, tmeh + 1.U, tmeh))
	msip := Mux(we && io.out.adr_i(4,2) === 4.U, io.out.dat_i, msip)

	ans := MuxLookup(io.out.adr_i(4,2), 0.U, Seq(
		0.U -> cmpl,
		1.U -> cmph,
		2.U -> tmel,
		3.U -> tmeh,
		4.U -> msip
	))

	//Time Interrupt Generate
	val cmp = Cat(cmph, cmpl)
	val tme = Cat(tmeh, tmel)
	when(tme >= cmp){ irq.tmr_irq_r := true.B } .otherwise { irq.tmr_irq_r := false.B }

	//Software Interrupt Generate
	when(msip === 1.U(32.W)){ irq.sft_irq_r := true.B } .otherwise { irq.sft_irq_r := false.B }

	io.out.stall_o := false.B
	io.out.ack_o := state
	io.out.err_o := false.B
	io.out.rty_o := false.B
	io.out.dat_o := ans
}