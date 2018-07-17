package systemoncat.mmu

import chisel3._
import chisel3.util._

class TLBEntry extends Bundle{
	val tag = UInt(MemoryConsts.TLBTagLength.W)
	val ppn = UInt(MemoryConsts.PPNLength.W)
}

class TLBIO extends Bundle{

	val vaddr = Input(UInt(MemoryConsts.VaLength.W))
	val asid = Input(UInt(MemoryConsts.ASIDLength.W))
	val passthrough = Input(Bool())
	val cmd = Input(UInt(2.W)) //Memory Command
	val tlb_request = Input(Bool())

	val paddr = Output(UInt(MemoryConsts.PaLength.W))
	val valid = Output(Bool())

	//Page Fault Exception(Leave for PTW to solve)
	//val iPF = Output(Bool())
	//val lPF = Output(Bool())
	//val sPF = Output(Bool()) 

	//Interaction with PTW
	val ptw = Flipped(new TLBPTWIO)

}

class TLB extends Module{
	val io = IO(new TLBIO)

	val totalEntries = MemoryConsts.TLBEntryNum
	val entries_valid = Mem(totalEntries, Bool())
	val entries = Mem(totalEntries, new TLBEntry())

	val s_ready :: s_request :: s_wait :: Nil = Enum(UInt(), 3)
	val state = Reg(init = s_ready)

	//Refill Store
	val refill_tag = Reg(UInt(MemoryConsts.TLBTagLength.W))
	val refill_offset = Reg(UInt(MemoryConsts.PageOffset.W))
	val refill_index = Reg(UInt(MemoryConsts.TLBIndexLength.W))

	//TLB init
	io.paddr := 0.U(21.W)
	io.valid := false.B

	// TLB LookUp
	val lookup_tag = Cat(io.asid, io.vaddr(31,16))
	val page_offset = io.vaddr(11,0)
	val lookup_index = io.vaddr(16,12)
	val TLBHit = entries_valid(lookup_index) & (entries(lookup_index).tag === lookup_tag) & ~io.passthrough
	when(TLBHit){
		io.paddr := Cat(entries(lookup_index).ppn, page_offset)
		io.valid := true.B
	} .otherwise{
		io.valid := false.B
	}
	
	// TLB Refill
	val do_refill = io.ptw.valid & ~(state === s_request)
	when(do_refill){
		val waddr = refill_index
		val refill_ppn = io.ptw.pte.ppn
		val newEntry = Wire(new TLBEntry)
		newEntry.ppn := refill_ppn
		newEntry.tag := refill_tag

		entries_valid(refill_index) := true.B
		entries(refill_index) := newEntry
	}

	//TLB State Machine
	when((state === s_ready) & ~TLBHit & io.tlb_request){
		state := s_request
		refill_index := lookup_index
		refill_tag := lookup_tag
		io.ptw.refill_request := true.B
	}
	when (state === s_request){
		when(io.ptw.finish){
			state := s_ready
		}
		when(io.ptw.pf){
			state := s_wait
		}
	}
	when (state === s_wait){
		when(io.ptw.finish){
			state := s_ready
		}
	}


}
