package systemoncat.mmu

import chisel3._
import chisel3.util._
import chisel3.testers._

object TestConsts{
	val ptbase_ppn = "h10".U(9.W)
	val vaddr = "h10000000".U(32.W)
	val paddr = "h1c9000".U(21.W)
	val pte_1_index = vaddr(31,22)
	val pte_1_addr = Cat(ptbase_ppn, pte_1_index) << 2 // 0x10100(21.W)
	val pte_1 = "h8001".U(31.W)
	val pte_2_index = vaddr(21,12)
	val pte_2_addr = Cat(pte_1(18,10), pte_2_index) << 2 // 0x20000(21.W)
	val pte_2 = "h72401".U(31.W)
}
class DummyMemory extends Module{
	
	val io = IO(Flipped(new PTWMEMIO))

	val addr = Reg(UInt(21.W))
	val get_data = Reg(UInt(32.W))

	when(io.request){
		addr := io.addr
	}

	printf("In memory -> Addr: %x\n", addr)

	when(addr === TestConsts.pte_1_addr.asUInt()){
		get_data := TestConsts.pte_1
	}
	when(addr === TestConsts.pte_2_addr.asUInt()){
		get_data := TestConsts.pte_2
	}
	
	io.valid := ((io.addr === TestConsts.pte_1_addr.asUInt()) & (get_data === TestConsts.pte_1)) | ((io.addr === TestConsts.pte_2_addr.asUInt()) & (get_data === TestConsts.pte_2))
	io.data := get_data
}
class DummyIO extends Bundle{
	val vaddr = Input(UInt(32.W))
	val refill_request = Input(Bool())
	val cmd = Input(UInt(2.W))

	val pte = Output(UInt(32.W))
	val ptw_finish = Output(Bool())
	val ptw_valid = Output(Bool())
	val pf = Output(Bool())
}
class DummyPTWIO extends Bundle{
	val tlb = new TLBPTWIO()
	val mem = new PTWMEMIO()
	val baseppn = Input(UInt(MemoryConsts.PPNLength.W))
	val priv = Input(UInt(2.W))
	
	val iPF = Output(Bool())
	val lPF = Output(Bool())
	val sPF = Output(Bool())
	
	val pf_vaddr = Output(UInt(MemoryConsts.VaLength.W))
}

class DummyPTE extends Bundle{
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

class DummyPTW extends Module{
	val io = IO(new DummyPTWIO)
	
	val temp_pte = Reg(new PTE)

	val s_ready :: s_request :: s_wait1 :: s_wait2 :: Nil = Enum(UInt(),4)
	val state = Reg(init = s_ready)

	val page_offset = Reg(UInt(MemoryConsts.OffsetLength.W))
	val vpn_1 = Reg(UInt(MemoryConsts.VPNLength1.W))
	val vpn_2 = Reg(UInt(MemoryConsts.VPNLength2.W))
	val mm_cmd = Reg(UInt(2.W))

	io.tlb.pte_ppn := 0.U
	io.tlb.ptw_finish := true.B
	io.tlb.ptw_valid := true.B
	io.tlb.pf := true.B
	io.mem.addr := 0.U
	io.mem.request := true.B

	io.iPF := false.B
	io.lPF := false.B
	io.sPF := false.B

	io.pf_vaddr := 0.U
}

class DummyTLBIO extends Bundle{
	val vaddr = Input(UInt(MemoryConsts.VaLength.W))
	val asid = Input(UInt(MemoryConsts.ASIDLength.W))
	val passthrough = Input(Bool())
	val cmd = Input(UInt(2.W)) //Memory Command
	val tlb_request = Input(Bool())
	val tlb_flush = Input(Bool())

	val paddr = Output(UInt(MemoryConsts.PaLength.W))
	val valid = Output(Bool())
	val ptw = Flipped(new TLBPTWIO)
}

class DummyTLB extends Module{
	val io = IO(new DummyTLBIO)
	val totalEntries = MemoryConsts.TLBEntryNum
	val entries_valid = Mem(totalEntries, Bool())
	val entries = Mem(totalEntries, new TLBEntry())
	val s_ready :: s_request :: s_wait :: Nil = Enum(UInt(), 3)
	val state = Reg(init = s_ready)
	val refill_tag = Reg(UInt(MemoryConsts.TLBTagLength.W))
	val refill_index = Reg(UInt(MemoryConsts.TLBIndexLength.W))

	val lookup_tag = Cat(io.asid, io.vaddr(31,16))
	val page_offset = io.vaddr(11,0)
	val lookup_index = io.vaddr(16,12)
	val TLBHit = entries_valid(lookup_index) & (entries(lookup_index).tag === lookup_tag) & ~io.passthrough
	when(TLBHit){
		printf("tlb hit\n")
		io.paddr := Cat(entries(lookup_index).ppn, page_offset)
		io.valid := true.B
	} .otherwise{
		printf("tlb miss\n")
		io.valid := false.B
		io.paddr := 0.U
	}

	val need_refill = ~TLBHit & io.tlb_request
	io.ptw.vaddr := io.vaddr
	io.ptw.cmd := io.cmd
	when(need_refill){
		io.ptw.refill_request := true.B
	} .otherwise{
		io.ptw.refill_request := false.B
	}

	// TLB Refill
	val do_refill = io.ptw.ptw_valid & ~(state === s_request)
	when(do_refill){
		val waddr = refill_index
		val refill_pte = io.ptw.pte_ppn
	}

}

class MMUTester() extends BasicTester{
	val mem = Module(new DummyMemory)	
	val tlb = Module(new TLB)
	val ptw = Module(new PTW)
	val (cntr, done) = Counter(true.B, 20)
	
	tlb.io.ptw <> ptw.io.tlb
	ptw.io.mem <> mem.io
	printf("At time: %d \n",cntr)
	ptw.io.baseppn := TestConsts.ptbase_ppn
	ptw.io.priv := 0.U
	tlb.io.vaddr := TestConsts.vaddr
	tlb.io.asid := 0.U(MemoryConsts.ASIDLength.W)
	tlb.io.passthrough := false.B
	tlb.io.cmd := MemoryConsts.Load
	tlb.io.tlb_request := false.B
	tlb.io.tlb_flush := false.B

	when(cntr === 1.U){printf("start tlb access\n")}
	when(cntr === 2.U){
		printf("Input Virtual Address: %x\n",TestConsts.vaddr)
		tlb.io.tlb_request := true.B
	}
	when(done) { 
		when(tlb.io.valid){
			printf("Final Physical Address: %x\n", tlb.io.paddr)
			assert(tlb.io.paddr === TestConsts.paddr)
		}
		stop(); stop() 
	}


}

class MMUTests extends org.scalatest.FlatSpec {
    "MMUTests" should "pass" in {
        assert(TesterDriver execute (() => new MMUTester()))
        //assert(1 === 2)
    }
}