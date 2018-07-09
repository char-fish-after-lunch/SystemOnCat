package systemoncat.core

import chisel3._
import chisel3.util._

object ALU {
  def ALU_OP_X      = 0.U(4.W)
  def ALU_OP_ADD    = 0.U(4.W)
  def ALU_OP_SUB    = 1.U(4.W)
  def ALU_OP_AND    = 2.U(4.W)
  def ALU_OP_OR     = 3.U(4.W)
  def ALU_OP_XOR    = 4.U(4.W)
  def ALU_OP_SLT    = 5.U(4.W)
  def ALU_OP_SGE    = 6.U(4.W)
  def ALU_OP_SLTU   = 7.U(4.W)
  def ALU_OP_SGEU   = 8.U(4.W)
  def ALU_OP_SL     = 9.U(4.W)
  def ALU_OP_SRL    = 10.U(4.W)
  def ALU_OP_SRA    = 11.U(4.W)
  def ALU_OP_SEQ    = 12.U(4.W)
  def ALU_OP_SNE    = 13.U(4.W)
}