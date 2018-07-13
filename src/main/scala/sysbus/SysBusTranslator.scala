package systemoncat.sysbus

import chisel3._
import chisel3.util.{BitPat, Lookup}

object SysBusTranslator{
    
}

class SysBusTranslator(map : Seq[(BitPat, UInt)], slaves : Seq[SysBusSlave]) extends Module with SysBusSlave{
    // val io = IO(new SysBusSlaveBundle)
    val slavesN = slaves.length

    val slaveSel = Lookup(io.out.adr_i, 0.U, map) // the selected slave

    // from slaves
    val slave_dat_o = Wire(Vec(slavesN, UInt(32.W)))
    val slave_ack_o = Wire(Vec(slavesN, Bool()))
    val slave_err_o = Wire(Vec(slavesN, Bool()))
    val slave_rty_o = Wire(Vec(slavesN, Bool()))
    val slave_stall_o = Wire(Vec(slavesN, Bool()))

    for(i <- 0 until slavesN){
        slave_dat_o(i) := slaves(i).io.out.dat_o
        slave_ack_o(i) := slaves(i).io.out.ack_o
        slave_err_o(i) := slaves(i).io.out.err_o
        slave_rty_o(i) := slaves(i).io.out.rty_o
        slave_stall_o(i) := slaves(i).io.out.stall_o

        slaves(i).io.out.dat_i := io.out.dat_i
        slaves(i).io.out.adr_i := io.out.adr_i
        slaves(i).io.out.cyc_i := io.out.cyc_i
        slaves(i).io.out.sel_i := io.out.sel_i
        slaves(i).io.out.we_i := io.out.we_i

        slaves(i).io.out.stb_i := (slaveSel === i.U)
    }

    io.out.dat_o := slave_dat_o(slaveSel)
    io.out.ack_o := slave_ack_o(slaveSel)
    io.out.err_o := slave_err_o(slaveSel)
    io.out.rty_o := slave_rty_o(slaveSel)
    io.out.stall_o := slave_stall_o(slaveSel)

}
