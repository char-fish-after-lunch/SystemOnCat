package systemoncat.sysbus

import chisel3._
import chisel3.util.log2Up
import chisel3.util.Cat
import chisel3.util.Log2
import systemoncat.core._

class SysBusArbiterIO(mastersN : Int) extends Bundle {
    val out = Flipped(new SysBusSlaveBundle)
    val in = Vec(mastersN, new SysBusSlaveBundle)
}

class SysBusArbiter(mastersN : Int) extends Module {
    val io = IO(new SysBusArbiterIO(mastersN))

    val masterWidth = log2Up(mastersN)

    val master_dat_i = Wire(Vec(mastersN, UInt(32.W)))
    val master_adr_i = Wire(Vec(mastersN, UInt(32.W)))
    val master_cyc_i = Wire(Vec(mastersN, Bool()))
    val master_sel_i = Wire(Vec(mastersN, UInt(4.W)))
    val master_stb_i = Wire(Vec(mastersN, Bool()))
    val master_we_i = Wire(Vec(mastersN, Bool()))


    for(i <- 0 until mastersN){
        master_dat_i(i) := io.in(i).dat_i
        master_adr_i(i) := io.in(i).adr_i
        master_cyc_i(i) := io.in(i).cyc_i
        master_sel_i(i) := io.in(i).sel_i
        master_stb_i(i) := io.in(i).stb_i
        master_we_i(i) := io.in(i).we_i
    }

    
    val prev_turn = RegInit(UInt(mastersN.W), 0.U)
    val turn = Wire(UInt(mastersN.W))
    val token = RegInit(UInt(mastersN.W), 1.U)

    prev_turn := turn
    
    val double_req = Wire(UInt((mastersN*2).W))
    val double_masked = Wire(UInt((mastersN*2).W))
    val double_low = Wire(UInt((mastersN*2).W))
    double_req := Cat(master_cyc_i.asUInt(), master_cyc_i.asUInt())
    double_masked := (~(Cat(0.U(mastersN.W), token) - 1.U)) & double_req
    double_low := double_masked & (-double_masked)
    turn := Mux(prev_turn === 0.U || io.out.rty_o || io.out.ack_o || io.out.err_o, 
        double_low(mastersN * 2 - 1, mastersN) | double_low(mastersN - 1, 0),
        prev_turn);


    token := Mux(turn === 0.U, token, Mux(turn(mastersN - 1) === 1.U, 1.U, turn << 1))

    for(i <- 0 until mastersN){
        io.in(i).dat_o := Mux(prev_turn(i), io.out.dat_o, 0.U)
        io.in(i).ack_o := Mux(prev_turn(i), io.out.ack_o, false.B)
        io.in(i).err_o := Mux(prev_turn(i), io.out.err_o, false.B)
        io.in(i).rty_o := Mux(prev_turn(i), io.out.rty_o, false.B)
        io.in(i).stall_o := Mux(turn(i), io.out.stall_o, true.B)
    }
    
    val turnNum = Wire(UInt(masterWidth.W))
    turnNum := Log2(turn | 1.U)
    io.out.dat_i := master_dat_i(turnNum)
    io.out.adr_i := master_adr_i(turnNum)
    io.out.cyc_i := master_cyc_i(turnNum)
    io.out.sel_i := master_sel_i(turnNum)
    io.out.stb_i := master_stb_i(turnNum)
    io.out.we_i := master_we_i(turnNum)
}
