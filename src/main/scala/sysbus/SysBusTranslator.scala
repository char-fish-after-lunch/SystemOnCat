package systemoncat.sysbus

import chisel3._
import chisel3.util.{BitPat, Lookup}

object SysBusTranslator{
    
}

class SysBusSlaveBundleIO(slavesN : Int) extends Bundle {
    val out = new SysBusSlaveBundle
    val in = Vec(slavesN, Flipped(new SysBusSlaveBundle))
}

class SysBusTranslator(map : Seq[(BitPat, UInt)], slaves : Seq[SysBusSlave]) extends Module {
    val slavesN = slaves.length
    val io = IO(new SysBusSlaveBundleIO(slavesN))

    val slaveSel = Lookup(io.out.adr_i, 0.U, map) // the selected slave

    // from slaves
    val slave_dat_o = Wire(Vec(slavesN, UInt(32.W)))
    val slave_ack_o = Wire(Vec(slavesN, Bool()))
    val slave_err_o = Wire(Vec(slavesN, Bool()))
    val slave_rty_o = Wire(Vec(slavesN, Bool()))
    val slave_stall_o = Wire(Vec(slavesN, Bool()))

    for(i <- 0 until slavesN){
        slave_dat_o(i) := io.in(i).dat_o
        slave_ack_o(i) := io.in(i).ack_o
        slave_err_o(i) := io.in(i).err_o
        slave_rty_o(i) := io.in(i).rty_o
        slave_stall_o(i) := io.in(i).stall_o

        io.in(i).dat_i := io.out.dat_i
        io.in(i).adr_i := io.out.adr_i
        io.in(i).cyc_i := io.out.cyc_i
        io.in(i).sel_i := io.out.sel_i
        io.in(i).we_i := io.out.we_i

        io.in(i).stb_i := (slaveSel === i.U)
    }

    io.out.dat_o := slave_dat_o(slaveSel)
    io.out.ack_o := slave_ack_o(slaveSel)
    io.out.err_o := slave_err_o(slaveSel)
    io.out.rty_o := slave_rty_o(slaveSel)
    io.out.stall_o := slave_stall_o(slaveSel)

}
