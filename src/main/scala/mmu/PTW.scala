package systemoncat.mmu

import chisel3._
import chisel3.util._

class PTE extends Bundle{
	val ppn = UInt(MemoryConsts.PPNLength)
}

class TLBPTWIO extends Bundle{
	val vaddr = Input(UInt(32.W))
	val refill_request = Input(Bool())
	val pte = Output(new PTE)
	val finish = Output(Bool())
	val valid = Output(Bool())
}