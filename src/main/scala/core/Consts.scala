package systemoncat.core

import chisel3._
import chisel3.util._

trait DecoderConstants {

    def X = false.B
    def N = false.B
    def Y = true.B

    def A1_X    = 0.U(2.W)
    def A1_ZERO = 0.U(2.W)
    def A1_RS1  = 1.U(2.W)
    def A1_PC   = 2.U(2.W)

    def A2_X    = 0.U(2.W)
    def A2_ZERO = 0.U(2.W)
    def A2_SIZE = 1.U(2.W)
    def A2_RS2  = 2.U(2.W)
    def A2_IMM  = 3.U(2.W)

    def IMM_X  = 0.U(3.W)
    def IMM_S  = 0.U(3.W)
    def IMM_B  = 1.U(3.W)
    def IMM_U  = 2.U(3.W)
    def IMM_J  = 3.U(3.W)
    def IMM_I  = 4.U(3.W)
    def IMM_Z  = 5.U(3.W)

    def MOP_X    = 0.U(3.W)
    def MOP_RD   = 1.U(3.W) // load
    def MOP_WR   = 2.U(3.W) // store
    def MOP_FL   = 3.U(3.W) // flush(used in fence)
    def MOP_LR   = 4.U(3.W) // load reserved
    def MOP_SC   = 5.U(3.W) // store conditional
    def MOP_A    = 6.U(3.W) // AMO_xxx
    def isRead(cmd: UInt) = (cmd === MOP_RD) || (cmd === MOP_LR)
    def isWrite(cmd: UInt) = cmd === MOP_WR


    def MEM_X  = 0.U(3.W)
    def MEM_B  = 1.U(3.W)
    def MEM_H  = 2.U(3.W)
    def MEM_W  = 3.U(3.W)
    def MEM_BU = 4.U(3.W)
    def MEM_HU = 5.U(3.W)
    def MEM_WU = 6.U(3.W)

    def WB_X   = 0.U(2.W)
    def WB_ALU = 0.U(2.W)
    def WB_MEM = 1.U(2.W)
    def WB_PC4 = 2.U(2.W)
    def WB_CSR = 3.U(2.W)
}
