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

    val ex_ctrl_sigs = Reg(new ControlSignals)
    val mem_ctrl_sigs = Reg(new ControlSignals)
    val wb_ctrl_sigs = Reg(new ControlSignals)

    val id_reg_valid = Reg(Bool()) // is a valid inst 
    val ex_reg_valid = Reg(Bool())
    val mem_reg_valid = Reg(Bool())
    val wb_reg_valid = Reg(Bool())

    val ex_reg_inst = Reg(Bits()) // original instruction
    val mem_reg_inst = Reg(Bits())
    val wb_reg_inst = Reg(Bits())

    val ex_waddr = ex_reg_inst(11,7) // rd can be directly extracted from inst
    val mem_waddr = mem_reg_inst(11,7)
    val wb_waddr = wb_reg_inst(11,7)

    val ex_reg_cause = Reg(UInt(4.W)) // exception cause
    val mem_reg_cause = Reg(UInt(4.W))
    val wb_reg_cause = Reg(UInt(4.W))

    val id_reg_pc = Reg(UInt())
    val ex_reg_pc = Reg(UInt())
    val mem_reg_pc = Reg(UInt())
    val wb_reg_pc = Reg(UInt())

    val mem_reg_rs2 = Reg(UInt()) // used as store address
    val dmem_reg = Reg(UInt())

    val mem_reg_wdata = Reg(Bits()) // data for write back
    val wb_reg_wdata = Reg(Bits())
    val wb_reg_wdata_forward = Reg(Bits())

    val ex_reg_imme = Reg(UInt()) // 32 bit immediate, sign extended if necessary

    val csr_branch = Wire(Bool()) // when csr branch happens, all prev stages will be flushed

    // ---------- NPC ----------
    val pc = RegInit(0.U(32.W)) // initial pc
    val pc_valid = (!csr_branch)
    val ex_branch_taken = Wire(Bool())
    val ex_branch_target = Wire(UInt())

    val id_exe_data_hazard = Wire(Bool())
    val pc_stall = id_exe_data_hazard || io.imem.locked

    val csr_epc = Wire(UInt())
    val mem_eret = Wire(Bool())
    val csr_evec = Wire(UInt())
    val mem_interp = Wire(Bool()) // used to flush pipeline at the end of MEM

    val csr_reg_epc = Reg(UInt())
    val mem_reg_eret = Reg(Bool())
    val csr_reg_evec = Reg(UInt())
    val mem_reg_interp = Reg(Bool()) // used to decide next pc at the beginning of WB

    val npc = Mux(mem_reg_interp, csr_reg_evec,
        Mux(mem_reg_eret, csr_reg_epc, 
        Mux(ex_branch_taken, ex_branch_target,
        Mux(pc_stall, pc, pc + 4.U))))
    pc := npc
    val inst_reg = RegInit(NOP) // instruction in IF

    // ---------- IF -----------
    io.imem.pc := npc

    // ---------- ID -----------
    // regs update

    io.ctrl.inst := inst_reg
    
    inst_reg := io.imem.inst
    id_reg_pc := pc

    id_reg_valid := ((!ex_branch_taken && !pc_stall) || id_exe_data_hazard) && (!csr_branch) && pc_valid
    // if pc stalled because of imem/dmem hazard, prev ID is duplicated and should be invalidated
    // but if pc stalled because of ID/EXE hazard, then ID is also stalled and should be kept

    val id_rs1 = inst_reg(19, 15) // rs1
    val id_rs2 = inst_reg(24, 20) // rs2
    val id_rd  = inst_reg(11, 7)  // rd
    val id_imme = ImmGen(io.ctrl.sig.imm_sel, inst_reg) // immediate
    val id_ren = IndexedSeq(io.ctrl.sig.rxs1, io.ctrl.sig.rxs2)
    val regfile = Module(new RegFile)
    regfile.io.raddr1 := id_rs1
    regfile.io.raddr2 := id_rs2
    regfile.io.wen := wb_ctrl_sigs.wb_en
    regfile.io.waddr := wb_waddr
    val reg_write = Wire(UInt()) // assigned later
    regfile.io.wdata := reg_write

    val id_raddr = IndexedSeq(id_rs1, id_rs2)
    val id_rdatas = IndexedSeq(regfile.io.rdata1, regfile.io.rdata2)
    val bypass_sources = IndexedSeq( // has priority !
        (true.B, 0.U, 0.U), // x0 = 0
        (ex_reg_valid && ex_ctrl_sigs.wb_en && !ex_ctrl_sigs.mem, ex_waddr, mem_reg_wdata),
        (mem_reg_valid && mem_ctrl_sigs.wb_en, mem_waddr, wb_reg_wdata),
        (wb_reg_valid && wb_ctrl_sigs.wb_en, wb_waddr, wb_reg_wdata_forward))

    val id_bypass_src = id_raddr.map(raddr => bypass_sources.map(s => s._1 && s._2 === raddr))
    id_exe_data_hazard := ex_reg_valid && ex_ctrl_sigs.wb_en && ex_ctrl_sigs.mem &&
        (ex_waddr === id_rs1 || ex_waddr === id_rs2) 
    // if a data loaded from RAM is immediately used, pipeline must be stalled

    // ---------- EXE ----------
    // regs update
    when (id_reg_valid) {
        ex_ctrl_sigs := io.ctrl.sig
        ex_reg_imme := id_imme
        ex_reg_inst := inst_reg
        ex_reg_pc := id_reg_pc
    }
    ex_reg_valid := ((!ex_branch_taken) && id_reg_valid && (!id_exe_data_hazard)) && (!csr_branch)
    // TODO: check valid (stall logic related)

    // bypass logic
    val ex_reg_rs_bypass = Reg(Vec(id_raddr.size, Bool())) // if this reg can be bypassed
    val ex_reg_rdatas = Reg(Vec(id_raddr.size, Bits())) // reg datas
    val ex_reg_bypass_src = Reg(Vec(id_raddr.size, UInt(log2Ceil(bypass_sources.size).W))) // which bypass is taken
    val bypass_mux = Seq(
        0.U -> 0.U,
        1.U -> mem_reg_wdata,
        2.U -> wb_reg_wdata,
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

    ex_branch_taken := (ex_reg_valid && alu.io.cmp_out && ex_ctrl_sigs.branch) || 
        ex_ctrl_sigs.jal || ex_ctrl_sigs.jalr

    ex_branch_target := Mux(ex_ctrl_sigs.jalr,
            alu.io.out, ex_reg_pc + ex_reg_imme)

    // ---------- MEM ----------
    // regs update
    when (ex_reg_valid) {
        mem_ctrl_sigs := ex_ctrl_sigs
        mem_reg_pc := ex_reg_pc
        mem_reg_inst := ex_reg_inst
        mem_reg_rs2 := ex_rs(1)
        mem_reg_wdata := alu.io.out
    }
    
    // alu.io.cmp_out decides whether a branch is taken



    mem_reg_valid := ex_reg_valid && (!csr_branch) // TODO: check valid (stall logic related)

    io.dmem.wr_data := ex_rs(1)
    io.dmem.addr := alu.io.out
    io.dmem.wr_en := ex_reg_valid && ex_ctrl_sigs.mem && isWrite(ex_ctrl_sigs.mem_cmd)
    io.dmem.rd_en := ex_reg_valid && ex_ctrl_sigs.mem && isRead(ex_ctrl_sigs.mem_cmd)
    io.dmem.mem_type := ex_ctrl_sigs.mem_type

    // ---------- CSR & Interrupt Client -----------
    val csr = Module(new CSRFile)
    csr.io.sig := wb_ctrl_sigs
    csr.io.inst := wb_reg_inst

    val last_valid_pc_from_wb = Mux(wb_reg_valid, wb_reg_pc, 
        Mux(mem_reg_valid, mem_reg_pc, 
        Mux(ex_reg_valid, ex_reg_pc, 
        Mux(id_reg_valid, id_reg_pc, pc)))) 
    // I guess there should be at least one valid instruction in pipeline...

    csr.io.pc := last_valid_pc_from_wb
    csr.io.addr := 0.U(32.W) // TODO: after implementing i/d memory exception, this should be assigned

    csr.io.csr_ena := wb_reg_valid && (wb_ctrl_sigs.csr_cmd =/= CSR.N)
    csr.io.csr_rd_en := wb_reg_valid && (wb_ctrl_sigs.csr_cmd =/= CSR.N)
    csr.io.csr_wr_en := wb_reg_valid && (wb_ctrl_sigs.csr_cmd =/= CSR.N)

    csr.io.csr_idx := wb_reg_inst(31,20)

    csr.io.ext_irq_r := false.B
    csr.io.sft_irq_r := io.irq_client.sft_irq_r
    csr.io.tmr_irq_r := io.irq_client.tmr_irq_r

    csr.io.csr_cmd := Mux(wb_reg_valid, wb_ctrl_sigs.csr_cmd, CSR.N)

    csr.io.instIv := false.B
    csr.io.laddrIv := false.B
    csr.io.saddrIv := false.B
    csr.io.pcIv := false.B // TODO: after implementing i/d memory exception, this should be assigned
    
    // Trap Instruction
    val wb_is_eret = wb_reg_inst === MRET || wb_reg_inst === URET || wb_reg_inst === SRET
    csr.io.isEcall := wb_reg_inst === ECALL
    csr.io.isEbreak := wb_reg_inst === EBREAK
    csr.io.isEret := wb_is_eret
    
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

    val mem_has_interrupt = csr.io.interrupt || io.irq_client.sft_irq_r
    val mem_has_exception = csr.io.expt
    mem_interp := mem_has_exception || mem_has_exception
    mem_eret := mem_reg_inst === MRET || mem_reg_inst === URET || mem_reg_inst === SRET
    csr_branch := mem_interp || mem_eret
    csr_epc := csr.io.epc
    csr_evec := csr.io.evec

    mem_reg_interp := mem_interp
    mem_reg_eret := mem_eret
    csr_reg_epc := csr_epc
    csr_reg_evec := csr_evec

    // ---------- WB -----------
    when (mem_reg_valid) {
        wb_ctrl_sigs := mem_ctrl_sigs
        wb_reg_pc := mem_reg_pc
        wb_reg_inst := mem_reg_inst
        wb_reg_wdata := mem_reg_wdata
    }

    wb_reg_valid := mem_reg_valid
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
    io.debug_devs.leds := Mux(io.debug_devs.dip_sw.orR, io.imem.inst, alu.io.out)
    io.debug_devs.dpy0 := pc(7, 0)
    io.debug_devs.dpy1 := npc(7, 0)

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
    
}