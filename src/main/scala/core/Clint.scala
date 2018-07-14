package systemoncat.core
import chisel3._
import chisel3.util._
import chisel3.testers._
object CliAddr{
	val msip_addr = "h2000000".U(32.W)
	val cmph_addr = "h2004004".U(32.W)
	val cmpl_addr = "h2004000".U(32.W)
	val tmeh_addr = "h200bffc".U(32.W)
	val tmel_addr = "h200bff8".U(32.W)
}

class ClientIO() extends Bundle{
	// commands
	val cli_en = Input(Bool())
	val cli_rd_en = Input(Bool())
	val cli_wr_en = Input(Bool())
	
	// Selection Address
	val addr = Input(UInt(32.W))

	// Read Write Data
	val read_cli_dat = Output(UInt(32.W))
	val wb_cli_dat = Output(UInt(32.W))

	//Interrupt
	val sft_irq_r = Output(Bool())
	val tmr_irq_r = Output(Bool())
}

class Client() extends Module{
	val io = IO(new ClientIO)

	//Software Interrupt Register
	val msip = Reg(UInt(32.W))

	//Time Interrupt Register
	val cmpl = Reg(UInt(32.W))
	val cmph = Reg(UInt(32.W))
	val tmel = Reg(UInt(32.W))
	val tmeh = Reg(UInt(32.W))

	//Time Add
	tmel := tmel + 1.U
	when(tmel.andR){tmeh := tmeh + 1.U}

	//Time Interrupt Generate
	val cmp = Cat(cmph, cmpl)
	val tme = Cat(tmeh, tmel)
	when(tme >= cmp){ io.tmr_irq_r := true.B } .otherwise { io.tmr_irq_r := false.B }

	//Software Interrupt Generate
	when(msip === 1.U(32.W)){ io.sft_irq_r := true.B } .otherwise { io.sft_irq_r := false.B }

	//Read Logic
	when(io.cli_en & io.cli_rd_en){
		when(io.addr === CliAddr.msip_addr){
			io.read_cli_dat := msip
		}
		.elsewhen(io.addr === CliAddr.cmph_addr){
			io.read_cli_dat := cmph
		}
		.elsewhen(io.addr === CliAddr.cmpl_addr){
			io.read_cli_dat := cmpl
		}
		.elsewhen(io.addr === CliAddr.tmeh_addr){
			io.read_cli_dat := tmeh
		}
		.elsewhen(io.addr === CliAddr.tmel_addr){
			io.read_cli_dat := tmel
		}
		.otherwise{
			io.read_cli_dat := 0.U(32.W)
		}
	}

	//Write Logic
	when(io.cli_en & io.cli_wr_en){
		when(io.addr === CliAddr.msip_addr){
			msip := io.wb_cli_dat
		}
		.elsewhen(io.addr === CliAddr.cmph_addr){
			cmph := io.wb_cli_dat
		}
		.elsewhen(io.addr === CliAddr.cmpl_addr){
			cmpl := io.wb_cli_dat
		}
		.elsewhen(io.addr === CliAddr.tmeh_addr){
			tmeh := io.wb_cli_dat
		}
		.elsewhen(io.addr === CliAddr.tmel_addr){
			tmel := io.wb_cli_dat
		}
		.otherwise{

		}
	}
}