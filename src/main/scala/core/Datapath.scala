package systemoncat.core

import chisel3._
import chisel3.util._

class DatapathIO() extends Bundle {
    val ctrl = Flipped(new DecoderIO)
}

class Datapath() extends Module {
    val io = IO(new DatapathIO())

    val ex_ctrl_sigs = Reg(new ControlSignals)
    val mem_ctrl_sigs = Reg(new ControlSignals)
    val wb_ctrl_sigs = Reg(new ControlSignals)

    val ex_reg_valid = Reg(Bool()) // is a valid inst
    val mem_reg_valid = Reg(Bool())
    val wb_reg_valid = Reg(Bool())

    val ex_reg_inst = Reg(Bits()) // original instruction
    val mem_reg_inst = Reg(Bits())
    val wb_reg_inst = Reg(Bits())

    val ex_waddr = ex_reg_inst(11,7) // rd can be directly extracted from inst
    val mem_waddr = mem_reg_inst(11,7)
    val wb_waddr = wb_reg_inst(11,7)

    val ex_reg_cause = Reg(UInt()) // exception cause
    val mem_reg_cause = Reg(UInt())
    val wb_reg_cause = Reg(UInt())

    val ex_reg_pc = Reg(UInt())
    val mem_reg_pc = Reg(UInt())
    val wb_reg_pc = Reg(UInt())

    val mem_reg_rs2 = Reg(UInt()) // used as store address

    val mem_reg_wdata = Reg(Bits()) // data for write back
    val wb_reg_wdata = Reg(Bits())


    // ---------- NPC ----------
    val pc = RegInit(0.U(32.W)) // initial pc

    // ---------- IF -----------
    val inst_reg = RegInit(NOP) // instruction in IF
    val ifetch = Module(new IFetch)
    ifetch.io.pc := pc
    inst_reg := ifetch.io.inst


    // ---------- ID -----------
    io.ctrl.inst := inst_reg

    val id_rs1 = inst_reg(19, 15) // rs1
    val id_rs2 = inst_reg(24, 20) // rs2
    val id_rd  = inst_reg(11, 7)  // rd
    val imme = ImmGen(io.ctrl.imm_sel, inst_reg) // immediate
    val id_ren = IndexedSeq(io.ctrl.rxs1, io.ctrl.rxs2)
    val regfile = Module(new RegFile)
    regfile.io.raddr1 := id_rs1
    regfile.io.raddr2 := id_rs2
    regfile.io.wen := wb_ctrl_sigs.wb_en
    regfile.io.waddr := wb_waddr
    regfile.io.wdata := wb_reg_wdata

    val id_raddr = IndexedSeq(id_rs1, id_rs2)
    val id_rdatas = IndexedSeq(regfile.io.rdata1, regfile.io.rdata2)
    val bypass_sources = IndexedSeq( // has priority !
        (Bool(true), UInt(0), UInt(0)), // x0 = 0
        (ex_reg_valid && ex_ctrl_sigs.wb_en, ex_waddr, mem_reg_wdata),
        (mem_reg_valid && mem_ctrl_sigs.wb_en && !mem_ctrl.mem, mem_waddr, wb_reg_wdata))

    val id_bypass_src = id_raddr.map(raddr => bypass_sources.map(s => s._1 && s._2 === raddr))

    // ---------- EXE ----------

    val ex_reg_rs_bypass = Reg(Vec(id_raddr.size, Bool())) // if this reg can be bypassed
    val ex_reg_rdatas = Reg(Vec(id_raddr.size, Bits())) // reg datas
    val ex_reg_bypass_src = Reg(Vec(id_raddr.size, UInt(width = log2Ceil(bypass_sources.size)))) // which bypass is taken
    val bypass_mux = bypass_sources.map(_._3)
    for (i <- 0 until id_raddr.size) {
        val do_bypass = id_bypass_src(i).reduce(_ || _) // at least one bypass is possible
        val bypass_src = PriorityEncoder(id_bypass_src(i))
        ex_reg_rs_bypass(i) := do_bypass
        ex_reg_bypass_src(i) := bypass_src
        ex_reg_rdatas(i) := id_rdatas(i)
    }

    val ex_rs = for (i <- 0 until id_raddr.size)
        yield Mux(ex_reg_rs_bypass(i), bypass_mux(ex_reg_bypass_src(i)), ex_reg_rdatas(i))

    // val ex_imm = ImmGen(ex_ctrl_sigs.imm_sel, ex_reg_inst)
    // val ex_op1 = MuxLookup(ex_ctrl.sel_alu1, SInt(0), Seq(
    //     A1_RS1 -> ex_rs(0).asSInt,
    //     A1_PC -> ex_reg_pc.asSInt))
    // val ex_op2 = MuxLookup(ex_ctrl.sel_alu2, SInt(0), Seq(
    //     A2_RS2 -> ex_rs(1).asSInt,
    //     A2_IMM -> ex_imm,
    //     A2_SIZE -> Mux(ex_reg_rvc, SInt(2), SInt(4))))


    // ---------- MEM ----------
    // ---------- WB -----------

}