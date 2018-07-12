package systemoncat.core

import chisel3._
import chisel3.util._
import chisel3.testers._
import ALU._

class ALUTester(alu: => ALU) extends BasicTester with TestUtils {
    val testalu = Module(alu)
    
    val ctrl = Module(new Control)

    val (cntr, done) = Counter(true.B, insts.size)
    val rs1  = Seq.fill(insts.size)(rnd.nextInt()) map toBigInt
    val rs2  = Seq.fill(insts.size)(rnd.nextInt()) map toBigInt
    val sum  = VecInit((rs1 zip rs2) map { case (a, b) => toBigInt(a.toInt + b.toInt).U(32.W) })
    val diff = VecInit((rs1 zip rs2) map { case (a, b) => toBigInt(a.toInt - b.toInt).U(32.W) })
    val and  = VecInit((rs1 zip rs2) map { case (a, b) => (a & b).U(32.W) })
    val or   = VecInit((rs1 zip rs2) map { case (a, b) => (a | b).U(32.W) })
    val xor  = VecInit((rs1 zip rs2) map { case (a, b) => (a ^ b).U(32.W) })
    val slt  = VecInit((rs1 zip rs2) map { case (a, b) => (if (a.toInt < b.toInt) true.B else false.B) })
    val sge  = VecInit((rs1 zip rs2) map { case (a, b) => (if (b.toInt <= a.toInt) true.B else false.B) })
    val sltu = VecInit((rs1 zip rs2) map { case (a, b) => (if (a < b) true.B else false.B) })
    val sgeu = VecInit((rs1 zip rs2) map { case (a, b) => (if (b <= a) true.B else false.B) })
    val seq = VecInit((rs1 zip rs2) map { case (a, b) => (if (a == b) true.B else false.B) })
    val sne = VecInit((rs1 zip rs2) map { case (a, b) => (if (a == b) false.B else true.B) })
    val sll  = VecInit((rs1 zip rs2) map { case (a, b) => toBigInt(a.toInt << (b.toInt & 0x1f)).U(32.W) })
    val srl  = VecInit((rs1 zip rs2) map { case (a, b) => toBigInt(a.toInt >>> (b.toInt & 0x1f)).U(32.W) })
    val sra  = VecInit((rs1 zip rs2) map { case (a, b) => toBigInt(a.toInt >> (b.toInt & 0x1f)).U(32.W) })
    val out = MuxLookup(testalu.io.fn, 0.U(32.W), Seq(
        ALU_OP_ADD -> sum(cntr),
        ALU_OP_SUB -> diff(cntr),
        ALU_OP_AND -> and(cntr),
        ALU_OP_OR -> or(cntr),
        ALU_OP_XOR -> xor(cntr),
        ALU_OP_SL -> sll(cntr),
        ALU_OP_SRL -> srl(cntr),
        ALU_OP_SRA -> sra(cntr)
    ))

    val cmp_out = MuxLookup(testalu.io.fn, false.B, Seq(
        ALU_OP_SEQ -> seq(cntr),
        ALU_OP_SNE -> sne(cntr),
        ALU_OP_SLT -> slt(cntr),
        ALU_OP_SLTU -> sltu(cntr),
        ALU_OP_SGE -> sge(cntr),
        ALU_OP_SGEU -> sgeu(cntr)
    ))

    // val out = (Mux(testalu.io.fn === ALU_OP_ADD,  sum(cntr),
    //             Mux(testalu.io.fn === ALU_OP_SUB,  diff(cntr),
    //             Mux(testalu.io.fn === ALU_OP_AND,  and(cntr),
    //             Mux(testalu.io.fn === ALU_OP_OR,   or(cntr),
    //             Mux(testalu.io.fn === ALU_OP_XOR,  xor(cntr),
    //             Mux(testalu.io.fn === ALU_OP_SLT,  slt(cntr),
    //             Mux(testalu.io.fn === ALU_OP_SLTU, sltu(cntr),
    //             Mux(testalu.io.fn === ALU_OP_SL,  sll(cntr),
    //             Mux(testalu.io.fn === ALU_OP_SRL,  srl(cntr),
    //             Mux(testalu.io.fn === ALU_OP_SRA,  sra(cntr),
    //             Mux(testalu.io.fn === ALU_OP_SGE, testalu.io.A, testalu.io.B))))))))))),
    //             Mux(testalu.io.fn(0), diff(cntr), sum(cntr)))

    ctrl.io.inst := VecInit(insts)(cntr)
    testalu.io.fn := ctrl.io.sig.alu_op
    testalu.io.in1 := VecInit(rs1 map (_.U))(cntr)
    testalu.io.in2 := VecInit(rs2 map (_.U))(cntr)

    when(done) { stop(); stop() } // from VendingMachine example...
    printf("Counter: %d, OP: 0x%x, A: 0x%x, B: 0x%x, OUT: 0x%x ?= 0x%x, CMP_OUT: 0x%x ?= 0x%x\n",
            cntr, testalu.io.fn, testalu.io.in1, testalu.io.in2, testalu.io.out, out, testalu.io.cmp_out, cmp_out) 
    assert(Mux(isCmp(testalu.io.fn), testalu.io.cmp_out === cmp_out, testalu.io.out === out))
}


class ALUTests extends org.scalatest.FlatSpec {
    "ALUTests" should "pass" in {
        assert(TesterDriver execute (() => new ALUTester(new ALU)))
    }
}