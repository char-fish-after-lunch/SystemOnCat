package systemoncat.core

import chisel3._
import chisel3.util._
import chisel3.Bits._


//object GateWayAddr{} implement by hardware can not be read/write by software

//object IPAddr{} implement by hardware can not be read/write by software

//object InterruptPriority{}

object InterruptRegisterAddr{ //Should be different for each core
	val Core1Addr = "hc200000".U(32.W) //For Core one Interrupt Register
	val Core2Addr = "hc200004".U(32.W) //For Core two Interrupt Register
}

object InterruptID{
	val SerialPortID = 1.U(32.W)
	val KeyboardID = 2.U(32.W)
	val NetID = 3.U(32.W)
	val ReservedID = 4.U(32.W)
}

class PLICIO() extends Bundle{
	//commands
	val plic_en = Input(Bool())
	val plic_rd_en = Input(Bool())
	val plic_wr_en = Input(Bool())

	//Selection Address
	val addr = Input(UInt(32.W))

	//Read Write Data
	val read_plic_dat = Output(UInt(32.W))
	val wb_plic_dat = Input(UInt(32.W))

	//Interrupt Input
	val serial_irq_r = Input(Bool())
	val keyboard_irq_r = Input(Bool())
	val net_irq_r = Input(Bool())
	val reserved_irq_r = Input(Bool())

	//Interrupt Output
	val core1_ext_irq_r = Output(UInt(32.W))
	val core2_ext_irq_r = Output(UInt(32.W))

}

class PLIC() extends Module{
	val io = IO(new PLICIO)

	//GateWay Register
	val serial_gate = Reg(Bool())
	val keyboard_gate = Reg(Bool())
	val net_gate = Reg(Bool())
	val reserved_gate = Reg(Bool())

	//IP Register
	val serial_ip = Reg(Bool())
	val keyboard_ip = Reg(Bool())
	val net_ip = Reg(Bool())
	val reserved_ip = Reg(Bool())

	//Core1 Interrupt Register
	val Core1IR = Reg(UInt(32.W))

	//Core2 Interrupt Register
	val Core2IR = Reg(UInt(32.W))

	//Interrupt Notification
	when(io.serial_irq_r & ~serial_gate){ 
		serial_ip := true.B
		serial_gate := true.B
	}
	when(io.keyboard_irq_r & ~keyboard_gate){
		keyboard_ip := true.B
		keyboard_gate := true.B
	}
	when(io.net_irq_r & ~net_gate){
		net_ip := true.B
		net_gate := true.B
	}

	io.core1_ext_irq_r := false.B
	io.core2_ext_irq_r := false.B

	when(keyboard_ip){
		//Notify Core 1
		Core1IR := InterruptID.KeyboardID
		io.core1_ext_irq_r := true.B

		//Notify Core 2
		Core2IR := InterruptID.KeyboardID
		io.core2_ext_irq_r := true.B

	} .elsewhen(serial_ip){
		//Notify Core 1
		Core1IR := InterruptID.SerialPortID
		io.core1_ext_irq_r := true.B

		//Notify Core 2
		Core2IR := InterruptID.SerialPortID
		io.core2_ext_irq_r := true.B

	} .elsewhen(net_ip){
		//Notify Core 1
		Core1IR := InterruptID.NetID
		io.core1_ext_irq_r := true.B

		//Notify Core 2
		Core2IR := InterruptID.NetID
		io.core2_ext_irq_r := true.B
	}

	//Register Read Logic(Interrupt Claim)
	when(io.plic_en & io.plic_rd_en){
		when(io.addr === InterruptRegisterAddr.Core1Addr){
			io.read_plic_dat := Core1IR.asUInt()
			io.core1_ext_irq_r := false.B
			when( Core1IR === InterruptID.KeyboardID ){
				keyboard_ip := false.B
			} .elsewhen( Core1IR === InterruptID.SerialPortID ){
				serial_ip := false.B
			} .elsewhen( Core1IR === InterruptID.NetID ){
				net_ip := false.B
			}
		} .elsewhen(io.addr === InterruptRegisterAddr.Core2Addr){
			io.read_plic_dat := Core2IR.asUInt()
			io.core2_ext_irq_r := false.B
			when( Core2IR === InterruptID.KeyboardID ){
				keyboard_ip := false.B
			} .elsewhen( Core2IR === InterruptID.SerialPortID ){
				serial_ip := false.B
			} .elsewhen( Core2IR === InterruptID.NetID ){
				net_ip := false.B
			}
		}
	}

	//Register Write Logic
	when(io.plic_en & io.plic_wr_en){
		when(io.addr === InterruptRegisterAddr.Core1Addr){
			Core1IR := io.wb_plic_dat
			when( Core1IR === InterruptID.KeyboardID ){
				keyboard_gate := false.B
			} .elsewhen( Core1IR === InterruptID.SerialPortID ){
				serial_gate := false.B
			} .elsewhen( Core1IR === InterruptID.NetID ){
				net_gate := false.B
			}
		} .elsewhen(io.addr === InterruptRegisterAddr.Core2Addr){
			Core2IR := io.wb_plic_dat
			when( Core2IR === InterruptID.KeyboardID ){
				keyboard_gate := false.B
			} .elsewhen( Core2IR === InterruptID.SerialPortID ){
				serial_gate := false.B
			} .elsewhen( Core2IR === InterruptID.NetID ){
				net_gate := false.B
			}
		}
	}

}

