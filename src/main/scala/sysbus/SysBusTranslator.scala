package systemoncat.sysbus

import chisel3._
import chisel3.util.{BitPat, Lookup}

object SysBusTranslator{
    
}

class SysBusTranslator(map : Seq[(BitPat, UInt)], slaves : Seq[SysBusSlave]) extends Module with SysBusSlave{
    // val io = IO(new SysBusSlaveBundle)
    val slavesN = slaves.length

    val slaveSel = Lookup(io.adr_i, 0.U, map) // the selected slave

    // from slaves
    val slave_dat_o = Wire(Vec(slavesN, UInt(32.W)))
    val slave_ack_o = Wire(Vec(slavesN, Bool()))
    val slave_stall_o = Wire(Vec(slavesN, Bool()))
    val slave_err_o = Wire(Vec(slavesN, Bool()))
    val slave_rty_o = Wire(Vec(slavesN, Bool()))

    val slave_sel_i = Wire(Vec(slavesN, Bool()))

    for(i <- 0 until slavesN){
        slave_dat_o(i) := slaves(i).io.dat_o
        slave_ack_o(i) := slaves(i).io.ack_o
        slave_stall_o(i) := slaves(i).io.stall_o
        slave_err_o(i) := slaves(i).io.err_o
        slave_rty_o(i) := slaves(i).io.rty_o

        slaves(i).io.dat_i := io.dat_i
        slaves(i).io.adr_i := io.adr_i
        slaves(i).io.cyc_i := io.cyc_i
        slaves(i).io.sel_i := io.sel_i
        slaves(i).io.we_i := io.we_i

        slaves(i).io.stb_i := (slaveSel === i.U)
    }

    io.dat_o := slave_dat_o(slaveSel)
    io.ack_o := slave_ack_o(slaveSel)
    io.stall_o := slave_stall_o(slaveSel)
    io.err_o := slave_err_o(slaveSel)
    io.rty_o := slave_rty_o(slaveSel)


}
