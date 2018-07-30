package systemoncat.core

import chisel3._
import chisel3.util._
import ALU._
import CSR._
import AMO_OP._

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
    val legal   = Bool()                   // is legal 

    val fence_i = Bool()                   // is fence
    val fence = Bool()                     // is fence.i
    val mul = Bool()                       // is mul
    val div = Bool()                       // is div
    val branch = Bool()                    // is branch
    val amo_op = Bits(AMO_X.getWidth.W)                    // amo op
}

object Decoder {
    // TODO carefully check controller
    def default =
                //              A2_sel              jal                                       fence.i 
                //   rxs1       |                   |       mem_cmd                           |       
                //   | rxs2     |                   | jalr  |               wb_sel            | fence 
                //   | | A1_sel |                   | |     |     mem_type  |                 | |     
                //   | | |      |    imm   alu_op   | | mem |     |         |     csr_cmd     | | mul 
                //   | | |      |    |     |        | | |   |     |     wb  |     |           | | |          amo_op
                //   | | |      |    |     |        | | |   |     |     |   |     |     legal | | | div      |
                //   | | |      |    |     |        | | |   |     |     |   |     |     |     | | | | branch |
                List(X,X,A1_X,  A2_X,IMM_X,ALU_OP_X,X,X,X,  MOP_X,MEM_X,X,  WB_X, CSR.X,N    ,X,X,X,X,X,     AMO_X)

    val table = Array(
        BNE->        List(Y,Y,A1_RS1, A2_RS2, IMM_B,ALU_OP_SNE, N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.N,Y,N,N,N,N,Y,AMO_X),
        BEQ->        List(Y,Y,A1_RS1, A2_RS2, IMM_B,ALU_OP_SEQ, N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.N,Y,N,N,N,N,Y,AMO_X),
        BLT->        List(Y,Y,A1_RS1, A2_RS2, IMM_B,ALU_OP_SLT, N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.N,Y,N,N,N,N,Y,AMO_X),
        BLTU->       List(Y,Y,A1_RS1, A2_RS2, IMM_B,ALU_OP_SLTU,N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.N,Y,N,N,N,N,Y,AMO_X),
        BGE->        List(Y,Y,A1_RS1, A2_RS2, IMM_B,ALU_OP_SGE, N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.N,Y,N,N,N,N,Y,AMO_X),
        BGEU->       List(Y,Y,A1_RS1, A2_RS2, IMM_B,ALU_OP_SGEU,N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.N,Y,N,N,N,N,Y,AMO_X),

        JAL->        List(N,N,A1_PC,  A2_SIZE,IMM_J,ALU_OP_ADD, Y,N,N,MOP_X, MEM_X, Y,WB_PC4,CSR.N,Y,N,N,N,N,N,AMO_X),
        JALR->       List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_ADD, N,Y,N,MOP_X, MEM_X, Y,WB_PC4,CSR.N,Y,N,N,N,N,N,AMO_X),
        AUIPC->      List(N,N,A1_PC,  A2_IMM, IMM_U,ALU_OP_ADD, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N,AMO_X),

        LB->         List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_ADD, N,N,Y,MOP_RD,MEM_B, Y,WB_MEM,CSR.N,Y,N,N,N,N,N,AMO_X),
        LH->         List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_ADD, N,N,Y,MOP_RD,MEM_H, Y,WB_MEM,CSR.N,Y,N,N,N,N,N,AMO_X),
        LW->         List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_ADD, N,N,Y,MOP_RD,MEM_W, Y,WB_MEM,CSR.N,Y,N,N,N,N,N,AMO_X),
        LBU->        List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_ADD, N,N,Y,MOP_RD,MEM_BU,Y,WB_MEM,CSR.N,Y,N,N,N,N,N,AMO_X),
        LHU->        List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_ADD, N,N,Y,MOP_RD,MEM_HU,Y,WB_MEM,CSR.N,Y,N,N,N,N,N,AMO_X),
        SB->         List(Y,Y,A1_RS1, A2_IMM, IMM_S,ALU_OP_ADD, N,N,Y,MOP_WR,MEM_B, N,WB_X,  CSR.N,Y,N,N,N,N,N,AMO_X),
        SH->         List(Y,Y,A1_RS1, A2_IMM, IMM_S,ALU_OP_ADD, N,N,Y,MOP_WR,MEM_H, N,WB_X,  CSR.N,Y,N,N,N,N,N,AMO_X),
        SW->         List(Y,Y,A1_RS1, A2_IMM, IMM_S,ALU_OP_ADD, N,N,Y,MOP_WR,MEM_W, N,WB_X,  CSR.N,Y,N,N,N,N,N,AMO_X),

        LUI->        List(N,N,A1_ZERO,A2_IMM, IMM_U,ALU_OP_ADD, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N,AMO_X),
        ADDI->       List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_ADD, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N,AMO_X),
        SLTI ->      List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_SLT, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N,AMO_X),
        SLTIU->      List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_SLTU,N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N,AMO_X),
        ANDI->       List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_AND, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N,AMO_X),
        ORI->        List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_OR,  N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N,AMO_X),
        XORI->       List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_XOR, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N,AMO_X),
        SLLI->       List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_SL,  N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N,AMO_X),
        SRLI->       List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_SRL, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N,AMO_X),
        SRAI->       List(Y,N,A1_RS1, A2_IMM, IMM_I,ALU_OP_SRA, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N,AMO_X),
        ADD->        List(Y,Y,A1_RS1, A2_RS2, IMM_X,ALU_OP_ADD, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N,AMO_X),
        SUB->        List(Y,Y,A1_RS1, A2_RS2, IMM_X,ALU_OP_SUB, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N,AMO_X),
        SLT->        List(Y,Y,A1_RS1, A2_RS2, IMM_X,ALU_OP_SLT, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N,AMO_X),
        SLTU->       List(Y,Y,A1_RS1, A2_RS2, IMM_X,ALU_OP_SLTU,N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N,AMO_X),
        AND->        List(Y,Y,A1_RS1, A2_RS2, IMM_X,ALU_OP_AND, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N,AMO_X),
        OR->         List(Y,Y,A1_RS1, A2_RS2, IMM_X,ALU_OP_OR,  N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N,AMO_X),
        XOR->        List(Y,Y,A1_RS1, A2_RS2, IMM_X,ALU_OP_XOR, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N,AMO_X),
        SLL->        List(Y,Y,A1_RS1, A2_RS2, IMM_X,ALU_OP_SL,  N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N,AMO_X),
        SRL->        List(Y,Y,A1_RS1, A2_RS2, IMM_X,ALU_OP_SRL, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N,AMO_X),
        SRA->        List(Y,Y,A1_RS1, A2_RS2, IMM_X,ALU_OP_SRA, N,N,N,MOP_X, MEM_X, Y,WB_ALU,CSR.N,Y,N,N,N,N,N,AMO_X),

        FENCE->      List(N,N,A1_X,   A2_X,   IMM_X,ALU_OP_X,   N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.N,Y,N,Y,N,N,N,AMO_X),
        FENCE_I->    List(N,N,A1_X,   A2_X,   IMM_X,ALU_OP_X,   N,N,Y,MOP_FL,MEM_X, N,WB_X,  CSR.N,Y,Y,N,N,N,N,AMO_X),
        SFENCE_VMA-> List(Y,Y,A1_RS1, A2_ZERO,IMM_X,ALU_OP_ADD, N,N,N,MOP_X ,MEM_X, N,WB_X,  CSR.N,Y,N,N,N,N,N,AMO_X),

        ECALL->      List(X,N,A1_X,   A2_X,   IMM_X,ALU_OP_X,   N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.I,Y,N,N,N,N,N,AMO_X),
        EBREAK->     List(X,N,A1_X,   A2_X,   IMM_X,ALU_OP_X,   N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.I,Y,N,N,N,N,N,AMO_X),
        MRET->       List(X,N,A1_X,   A2_X,   IMM_X,ALU_OP_X,   N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.I,Y,N,N,N,N,N,AMO_X),
        WFI->        List(X,N,A1_X,   A2_X,   IMM_X,ALU_OP_X,   N,N,N,MOP_X, MEM_X, N,WB_X,  CSR.I,Y,N,N,N,N,N,AMO_X),
        CSRRW->      List(Y,N,A1_RS1, A2_ZERO,IMM_X,ALU_OP_ADD, N,N,N,MOP_X, MEM_X, Y,WB_CSR,CSR.W,Y,N,N,N,N,N,AMO_X),
        CSRRS->      List(Y,N,A1_RS1, A2_ZERO,IMM_X,ALU_OP_ADD, N,N,N,MOP_X, MEM_X, Y,WB_CSR,CSR.S,Y,N,N,N,N,N,AMO_X),
        CSRRC->      List(Y,N,A1_RS1, A2_ZERO,IMM_X,ALU_OP_ADD, N,N,N,MOP_X, MEM_X, Y,WB_CSR,CSR.C,Y,N,N,N,N,N,AMO_X),
        CSRRWI->     List(N,N,A1_ZERO,A2_IMM, IMM_Z,ALU_OP_ADD, N,N,N,MOP_X, MEM_X, Y,WB_CSR,CSR.W,Y,N,N,N,N,N,AMO_X),
        CSRRSI->     List(N,N,A1_ZERO,A2_IMM, IMM_Z,ALU_OP_ADD, N,N,N,MOP_X, MEM_X, Y,WB_CSR,CSR.S,Y,N,N,N,N,N,AMO_X),
        CSRRCI->     List(N,N,A1_ZERO,A2_IMM, IMM_Z,ALU_OP_ADD, N,N,N,MOP_X, MEM_X, Y,WB_CSR,CSR.C,Y,N,N,N,N,N,AMO_X),

        LR->         List(Y,N,A1_RS1 ,A2_ZERO,IMM_X,ALU_OP_ADD, N,N,Y,MOP_LR,MEM_W, Y,WB_MEM,CSR.N,Y,N,N,N,N,N,AMO_X),
        SC->         List(Y,Y,A1_RS1 ,A2_ZERO,IMM_X,ALU_OP_ADD, N,N,Y,MOP_SC,MEM_W, Y,WB_MEM,CSR.N,Y,N,N,N,N,N,AMO_X),

        AMOSWAP->    List(Y,Y,A1_RS1 ,A2_ZERO,IMM_X,ALU_OP_ADD, N,N,Y,MOP_A ,MEM_W, Y,WB_MEM,CSR.N,Y,N,N,N,N,N,AMO_SWAP),
        AMOADD->     List(Y,Y,A1_RS1 ,A2_ZERO,IMM_X,ALU_OP_ADD, N,N,Y,MOP_A ,MEM_W, Y,WB_MEM,CSR.N,Y,N,N,N,N,N,AMO_ADD),
        AMOAND->     List(Y,Y,A1_RS1 ,A2_ZERO,IMM_X,ALU_OP_ADD, N,N,Y,MOP_A ,MEM_W, Y,WB_MEM,CSR.N,Y,N,N,N,N,N,AMO_AND),
        AMOOR->      List(Y,Y,A1_RS1 ,A2_ZERO,IMM_X,ALU_OP_ADD, N,N,Y,MOP_A ,MEM_W, Y,WB_MEM,CSR.N,Y,N,N,N,N,N,AMO_OR),
        AMOXOR->     List(Y,Y,A1_RS1 ,A2_ZERO,IMM_X,ALU_OP_ADD, N,N,Y,MOP_A ,MEM_W, Y,WB_MEM,CSR.N,Y,N,N,N,N,N,AMO_XOR),
        AMOMAX->     List(Y,Y,A1_RS1 ,A2_ZERO,IMM_X,ALU_OP_ADD, N,N,Y,MOP_A ,MEM_W, Y,WB_MEM,CSR.N,Y,N,N,N,N,N,AMO_MAX),
        AMOMIN->     List(Y,Y,A1_RS1 ,A2_ZERO,IMM_X,ALU_OP_ADD, N,N,Y,MOP_A ,MEM_W, Y,WB_MEM,CSR.N,Y,N,N,N,N,N,AMO_MIN),
        AMOMAXU->    List(Y,Y,A1_RS1 ,A2_ZERO,IMM_X,ALU_OP_ADD, N,N,Y,MOP_A ,MEM_W, Y,WB_MEM,CSR.N,Y,N,N,N,N,N,AMO_MAXU),
        AMOMINU->    List(Y,Y,A1_RS1 ,A2_ZERO,IMM_X,ALU_OP_ADD, N,N,Y,MOP_A ,MEM_W, Y,WB_MEM,CSR.N,Y,N,N,N,N,N,AMO_MINU),
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
    io.sig.legal       := ctrlSignals(14)
    io.sig.fence       := ctrlSignals(15)
    io.sig.fence_i     := ctrlSignals(16)
    io.sig.mul         := ctrlSignals(17)
    io.sig.div         := ctrlSignals(18)
    io.sig.branch      := ctrlSignals(19)
    io.sig.amo_op      := ctrlSignals(20)
}