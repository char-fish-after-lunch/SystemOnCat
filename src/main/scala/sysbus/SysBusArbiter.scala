package systemoncat.sysbus

import chisel3._
import chisel3.util.log2Up

class SysBusArbiter(masters: Seq[SysBusMaster]) extends Module with SysBusMaster{
    // val io = IO(new Bundle{
    //     val dat_o = Output(UInt(32.W))
    //     val adr_o = Output(UInt(32.W))
    //     val cyc_o = Output(Bool())
    //     val lock_o = Output(Bool())
    //     val sel_o = Output(UInt(4.W))
    //     val stb_o = Output(Bool())
    //     val we_o = Output(Bool())
    // })

    val mastersN = masters.length
    val masterWidth = log2Up(mastersN)

    // val master_dat_i = Wire(Vec(mastersN, UInt(32.W)))
    // val master_ack_i = Wire(Vec(mastersN, Bool()))
    // val master_stall_i = Wire(Vec(mastersN, Bool()))
    // val master_err_i = Wire(Vec(mastersN, Bool()))

    val master_dat_o = Wire(Vec(mastersN, UInt(32.W)))
    val master_adr_o = Wire(Vec(mastersN, UInt(32.W)))
    val master_cyc_o = Wire(Vec(mastersN, Bool()))
    val master_lock_o = Wire(Vec(mastersN, Bool()))
    val master_sel_o = Wire(Vec(mastersN, UInt(4.W)))
    val master_stb_o = Wire(Vec(mastersN, Bool()))
    val master_we_o = Wire(Vec(mastersN, Bool()))

    for(i <- 0 until mastersN){
        master_dat_o(i) := masters(i).io.dat_o
        master_adr_o(i) := masters(i).io.adr_o
        master_cyc_o(i) := masters(i).io.cyc_o
        master_lock_o(i) := masters(i).io.lock_o
        master_sel_o(i) := masters(i).io.sel_o
        master_stb_o(i) := masters(i).io.stb_o
        master_we_o(i) := masters(i).io.we_o
    }
    val turn = RegInit(0.U)
    turn := Mux(master_cyc_o(turn),
        turn, 
        Mux(turn + 1.U === mastersN.U, 0.U, turn + 1.U))

    for(i <- 0 until mastersN){
        val sel = (turn === i.U)
        masters(i).io.dat_i := Mux(sel, io.dat_i, 0.U)
        masters(i).io.ack_i := Mux(sel, io.ack_i, false.B)
        masters(i).io.err_i := Mux(sel, io.err_i, false.B)
        masters(i).io.rty_i := Mux(sel, io.rty_i, false.B)
    }
    
    
    io.dat_o := master_dat_o(turn)
    io.adr_o := master_adr_o(turn)
    io.cyc_o := master_cyc_o(turn)
    io.lock_o := master_lock_o(turn)
    io.sel_o := master_sel_o(turn)
    io.stb_o := master_stb_o(turn)
    io.we_o := master_we_o(turn)

}
