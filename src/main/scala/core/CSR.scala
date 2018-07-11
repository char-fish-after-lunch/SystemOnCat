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
  val lip = UInt(16.W)
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
    def X = 0.U(3.W) //CSR operator type
    def N = 0.U(3.W) //CSR disable
    def W = 1.U(3.W) //CSR Write
    def S = 2.U(3.W) //CSR Set
    def C = 3.U(3.W) //CSR Clear
    def I = 4.U(3.W) //CSR Interupt
    def R = 5.U(3.W) //CSR Read
}

class CSRFileIO() extends Bundle{
    // commands
    val csr_ena = Input(Bool())
    val csr_rd_en = Input(Bool())
    val csr_wr_en = Input(Bool())
    val csr_cmd = Input(UInt(3.W))

    // interupt request
    val ext_irq_r = Input(Bool()) //external interupt
    val sft_irq_r = Input(Bool()) //software interupt
    val tmr_irq_r = Input(Bool()) //timer interupt

    //csr index
    val csr_idx = Input(UInt(12.W))

    //Read Write data
    val wb_csr_dat = Input(UInt(32.W))
    val read_csr_dat = Output(UInt(32.W))
}

class CSRFile() extends Module{
  val io = IO(new CSRFileIO)

  val mstatus = Reg(new MStatus) // 0x300
//val mhartid = Reg(UInt(32.W))  // 0xF14
  val mie = Reg(UInt(32.W))      // 0x304
  val mip = Reg(new MIP)         // 0x344
  val mtvec = Reg(UInt(32.W))    // 0x305
  val mscratch = Reg(UInt(32.W)) // 0x340
  val mepc = Reg(UInt(32.W))     // 0x341
  val mcause = Reg(UInt(32.W))   // 0x342
  val mcycle = Reg(UInt(32.W))   // 0xB00
  val mcycleh = Reg(UInt(32.W))  // 0xB80
  val mtime = Reg(UInt(32.W))
  val mtimecmp = Reg(UInt(32.W))
  val msip = Reg(UInt(32.W))

  //Read CSR logic
  when(io.csr_ena & io.csr_rd_en){
    when(io.csr_idx === "h300".U(12.W)){
      io.read_csr_dat := mstatus
    } 
    .elsewhen(io.csr_idx === "h304".U(12.W)){
      io.read_csr_dat := mie
    } 
    .elsewhen(io.csr_idx === "h344".U(12.W)){
      io.read_csr_dat := mip
    } 
    .elsewhen(io.csr_idx === "h305".U(12.W)){
      io.read_csr_dat := mtvec
    } 
    .elsewhen(io.csr_idx === "h340".U(12.W)){
      io.read_csr_dat := mscratch
    } 
    .elsewhen(io.csr_idx === "h341".U(12.W)){
      io.read_csr_dat := mepc
    } 
    .elsewhen(io.csr_idx === "h342".U(12.W)){
      io.read_csr_dat := mcause
    } 
    .elsewhen(io.csr_idx === "hb00".U(12.W)){
      io.read_csr_dat := mcycle
    } 
    .elsewhen(io.csr_idx === "hb80".U(12.W)){
      io.read_csr_dat := mcycleh
    }
  }

  val wb_dat = MuxLookup(io.csr_cmd, 0.U, Seq(
    CSR.W -> io.wb_csr_dat,
    CSR.S -> (io.read_csr_dat | io.wb_csr_dat),
    CSR.C -> (io.read_csr_dat & ~io.wb_csr_dat)
  ))  


  //Write CSR logic
  when(io.csr_ena & io.csr_wr_en){
    when(io.csr_idx === "h300".U(12.W)){
      mstatus := wb_dat
    } 
    .elsewhen(io.csr_idx === "h304".U(12.W)){
      mie := wb_dat
    } 
    .elsewhen(io.csr_idx === "h344".U(12.W)){
      mip := wb_dat
    } 
    .elsewhen(io.csr_idx === "h305".U(12.W)){
      mtvec := wb_dat
    } 
    .elsewhen(io.csr_idx === "h340".U(12.W)){
      mscratch := wb_dat
    } 
    .elsewhen(io.csr_idx === "h341".U(12.W)){
      mepc := wb_dat
    } 
    .elsewhen(io.csr_idx === "h342".U(12.W)){
      mcause := wb_dat
    } 
    .elsewhen(io.csr_idx === "hb00".U(12.W)){
      mcycle := wb_dat
    } 
    .elsewhen(io.csr_idx === "hb80".U(12.W)){
      mcycleh := wb_dat
    }
  }

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
