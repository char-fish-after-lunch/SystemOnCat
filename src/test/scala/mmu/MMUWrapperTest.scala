package systemoncat.mmu

import chisel3._
import chisel3.util._
import chisel3.testers._

object MMUTestConsts{
	val ptbase_ppn = "h10".U(9.W)
	
	val vaddr1 = "h10000000".U(32.W)
	val vaddr2 = "h10000004".U(32.W)
	val paddr1 = "h1c9000".U(21.W)
	val paddr2 = "h1c9004".U(21.W)

	val pte_1_index = vaddr(31,22)
	val pte_1_addr = "h10100".U(21.W) //Cat(ptbase_ppn, pte_1_index) << 2
	val pte_1 = "h8001".U(31.W)
	
	val pte_2_index = vaddr(21,12)
	val pte_2_addr = "h20000".U(21.W) //Cat(pte_1(18,10), pte_2_index) << 2
	val pte_2 = "h72401".U(31.W)

	val data1 = "h1c9f048e".U(32.W)
	val data2 = "hf673cdea".U(32.W)
}

class DummyTranslatorIO extends Bundle{
	val adr_i = Input(UInt(21.W))
	val dat_i = Input(UInt(32.W))
	val sel_i = Input(UInt(4.W))
	val stb_i = Input(Bool())
	val cyc_i = Input(Bool())
	val we_i = Input(Bool())

	val dat_o = Output(UInt(32.W))
	val stall_o = Output(Bool())
	val err_o = Output(Bool()) 
}

class DummyIO extends Bundle{
	val out = new DummyTranslatorIO()
}

class DummyTranslator extends Module{
	val io = IO(new DummyIO)
	val s_ready :: s_memory :: s_stall :: Nil = Enum(UInt(),3)
	val state = Reg(init = s_ready)

	val addr = Reg(UInt(21.W))
	val data = Reg(UInt(32.W))

	io.out.dat_o := data
	when(state === s_ready){
		
	}
	

}