package systemoncat.mmu

import chisel3._
import chisel3.util._
import systemoncat.core._
import systemoncat.sysbus._

class MMURequest extends Bundle {
    val addr = UInt(32.W)
    val data_wr = UInt(32.W)
    val sel = UInt(4.W)
    val wen = Bool()
    val ren = Bool()
    val cmd = UInt(2.W)
}

class MMUResponse extends Bundle {
    val data_rd = Output(UInt(32.W))
    val locked = Output(Bool())
    val err = Output(Bool())
}

class CSRInfo extends Bundle {
    val base_ppn = Input(UInt(MemoryConsts.PPNLength.W))
    val passthrough = Input(Bool())
    val asid = Input(UInt(MemoryConsts.ASIDLength.W))
    val priv = Input(UInt(2.W))
    val tlb_flush = Input(Bool())
}

class MMUException extends Bundle {
    val iPF = Output(Bool())
    val lPF = Output(Bool())
    val sPF = Output(Bool())

    //Page Fault PTE
    val pf_vaddr = Output(UInt(MemoryConsts.VaLength.W))
}

class MMUWrapperIO extends Bundle {
    val req = Input(new MMURequest)
    val res = new MMUResponse
    val external = new SysBusExternal
    val csr_info = new CSRInfo
    val expt = new MMUException 
}

class MMUWrapper(map : Seq[(BitPat, UInt)], slaves : Seq[SysBusSlave]) extends Module {
    val io = IO(new MMUWrapperIO)
    val ptw = Module(new PTW)
    val tlb = Module(new TLB)
    val translator = Module(new SysBusTranslator(map, slaves))
    //val translator = Module(new DummyTranslator)

    val phase_1 = RegInit(false.B)
    val req_reg = RegInit(0.U.asTypeOf(new MMURequest()))
    val prev_cache_hit = false.B // placeholder for future cache support. TODO: implement me

    val paddr_reg_accessing = RegInit(Bool(), true.B)
    val paddr_reg_pagefault = RegInit(Bool(), false.B)
    val ptw_reg_accessing = RegInit(Bool(), false.B)

    val page_fault = ptw.io.expt.iPF | ptw.io.expt.lPF | ptw.io.expt.sPF
    
    req_reg := Mux(page_fault, 0.U.asTypeOf(new MMURequest()),
        Mux(phase_1 && !prev_cache_hit, req_reg, io.req)) 

    phase_1 := Mux(page_fault, false.B, Mux(phase_1 && !prev_cache_hit, !tlb.io.valid, io.req.wen || io.req.ren))
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
    io.external.ram <> translator.io.in(0)
    io.external.ram2 <> translator.io.in(1)
    io.external.serial <> translator.io.in(2)
    io.external.irq_client <> translator.io.in(3)
    io.external.plic <> translator.io.in(4)
    io.external.flash <> translator.io.in(5)
    io.external.rom <> translator.io.in(6)

    // translator.io.out

    translator.io.out.adr_i := Mux(tlb.io.valid, tlb.io.paddr, ptw.io.mem.addr)
    translator.io.out.dat_i := Mux(tlb.io.valid, req_reg.data_wr, ptw.io.mem.addr)
    translator.io.out.sel_i := Mux(tlb.io.valid, req_reg.sel, "b1111".U(4.W))
    translator.io.out.stb_i := Mux(tlb.io.valid, phase_1 || prev_cache_hit, ptw.io.mem.request)
    translator.io.out.cyc_i := true.B
    translator.io.out.we_i := Mux(tlb.io.valid, req_reg.wen, false.B) // ptw never writes

    paddr_reg_accessing := tlb.io.valid // if tlb is valid in the prev cycle, then in the next cycle r/w is finished
    ptw_reg_accessing := ptw.io.mem.request // ptw asks for memory access
    paddr_reg_pagefault := page_fault

    assert(!(paddr_reg_accessing && ptw_reg_accessing)) // only 1 in 2 cases is allowed

    ptw.io.mem.data := Mux(ptw_reg_accessing, translator.io.out.dat_o, 0.U(32.W))
    ptw.io.mem.valid := Mux(ptw_reg_accessing, !(translator.io.out.stall_o), false.B)

    io.res.data_rd := Mux(paddr_reg_accessing, translator.io.out.dat_o, 0.U(32.W))
    io.res.locked := !prev_cache_hit && (translator.io.out.stall_o || phase_1 || !(paddr_reg_accessing || paddr_reg_pagefault))
    io.res.err :=  Mux(paddr_reg_accessing, translator.io.out.err_o, false.B)

}
