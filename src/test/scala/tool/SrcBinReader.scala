package test.tool

import java.nio.file.{Files, Paths}

import chisel3._
import systemoncat.core._


import scala.collection.mutable.ArrayBuffer

object SrcBinReader {
  var fname = ""

  def read_insts(): Seq[UInt] = {
    if (fname.isEmpty) {
      // this should not happen because it means
      //  either using simulational MMU in verilog generation
      //  or that fname is not set up
      return Seq(Const.NOP_INST, Const.NOP_INST)
    }
    val rv = ArrayBuffer.empty[UInt]
    val bytes = Files.readAllBytes(Paths.get(fname))
    for (i <- 0 until bytes.length)
      rv += "h_%02x".format(bytes(i)).U(8.W)
    return rv.seq.toIndexedSeq
  }
}
