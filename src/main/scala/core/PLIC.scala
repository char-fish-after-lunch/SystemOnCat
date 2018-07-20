package systemoncat.core

import chisel3._
import chisel3.util._
import chisel3.Bits._

import systemoncat.sysbus.SysBusSlave
import systemoncat.devices.PLICInterface

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

class Serial_Bundle extends Bundle{

}

class PLICIO() extends Bundle{
	//Interrupt Input
	val external = Flipped(new PLICInterface)

	//Interrupt Output
	val core1_ext_irq_r = Output(UInt(32.W))
	val core2_ext_irq_r = Output(UInt(32.W))

}

class PLIC() extends SysBusSlave(new PLICIO){
	val plicio = Wire(new PLICIO)
	io.in <> plicio

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

	//Core1 Interrupt Register(Read Only)
	val Core1IR = Reg(UInt(32.W))
	//Core2 Interrupt Register(Read Only)
	val Core2IR = Reg(UInt(32.W))

	//Interrupt Permission
	plicio.external.serial_permission := ~serial_gate
	plicio.external.keyboard_permission := ~keyboard_gate
	plicio.external.net_permission := ~net_gate
	plicio.external.reserved_permission := false.B

	//Interrupt Notification
	when(plicio.external.serial_irq_r & ~serial_gate){ 
		printf("New Serial Request\n")
		serial_ip := true.B
		serial_gate := true.B
	}
	when(plicio.external.keyboard_irq_r & ~keyboard_gate){
		printf("New Keyboard Request\n")
		keyboard_ip := true.B
		keyboard_gate := true.B
	}
	when(plicio.external.net_irq_r & ~net_gate){
		printf("New Net Request\n")
		net_ip := true.B
		net_gate := true.B
	}

	plicio.core1_ext_irq_r := (Core1IR === InterruptID.KeyboardID & keyboard_ip === true.B) | (Core1IR === InterruptID.SerialPortID & serial_ip === true.B) | (Core1IR === InterruptID.NetID & net_ip === true.B)

	plicio.core2_ext_irq_r := (Core2IR === InterruptID.KeyboardID & keyboard_ip === true.B) | (Core2IR === InterruptID.SerialPortID & serial_ip === true.B) | (Core2IR === InterruptID.NetID & net_ip === true.B)


	when(keyboard_ip){
		printf("Keyboard interrupt, Notify Core 1\n")
		Core1IR := InterruptID.KeyboardID

		printf("Keyboard interrupt, Notify Core 2\n")
		Core2IR := InterruptID.KeyboardID

	} .elsewhen(serial_ip){
		printf("Serial interrupt, Notify Core 1\n")
		Core1IR := InterruptID.SerialPortID

		printf("Serial interrupt, Notify Core 2\n")
		Core2IR := InterruptID.SerialPortID

	} .elsewhen(net_ip){
		printf("Net interrupt, Notify Core 1\n")
		Core1IR := InterruptID.NetID

		printf("Net interrupt, Notify Core 2\n")
		Core2IR := InterruptID.NetID
	}

	val req = io.out.cyc_i && io.out.stb_i
	val we = req & io.out.we_i

	val state = RegInit(Bool(), false.B)
	val ans = RegInit(UInt(32.W), 0.U)

	state := req
	ans := Mux(io.out.adr_i(2), Core1IR.asUInt(),Core2IR.asUInt())

	io.out.ack_o := state
	io.out.stall_o := false.B
	io.out.err_o := false.B
	io.out.rty_o := false.B
	io.out.dat_o := ans

	when(req){
		when(io.out.we_i){
			val core = Mux(io.out.adr_i(2), Core1IR, Core2IR)
			switch(core){
				is(InterruptID.KeyboardID){
					keyboard_ip := false.B
				}
				is(InterruptID.SerialPortID){
					serial_ip := false.B
				}
				is(InterruptID.NetID){
					net_ip := false.B
				}
			}
		}.otherwise{
			switch(io.out.dat_i(1, 0)){
				is(InterruptID.KeyboardID){
					keyboard_gate := false.B
				}
				is(InterruptID.SerialPortID){
					serial_gate := false.B
				}
				is(InterruptID.NetID){
					net_gate := false.B
				}
			}
		}
	}
}

