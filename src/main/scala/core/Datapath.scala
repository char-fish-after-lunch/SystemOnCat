package systemoncat.core

import chisel3._
import chisel3.util._

class DatapathIO() extends Bundle {
    val ctrl = Flipped(new DecoderIO)
    val debug_devs = new DebugDevicesIO()
    val imem = Flipped(new IFetchCoreIO)
    val dmem = Flipped(new DMemCoreIO)
    val irq_client = Flipped(new ClientIrqIO)
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


    val ex_reg_inst = RegInit(Bits(), NOP) // original instruction
    val mem_reg_inst = RegInit(Bits(), NOP)
    val wb_reg_inst = RegInit(Bits(), NOP)

    val ex_waddr = ex_reg_inst(11,7) // rd can be directly extracted from inst
    val mem_waddr = mem_reg_inst(11,7)
    val wb_waddr = wb_reg_inst(11,7)

    val pc_expt = Wire(Bool())
    val id_reg_expt = RegInit(Bool(), false.B) // has exception
    val id_expt = Wire(Bool())
    val ex_reg_expt = RegInit(Bool(), false.B)
    val mem_reg_expt = RegInit(Bool(), false.B)
    val mem_expt = Wire(Bool())
    val wb_reg_expt = RegInit(Bool(), false.B)

    val id_reg_cause = RegInit(UInt(), 0.U(4.W)) // exception cause
    val ex_reg_cause = RegInit(UInt(), 0.U(4.W))
    val mem_reg_cause = RegInit(UInt(), 0.U(4.W))
    val mem_cause = Wire(UInt(4.W))

    val id_reg_expt_val = RegInit(UInt(), 0.U(32.W)) // addr stored for mtval
    val ex_reg_expt_val = RegInit(UInt(), 0.U(32.W))
    val mem_reg_expt_val = RegInit(UInt(), 0.U(32.W))
    val mem_expt_val = RegInit(UInt(), 0.U(32.W))

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

    // ---------- NPC ----------
    val pc = RegInit((-4).S(32.W).asUInt) // initial pc
    pc_reg_valid := !csr_branch
    val ex_branch_mistaken = Wire(Bool())
    val ex_branch_target = Wire(UInt())

    val id_jump_expected = Wire(Bool())
    val id_branch_target = Wire(UInt())

    val id_exe_data_hazard = Wire(Bool())
    val id_csr_data_hazard = Wire(Bool())
    val pc_stall = id_exe_data_hazard || id_csr_data_hazard || io.imem.locked
    val id_replay = Wire(Bool())

    val csr_epc = Wire(UInt())
    val mem_eret = Wire(Bool())
    val csr_evec = Wire(UInt())
    val mem_interp = Wire(Bool()) // used to flush pipeline at the end of MEM

    val csr_reg_epc = RegInit(UInt(), 0.U(32.W))
    val wb_reg_eret = RegInit(Bool(), false.B)
    val csr_reg_evec = RegInit(UInt(), 0.U(32.W))
    val wb_reg_interp = RegInit(Bool(), false.B) // used to decide next pc at the beginning of WB

    val npc = Mux(wb_reg_interp, csr_reg_evec, // an interrupt/exception happened
        Mux(wb_reg_eret, csr_reg_epc,  // eret executed
        Mux(ex_branch_mistaken, ex_branch_target, // branch prediction failed
        Mux(id_jump_expected, id_branch_target, // branch prediction
        Mux(pc_stall, pc, pc + 4.U))))) // pc stall
    pc := npc
    val inst_reg = RegInit(NOP) // instruction in IF

    // ---------- IF -----------
    io.imem.pc := npc

    // ---------- ID -----------
    // regs update

    io.ctrl.inst := inst_reg
    pc_expt := io.imem.pc_invalid_expt || io.imem.pc_err_expt
    
    inst_reg := Mux(id_replay, inst_reg, io.imem.inst) 
    id_reg_pc := Mux(id_replay, id_reg_pc, pc) 
    id_replay := id_exe_data_hazard || id_csr_data_hazard
    id_reg_valid := ((!id_jump_expected && !ex_branch_mistaken && !pc_stall && pc_reg_valid) || id_replay) &&
         (!csr_branch)
    // if pc stalled because of imem/dmem hazard, prev ID is duplicated and should be invalidated
    // but if pc stalled because of ID/EXE(or ID/CSR) hazard, then ID is also stalled and should be kept
    
    id_reg_expt := pc_expt && pc_reg_valid
    id_reg_cause := Mux(io.imem.pc_invalid_expt, Cause.IAM(3, 0), 
        Mux(io.imem.pc_err_expt, Cause.IAF(3, 0), 0.U(4.W)))
    id_reg_expt_val := Mux(pc_expt, pc, 0.U(32.W))

    id_expt := id_reg_expt || (!io.ctrl.sig.legal)
    id_functioning := id_reg_valid && (!id_expt)

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

    id_jump_expected := id_reg_valid && ((io.ctrl.sig.branch && inst_reg(31)) || io.ctrl.sig.jal)
    id_branch_target := id_reg_pc + id_imme
    // when jumping backwards, assume the branch will be taken (suggested static branch in risc-v spec)
    // reasonably, jal is always predicted taken
    // however, the branch target of jalr cannot be confirmed till EXE, so it's ignored.

    // ---------- EXE ----------
    // regs update
    when (id_functioning) {
        ex_ctrl_sigs := io.ctrl.sig
        ex_reg_imme := id_imme
        ex_reg_inst := inst_reg
        ex_reg_pc := id_reg_pc
    }
    ex_reg_valid := (!ex_branch_mistaken) && id_reg_valid && (!id_exe_data_hazard) && 
        (!csr_branch) && (!id_csr_data_hazard)
    ex_reg_expt := id_expt && id_reg_valid
    ex_reg_cause := Mux(id_reg_expt && id_reg_valid, id_reg_cause,
        Mux((!io.ctrl.sig.legal), Cause.II(3, 0), 0.U(4.W)))

    ex_reg_expt_val := Mux(id_reg_expt && id_reg_valid, id_reg_expt_val, 0.U(4.W))

    ex_functioning := ex_reg_valid && (!ex_reg_expt)

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
        ex_reg_rs_bypass(i) := do_bypass // bypass is checked at ID, but done in EXE
        ex_reg_bypass_src(i) := bypass_src
        ex_reg_rdatas(i) := id_rdatas(i)
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
    when (ex_functioning) {
        mem_ctrl_sigs := ex_ctrl_sigs
        mem_reg_pc := ex_reg_pc
        mem_reg_inst := ex_reg_inst
        mem_reg_rs2 := ex_rs(1)
        mem_reg_wdata := alu.io.out
    }
    // alu.io.cmp_out decides whether a branch is taken



    mem_reg_valid := ex_reg_valid && (!csr_branch)

    io.dmem.wr_data := ex_rs(1)
    io.dmem.addr := alu.io.out
    io.dmem.wr_en := ex_functioning && ex_ctrl_sigs.mem && isWrite(ex_ctrl_sigs.mem_cmd)
    io.dmem.rd_en := ex_functioning && ex_ctrl_sigs.mem && isRead(ex_ctrl_sigs.mem_cmd)
    io.dmem.mem_type := ex_ctrl_sigs.mem_type

    mem_reg_expt := ex_reg_expt && ex_reg_valid
    mem_reg_cause := ex_reg_cause // no exception would happen during EXE
    mem_reg_expt_val := ex_reg_expt_val

    mem_expt := (mem_reg_expt && mem_reg_valid) || io.dmem.wr_addr_invalid_expt || io.dmem.wr_access_err_expt ||
         io.dmem.rd_addr_invalid_expt || io.dmem.rd_access_err_expt
    // this is a wire, so that exception can be handled before next posclk

    mem_cause := Mux(mem_reg_expt && mem_reg_valid, mem_reg_cause, 
        Mux(io.dmem.wr_addr_invalid_expt, Cause.SAM(3, 0), 
        Mux(io.dmem.rd_addr_invalid_expt, Cause.LAM(3, 0),
        Mux(io.dmem.wr_access_err_expt, Cause.SAF(3, 0), 
        Mux(io.dmem.rd_access_err_expt, Cause.LAF(3, 0), 0.U(4.W))))))

    mem_expt_val := Mux(mem_reg_expt && mem_reg_valid, mem_reg_expt_val,
        Mux(mem_expt, alu.io.out, 0.U(32.U)))
    
    mem_functioning := mem_reg_valid && (!mem_expt)

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
    csr.io.addr := mem_expt_val

    csr.io.csr_ena := wb_functioning && (wb_ctrl_sigs.csr_cmd =/= CSR.N)
    csr.io.csr_rd_en := wb_functioning && (wb_ctrl_sigs.csr_cmd =/= CSR.N)
    csr.io.csr_wr_en := wb_functioning && (wb_ctrl_sigs.csr_cmd =/= CSR.N)

    csr.io.csr_idx := wb_reg_inst(31,20)

    csr.io.ext_irq_r := false.B
    csr.io.sft_irq_r := io.irq_client.sft_irq_r
    csr.io.tmr_irq_r := io.irq_client.tmr_irq_r

    csr.io.csr_cmd := Mux(wb_functioning, wb_ctrl_sigs.csr_cmd, CSR.N)

    csr.io.pcIv := mem_expt && (mem_cause === Cause.IAM(3, 0)) && mem_reg_valid
    csr.io.instIv := mem_expt && (mem_cause === Cause.II(3, 0)) && mem_reg_valid
    csr.io.laddrIv := mem_expt && (mem_cause === Cause.LAM(3, 0)) && mem_reg_valid
    csr.io.saddrIv := mem_expt && (mem_cause === Cause.SAM(3, 0)) && mem_reg_valid
    
    // Trap Instruction
    val wb_is_eret = wb_reg_inst === MRET || wb_reg_inst === URET || wb_reg_inst === SRET
    csr.io.isEcall := wb_reg_inst === ECALL && wb_functioning
    csr.io.isEbreak := wb_reg_inst === EBREAK && wb_functioning
    csr.io.isEret := wb_is_eret && wb_functioning
    
    // page Fault. TODO: this should be assigned after implementing MMU
    csr.io.iPF := false.B //Instruction Page Fault 
    csr.io.lPF := false.B //Load Page Fault
    csr.io.sPF := false.B //Store Page Fault

    // write data
    csr.io.wb_csr_dat := wb_reg_wdata

    // val epc = Output(UInt(32.W))  //EPC

    // val interp = Output(Bool())   // Interrupt Occur
    // val expt = Output(Bool())     // Exception Occur
    // val evec = Output(UInt(32.W)) //Exception Handler Entry

    val mem_has_interrupt = csr.io.interrupt
    val mem_has_exception = csr.io.expt
    mem_interp := mem_has_exception || mem_has_interrupt
    mem_eret := (mem_reg_inst === MRET || mem_reg_inst === URET || mem_reg_inst === SRET) && mem_functioning
    csr_branch := mem_interp || mem_eret
    csr_epc := csr.io.epc
    csr_evec := csr.io.evec

    wb_reg_interp := mem_interp
    wb_reg_eret := mem_eret
    csr_reg_epc := csr_epc
    csr_reg_evec := csr_evec

    // ---------- WB -----------
    when (mem_functioning) {
        wb_ctrl_sigs := mem_ctrl_sigs
        wb_reg_pc := mem_reg_pc
        wb_reg_inst := mem_reg_inst
        wb_reg_wdata := mem_reg_wdata
    }

    wb_reg_valid := mem_reg_valid
    wb_reg_expt := mem_expt && mem_reg_valid
    wb_functioning := wb_reg_valid && (!wb_reg_expt)

    dmem_reg := io.dmem.rd_data

    reg_write := MuxLookup(wb_ctrl_sigs.wb_sel, wb_reg_wdata, Seq(
        WB_MEM -> dmem_reg,
        WB_PC4 -> (wb_reg_pc + 4.U),
        WB_ALU -> wb_reg_wdata,
        WB_CSR -> csr.io.read_csr_dat
    )).asUInt

    wb_reg_wdata_forward := reg_write
    // temporary init
    // io.debug_devs.leds := alu.io.out
    io.debug_devs.leds := MuxLookup(io.debug_devs.dip_sw(1, 0), io.imem.inst, Seq(
        1.U -> Cat(pc(7, 0), csr_epc(7, 0)), 
        2.U -> Cat(io.dmem.rd_data(7, 0), io.dmem.wr_data(7, 0))
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