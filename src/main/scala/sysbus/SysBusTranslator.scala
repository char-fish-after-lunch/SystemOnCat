package systemoncat.sysbus

import chisel3._
import chisel3.util.{BitPat, Lookup}

object SysBusTranslator{
    
}

class SysBusTranslator(map : Seq[(BitPat, UInt)], slaves : Seq[SysBusSlave]) extends Module with SysBusSlave{
    // val io = IO(new SysBusSlaveBundle)
    val slavesN = slaves.length


    val prevSlave = RegInit(0.U)
    val slaveSel = Lookup(io.adr_i, 0.U, map) // the selected slave

    prevSlave := slaveSel

    // from slaves
    val slave_dat_o = Wire(Vec(slavesN, UInt(32.W)))
    val slave_ack_o = Wire(Vec(slavesN, Bool()))
    val slave_err_o = Wire(Vec(slavesN, Bool()))
    val slave_rty_o = Wire(Vec(slavesN, Bool()))
    val slave_stall_o = Wire(Vec(slavesN, Bool()))

    for(i <- 0 until slavesN){
        slave_dat_o(i) := slaves(i).io.dat_o
        slave_ack_o(i) := slaves(i).io.ack_o
        slave_err_o(i) := slaves(i).io.err_o
        slave_rty_o(i) := slaves(i).io.rty_o
        slave_stall_o(i) := slaves(i).io.stall_o

        val prevSelected = (prevSlave === i.U)
        val selected = (slaveSel === i.U)

        slaves(i).io.dat_i := io.dat_i
        slaves(i).io.adr_i := io.adr_i
        slaves(i).io.cyc_i := Mux(prevSelected || selected, io.cyc_i, false.B)
        slaves(i).io.sel_i := io.sel_i
        slaves(i).io.we_i := io.we_i

        slaves(i).io.stb_i := Mux(selected, io.stb_i, false.B)
    }

    io.dat_o := slave_dat_o(prevSlave)
    io.ack_o := slave_ack_o(prevSlave)
    io.err_o := slave_err_o(prevSlave)
    io.rty_o := slave_rty_o(prevSlave)
    io.stall_o := slave_stall_o(slaveSel)

}
