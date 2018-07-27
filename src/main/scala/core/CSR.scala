package systemoncat.core

import chisel3._
import chisel3.util._
import chisel3.Bits._
import systemoncat.mmu.MemoryConsts._

object CSRConsts {
  def MSTATUS  = "h300".U(12.W)
  def MIE      = "h304".U(12.W)
  def MIP      = "h344".U(12.W)
  def MTVEC    = "h305".U(12.W)
  def MSCRATCH = "h340".U(12.W)
  def MEPC     = "h341".U(12.W)
  def MCAUSE   = "h342".U(12.W)
  def MTVAL    = "h343".U(12.W)
  def SATP     = "h180".U(12.W)
}

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

  val USI = 0.U(31.W) //User software interrupt
  val SSI = 1.U(31.W) 
  val HSI = 2.U(31.W)
  val MSI = 3.U(31.W) //Machine software interrupt

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
  val SPF = 15.U(31.W)//Save page Fault
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
  val spp = UInt(1.W)
  
  val mpie = Bool() //previous mie (Saved status)
  val hpie = Bool()
  val spie = Bool()
  val upie = Bool()
  
  val mie = Bool() //globle interupt enable
  val hie = Bool()
  val sie = Bool()
  val uie = Bool()
}

class SATP extends Bundle{ //For VM mapping
  val mode = Bool()
  val asid = UInt(9.W)
  val ppn = UInt(22.W)
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
    // val sig = Input(new ControlSignals()) //interrupt instruction message
    val next_pc = Input(Bool())
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

    //Interrupt Output
    val epc = Output(UInt(32.W))  //EPC
    val expt = Output(Bool())     // Exception Occur
    val interrupt = Output(Bool())   // Interrupt Occur
    val evec = Output(UInt(32.W)) //Exception Handler Entry

    //VM Output
    val baseppn = Output(UInt(PPNLength.W))
    val asid = Output(UInt(ASIDLength.W))
    val mode = Output(Bool())
    val priv = Output(UInt(2.W))
}

class CSRFile() extends Module{
  val io = IO(new CSRFileIO)

  val prv = RegInit(PRV.M)

  // Status Register
  val reset_mstatus = 0.U(32.W).asTypeOf(new MStatus())
  reset_mstatus.mpp := PRV.M
  val mstatus = RegInit(reset_mstatus) // 0x300
//val mhartid = Reg(UInt(32.W))  // 0xF14
  
  // Interrupt Enable
  val mie = RegInit(0.U(32.W).asTypeOf(new MIE))      // 0x304
  // Interrupt Waiting  
  val mip = RegInit(0.U(32.W).asTypeOf(new MIP))         // 0x344
  // Interrupt Entry Address
  val mtvec = RegInit(0.U(32.W).asTypeOf(new MTVEC()))    // 0x305
  // Error Address
  val mtval = RegInit(0.U(32.W))
  // Interrupt Temp Register
  val mscratch = RegInit(0.U(32.W)) // 0x340
  // Interrupt epc
  val mepc = RegInit(0.U(32.W))     // 0x341
  // Interrupt Cause
  val mcause = RegInit(0.U(32.W))   // 0x342

  //Machine Cycle
  //val mcycle = Reg(UInt(32.W))   // 0xB00
  //val mcycleh = Reg(UInt(32.W))  // 0xB80
  val satp = Reg(new SATP)
  //Client Register (For software interrupt and time interrupt)
  //val mtime = Reg(UInt(32.W))
  //val mtimecmp = Reg(UInt(32.W))
  //val msip = Reg(UInt(32.W))
  //Read CSR
  io.asid := satp.asid(ASIDLength - 1,0)
  io.baseppn := satp.ppn(PPNLength - 1,0)
  io.mode := satp.mode
  io.priv := prv

  io.read_csr_dat := 0.U(32.W)
  when(io.csr_ena & io.csr_rd_en){
    io.read_csr_dat := MuxLookup(io.csr_idx, 0.U(32.W), Seq(
        CSRConsts.MSTATUS  -> mstatus.asUInt(),
        CSRConsts.MIE      -> mie.asUInt(),
        CSRConsts.MIP      -> mip.asUInt(),
        CSRConsts.MTVEC    -> mtvec.asUInt(),
        CSRConsts.MTVAL    -> mtval.asUInt(),
        CSRConsts.MSCRATCH -> mscratch,
        CSRConsts.MEPC     -> mepc,
        CSRConsts.MCAUSE   -> mcause,
        CSRConsts.SATP     -> satp.asUInt()
        // "hb00".U(12.W) -> mcycle,
        // "hb80".U(12.W) -> mcycleh,
    ))
  }

  val wb_dat = MuxLookup(io.csr_cmd, 0.U, Seq(
    CSR.W -> io.wb_csr_dat,
    CSR.S -> (io.read_csr_dat | io.wb_csr_dat),
    CSR.C -> (io.read_csr_dat & ~io.wb_csr_dat)
  ))  

  val write_zero = Mux(io.csr_cmd === CSR.S || io.csr_cmd === CSR.C, io.inst(19,15) === 0.U(5.W), false.B)
  // in CSRRS/CSRRC/CSRRSI/CSRRCI, inst(19:15) == 0 indicates no writing.
  // but in CSRRW/CSRRWI, that means setting CSR to 0.U(32.W)

  //Write CSR logic
  when(io.csr_ena & io.csr_wr_en & ~write_zero){
    when(io.csr_idx === CSRConsts.MSTATUS){
      mstatus := wb_dat.asTypeOf(new MStatus())
    } 
    .elsewhen(io.csr_idx === CSRConsts.MIE){
      mie := wb_dat.asTypeOf(new MIE())
    }
    //.elsewhen(io.csr_idx === "h344".U(12.W)){ MIP is read only
      //mip := wb_dat
    //}
    .elsewhen(io.csr_idx === CSRConsts.MTVEC){
      mtvec := wb_dat.asTypeOf(new MTVEC())
    } 
    .elsewhen(io.csr_idx === CSRConsts.MSCRATCH){
      mscratch := wb_dat
    } 
    .elsewhen(io.csr_idx === CSRConsts.MEPC){
      mepc := wb_dat
    } 
    .elsewhen(io.csr_idx === CSRConsts.MCAUSE){
      mcause := wb_dat
    } 
    .elsewhen(io.csr_idx === CSRConsts.SATP){
      satp := wb_dat.asTypeOf(new SATP())
    }
    // .elsewhen(io.csr_idx === "hb00".U(12.W)){
    //   mcycle := wb_dat
    // } 
    // .elsewhen(io.csr_idx === "hb80".U(12.W)){
    //   mcycleh := wb_dat
    // }
    .otherwise{

    }
  }
  //Counters
  //cycle := cycle + 1.U
  //when(cycle.andR) { cycleh := cycleh + 1.U}
  //Exception Request
  io.expt := io.instIv || io.laddrIv || io.saddrIv || io.pcIv || io.isEcall || io.isEbreak || io.iPF || io.lPF || io.sPF
  io.evec := mtvec.base << 2
  io.epc := mepc
  io.interrupt := false.B
  //Interrupt Request
  val next_pc = Mux(io.next_pc, (io.pc >> 2 << 2) + 4.U(32.W), (io.pc >> 2 << 2))
  mip.meip := io.ext_irq_r
  mip.mtip := io.tmr_irq_r
  mip.msip := io.sft_irq_r
    
  //Handler
  when(io.expt){ //Exception Handler
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
                Mux(io.iPF, io.addr,
                Mux(io.lPF, io.addr,
                Mux(io.sPF, io.addr, 0.U(32.W))))))))

    prv := PRV.M
    mstatus.mpp := prv
    mstatus.mie := false.B
    mstatus.mpie := mstatus.mie
  } .elsewhen(io.isEret) { //Eret Handler

    prv := mstatus.mpp
    mstatus.mie := mstatus.mpie
  } .elsewhen((mip.meip | io.ext_irq_r) && mstatus.mie && mie.meie) { //Extenral Interrupt Handler
    mepc := next_pc
    mcause := Cat(Cause.Interrupt, Cause.MEI)
    //when(~io.ext_irq_r) {mip.meip := false.B}
    io.interrupt := true.B
    prv := PRV.M
    mstatus.mpp := prv
    mstatus.mie := false.B
    mstatus.mpie := mstatus.mie
  } .elsewhen((mip.msip | io.sft_irq_r) && mstatus.mie && mie.msie) { //Software Interrupt Handler
    mepc := next_pc
    mcause := Cat(Cause.Interrupt, Cause.MSI)
    //when(~io.tmr_irq_r) {mip.mtip := false.B}
    io.interrupt := true.B
    prv := PRV.M
    mstatus.mpp := prv
    mstatus.mie := false.B
    mstatus.mpie := mstatus.mie
  } .elsewhen(mip.mtip | io.tmr_irq_r){ //Time Interrupt Handler
    when(mstatus.mie & mie.mtie){
      mepc := next_pc
      mcause := Cat(Cause.Interrupt, Cause.MTI)
      //when(~io.sft_irq_r) {mip.msip := false.B}
      io.interrupt := true.B
      prv := PRV.M
      mstatus.mpp := prv
      mstatus.mie := false.B
      mstatus.mpie := mstatus.mie
    }
  }

  printf("mepc: %x, mcause: %x, mstatus: %x, mtvec: %x, mip: %x, mie: %x\n", mepc, mcause.asUInt, mstatus.asUInt, mtvec.asUInt, mip.asUInt, mie.asUInt)
  printf("io info: expt[%x], interrupt[%x], evec[%x], epc[%x]\n", io.expt, io.interrupt, io.evec, io.epc)

}
