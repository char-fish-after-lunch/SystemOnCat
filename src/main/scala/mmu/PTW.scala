package systemoncat.mmu

import chisel3._
import chisel3.util._

class PTE extends Bundle{
	val zero = UInt(MemoryConsts.PTEZero)
	val ppn = UInt(MemoryConsts.PPNLength)
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

	val pte = Output(new PTE)
	val finish = Output(Bool())
	val valid = Output(Bool())
	val pf = Output(Bool())
}

class PTWMEMIO extends Bundle{
	val addr = Output(UInt(21.W)) //Target Address
	val data = Input(UInt(32.W)) //Read Data
	
	val request = Output(Bool())
	val valid = Input(Bool())
}

class PTWIO extends Bundle{
	val tlb = new TLBPTWIO
	val mem = new PTWMEMIO
	val baseppn = Input(UInt(MemoryConsts.PPNLength.W))
	val priv = Input(UInt(2.W))

	//Page Fault Exception
	val iPF = Output(Bool())
	val lPF = Output(Bool())
	val sPF = Output(Bool())

	//Page Fault PTE
	val pf_pte = Output(new PTE)
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
	val pte_1_valid = (state === s_wait1) & (~temp_pte.X & ~temp_pte.W & ~temp_pte.R)
	//Level 2 PTE Check
	val pte_2_valid = (state === s_wait2) & (temp_pte.V)
	//Page Fault Signal
	val page_fault = ~pte_1_valid | ~pte_2_valid 

	//Page Fault Handler
	when(page_fault){
		io.pf_pte := temp_pte
		when(mm_cmd === MemoryConsts.Load){
			io.lPF := true.B
		}
		when (mm_cmd === MemoryConsts.Store){
			io.sPF := true.B
		}
		when (mm_cmd === MemoryConsts.PC){
			io.iPF := true.B
		}
		io.tlb.finish := true.B
		io.tlb.valid := false.B
		io.tlb.pf := true.B
	}

	//State Control
	when((state === s_ready) & io.tlb.refill_request){
		state := s_request
		page_offset := io.tlb.vaddr(11,0)
		vpn_1 := io.tlb.vaddr(31,22)
		vpn_2 := io.tlb.vaddr(21,12)
		mm_cmd := io.tlb.cmd
	}
	when ((state === s_request)){
		io.mem.addr := (Cat(temp_pte.ppn, vpn_1) << 2)
		io.mem.request := true.B
		when(io.mem.valid === true.B) { 
			state := s_wait1 
			temp_pte := io.mem.data
		}
	}
	when ((state === s_wait1)){
		io.mem.addr := (Cat(temp_pte.ppn, vpn_2) << 2)
		io.mem.request := true.B
		when(page_fault){
			state := s_ready
		}
		.elsewhen(io.mem.valid === true.B) {
			state := s_wait2
			temp_pte := io.mem.data
		} 	
	}
	when ((state === s_wait2)){
		when(page_fault){
			state := s_ready
		}
		.otherwise{
			io.tlb.pte := temp_pte
			io.tlb.valid := true.B
			io.tlb.finish := true.B
			state := s_ready
		}

	}
}
