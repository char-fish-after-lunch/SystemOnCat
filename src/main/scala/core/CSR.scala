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

class MStatus extends Budle{
	val uie = Bool()
	val sie = Bool()
	val zero1 = UInt(width = 1)
	val mie = Bool() //globle interupt enable

	val upie = Bool()
	val spie = Bool()
	val zero2 = UInt(width = 1)
	val mpie = Bool() //previous mie (Saved status)
	
	val spp = UInt(width = 1)
	val zero3 = UInt(width = 2)
	val mpp = UInt(width = 2) //previous mode

	val fs = UInt(width = 2) //Floating part status
	val xs = UInt(width = 2) 

	val mprv = Bool() // Memory translation according to which privilege
	val mxr = Bool() // Make executable Readable(hard wired to 0 if S-mode is not supported)
	val sum = Bool() // Permit Supervisor User Memory access (hard wired to 0 if S-mode is not supported)
	val tvm = Bool() //(hard wired to 0 if S-mode is not supported)
	val tw = Bool() // (hard wired to 0 if S-mode is not supported)
	val tsr = Bool() // (hard wired to 0 if S-mode is not supported)

	val zero4 = UInt(width = 8)
	val sd = Bool() // Mark fs xs dirty
}

class DCSR extends Bundle { //Debug Registers
  val xdebugver = UInt(width = 2)
  val zero4 = UInt(width=2)
  val zero3 = UInt(width = 12)
  val ebreakm = Bool()
  val ebreakh = Bool()
  val ebreaks = Bool()
  val ebreaku = Bool()
  val zero2 = Bool()
  val stopcycle = Bool()
  val stoptime = Bool()
  val cause = UInt(width = 3)
  val zero1 = UInt(width=3)
  val step = Bool()
  val prv = UInt(width = PRV.SZ)
}

class MIP(implicit p: Parameters) extends CoreBundle()(p)
    with HasCoreParameters {
  val lip = Vec(coreParams.nLocalInterrupts, Bool())
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

class PTBR(implicit p: Parameters) extends CoreBundle()(p) { //For Page Table 
  def pgLevelsToMode = 1
  val modeBits = 1
  val maxASIdBits = 9
  
  //require(modeBits + maxASIdBits + maxPAddrBits - pgIdxBits == xLen)

  val mode = UInt(width = modeBits)
  val asid = UInt(width = maxASIdBits)
  val ppn = UInt(width = maxPAddrBits - pgIdxBits)
}

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


