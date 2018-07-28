package systemoncat.mmu

import chisel3._
import chisel3.util._
import chisel3.testers._
import systemoncat.sysbus._

object MMUTestConsts{
    val ptbase_ppn = "h1".U(20.W)
    
    val vaddr1 = "h8".U(32.W)
    val vaddr2 = "h4".U(32.W)
    val vaddr3 = "hf000".U(32.W)
    val pf_vaddr = "h30000000".U(32.W)

    val paddr1 = "h8".U(32.W)
    val paddr2 = "h4".U(32.W)
    val paddr3 = "hf000".U(32.W)
//-------- for vaddr 1,2 --------
    val pte_1_addr = "h1000".U(32.W) //Cat(ptbase_ppn, pte_1_index) << 2
    val pte_1 = "h00000801".U(32.W)
    val pte_2_addr = "h2000".U(32.W) //Cat(pte_1(18,10), pte_2_index) << 2
    val pte_2 = "h000000fe".U(32.W)

//-------- for vaddr 3 -------------
    val pte_3 = "h00003c01".U(32.W)
    val pte_3_addr = "h203c".U(32.W)

//------- for page fault ---------
    val pf_pte1_addr = "h10300".U(32.W)
    val pf_pte1 = "hc001".U(32.W)
    val pf_pte2_addr = "h30000".U(32.W)
    val pf_pte2 = "h73400".U(32.W)

    val data1 = "h1c9f048e".U(32.W)
    val data2 = "hf673cdea".U(32.W)
    val data3 = "hfffffff3".U(32.W)

}

class DummyTranslatorIO extends Bundle{
    val adr_i = Input(UInt(32.W))
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

    val addr = Reg(UInt(32.W))
    val data = Reg(UInt(32.W))

    io.out.dat_o := data
    io.out.stall_o := (state === s_stall) | (state === s_ready)
    io.out.err_o := false.B

    when(state === s_ready){
        printf("Memory: Ready to receive request\n")
        when(io.out.stb_i){
            printf("Memory: Receive request, Addr: %x\n", io.out.adr_i)

            state := s_memory
            addr := io.out.adr_i
        }
    }
    when(state === s_stall){
        state := s_memory
        printf("Memory: Stalling\n")
    }
    when(state === s_memory){
        addr := io.out.adr_i
        printf("Memory: Access Address: %x\n",addr)
        val temp_data = MuxLookup(addr, 0.U, Seq(
            MMUTestConsts.pte_1_addr -> MMUTestConsts.pte_1,
            MMUTestConsts.pte_2_addr -> MMUTestConsts.pte_2,
            MMUTestConsts.pte_3_addr -> MMUTestConsts.pte_3,
            MMUTestConsts.paddr1 -> MMUTestConsts.data1,
            MMUTestConsts.paddr2 -> MMUTestConsts.data2,
        	MMUTestConsts.paddr3 -> MMUTestConsts.data3,
            MMUTestConsts.pf_pte1_addr -> MMUTestConsts.pf_pte1,
            MMUTestConsts.pf_pte2_addr -> MMUTestConsts.pf_pte2
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

    val page_fault = ptw.io.expt.iPF | ptw.io.expt.lPF | ptw.io.expt.sPF
    when(page_fault){
        printf("PAGE FAULT\n")

    }
    printf("MMU: page_fault: %d\n",page_fault)
    printf("MMU: io.expt: %d\n", io.expt.lPF)

    req_reg := Mux(phase_1 && !prev_cache_hit && !page_fault, req_reg, io.req) 

    phase_1 := Mux(phase_1 && !prev_cache_hit, !tlb.io.valid & !page_fault , io.req.wen || io.req.ren)
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

    val expt = Reg(new MMUException())
    expt := ptw.io.expt
    
    io.expt := expt

    printf("MMU: expt: %x\n", io.expt.pf_vaddr)
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

    val paddr_reg_accessing = RegInit(Bool(), true.B)
    val paddr_reg_pagefault = RegInit(Bool(), false.B)
    val ptw_reg_accessing = RegInit(Bool(), false.B)

    paddr_reg_accessing := tlb.io.valid // if tlb is valid in the prev cycle, then in the next cycle r/w is finished
    ptw_reg_accessing := ptw.io.mem.request // ptw asks for memory access
    paddr_reg_pagefault := page_fault
    assert(!(paddr_reg_accessing && ptw_reg_accessing)) // only 1 in 2 cases is allowed

    ptw.io.mem.data := Mux(ptw_reg_accessing, translator.io.out.dat_o, 0.U(32.W))
    ptw.io.mem.valid := Mux(ptw_reg_accessing, !(translator.io.out.stall_o), false.B)

    io.res.data_rd := Mux(paddr_reg_accessing, translator.io.out.dat_o, 0.U(32.W))
    io.res.locked := !prev_cache_hit && (translator.io.out.stall_o || phase_1 || !(paddr_reg_accessing || paddr_reg_pagefault))
    io.res.err :=  Mux(paddr_reg_accessing, translator.io.out.err_o, false.B)

    printf("MMU: state: %d\n", phase_1)
}

class MMUWrapperTester() extends BasicTester{
	val mmu = Module(new TMMUWrapper())

	val (cntr, done) = Counter(true.B, 50)
	

	mmu.io.req.cmd := 0.U
	mmu.io.req.ren := (cntr === 4.U) | (cntr === 15.U) | (cntr === 27.U)
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

	when(cntr === 4.U){
		printf("At time %d, We start to read %x\n",cntr, MMUTestConsts.vaddr1)
		mmu.io.req.addr := MMUTestConsts.vaddr1
		mmu.io.req.sel := "b1111".U(4.W)
		mmu.io.req.wen := false.B
		mmu.io.req.cmd := MemoryConsts.Load
	}

	when(cntr === 15.U){
		printf("At time %d, We start to read %x\n", cntr, MMUTestConsts.vaddr2)
		mmu.io.req.addr := MMUTestConsts.vaddr2
		mmu.io.req.sel := "b1111".U(4.W)
		mmu.io.req.wen := false.B
		mmu.io.req.cmd := MemoryConsts.Load
	}


    when(cntr === 27.U){
        printf("At time %d, We start to read %x\n", cntr, MMUTestConsts.vaddr3)
        mmu.io.req.addr := MMUTestConsts.vaddr3
        mmu.io.req.data_wr := 0.U
        mmu.io.req.sel := "b1111".U(4.W)
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
       // assert(1 === 2)
    }
}