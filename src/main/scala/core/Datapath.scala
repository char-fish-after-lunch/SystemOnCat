package systemoncat.core

import chisel3._
import chisel3.util._
import systemoncat.mmu._

class DatapathIO() extends Bundle {
    val ctrl = Flipped(new DecoderIO)
    val debug_devs = new DebugDevicesIO()
    val imem = Flipped(new IFetchCoreIO)
    val dmem = Flipped(new DMemCoreIO)
    val irq_client = Flipped(new ClientIrqIO)
    val mmu_csr_info = Flipped(new CSRInfo())
    val mmu_expt = Flipped(new MMUException())
    val ext_irq_r = Input(Bool())
}

class Datapath() extends Module {
    val io = IO(new DatapathIO())

    val ex_ctrl_sigs = RegInit(0.U.asTypeOf(new ControlSignals))
    val mem_ctrl_sigs = RegInit(0.U.asTypeOf(new ControlSignals))
    val wb_ctrl_sigs = RegInit(0.U.asTypeOf(new ControlSignals))

    val pc_reg_valid = RegInit(Bool(), false.B) // is a valid inst 
    val id_reg_valid = RegInit(Bool(), false.B)
    val ex_reg_valid = RegInit(Bool(), false.B)
    val mem_reg_valid = RegInit(Bool(), false.B)
    val wb_reg_valid = RegInit(Bool(), false.B)

    val id_functioning = Wire(Bool()) // is allowed to execute
    val ex_functioning = Wire(Bool())
    val mem_functioning = Wire(Bool())
    val wb_functioning = Wire(Bool())
    // a stage A can be invalid because:
    //   1. pipeline has been stalled / flushed, stage A is keeping garbage data.
    //   2. this instruction raised(will raise) an exception.
    // in both cases, the instruction should not be allowed to r/w memory & registers,
    // but in case 2, the exception caused by inst should be passed into CSR, while in case 1 we shouldn't.
    // that's why we need 2 signals(valid & functioning) for each stage.

    val dmem_locked = Wire(Bool())
    val imem_locked = Wire(Bool())
    val imem_pending = Wire(Bool())

    val ex_reg_inst = RegInit(Bits(), NOP) // original instruction
    val mem_reg_inst = RegInit(Bits(), NOP)
    val wb_reg_inst = RegInit(Bits(), NOP)

    val ex_waddr = ex_reg_inst(11,7) // rd can be directly extracted from inst
    val mem_waddr = mem_reg_inst(11,7)
    val wb_waddr = wb_reg_inst(11,7)

    val pc_expt = Wire(Bool())
    val id_reg_expt = RegInit(Bool(), false.B)
    val id_expt = Wire(Bool())
    val ex_reg_expt = RegInit(Bool(), false.B)
    val mem_reg_expt = RegInit(Bool(), false.B)
    val mem_expt = Wire(Bool())
    val wb_reg_expt = RegInit(Bool(), false.B)

    val pc_reg_page_fault = RegInit(Bool(), false.B)
    val pc_page_fault = Wire(Bool())

    val id_reg_cause = RegInit(UInt(), 0.U(4.W)) // exception cause
    val ex_reg_cause = RegInit(UInt(), 0.U(4.W))
    val mem_reg_cause = RegInit(UInt(), 0.U(4.W))
    val mem_cause = Wire(UInt(4.W))

    val id_reg_expt_val = RegInit(UInt(), 0.U(32.W)) // addr stored for mtval
    val ex_reg_expt_val = RegInit(UInt(), 0.U(32.W))
    val mem_reg_expt_val = RegInit(UInt(), 0.U(32.W))
    val mem_expt_val = Wire(UInt())

    val id_reg_pc = RegInit(UInt(), 0.U(32.W))
    val ex_reg_pc = RegInit(UInt(), 0.U(32.W))
    val mem_reg_pc = RegInit(UInt(), 0.U(32.W))
    val wb_reg_pc = RegInit(UInt(), 0.U(32.W))

    val mem_reg_rs2 = RegInit(UInt(), 0.U(32.W)) // used as store address
    val dmem_reg = RegInit(UInt(), 0.U(32.W))

    val mem_reg_wdata = RegInit(Bits(), 0.U(32.W)) // data for write back
    val wb_reg_wdata = RegInit(Bits(), 0.U(32.W))
    val wb_reg_wdata_forward = RegInit(Bits(), 0.U(32.W))

    val ex_reg_imme = RegInit(UInt(), 0.U(32.W)) // 32 bit immediate, sign extended if necessary

    val csr_branch = Wire(Bool()) // when csr branch happens, all prev stages will be flushed
    val tlb_flush_pipe = Wire(Bool())
    val tlb_flush_pipe_npc = Wire(Bits())
    val tlb_reg_flush_pipe = RegInit(Bool(), false.B)
    val tlb_reg_flush_pipe_npc = RegInit(Bits(), 0.U(32.W))
    val tlb_pipe_branch = Wire(Bool())
    val tlb_pipe_branch_target = Wire(Bits())
    // when sfence.vma/satp writing happens, flush all stages

    // ---------- NPC ----------
    val pc = RegInit((0xffffffc0 - 4).S(32.W).asUInt) // initial pc
    // val pc = RegInit((-4).S(32.W).asUInt) // initial pc
    val ex_branch_mistaken = Wire(Bool())
    val ex_branch_target = Wire(UInt())

    val id_jump_expected = Wire(Bool())
    val id_branch_target = Wire(UInt())

    val id_exe_data_hazard = Wire(Bool())
    val id_csr_data_hazard = Wire(Bool())
    val pc_stall = id_exe_data_hazard || id_csr_data_hazard || imem_locked || dmem_locked || imem_pending
    pc_reg_valid := Mux(pc_stall, pc_reg_valid, true.B) && (!csr_branch) && (!tlb_flush_pipe)

    val pc_reg_locked = RegInit(Bool(), false.B)
    val id_replay = Wire(Bool()) //happens when stalled
    val ex_replay = Wire(Bool())
    val mem_replay = Wire(Bool())
    val mem_reg_replay = RegInit(Bool(), false.B)
    val wb_replay = Wire(Bool())

    val csr_epc = Wire(UInt())
    val mem_eret = Wire(Bool())
    val csr_evec = Wire(UInt())
    val mem_interp = Wire(Bool()) // used to flush pipeline at the end of MEM
    val prev_mem_interp = RegInit(Bool(), false.B) 
    val mem_has_expt = Wire(Bool()) // used to flush pipeline at the end of MEM
    val prev_mem_has_expt = RegInit(Bool(), false.B) 
    // interrupt signal from csr keeps only for 1 cycle, therefore it has to be kept for future reference

    val csr_reg_epc = RegInit(UInt(), 0.U(32.W))
    val wb_reg_eret = RegInit(Bool(), false.B)
    val csr_reg_evec = RegInit(UInt(), 0.U(32.W))
    val wb_reg_has_expt = RegInit(Bool(), false.B)
    val wb_reg_interp = RegInit(Bool(), false.B) // used to decide next pc at the beginning of WB

    val npc = Mux(imem_locked || dmem_locked, pc, // pc stall because of imem lock (avoid mem access FSM interruption)
        Mux(wb_reg_interp || wb_reg_has_expt, csr_reg_evec, // an interrupt/exception happened
        Mux(wb_reg_eret && wb_functioning, csr_reg_epc,  // eret executed
        Mux(tlb_pipe_branch, tlb_pipe_branch_target, // jump when tlb flushed
        Mux(ex_branch_mistaken, ex_branch_target, // branch prediction failed
        Mux(id_jump_expected, id_branch_target, // branch prediction
        Mux(pc_stall, pc, pc + 4.U))))))) // pc stall
    pc := npc
    val inst_reg = RegInit(NOP) // instruction in IF

    // ---------- IF -----------
    io.imem.pc := npc
    imem_locked := io.imem.locked
    imem_pending := io.imem.pending

    // ---------- ID -----------
    // regs update

    io.ctrl.inst := inst_reg
    pc_reg_locked := imem_locked
    val pc_access_expt = io.imem.pc_invalid_expt || io.imem.pc_err_expt
    val pc_cur_page_fault = io.mmu_expt.iPF
    pc_reg_page_fault := Mux(pc_reg_locked, pc_reg_page_fault || pc_cur_page_fault, pc_cur_page_fault)
    pc_page_fault := pc_reg_page_fault || pc_cur_page_fault
    pc_expt := pc_access_expt || pc_page_fault
    // if a page fault happens during inst fetching, then info has to be recorded
    // and passed to next stages until collected by CSR in MEM.
    
    inst_reg := Mux(id_replay, inst_reg, io.imem.inst) 
    id_reg_pc := Mux(id_replay, id_reg_pc, pc) 
    id_replay := id_exe_data_hazard || id_csr_data_hazard || dmem_locked || imem_locked
    id_reg_valid := ((!pc_stall && pc_reg_valid) || (id_replay && id_reg_valid)) &&
         (!csr_branch) && (!id_jump_expected) && (!ex_branch_mistaken) && (!tlb_flush_pipe)
    // if pc stalled because of imem/dmem hazard, prev ID is duplicated and should be invalidated
    // but if pc stalled because of ID/EXE(or ID/CSR) hazard, then ID is also stalled and should be kept
    
    id_reg_expt := Mux(id_replay, id_reg_expt, pc_expt && pc_reg_valid && !pc_stall)
    id_reg_cause := Mux(id_replay, id_reg_cause,
        Mux(io.imem.pc_invalid_expt, Cause.IAM(3, 0), 
        Mux(io.imem.pc_err_expt, Cause.IAF(3, 0), 
        Mux(pc_page_fault, Cause.IPF(3, 0), 0.U(4.W)))))
    id_reg_expt_val := Mux(id_replay, id_reg_expt_val, 
        Mux(pc_expt, pc, 0.U(32.W)))

    id_expt := id_reg_expt || (!io.ctrl.sig.legal && pc_reg_valid)
    id_functioning := id_reg_valid && (!id_expt) && (!id_replay)

    val id_rs1 = inst_reg(19, 15) // rs1
    val id_rs2 = inst_reg(24, 20) // rs2
    val id_rd  = inst_reg(11, 7)  // rd
    val id_imme = ImmGen(io.ctrl.sig.imm_sel, inst_reg) // immediate
    val id_ren = IndexedSeq(io.ctrl.sig.rxs1, io.ctrl.sig.rxs2)
    val regfile = Module(new RegFile)
    regfile.io.raddr1 := id_rs1
    regfile.io.raddr2 := id_rs2
    regfile.io.wen := wb_ctrl_sigs.wb_en && wb_functioning
    regfile.io.waddr := wb_waddr
    val reg_write = Wire(UInt()) // assigned later
    regfile.io.wdata := reg_write

    val id_raddr = IndexedSeq(id_rs1, id_rs2)
    val id_rdatas = IndexedSeq(regfile.io.rdata1, regfile.io.rdata2)
    val bypass_sources = IndexedSeq( // has priority !
        (true.B, 0.U, 0.U), // x0 = 0
        (ex_functioning && ex_ctrl_sigs.wb_en && !ex_ctrl_sigs.mem, ex_waddr, mem_reg_wdata),
        (mem_functioning && mem_ctrl_sigs.wb_en, mem_waddr, reg_write),
        (wb_functioning && wb_ctrl_sigs.wb_en, wb_waddr, wb_reg_wdata_forward))

    val id_bypass_src = id_raddr.map(raddr => bypass_sources.map(s => s._1 && s._2 === raddr))
    id_exe_data_hazard := ex_functioning && ex_ctrl_sigs.wb_en && ex_ctrl_sigs.mem &&
        ((io.ctrl.sig.rxs1 && ex_waddr === id_rs1) || (io.ctrl.sig.rxs2 && ex_waddr === id_rs2)) 
    // if a data loaded from RAM is immediately used, pipeline must be stalled

    id_csr_data_hazard := (ex_functioning && ex_ctrl_sigs.wb_en && ex_ctrl_sigs.csr_cmd =/= CSR.N &&
        ((io.ctrl.sig.rxs1 && ex_waddr === id_rs1) || (io.ctrl.sig.rxs2 && ex_waddr === id_rs2))) ||
        (mem_functioning && mem_ctrl_sigs.wb_en && mem_ctrl_sigs.csr_cmd =/= CSR.N &&
        ((io.ctrl.sig.rxs1 && mem_waddr === id_rs1) || (io.ctrl.sig.rxs2 && mem_waddr === id_rs2)))
    // data loaded from CSR can only be used after 2 clocks, pipeline must be stalled before that

    id_jump_expected := id_functioning && ((io.ctrl.sig.branch && inst_reg(31)) || io.ctrl.sig.jal)
    id_branch_target := id_reg_pc + id_imme
    // when jumping backwards, assume the branch will be taken (suggested static branch in risc-v spec)
    // reasonably, jal is always predicted taken
    // however, the branch target of jalr cannot be confirmed till EXE, so it's ignored.

    // ---------- EXE ----------
    // regs update

    ex_replay := dmem_locked || imem_locked
    when (!ex_replay && id_functioning) {
        ex_ctrl_sigs := io.ctrl.sig
        ex_reg_imme := id_imme
        ex_reg_inst := inst_reg
        ex_reg_pc := id_reg_pc
    }
    ex_reg_valid := ((id_reg_valid && !id_replay) || (ex_replay && ex_reg_valid)) &&
        (!ex_branch_mistaken) && (!csr_branch) && (!tlb_flush_pipe)

    ex_reg_expt := Mux(ex_replay, ex_reg_expt, id_expt && id_reg_valid && (!id_replay))
    ex_reg_cause := Mux(ex_replay, ex_reg_cause,
        Mux(id_reg_expt && id_reg_valid, id_reg_cause,
        Mux((!io.ctrl.sig.legal), Cause.II(3, 0), 0.U(4.W))))

    ex_reg_expt_val := Mux(ex_replay, ex_reg_expt_val, 
        Mux(id_reg_expt && id_reg_valid, id_reg_expt_val, 0.U(4.W)))

    ex_functioning := ex_reg_valid && (!ex_reg_expt) && (!ex_replay) && (!csr_branch) && (!tlb_flush_pipe)

    // bypass logic
    val ex_reg_rs_bypass = Reg(Vec(id_raddr.size, Bool())) // if this reg can be bypassed
    val ex_reg_rdatas = Reg(Vec(id_raddr.size, Bits())) // reg datas
    val ex_reg_bypass_src = Reg(Vec(id_raddr.size, UInt(log2Ceil(bypass_sources.size).W))) // which bypass is taken
    val bypass_mux = Seq(
        0.U -> 0.U,
        1.U -> mem_reg_wdata,
        2.U -> reg_write,
        3.U -> wb_reg_wdata_forward
    )
    for (i <- 0 until id_raddr.size) {
        val do_bypass = id_bypass_src(i).reduce(_ || _) // at least one bypass is possible
        val bypass_src = PriorityEncoder(id_bypass_src(i))
        when (!ex_replay && id_functioning) {   
            ex_reg_rs_bypass(i) := do_bypass // bypass is checked at ID, but done in EXE
            ex_reg_bypass_src(i) := bypass_src
            ex_reg_rdatas(i) := id_rdatas(i)
        }
    }

    val ex_rs = for (i <- 0 until id_raddr.size)
        yield Mux(ex_reg_rs_bypass(i), MuxLookup(ex_reg_bypass_src(i), ex_reg_rdatas(i), bypass_mux), ex_reg_rdatas(i))

    // alu logic
    val ex_op1 = MuxLookup(ex_ctrl_sigs.A1_sel, 0.U, Seq(
        A1_RS1 -> ex_rs(0),
        A1_PC -> ex_reg_pc))
    val ex_op2 = MuxLookup(ex_ctrl_sigs.A2_sel, 0.U, Seq(
        A2_RS2 -> ex_rs(1),
        A2_IMM -> ex_reg_imme,
        A2_SIZE -> 4.U))

    val alu = Module(new ALU)
    alu.io.fn := ex_ctrl_sigs.alu_op
    alu.io.in1 := ex_op1
    alu.io.in2 := ex_op2

    val ex_branch_mispredicted = Mux(ex_ctrl_sigs.branch, ex_reg_imme(31) ^ alu.io.cmp_out, // mistaken if pred =/= real 
        Mux(ex_ctrl_sigs.jal, false.B, // jal is always taken
        Mux(ex_ctrl_sigs.jalr, true.B, false.B))) // jalr is always not taken
    ex_branch_mistaken := ex_functioning && ex_branch_mispredicted

    ex_branch_target := Mux(ex_ctrl_sigs.jalr, alu.io.out,
        Mux(ex_ctrl_sigs.branch, Mux(alu.io.cmp_out, ex_reg_pc + ex_reg_imme, ex_reg_pc + 4.U), 0.U(32.W)))

    // ---------- MEM ----------
    // regs update

    when (!mem_replay && ex_functioning) {
        mem_ctrl_sigs := ex_ctrl_sigs
        mem_reg_pc := ex_reg_pc
        mem_reg_inst := ex_reg_inst
        mem_reg_rs2 := ex_rs(1)
        mem_reg_wdata := alu.io.out
    }
    // alu.io.cmp_out decides whether a branch is taken

    mem_replay := dmem_locked || imem_locked
    mem_reg_replay := mem_replay
    mem_reg_valid := ((ex_reg_valid && !ex_replay) || (mem_replay && mem_reg_valid)) && (!csr_branch) && (!tlb_flush_pipe)
    io.dmem.req.wr_data := ex_rs(1)
    io.dmem.req.addr := alu.io.out
    io.dmem.req.wr_en := ex_functioning && ex_ctrl_sigs.mem && isWrite(ex_ctrl_sigs.mem_cmd)
    io.dmem.req.rd_en := ex_functioning && ex_ctrl_sigs.mem && isRead(ex_ctrl_sigs.mem_cmd)
    io.dmem.req.lr_en := ex_functioning && ex_ctrl_sigs.mem && ex_ctrl_sigs.mem_cmd === MOP_LR
    io.dmem.req.sc_en := ex_functioning && ex_ctrl_sigs.mem && ex_ctrl_sigs.mem_cmd === MOP_SC
    io.dmem.req.amo_en := ex_functioning && ex_ctrl_sigs.mem && ex_ctrl_sigs.mem_cmd === MOP_A
    // tricky. 

    io.dmem.req.mem_type := ex_ctrl_sigs.mem_type
    io.dmem.req.amo_op := ex_ctrl_sigs.amo_op

    when (!mem_replay && !ex_replay) {
        mem_reg_expt := ex_reg_expt && ex_reg_valid
        mem_reg_cause := ex_reg_cause // no exception would happen during EXE
        mem_reg_expt_val := ex_reg_expt_val
    }

    mem_expt := (mem_reg_expt && mem_reg_valid) || io.dmem.res.expt.wr_addr_invalid_expt || io.dmem.res.expt.wr_access_err_expt ||
         io.dmem.res.expt.rd_addr_invalid_expt || io.dmem.res.expt.rd_access_err_expt || io.mmu_expt.lPF || io.mmu_expt.sPF
    // this is a wire, so that exception can be handled before next posclk

    mem_cause := PriorityMux(Seq(
            (mem_reg_expt && mem_reg_valid, mem_reg_cause), 
            (io.dmem.res.expt.wr_addr_invalid_expt, Cause.SAM(3, 0)),
            (io.dmem.res.expt.rd_addr_invalid_expt, Cause.LAM(3, 0)),
            (io.dmem.res.expt.wr_access_err_expt, Cause.SAF(3, 0)), 
            (io.dmem.res.expt.rd_access_err_expt, Cause.LAF(3, 0)),
            (io.mmu_expt.lPF, Cause.LPF(3, 0)),
            (io.mmu_expt.sPF, Cause.SPF(3, 0))
        ))
    // Exception may happen at any time during memory accessing. (page fault)
    // However, once an exception signal occurs, CSR immediately stores it and triggers another signal.
    // So here we don't need to worry about being unable to deal with incoming exception/interrupt when pipeline is stuck.

    mem_expt_val := Mux(mem_reg_expt && mem_reg_valid, mem_reg_expt_val,
        Mux(mem_expt, alu.io.out, 0.U(32.U)))
    
    mem_functioning := mem_reg_valid && (!mem_expt) && (!mem_replay)

    // ---------- CSR & Interrupt Client -----------
    val csr = Module(new CSRFile)

    val last_valid_pc_from_wb = Mux(mem_reg_valid, mem_reg_pc, 
        Mux(ex_reg_valid, ex_reg_pc, 
        Mux(id_reg_valid, id_reg_pc, pc)))
    // I guess there should be at least one valid instruction in pipeline...
    // also, even if the instruction will cause an exception, it's still valid.
    // so we check 'valid' instead of 'functioning' here.
    csr.io.next_pc := Mux(mem_reg_valid, !(mem_ctrl_sigs.jal || mem_ctrl_sigs.jalr || mem_ctrl_sigs.branch), false.B)
    csr.io.inst := wb_reg_inst 

    csr.io.pc := last_valid_pc_from_wb
    csr.io.addr := Mux(io.mmu_expt.sPF || io.mmu_expt.lPF, io.mmu_expt.pf_vaddr, mem_expt_val)

    csr.io.csr_ena := wb_functioning && (wb_ctrl_sigs.csr_cmd =/= CSR.N)
    csr.io.csr_rd_en := wb_functioning && (wb_ctrl_sigs.csr_cmd =/= CSR.N)
    csr.io.csr_wr_en := wb_functioning && (wb_ctrl_sigs.csr_cmd =/= CSR.N)

    csr.io.csr_idx := wb_reg_inst(31,20)

    csr.io.ext_irq_r := io.ext_irq_r
    csr.io.sft_irq_r := io.irq_client.sft_irq_r
    csr.io.tmr_irq_r := io.irq_client.tmr_irq_r

    csr.io.csr_cmd := Mux(wb_functioning, wb_ctrl_sigs.csr_cmd, CSR.N)

    csr.io.pcIv := mem_expt && (mem_cause === Cause.IAM(3, 0)) && mem_reg_valid
    csr.io.instIv := mem_expt && (mem_cause === Cause.II(3, 0)) && mem_reg_valid
    csr.io.laddrIv := mem_expt && (mem_cause === Cause.LAM(3, 0)) && mem_reg_valid
    csr.io.saddrIv := mem_expt && (mem_cause === Cause.SAM(3, 0)) && mem_reg_valid
    
    // Trap Instruction
    val wb_is_eret = wb_reg_inst === MRET || wb_reg_inst === URET || wb_reg_inst === SRET
    csr.io.isEcall := mem_reg_inst === ECALL && mem_functioning
    csr.io.isEbreak := mem_reg_inst === EBREAK && mem_functioning
    csr.io.isEret := wb_is_eret && wb_functioning
    
    // page Fault. TODO: page fault triggering should be carefully examined
    csr.io.iPF := mem_expt && (mem_cause === Cause.IPF(3, 0)) && mem_reg_valid //Instruction Page Fault 
    csr.io.lPF := io.mmu_expt.lPF //Load Page Fault
    csr.io.sPF := io.mmu_expt.sPF //Store Page Fault
    // iPF comes from IF stage, lPF/sPF comes from MEM stage

    // write data
    csr.io.wb_csr_dat := wb_reg_wdata

    val mem_is_sfence = mem_reg_valid && mem_reg_inst === SFENCE_VMA
    val mem_will_write_satp = mem_reg_valid && mem_ctrl_sigs.csr_cmd =/= CSR.N && mem_reg_inst(31, 20) === CSRConsts.SATP
    tlb_flush_pipe := (mem_is_sfence || mem_will_write_satp) && mem_functioning
    tlb_flush_pipe_npc := mem_reg_pc + 4.U(32.W)

    io.mmu_csr_info.base_ppn := csr.io.baseppn
    io.mmu_csr_info.passthrough := !csr.io.mode
    io.mmu_csr_info.asid := csr.io.asid
    io.mmu_csr_info.priv := csr.io.priv
    io.mmu_csr_info.tlb_flush := tlb_flush_pipe
    // TODO: check correctness of SFENCE.VMA & tlb flush

    val mem_has_interrupt = csr.io.interrupt
    val mem_has_exception = csr.io.expt
    val cur_mem_interp = mem_has_interrupt
    val cur_mem_expt = mem_has_exception
    prev_mem_interp := Mux(mem_reg_replay, prev_mem_interp || mem_has_interrupt, mem_has_interrupt) 
    mem_interp := mem_has_interrupt || prev_mem_interp
    prev_mem_has_expt := Mux(mem_reg_replay, prev_mem_interp || mem_has_exception, mem_has_exception) 
    mem_has_expt := mem_has_exception || prev_mem_has_expt
    // tricky. collect all interrupts happened during memory stall.

    mem_eret := (mem_reg_inst === MRET || mem_reg_inst === URET || mem_reg_inst === SRET) && mem_functioning
    csr_branch := (mem_interp || mem_has_expt || mem_eret) && (!mem_replay)
    csr_epc := csr.io.epc
    csr_evec := csr.io.evec

    // ---------- WB -----------
    wb_replay := dmem_locked || imem_locked

    when (!wb_replay && !mem_replay) {
        // tricky. Exception happens only when MEM is valid,
        // but interrupt can happen at any time.
        when (mem_reg_valid) {
            wb_reg_has_expt := mem_has_expt
            tlb_reg_flush_pipe := tlb_flush_pipe
            tlb_reg_flush_pipe_npc := tlb_flush_pipe_npc
        }
        wb_reg_interp := mem_interp

        csr_reg_epc := csr_epc
        csr_reg_evec := csr_evec        

        when (mem_reg_valid) {
            wb_reg_expt := mem_expt
        }
    }

    tlb_pipe_branch := tlb_reg_flush_pipe && wb_functioning
    tlb_pipe_branch_target := tlb_reg_flush_pipe_npc

    when (!wb_replay && mem_functioning) {
        wb_ctrl_sigs := mem_ctrl_sigs
        wb_reg_pc := mem_reg_pc
        wb_reg_inst := mem_reg_inst
        wb_reg_wdata := mem_reg_wdata
        wb_reg_eret := mem_eret
        dmem_reg := io.dmem.res.rd_data
    }

    wb_reg_valid := (mem_reg_valid && (!mem_replay)) || (wb_replay && wb_reg_valid)
    
    wb_functioning := wb_reg_valid && (!wb_reg_expt) && (!wb_replay)

    dmem_locked := io.dmem.res.locked

    reg_write := MuxLookup(wb_ctrl_sigs.wb_sel, wb_reg_wdata, Seq(
        WB_MEM -> dmem_reg,
        WB_PC4 -> (wb_reg_pc + 4.U),
        WB_ALU -> wb_reg_wdata,
        WB_CSR -> csr.io.read_csr_dat
    )).asUInt

    when (wb_functioning) {
        wb_reg_wdata_forward := reg_write
    }

    // temporary init
    // io.debug_devs.leds := alu.io.out
    io.debug_devs.leds := MuxLookup(io.debug_devs.dip_sw(1, 0), io.imem.inst, Seq(
        1.U -> io.dmem.req.addr(15, 0), 
        2.U -> Cat(io.dmem.res.rd_data(7, 0), io.dmem.req.wr_data(7, 0)),
        3.U -> Cat(pc_reg_valid, id_reg_valid, ex_reg_valid, mem_reg_valid,
            ex_reg_expt, mem_reg_expt, wb_reg_expt, wb_reg_interp, 
            cur_mem_interp, prev_mem_interp, mem_has_exception, io.ext_irq_r,
            io.irq_client.tmr_irq_r, csr_branch, mem_reg_replay, mem_replay)
    ))
    io.debug_devs.dpy0 := pc(7, 0)
    io.debug_devs.dpy1 := npc(7, 0)



    // debug info below:
    printf("inst[%x] -> %x\n", pc, io.imem.inst)
    printf("alu out: %x\n", alu.io.out)
        // ex_reg_rs_bypass(i) := do_bypass // bypass is checked at IF, but done in EXE
        // ex_reg_bypass_src(i) := bypass_src
        // ex_reg_rdatas(i) := id_rdatas(i)
    printf("bypass check rs1: src=%d, can bypass=%d\n", ex_reg_bypass_src(0), ex_reg_rs_bypass(0))
    printf("bypass check rs2: src=%d, can bypass=%d\n", ex_reg_bypass_src(1), ex_reg_rs_bypass(1))
    printf("bypass sources:\n (%x, %x, %x)\n", mem_reg_wdata, wb_reg_wdata, wb_reg_wdata_forward)
    printf("bypass source regs: (%x, %x, %x)\n", ex_waddr, mem_waddr, wb_waddr)
    printf("regs ind: (%x, %x)\n", id_rs1, id_rs2)
    // 

    printf("ex_branch_target: (%x, taken: %x), ", ex_branch_target, ex_branch_mistaken)
    printf("pc_stall: %x, io.imem.locked: %x\n", pc_stall, io.imem.locked)

}