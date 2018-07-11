package systemoncat.core

import chisel3._
import chisel3.util._

object PRV
{
  val SZ = 2
  val U = 0
  val S = 1
  val H = 2
  val M = 3
}

class MStatus extends Bundle{
	val uie = Bool()
	val sie = Bool()
	val zero1 = UInt(1.W)
	val mie = Bool() //globle interupt enable

	val upie = Bool()
	val spie = Bool()
	val zero2 = UInt(1.W)
	val mpie = Bool() //previous mie (Saved status)
	
	val spp = UInt(1.W)
	val zero3 = UInt(2.W)
	val mpp = UInt(2.W) //previous mode

	val fs = UInt(2.W) //Floating part status
	val xs = UInt(2.W) 

	val mprv = Bool() // Memory translation according to which privilege
	val mxr = Bool() // Make executable Readable(hard wired to 0 if S-mode is not supported)
	val sum = Bool() // Permit Supervisor User Memory access (hard wired to 0 if S-mode is not supported)
	val tvm = Bool() //(hard wired to 0 if S-mode is not supported)
	val tw = Bool() // (hard wired to 0 if S-mode is not supported)
	val tsr = Bool() // (hard wired to 0 if S-mode is not supported)

	val zero4 = UInt(8.W)
	val sd = Bool() // Mark fs xs dirty
}

class DCSR extends Bundle { //Debug Registers
  val xdebugver = UInt(2.W)
  val zero4 = UInt(2.W)
  val zero3 = UInt(12.W)
  val ebreakm = Bool()
  val ebreakh = Bool()
  val ebreaks = Bool()
  val ebreaku = Bool()
  val zero2 = Bool()
  val stopcycle = Bool()
  val stoptime = Bool()
  val cause = UInt(3.W)
  val zero1 = UInt(3.W)
  val step = Bool()
  val prv = UInt(PRV.SZ.W)
}

class MIP() extends Bundle() {
  //val lip = Vec(coreParams.nLocalInterrupts, Bool())
  val zero2 = Bool()
  val debug = Bool() // keep in sync with CSR.debugIntCause
  val zero1 = Bool()
  val rocc = Bool()
  val meip = Bool()
  val heip = Bool()
  val seip = Bool()
  val ueip = Bool()
  val mtip = Bool()
  val htip = Bool()
  val stip = Bool()
  val utip = Bool()
  val msip = Bool()
  val hsip = Bool()
  val ssip = Bool()
  val usip = Bool()
}
/*
class PTBR() { //For Page Table 
  def pgLevelsToMode = 1
  val modeBits = 1
  val maxASIdBits = 9
  
  //require(modeBits + maxASIdBits + maxPAddrBits - pgIdxBits == xLen)

  val mode = UInt(modeBits.W)
  val asid = UInt(maxASIdBits.W)
  val ppn = UInt((maxPAddrBits - pgIdxBits).W)
}
*/
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
/*
class CSR extends Module
{
  val io = IO(new Bundle{
	 // commands
  	val csr_ena = Input(Bool())
  	val csr_wr_en = Input(Bool())
  	val csr_rd_en = Input(Bool())

  	// interupt request
  	val ext_irq_r = Input(Bool()) //external interupt
  	val sft_irq_r = Input(Bool()) //software interupt
  	val tmr_irq_r = Input(Bool()) //timer interupt

  	//csr index
  	val csr_idx = Input(UInt(12.W))

  	//Read Write data
  	val wb_csr_dat = Input(UInt(32.W))
  	val read_csr_dat = Output(UInt(32.W))

  })
}
*/
