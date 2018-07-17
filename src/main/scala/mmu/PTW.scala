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
} 
/*
class PTW extends Module{
	val io = IO(new PTWIO)
}
*/