package systemoncat.mmu

import chisel3._
import chisel3.util._
import systemoncat.core._

class TLBEntry extends Bundle{
	val tag = UInt(MemoryConsts.TLBTagLength.W)
	//val ppn = UInt(MemoryConsts.PPNLength.W)
	val pte = new PTE()
}

class TLBIO extends Bundle{

	val vaddr = Input(UInt(MemoryConsts.VaLength.W))
	val asid = Input(UInt(MemoryConsts.ASIDLength.W))
	val passthrough = Input(Bool())
	val cmd = Input(UInt(2.W)) //Memory Command
	val tlb_request = Input(Bool())
	val tlb_flush = Input(Bool())
	val priv = Input(UInt(2.W))

	val paddr = Output(UInt(32.W))
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
	val refill_index = Reg(UInt(MemoryConsts.TLBIndexLength.W))

	//TLB flush
	when(io.tlb_flush){
		for(j <- 0 until totalEntries){
			entries_valid(j) := false.B
		}
	}

	//
	def is_valid(pte_u: Bool, pte_x: Bool, pte_w: Bool, pte_r: Bool, cmd: UInt, prv: UInt): Bool = (pte_u | (prv === PRV.M)) & (pte_x | (cmd =/= MemoryConsts.PC)) & (pte_w | (cmd =/= MemoryConsts.Store)) & (pte_r | cmd === MemoryConsts.PC)
	// TLB LookUp
	val lookup_tag = Cat(io.asid, io.vaddr(31,17))
	val page_offset = io.vaddr(11,0)
	val lookup_index = io.vaddr(16,12)
	val TLBHit = entries_valid(lookup_index) & (entries(lookup_index).tag === lookup_tag) & ~io.passthrough & is_valid(entries(lookup_index).pte.U, entries(lookup_index).pte.X, entries(lookup_index).pte.W, entries(lookup_index).pte.R, io.cmd, io.priv)
	
	when(TLBHit | io.passthrough){
		printf("tlb hit\n")
		io.paddr := MuxLookup(io.passthrough, 0.U(32.W), Seq(
			false.B -> Cat(entries(lookup_index).pte.ppn, page_offset),
			true.B -> io.vaddr
		))
		io.valid := true.B
	} .otherwise{
		printf("tlb miss\n")
		io.valid := false.B
		io.paddr := 0.U
	}
	
	// TLB Refill
	val do_refill = io.ptw.ptw_valid & (state === s_request)
	when(do_refill & ~io.tlb_flush){
		printf("TLB Start to Refill\n")
		val waddr = refill_index
		val refill_pte = io.ptw.pte.asTypeOf(new PTE)
		val newEntry = Wire(new TLBEntry)
		newEntry.pte := refill_pte
		newEntry.tag := refill_tag

		entries_valid(refill_index) := true.B
		entries(refill_index) := newEntry
	}

	// TLB Refill request
	val need_refill = ~TLBHit & io.tlb_request & ~io.passthrough
	io.ptw.vaddr := io.vaddr
	io.ptw.cmd := io.cmd
	io.ptw.refill_request := (state === s_ready) & need_refill

	//TLB State Machine
	when(state === s_ready){
		printf("tlb ready state!\n")
		
		when(need_refill){
			printf("need refill\n")
			refill_index := lookup_index
			refill_tag := lookup_tag
			state := s_request
		} 

	}
	when (state === s_request){
		printf("tlb request state!\n")
		when(io.ptw.ptw_finish){
			state := s_ready
		}
		//when(io.ptw.pf){
		//	state := s_wait
		//}
	}
	
	//when (state === s_wait){
	//	when(io.ptw.finish){
	//		state := s_ready
	//	}
	//}


}
