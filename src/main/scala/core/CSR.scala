package systemoncat.core

import chisel3._
import chisel3.util._

object CSR
{
    def X = 0.U(3.W)
    def N = 0.U(3.W)
    def W = 1.U(3.W)
    def S = 2.U(3.W)
    def C = 3.U(3.W)
    def I = 4.U(3.W)
    def R = 5.U(3.W)
}