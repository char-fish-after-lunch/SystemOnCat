package systemoncat.mmu

import chisel3._
import chisel3.util._
import chisel3.Bits._

class PTE extends Bundle{
	val zero = UInt(MemoryConsts.PTEZero.W)
	val ppn = UInt(MemoryConsts.PPNLength.W)
	val rsw = UInt(MemoryConsts.RSWLength.W)
	
	val D = Bool()
	val A = Bool()
	val G = Bool()
	val U = Bool()
	val X = Bool()
	val W = Bool()
	val R = Bool()
	val V = Bool()
}

class TLBPTWIO extends Bundle{
	val vaddr = Input(UInt(32.W))
	val refill_request = Input(Bool())
	val cmd = Input(UInt(2.W))

	val pte_ppn = Output(UInt(MemoryConsts.PPNLength.W))
	val ptw_finish = Output(Bool())
	val ptw_valid = Output(Bool())
	val pf = Output(Bool())
}

class PTWMEMIO extends Bundle{
	val addr = Output(UInt(32.W)) //Target Address
	val data = Input(UInt(32.W)) //Read Data
	
	val request = Output(Bool())
	val valid = Input(Bool())
}

class PTWIO extends Bundle{
	val tlb = new TLBPTWIO()
	val mem = new PTWMEMIO()
	val baseppn = Input(UInt(MemoryConsts.PPNLength.W))
	val priv = Input(UInt(2.W))

	//Page Fault Exception
	val expt = new MMUException
}

class PTW extends Module{
	val io = IO(new PTWIO)

	val temp_pte = Reg(new PTE)

	val s_ready :: s_request :: s_wait1 :: s_wait2 :: Nil = Enum(UInt(),4)
	val state = Reg(init = s_ready)

	val page_offset = Reg(UInt(MemoryConsts.OffsetLength.W))
	val vpn_1 = Reg(UInt(MemoryConsts.VPNLength1.W))
	val vpn_2 = Reg(UInt(MemoryConsts.VPNLength2.W))
	val mm_cmd = Reg(UInt(2.W))

	//Level 1 PTE Check
	val pte_1_valid = (state =/= s_wait1) | ((state === s_wait1) & (~temp_pte.X & ~temp_pte.W & ~temp_pte.R & temp_pte.V))
	//Level 2 PTE Check
	val pte_2_valid = (state =/= s_wait2) | ((state === s_wait2) & (temp_pte.V))
	//Page Fault Signal
	val page_fault = ~pte_1_valid | ~pte_2_valid 

	//mem access

	io.mem.request := ((state === s_request) | (state === s_wait1)) & ~io.mem.valid
	io.mem.addr := MuxLookup(state, 0.U(32.W), Seq(
		s_request -> (Cat(io.baseppn, vpn_1) << 2),
		s_wait1 -> (Cat(temp_pte.ppn, vpn_2) << 2)
	))


	//Page Fault Handler
	io.expt.pf_vaddr := io.tlb.vaddr
	io.expt.lPF := (mm_cmd === MemoryConsts.Load) & page_fault
	io.expt.sPF := (mm_cmd === MemoryConsts.Store) & page_fault
	io.expt.iPF := (mm_cmd === MemoryConsts.PC) & page_fault
	io.tlb.pf := page_fault
	when(page_fault){
		printf("ptw: Page Fault!\n")
	}

	//finish logic
	io.tlb.ptw_finish := page_fault | (state === s_wait2)
	io.tlb.pte_ppn := temp_pte.ppn

	//valid logic
	io.tlb.ptw_valid := (state === s_wait2) & (~page_fault)

	//State Control
	when((state === s_ready) & io.tlb.refill_request){
		printf("ptw ready state\n")
		state := s_request
		page_offset := io.tlb.vaddr(11,0)
		vpn_1 := io.tlb.vaddr(31,22)
		vpn_2 := io.tlb.vaddr(21,12)
		mm_cmd := io.tlb.cmd
	}
	when ((state === s_request)){
		printf("ptw request state\n")
		when(io.mem.valid === true.B) { 
			state := s_wait1 
			temp_pte := io.mem.data.asTypeOf(new PTE)
		}
	}
	when ((state === s_wait1)){
		printf("ptw wait 1 state\n")
		printf("ptw: first memory access get: %x\n", temp_pte.asUInt())

		when(page_fault){
			state := s_ready
		}
		.elsewhen(io.mem.valid === true.B) {
			state := s_wait2
			temp_pte := io.mem.data.asTypeOf(new PTE)
		}
	}
	when ((state === s_wait2)){
		printf("ptw wait 2 state\n")
		printf("ptw: second memory access get: %x\n",temp_pte.asUInt())
		when(page_fault){
			state := s_ready
		}
		.otherwise{
			state := s_ready
		}

	}
}
