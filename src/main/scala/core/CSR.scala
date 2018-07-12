package systemoncat.core

import chisel3._
import chisel3.util._
import chisel3.Bits._


object PRV
{
  val SZ = 2.U(2.W)
  val U = 0.U(2.W)
  val S = 1.U(2.W)
  val H = 2.U(2.W)
  val M = 3.U(2.W)
}

object Cause{
  val Interrupt = 1.U(1.W)
  val Exception = 0.U(1.W)

  val UFI = 0.U(31.W) //User software interrupt
  val SFI = 1.U(31.W) 
  val HFI = 2.U(31.W)
  val MFI = 3.U(31.W) //Machine software interrupt

  val UTI = 4.U(31.W) 
  val STI = 5.U(31.W)
  val HTI = 6.U(31.W)
  val MTI = 7.U(31.W) //Machine timer interrupt

  val UEI = 8.U(31.W)
  val SEI = 9.U(31.W)
  val HEI = 10.U(31.W)
  val MEI = 11.U(31.W) //Machine External interrupt

  val IAM = 0.U(31.W) //Instruction Address Misaligned
  val IAF = 1.U(31.W) //Instruction Access Fault
  val II = 2.U(31.W)  //Illegal Instruction
  val BP = 3.U(31.W)  //BreakPoint
  val LAM = 4.U(31.W) //Load Address Misaligned
  val LAF = 5.U(31.W) //Load Access Fault
  val SAM = 6.U(31.W) //Store Address Misaligned
  val SAF = 7.U(31.W) //Store Access Fault
  val ECU = 8.U(31.W) //Environment Call of U-mode
  val ECS = 9.U(31.W) //Environment Call of S-mode
  val ECH = 10.U(31.W)//Environment Call of H-mode
  val ECM = 11.U(31.W)//Environment Call of M-mode
  val IPF = 12.U(31.W)//Instruction page Fault
  val LPF = 13.U(31.W)//Load page Fault
  val SPF = 14.U(31.W)//Save page Fault
}
class MStatus extends Bundle{
  val sd = Bool()
  val zero1 = UInt(8.W)

  //Memory Accessing Realted Regeisters
  val tsr = Bool()
  val tw = Bool()
  val tvm = Bool()
  val mxr = Bool()
  val sum = Bool()
  val mprv = Bool()
  
  val xs = UInt(2.W)
  val fs = UInt(2.W) //Floating part status
  
  val mpp = UInt(2.W) //previous mode
  val hpp = UInt(2.W)
  val spp = UInt(2.W)
  
  val mpie = Bool() //previous mie (Saved status)
  val hpie = Bool()
  val spie = Bool()
  val upie = Bool()
  
  val mie = Bool() //globle interupt enable
  val hie = Bool()
  val sie = Bool()
  val uie = Bool()
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
  val prv = UInt(2.W)
}

class MIE() extends Bundle(){
  
  val zero1 = UInt(20.W)
  val meie = Bool()
  val heie = Bool()
  val seie = Bool()
  val ueie = Bool()
  val mtie = Bool()
  val htie = Bool()
  val stie = Bool()
  val utie = Bool()
  val msie = Bool()
  val hsie = Bool()
  val ssie = Bool()
  val usie = Bool()

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

class MTVEC() extends Bundle(){
  val base = UInt(30.W)
  val mode = UInt(2.W)
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

    // interrupt request
    val ext_irq_r = Input(Bool()) //external interrupt
    val sft_irq_r = Input(Bool()) //software interrupt
    val tmr_irq_r = Input(Bool()) //timer interrupt

    // interrupt message
    val sig = Input(new ControlSignals()) //interrupt instruction message
    val inst = Input(UInt(32.W))           //interrupt instruction
    val pc  = Input(UInt(32.W))           //interrupt pc
    val addr = Input(UInt(32.W))          //interrupt address
    
    // Invalid message
    val instIv = Input(Bool()) //Instruction Invalid
    val laddrIv = Input(Bool()) //Load Address Invalid
    val saddrIv = Input(Bool()) //Store Address Invalid
    val pcIv = Input(Bool())   //PC Invalid
    
    // Trap Instruction
    val isEcall = Input(Bool())
    val isEbreak = Input(Bool())
    val isEret = Input(Bool())
    
    // Page Fault
    val iPF = Input(Bool()) //Instruction Page Fault
    val lPF = Input(Bool()) //Load Page Fault
    val sPF = Input(Bool()) //Store Page Fault

    //csr index
    val csr_idx = Input(UInt(12.W))

    //Read Write data
    val wb_csr_dat = Input(UInt(32.W))
    val read_csr_dat = Output(UInt(32.W))

    val epc = Output(UInt(32.W)) //EPC
    val expt = Output(Bool()) // Exception Occur
    val interp = Output(Bool()) // Interrupt Occur
    val evec = Output(UInt(32.W)) //Exception Handler Entry
}

class CSRFile() extends Module{
  val io = IO(new CSRFileIO)

  val prv = Reg(UInt(2.W))

  // Status Register
  val reset_mstatus = Wire(init=new MStatus())
  reset_mstatus.mpp := PRV.M
  val mstatus = Reg(init=reset_mstatus) // 0x300
//val mhartid = Reg(UInt(32.W))  // 0xF14
  
  // Interrupt Enable
  val mie = Reg(UInt(32.W))      // 0x304
  // Interrupt Waiting  
  val mip = Reg(new MIP)         // 0x344
  // Interrupt Entry Address
  val mtvec = Reg(new MTVEC)    // 0x305
  // Error Address
  val mtval = Reg(UInt(32.W))
  // Interrupt Temp Register
  val mscratch = Reg(UInt(32.W)) // 0x340
  // Interrupt epc
  val mepc = Reg(UInt(32.W))     // 0x341
  // Interrupt Cause
  val mcause = Reg(UInt(32.W))   // 0x342

  //Machine Cycle
  val mcycle = Reg(UInt(32.W))   // 0xB00
  val mcycleh = Reg(UInt(32.W))  // 0xB80
  
  //Client Register (For software interrupt and time interrupt)
  //val mtime = Reg(UInt(32.W))
  //val mtimecmp = Reg(UInt(32.W))
  //val msip = Reg(UInt(32.W))
  //Read CSR

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
    .otherwise{
      io.read_csr_dat := 0.U(32.W)
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
    //.elsewhen(io.csr_idx === "h344".U(12.W)){ MIP is read only
      //mip := wb_dat
    //} 
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
    .otherwise{

    }
  }
  //Counters
  //cycle := cycle + 1.U
  //when(cycle.andR) { cycleh := cycleh + 1.U}

  //Exception Handler

  io.expt := io.instIv || io.laddrIv || io.saddrIv || io.pcIv || io.isEcall || io.isEbreak || io.iPF || io.lPF || io.sPF
  
  io.evec := mtvec.base << 2
  io.epc := mepc
  
  when(io.expt){
    mepc := io.pc >> 2 << 2
    
    mcause :=   Cat(Cause.Exception, 
                Mux(io.instIv, Cause.II,
                Mux(io.laddrIv, Cause.LAM,
                Mux(io.saddrIv, Cause.SAM,
                Mux(io.pcIv, Cause.IAM,
                Mux(io.iPF, Cause.IPF,
                Mux(io.lPF, Cause.LPF,
                Mux(io.sPF, Cause.SPF,
                Mux(io.isEcall, Cause.ECM,
                Mux(io.isEbreak,     Cause.BP, Cause.II)))))))))
                )

    mtval :=    Mux(io.instIv, io.inst,
                Mux(io.laddrIv, io.addr,
                Mux(io.saddrIv, io.addr,
                Mux(io.pcIv, io.pc,
                Mux(io.iPF, io.pc,
                Mux(io.lPF, io.addr,
                Mux(io.sPF, io.addr, 0.U(32.W))))))))

    prv := PRV.M
    mstatus.mpp := prv
    mstatus.mie := false.B
    mstatus.mpie := mstatus.mie
  } .elsewhen(io.isEret){
    prv := mstatus.mpp
    mstatus.mie := mstatus.mpie
  }


}
