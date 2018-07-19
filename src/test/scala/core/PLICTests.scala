package systemoncat.core

import chisel3._
import chisel3.util._
import chisel3.testers._

class DummyIO() extends Bundle{
	val irq_r = Input(Bool())
	val irq_id = Input(UInt(32.W))

	val plic_read = Output(Bool())
	val plic_write = Output(Bool())
	val addr = Output(UInt(32.W))
	val wdata = Output(UInt(32.W))
}

class DummyCore() extends Module{
	val io = IO(new DummyIO)

	val global_timer = Reg(UInt(32.W))
	val interrupt_timer = Reg(UInt(32.W))
	val interrupt_id = Reg(UInt(32.W))
	val in_interrupt = Reg(Bool())

	global_timer := global_timer + 1.U
	
	val s_ready :: s_read :: s_write :: Nil = Enum(UInt(),3)
	val state = Reg(init = s_ready)

	io.plic_read := (io.irq_r) & (state === s_read)

	io.plic_write := (state === s_write)

	io.wdata := MuxLookup((state === s_write), 0.U, Seq(
		true.B -> interrupt_id,
		false.B -> 0.U
	))
	io.addr := MuxLookup(((state === s_read) | (state === s_write)), 0.U, Seq(
		true.B -> InterruptRegisterAddr.Core1Addr,
		false.B -> 0.U
	))

	when(state === s_ready){
		when(io.irq_r){
			printf("Core1: New Interrupt Arrive At: %d\n", global_timer)
			state := s_read
		}
	}

	when(state === s_read){
		printf("Core1: New Interrupt is: %d\n", io.irq_id)
		interrupt_id := io.irq_id
		state := s_write
	}
	when(state === s_write){
		printf("Core1: Interrupt %d Complete\n", interrupt_id)
		io.wdata := interrupt_id
		state := s_ready
	}

	
	printf("Core1: Now Global time: %d, Interrupt time: %d, Now Interrupt is %d\n",global_timer, interrupt_timer, interrupt_id)
}


class DummyDeviceIO() extends Bundle{
	val serial_p = Input(Bool())
	val keyboard_p = Input(Bool())
	val net_p = Input(Bool())

	val serial_irq = Output(Bool())
	val keyboard_irq = Output(Bool())
	val net_irq = Output(Bool())
}

class DummyDevice() extends Module{
	val io = IO(new DummyDeviceIO)

	val global_timer = Reg(UInt(32.W))
	val serial_timer = Reg(UInt(32.W))
	val keyboard_timer = Reg(UInt(32.W))
	val net_timer = Reg(UInt(32.W))

	val s_ready :: s_serial :: s_keyboard :: s_net :: s_together :: s_wait1 :: s_wait2 :: s_wait3 :: s_wait4 :: Nil = Enum(UInt(), 9)
	val state = Reg(init = s_serial)

	global_timer := (global_timer + 1.U)
	
	//io.serial_irq := (state === s_serial) | (state === s_wait1) | (state === s_wait2) | (state === s_wait3)
	io.serial_irq := (global_timer === 3.U)
	io.keyboard_irq := (global_timer === 3.U)
	io.net_irq := (global_timer === 3.U)
	/*
	when(state === s_serial){
		when(io.serial_p){
			state := s_wait1
		}
	}
	when(state === s_wait1){
		when(io.serial_p){
			state := s_wait2
		}
	}
	when(state === s_wait2){
		when(io.serial_p){
			state := s_wait3
		}
	}
	when(state === s_wait3){
		when(io.serial_p){
			state := s_ready
		}
	}
	*/
	//io.serial_irq := (io.serial_p) & ((state === s_serial) | (state === s_together))
	//io.keyboard_irq := (io.keyboard_p) & ((state === s_keyboard) | (state === s_together))
	//io.net_irq := (io.net_p) & ((state === s_net) | (state === s_together))
/*
	when(state === s_serial){
		//printf("Device:At state serial\n")
		state := s_keyboard
	}
	when(state === s_keyboard){
		//printf("Device:At state keyboard\n")
		state := s_net
	}
	when(state === s_net){
		//printf("Device:At state net\n")
		state := s_together
	}
	when(state === s_together){
		//printf("DeviceAt state together\n")
		state := s_serial
	}
*/
	when(io.net_irq){
		printf("Device: At time %d, An net iterrupt request is sent\n", global_timer)
	}
	when(io.serial_irq){
		printf("Device: At time %d, An serial iterrupt request is sent\n", global_timer)
	}
	when(io.keyboard_irq){
		printf("Device: At time %d, An keyboard iterrupt request is sent\n", global_timer)
	}

}
class PLICTester() extends BasicTester{
	val plic = Module(new PLIC)
	val device = Module(new DummyDevice)
	val core = Module(new DummyCore)

	val (cntr, done) = Counter(true.B, 50)
	plic.io.plic_en := true.B
	plic.io.plic_rd_en := core.io.plic_read
	plic.io.plic_wr_en := core.io.plic_write
	plic.io.addr := core.io.addr
	plic.io.wb_plic_dat := core.io.wdata

	plic.io.serial_irq_r := device.io.serial_irq
	plic.io.keyboard_irq_r := device.io.keyboard_irq
	plic.io.net_irq_r := device.io.net_irq
	plic.io.reserved_irq_r := false.B

	core.io.irq_r := plic.io.core1_ext_irq_r
	core.io.irq_id := plic.io.read_plic_dat

	device.io.serial_p := plic.io.serial_permission
	device.io.keyboard_p := plic.io.keyboard_permission
	device.io.net_p := plic.io.net_permission

	when(done) { stop(); stop() } 
}

class PLICTests extends org.scalatest.FlatSpec{
	"PLICTests" should "pass" in{
		assert(TesterDriver execute(() => new PLICTester()))
		//assert(1 === 2)
	}
}