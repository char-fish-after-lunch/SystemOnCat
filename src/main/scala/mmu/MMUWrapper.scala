package systemoncat.mmu

import chisel3._
import chisel3.util._
import systemoncat.core._
import systemoncat.sysbus._

class MMURequest extends Bundle { // MMURequest is undirected because we need it as a Chisel type later
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
    val csr_info = new CSRInfo
    val expt = new MMUException 
    val bus_request = Flipped(new SysBusSlaveBundle)
}

class MMUWrapper extends Module {
    val io = IO(new MMUWrapperIO)
    val ptw = Module(new PTW)
    val tlb = Module(new TLB)
    //val translator = Module(new DummyTranslator)

    val locked_for_next_req = RegInit(false.B)
    val req_reg = RegInit(0.U.asTypeOf(new MMURequest()))
    val prev_cache_hit = tlb.io.valid && io.bus_request.ack_o
    // placeholder for future cache support. TODO: implement me

    val paddr_reg_pagefault = RegInit(Bool(), false.B)

    val page_fault = ptw.io.expt.iPF | ptw.io.expt.lPF | ptw.io.expt.sPF
    
    req_reg := Mux(page_fault, 0.U.asTypeOf(new MMURequest()),
        Mux(prev_cache_hit || !locked_for_next_req, io.req, req_reg)) 

    locked_for_next_req := Mux(page_fault, false.B, Mux(prev_cache_hit || !locked_for_next_req, io.req.wen || io.req.ren, true.B))
    
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

    io.bus_request.adr_i := Mux(tlb.io.valid, tlb.io.paddr, ptw.io.mem.addr)
    io.bus_request.dat_i := Mux(tlb.io.valid, req_reg.data_wr, ptw.io.mem.addr)
    io.bus_request.sel_i := Mux(tlb.io.valid, req_reg.sel, "b1111".U(4.W))
    io.bus_request.stb_i := Mux(page_fault, false.B, Mux(tlb.io.valid, locked_for_next_req, ptw.io.mem.request)) 
    io.bus_request.cyc_i := true.B
    io.bus_request.we_i := Mux(tlb.io.valid, req_reg.wen, false.B) // ptw never writes

    paddr_reg_pagefault := page_fault

    ptw.io.mem.data := Mux(ptw.io.mem.request && !prev_cache_hit, io.bus_request.dat_o, 0.U(32.W))
    ptw.io.mem.valid := Mux(ptw.io.mem.request, (io.bus_request.ack_o), false.B)

    io.res.data_rd := Mux(prev_cache_hit, io.bus_request.dat_o, 0.U(32.W))
    io.res.locked := !prev_cache_hit && locked_for_next_req && !paddr_reg_pagefault
    io.res.err :=  Mux(prev_cache_hit, io.bus_request.err_o, false.B)

}
