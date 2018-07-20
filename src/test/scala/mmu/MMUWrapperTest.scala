package systemoncat.mmu

import chisel3._
import chisel3.util._
import chisel3.testers._
import systemoncat.sysbus._

object MMUTestConsts{
    val ptbase_ppn = "h10".U(9.W)
    
    val vaddr1 = "h10000000".U(32.W)
    val vaddr2 = "h10000004".U(32.W)
    val paddr1 = "h1c9000".U(21.W)
    val paddr2 = "h1c9004".U(21.W)

    val pte_1_index = vaddr1(31,22)
    val pte_1_addr = "h10100".U(21.W) //Cat(ptbase_ppn, pte_1_index) << 2
    val pte_1 = "h8001".U(31.W)
    
    val pte_2_index = vaddr1(21,12)
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

class MDummyIO extends Bundle{
    val out = new DummyTranslatorIO()
}

class DummyTranslator extends Module{
    val io = IO(new MDummyIO)
    val s_ready :: s_memory :: s_stall :: Nil = Enum(UInt(),3)
    val state = Reg(init = s_memory)

    val addr = Reg(UInt(21.W))
    val data = Reg(UInt(32.W))

    io.out.dat_o := data
    io.out.stall_o := (state === s_stall) | (state === s_ready)
    io.out.err_o := false.B

    when(state === s_ready){
        printf("Memory: Ready to receive request\n")
        when(io.out.stb_i){
            printf("Memory: Receive request, Addr: %x\n", io.out.adr_i)
            state := s_stall
            addr := io.out.adr_i
        }
    }
    when(state === s_stall){
        state := s_memory
        printf("Memory: Stalling\n")
    }
    when(state === s_memory){
        
        val temp_data = MuxLookup(io.out.adr_i, 0.U, Seq(
            MMUTestConsts.pte_1_addr -> MMUTestConsts.pte_1,
            MMUTestConsts.pte_2_addr -> MMUTestConsts.pte_2,
            MMUTestConsts.paddr1 -> MMUTestConsts.data1,
            MMUTestConsts.paddr2 -> MMUTestConsts.data2
        	
        ))
        printf("Memory: Get data: %x\n", temp_data)
        io.out.dat_o := temp_data
        state := s_memory
    }
    
}
class TMMUWrapperIO extends Bundle {
    val req = Input(new MMURequest)
    val res = new MMUResponse
    val csr_info = new CSRInfo
    val expt = new MMUException 
}
class TMMUWrapper() extends Module {
    val io = IO(new TMMUWrapperIO)
    val ptw = Module(new PTW)
    val tlb = Module(new TLB)
    //val translator = Module(new SysBusTranslator(map, slaves))
    val translator = Module(new DummyTranslator)

    val phase_1 = RegInit(false.B)
    val req_reg = RegInit(0.U.asTypeOf(new MMURequest()))
    val prev_cache_hit = false.B // placeholder for future cache support. TODO: implement me

    req_reg := Mux(phase_1 && !prev_cache_hit, req_reg, io.req) 

    phase_1 := Mux(phase_1 && !prev_cache_hit, !tlb.io.valid, io.req.wen || io.req.ren)
    // phase 1. vaddr -> paddr, 1 cycle if tlb hit, more cycles if tlb miss
    // phase 2. paddr -> data, 0 cycle if cache hit, 1 cycle if cache miss

    tlb.io.vaddr := req_reg.addr
    tlb.io.asid := io.csr_info.asid
    tlb.io.passthrough := io.csr_info.passthrough
    tlb.io.cmd := req_reg.cmd
    tlb.io.tlb_flush := io.csr_info.tlb_flush
    tlb.io.tlb_request := req_reg.wen | req_reg.ren

    tlb.io.ptw <> ptw.io.tlb

    // ptw.io.mem
    ptw.io.baseppn := io.csr_info.base_ppn
    ptw.io.priv := io.csr_info.priv

    io.expt <> ptw.io.expt

    // io.external.ram <> translator.io.in(0)
    // io.external.serial <> translator.io.in(1)
    // io.external.irq_client <> translator.io.in(2)
    // translator.io.out

    translator.io.out.adr_i := Mux(tlb.io.valid, tlb.io.paddr, ptw.io.mem.addr)
    translator.io.out.dat_i := Mux(tlb.io.valid, req_reg.data_wr, ptw.io.mem.addr)
    translator.io.out.sel_i := Mux(tlb.io.valid, req_reg.sel, "b1111".U(4.W))
    translator.io.out.stb_i := Mux(tlb.io.valid, phase_1 || prev_cache_hit, ptw.io.mem.request)
    translator.io.out.cyc_i := true.B
    translator.io.out.we_i := Mux(tlb.io.valid, req_reg.wen, false.B) // ptw never writes

    val paddr_reg_accessing = RegInit(Bool(), false.B)
    val ptw_reg_accessing = RegInit(Bool(), false.B)

    paddr_reg_accessing := tlb.io.valid // if tlb is valid in the prev cycle, then in the next cycle r/w is finished
    ptw_reg_accessing := ptw.io.mem.request // ptw asks for memory access
    assert(!(paddr_reg_accessing && ptw_reg_accessing)) // only 1 in 2 cases is allowed

    ptw.io.mem.data := Mux(ptw_reg_accessing, translator.io.out.dat_o, 0.U(32.W))
    ptw.io.mem.valid := Mux(ptw_reg_accessing, !(translator.io.out.stall_o), false.B)

    io.res.data_rd := Mux(paddr_reg_accessing, translator.io.out.dat_o, 0.U(32.W))
    io.res.locked := !prev_cache_hit && (translator.io.out.stall_o || phase_1 || !paddr_reg_accessing)
    io.res.err :=  Mux(paddr_reg_accessing, translator.io.out.err_o, false.B)
}

class MMUWrapperTester() extends BasicTester{
	val mmu = Module(new TMMUWrapper())

	val (cntr, done) = Counter(true.B, 40)
	

	mmu.io.req.cmd := 0.U
	mmu.io.req.ren := false.B
	mmu.io.req.data_wr := 0.U
	mmu.io.req.addr := 0.U
	mmu.io.req.wen := false.B
	mmu.io.req.sel := 0.U

	mmu.io.csr_info.base_ppn := MMUTestConsts.ptbase_ppn
	mmu.io.csr_info.passthrough := false.B
	mmu.io.csr_info.asid := 0.U
	mmu.io.csr_info.priv := 0.U
	mmu.io.csr_info.tlb_flush := false.B

	printf("At time: %d, mmu stall state: %d, mmu data: %x\n",cntr, mmu.io.res.locked, mmu.io.res.data_rd)

	when(cntr === 1.U){
		printf("At time %d, We start to read %x\n",cntr, MMUTestConsts.vaddr1)
		mmu.io.req.addr := MMUTestConsts.vaddr1
		mmu.io.req.data_wr := 0.U
		mmu.io.req.sel := "b1111".U(4.W)
		mmu.io.req.wen := false.B
		mmu.io.req.ren := true.B
		mmu.io.req.cmd := MemoryConsts.Load
	}

	when(cntr === 9.U){
		printf("At time %d, We start to read %x\n", cntr, MMUTestConsts.vaddr2)
		mmu.io.req.addr := MMUTestConsts.vaddr2
		mmu.io.req.data_wr := 0.U
		mmu.io.req.sel := "b1111".U(4.W)
		mmu.io.req.wen := false.B
		mmu.io.req.ren := true.B
		mmu.io.req.cmd := MemoryConsts.Load
	}


	when(done) { 
		// when(tlb.io.valid){
		// 	printf("Final Physical Address: %x\n", tlb.io.paddr)
		// 	assert(tlb.io.paddr === TestConsts.paddr)
		// }
		stop(); stop() 
	}


}

class MMUWrapperTests extends org.scalatest.FlatSpec {
    "MMUWrapperTests" should "pass" in {
        assert(TesterDriver execute (() => new MMUWrapperTester()))
        //assert(1 === 2)
    }
}