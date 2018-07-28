package systemoncat.core

import chisel3._
import chisel3.util._
import ALU._
import CSR._

class DecoderIO extends Bundle() {
    val inst = Input(Bits(32.W))  // original instruction, input
    val sig = Output(new ControlSignals()) 
}

class ControlSignals() extends Bundle() {

    val rxs1 = Bool() // whether register1(rs1 in inst) is used
    val rxs2 = Bool() // whether register2(rs2 in inst) is used

    val A1_sel    = Bits(A1_X.getWidth.W)     // alu input1 data source, more in Consts.scala
    val A2_sel    = Bits(A2_X.getWidth.W)     // alu input2 data source
    val imm_sel   = Bits(IMM_X.getWidth.W)    // imm type
    val alu_op    = Bits(ALU_OP_X.getWidth.W) // alu operation type

    val jal = Bool()  // is jal
    val jalr = Bool() // is jalr

    val mem = Bool()                       // will access memory
    val mem_cmd = Bits(MOP_X.getWidth.W)   // memory command type (load, store, amo, ..)
    val mem_type  = Bits(MEM_X.getWidth.W) // data width(B, H, W)

    val wb_en     = Bool()                 // will write back
    val wb_sel    = Bits(WB_X.getWidth.W)  // write back target

    val csr_cmd   = Bits(CSR.X.getWidth.W) // CSR op command type (W, S, C)
    val legal   = Bool()                 // is legal 

    val fence_i = Bool()                   // is fence
    val fence = Bool()                     // is fence.i
    val mul = Bool()                       // is mul
    val div = Bool()                       // is div
    val branch = Bool()                    // is branch
}

object Decoder {
    // TODO carefully check controller
    def default =
                //              A2_sel              jal                                       fence.i 
                //   rxs1       |                   |       mem_cmd                           |       
                //   | rxs2     |                   | jalr  |               wb_sel            | fence 
                //   | | A1_sel |                   | |     |     mem_type  |                 | |     
                //   | | |      |    imm   alu_op   | | mem |     |         |     csr_cmd     | | mul 
                //   | | |      |    |     |        | | |   |     |     wb  |     |           | | |  
                //   | | |      |    |     |        | | |   |     |     |   |     |     legal | | | div
                //   | | |      |    |     |        | | |   |     |     |   |     |     |     | | | | branch 
                List(X,X,A1_X,  A2_X,IMM_X,ALU_OP_X,X,X,X,  MOP_X,MEM_X,X,  WB_X, CSR.X,N    ,X,X,X,X,X)

    val table = Array(
        BNE->        List(Y,Y,A1_RS1, A2_RS2, IMM_B,ALU_OP_SNE, N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.N,Y,N,N,N,N,Y),
        BEQ->        List(Y,Y,A1_RS1, A2_RS2, IMM_B,ALU_OP_SEQ, N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.N,Y,N,N,N,N,Y),
        BLT->        List(Y,Y,A1_RS1, A2_RS2, IMM_B,ALU_OP_SLT, N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.N,Y,N,N,N,N,Y),
        BLTU->       List(Y,Y,A1_RS1, A2_RS2, IMM_B,ALU_OP_SLTU,N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.N,Y,N,N,N,N,Y),
        BGE->        List(Y,Y,A1_RS1, A2_RS2, IMM_B,ALU_OP_SGE, N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.N,Y,N,N,N,N,Y),
        BGEU->       List(Y,Y,A1_RS1, A2_RS2, IMM_B,ALU_OP_SGEU,N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.N,Y,N,N,N,N,Y),

        JAL->        List(N,N,A1_PC,  A2_SIZE,IMM_J,ALU_OP_ADD, Y,N,N,MOP_X, MEM_X, Y,WB_PC4,CSR.N,Y,N,N,N,N,N),
        JALR->       List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_ADD, N,Y,N,MOP_X, MEM_X, Y,WB_PC4,CSR.N,Y,N,N,N,N,N),
        AUIPC->      List(N,N,A1_PC,  A2_IMM, IMM_U,ALU_OP_ADD, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N),

        LB->         List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_ADD, N,N,Y,MOP_RD,MEM_B, Y,WB_MEM,CSR.N,Y,N,N,N,N,N),
        LH->         List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_ADD, N,N,Y,MOP_RD,MEM_H, Y,WB_MEM,CSR.N,Y,N,N,N,N,N),
        LW->         List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_ADD, N,N,Y,MOP_RD,MEM_W, Y,WB_MEM,CSR.N,Y,N,N,N,N,N),
        LBU->        List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_ADD, N,N,Y,MOP_RD,MEM_BU,Y,WB_MEM,CSR.N,Y,N,N,N,N,N),
        LHU->        List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_ADD, N,N,Y,MOP_RD,MEM_HU,Y,WB_MEM,CSR.N,Y,N,N,N,N,N),
        SB->         List(Y,Y,A1_RS1, A2_IMM, IMM_S,ALU_OP_ADD, N,N,Y,MOP_WR,MEM_B, N,WB_X,  CSR.N,Y,N,N,N,N,N),
        SH->         List(Y,Y,A1_RS1, A2_IMM, IMM_S,ALU_OP_ADD, N,N,Y,MOP_WR,MEM_H, N,WB_X,  CSR.N,Y,N,N,N,N,N),
        SW->         List(Y,Y,A1_RS1, A2_IMM, IMM_S,ALU_OP_ADD, N,N,Y,MOP_WR,MEM_W, N,WB_X,  CSR.N,Y,N,N,N,N,N),

        LUI->        List(N,N,A1_ZERO,A2_IMM, IMM_U,ALU_OP_ADD, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N),
        ADDI->       List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_ADD, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N),
        SLTI ->      List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_SLT, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N),
        SLTIU->      List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_SLTU,N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N),
        ANDI->       List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_AND, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N),
        ORI->        List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_OR,  N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N),
        XORI->       List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_XOR, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N),
        SLLI->       List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_SL,  N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N),
        SRLI->       List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_SRL, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N),
        SRAI->       List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_SRA, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N),
        ADD->        List(Y,Y,A1_RS1, A2_RS2, IMM_X,ALU_OP_ADD, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N),
        SUB->        List(Y,Y,A1_RS1, A2_RS2, IMM_X,ALU_OP_SUB, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N),
        SLT->        List(Y,Y,A1_RS1, A2_RS2, IMM_X,ALU_OP_SLT, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N),
        SLTU->       List(Y,Y,A1_RS1, A2_RS2, IMM_X,ALU_OP_SLTU,N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N),
        AND->        List(Y,Y,A1_RS1, A2_RS2, IMM_X,ALU_OP_AND, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N),
        OR->         List(Y,Y,A1_RS1, A2_RS2, IMM_X,ALU_OP_OR,  N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N),
        XOR->        List(Y,Y,A1_RS1, A2_RS2, IMM_X,ALU_OP_XOR, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N),
        SLL->        List(Y,Y,A1_RS1, A2_RS2, IMM_X,ALU_OP_SL,  N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N),
        SRL->        List(Y,Y,A1_RS1, A2_RS2, IMM_X,ALU_OP_SRL, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N),
        SRA->        List(Y,Y,A1_RS1, A2_RS2, IMM_X,ALU_OP_SRA, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N),

        FENCE->      List(N,N,A1_X,   A2_X,   IMM_X,ALU_OP_X,   N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.N,Y,N,Y,N,N,N),
        FENCE_I->    List(N,N,A1_X,   A2_X,   IMM_X,ALU_OP_X,   N,N,Y,MOP_FL,MEM_X, N,WB_X,  CSR.N,Y,Y,N,N,N,N),
        SFENCE_VMA-> List(Y,Y,A1_RS1, A2_ZERO,IMM_X,ALU_OP_ADD, N,N,N,MOP_X ,MEM_X, N,WB_X,  CSR.N,Y,N,N,N,N,N),

        ECALL->      List(X,N,A1_X,   A2_X,   IMM_X,ALU_OP_X,   N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.I,Y,N,N,N,N,N),
        EBREAK->     List(X,N,A1_X,   A2_X,   IMM_X,ALU_OP_X,   N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.I,Y,N,N,N,N,N),
        MRET->       List(X,N,A1_X,   A2_X,   IMM_X,ALU_OP_X,   N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.I,Y,N,N,N,N,N),
        WFI->        List(X,N,A1_X,   A2_X,   IMM_X,ALU_OP_X,   N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.I,Y,N,N,N,N,N),
        CSRRW->      List(Y,N,A1_RS1, A2_ZERO,IMM_X,ALU_OP_ADD, N,N,N,MOP_X, MEM_X, Y,WB_CSR,CSR.W,Y,N,N,N,N,N),
        CSRRS->      List(Y,N,A1_RS1, A2_ZERO,IMM_X,ALU_OP_ADD, N,N,N,MOP_X, MEM_X, Y,WB_CSR,CSR.S,Y,N,N,N,N,N),
        CSRRC->      List(Y,N,A1_RS1, A2_ZERO,IMM_X,ALU_OP_ADD, N,N,N,MOP_X, MEM_X, Y,WB_CSR,CSR.C,Y,N,N,N,N,N),
        CSRRWI->     List(N,N,A1_ZERO,A2_IMM, IMM_Z,ALU_OP_ADD, N,N,N,MOP_X, MEM_X, Y,WB_CSR,CSR.W,Y,N,N,N,N,N),
        CSRRSI->     List(N,N,A1_ZERO,A2_IMM, IMM_Z,ALU_OP_ADD, N,N,N,MOP_X, MEM_X, Y,WB_CSR,CSR.S,Y,N,N,N,N,N),
        CSRRCI->     List(N,N,A1_ZERO,A2_IMM, IMM_Z,ALU_OP_ADD, N,N,N,MOP_X, MEM_X, Y,WB_CSR,CSR.C,Y,N,N,N,N,N)
    )
}

class Control() extends Module {
    val io = IO(new DecoderIO)
    val ctrlSignals = ListLookup(io.inst, Decoder.default, Decoder.table)
    io.sig.rxs1        := ctrlSignals(0)
    io.sig.rxs2        := ctrlSignals(1)
    io.sig.A1_sel      := ctrlSignals(2)
    io.sig.A2_sel      := ctrlSignals(3)
    io.sig.imm_sel     := ctrlSignals(4)
    io.sig.alu_op      := ctrlSignals(5)
    io.sig.jal         := ctrlSignals(6)
    io.sig.jalr        := ctrlSignals(7)
    io.sig.mem         := ctrlSignals(8)
    io.sig.mem_cmd     := ctrlSignals(9)
    io.sig.mem_type    := ctrlSignals(10)
    io.sig.wb_en       := ctrlSignals(11)
    io.sig.wb_sel      := ctrlSignals(12)
    io.sig.csr_cmd     := ctrlSignals(13)
    io.sig.legal     := ctrlSignals(14)
    io.sig.fence       := ctrlSignals(15)
    io.sig.fence_i     := ctrlSignals(16)
    io.sig.mul         := ctrlSignals(17)
    io.sig.div         := ctrlSignals(18)
    io.sig.branch      := ctrlSignals(19)
}