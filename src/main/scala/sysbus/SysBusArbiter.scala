package systemoncat.sysbus

import chisel3._
import chisel3.util.log2Up
import chisel3.util.Cat
import chisel3.util.Log2

class SysBusArbiter(masters: Seq[SysBusMaster]) extends Module with SysBusMaster{
    val mastersN = masters.length
    val masterWidth = log2Up(mastersN)

    val master_dat_o = Wire(Vec(mastersN, UInt(32.W)))
    val master_adr_o = Wire(Vec(mastersN, UInt(32.W)))
    val master_cyc_o = Wire(Vec(mastersN, Bool()))
    val master_sel_o = Wire(Vec(mastersN, UInt(4.W)))
    val master_stb_o = Wire(Vec(mastersN, Bool()))
    val master_we_o = Wire(Vec(mastersN, Bool()))


    for(i <- 0 until mastersN){
        master_dat_o(i) := masters(i).io.dat_o
        master_adr_o(i) := masters(i).io.adr_o
        master_cyc_o(i) := masters(i).io.cyc_o
        master_sel_o(i) := masters(i).io.sel_o
        master_stb_o(i) := masters(i).io.stb_o
        master_we_o(i) := masters(i).io.we_o
    }

    
    val prev_turn = RegInit(UInt(mastersN.W), 0.U)
    val turn = Wire(UInt(mastersN.W))
    val token = RegInit(UInt(mastersN.W), 1.U)

    prev_turn := turn
    
    val double_req = Wire(UInt((mastersN*2).W))
    val double_masked = Wire(UInt((mastersN*2).W))
    val double_low = Wire(UInt((mastersN*2).W))
    double_req := Cat(master_cyc_o.asUInt(), master_cyc_o.asUInt())
    double_masked := (~(Cat(0.U(mastersN.W), token) - 1.U)) & double_req
    double_low := double_masked & (-double_masked)
    turn := Mux(prev_turn === 0.U || io.rty_i || io.ack_i || io.err_i, 
        double_low(mastersN * 2 - 1, mastersN) | double_low(mastersN - 1, 0),
        prev_turn);


    token := Mux(turn === 0.U, token, Mux(turn(mastersN - 1) === 1.U, 1.U, turn << 1))

    for(i <- 0 until mastersN){
        masters(i).io.dat_i := Mux(prev_turn(i), io.dat_i, 0.U)
        masters(i).io.ack_i := Mux(prev_turn(i), io.ack_i, false.B)
        masters(i).io.err_i := Mux(prev_turn(i), io.err_i, false.B)
        masters(i).io.rty_i := Mux(prev_turn(i), io.rty_i, false.B)
        masters(i).io.stall_i := Mux(turn(i), io.stall_i, true.B)
    }
    
    val turnNum = Wire(UInt(masterWidth.W))
    turnNum := Log2(turn | 1.U)
    io.dat_o := master_dat_o(turnNum)
    io.adr_o := master_adr_o(turnNum)
    io.cyc_o := master_cyc_o(turnNum)
    io.sel_o := master_sel_o(turnNum)
    io.stb_o := master_stb_o(turnNum)
    io.we_o := master_we_o(turnNum)

}
