package systemoncat.cache

import systemoncat.sysbus.SysBusFilterBundle

import chisel3._
import chisel3.core.Module
import chisel3.util._


class CacheBundle extends Bundle{
    val bus = new SysBusFilterBundle
}

class Cache(blockWidth : Int, wayCount : Int, indexWidth : Int) extends Module{
    private def STATE_IDLE = 0.U
    private def STATE_READ = 1.U
    private def STATE_WRITE = 2.U

    val io = IO(new CacheBundle)
    val indexStart = blockWidth
    val tagStart = blockWidth + indexWidth
    val blockSize = 1 << (blockWidth - 2)
    
    val tags = (Mem(1 << indexWidth, Vec(wayCount, UInt(32 - indexWidth - blockWidth))))
    val valids = (Mem(1 << indexWidth, Vec(wayCount, Bool())))
    val dirtys = (Mem(1 << indexWidth, Vec(wayCount, Bool())))
    val blocks = (Mem(1 << indexWidth, Vec(wayCount, UInt(3 << blockWidth))))

    val state = RegInit(UInt(4.W), STATE_IDLE)
    
    val cur_adr = RegInit(UInt(32.W), 0.U)
    val cur_index = cur_adr(tagStart - 1, indexStart)
    val cur_tag = cur_adr(31, tagStart)
    val cur_offset = cur_adr(indexStart - 1, 0) & ~3.U
    val cur_way_index = tags(cur_index).indexWhere((entry) => entry === cur_tag)
    val cur_entry_valid = tags(cur_index)(cur_way_index) === cur_tag && 
        valids(cur_index)(cur_way_index)
    val cur_dat = RegInit(UInt(32.W), 0.U)
    val cur_sel = RegInit(UInt(4.W), 0.U)

    val ack = (state === STATE_READ || state === STATE_WRITE) && cur_entry_valid
    val stall = Wire(Bool())
    
    val ans = Wire(UInt(32.W))
    for(i <- 0 until 32){
        dat_o(i) := blocks(cur_index)(cur_way_index)((cur_offset << 3.U) + i.U)
    }
    val next_victim = RegInit(UInt(log2Up(wayCount).W), 0.U)

    val ld_count = RegInit(0.U)
    val wb_count = RegInit(0.U)

    val adr_o = Wire(UInt(32.W))
    val we_o = Wire(Bool())
    val cyc_o = Wire(Bool())
    val dat_o = Wire(UInt(32.W))

    adr_o := 0.U
    cyc_o := false.B
    we_o := false.B
    dat_o := 0.U

    val ack_i = RegInit(Bool(), false.B)
    val dat_i = RegInit(UInt(32.W), 0.U)
    ack_i := io.bus.master.ack_i
    dat_i := io.bus.master.dat_i

    io.bus.master.cyc_o := cyc_o
    io.bus.master.stb_o := cyc_o
    io.bus.master.dat_o := dat_o
    io.bus.master.we_o := we_o
    io.bus.master.adr_o := adr_o
    io.bus.master.sel_o := 0xf.U

    io.bus.slave.ack_o := ack
    io.bus.slave.err_o := false.B
    io.bus.slave.rty_o := false.B
    io.bus.slave.stall_o := state != STATE_IDLE && !ack

    when(io.bus.slave.cyc_i && io.bus.slave.stb_i && !stall){
        state := Mux(io.bus.slave.we_i, STATE_WRITE, STATE_READ)
        cur_adr := io.bus.slave.adr_i
        cur_dat := io.bus.slave.dat_i
        cur_sel := io.bus.slave.sel_i
        ld_count := 0.U
        wb_count := 0.U
    }.otherwise {
        when(state === STATE_READ || state === STATE_WRITE){
            when(ack){
                when(state === STATE_WRITE){
                    for(i <- 0 until 4){
                        when(cur_sel(i)){
                            for(j <- 0 until 8){
                                blocks(cur_index)(cur_way_index)(((cur_offset + i.U) << 3.U) + j.U) := cur_dat((i << 3) + j)
                            }
                        }
                    }
                    dirtys(cur_index)(cur_way_index) := true.B
                }
                state := STATE_IDLE
            }.elsewhen(ld_count === 0.U){
                // prepare
                val vacant_way_index = valids(cur_index).indexWhere((entry) => !entry)
                when(!valids(cur_index)(vacant_way_index)){
                    // happily, a vacant slot found
                    tags(cur_index)(vacant_way_index) := cur_tag
                    valids(cur_index)(vacant_way_index) := false.B
                    ld_count := ld_count + 1.U

                    we_o := false.B
                    adr_o := Cat(Seq(cur_tag, cur_index, 0.U(blockWidth)))
                    cyc_o := true.B                    
                }.elsewhen(!dirtys(cur_index)(next_victim) || (wb_count === (blockSize - 1).U && ack_i)){
                    // we can safely remove this block
                    tags(cur_index)(next_victim) := cur_tag
                    valids(cur_index)(next_victim) := false.B
                    next_victim := next_victim + 1.U
                    ld_count := ld_count + 1.U
                    
                    we_o := false.B
                    adr_o := Cat(Seq(cur_tag, cur_index, 0.U(blockWidth.W)))
                    cyc_o := true.B
                }.otherwise{
                    // write back
                    val offset = Wire(UInt(blockWidth))
                    when(wb_count > 0.U && !io.bus.master.ack_i){
                        offset := (wb_count - 1.U) << 2.U
                    }.otherwise{
                        offset := wb_count << 2.U
                        wb_count := wb_count + 1.U
                    }
                    for(i <- 0 until 32){
                        dat_o(i) := blocks(cur_index)(next_victim)((offset << 3.U) + i.U)
                    }
                    adr_o := Cat(Seq(tags(cur_index)(next_victim), cur_index, offset))
                    we_o := true.B
                    cyc_o := true.B
                }
            }.elsewhen(ld_count === (blockSize - 1).U && ack_i){
                for(i <- 0 until 32){
                    blocks(cur_index)(cur_way_index)(((ld_count - 1.U) << 5.U) + i.U) := dat_i(i)
                }
                valids(cur_index)(cur_way_index) := true.B
                dirtys(cur_index)(cur_way_index) := state === STATE_WRITE
            }.otherwise{
                val offset = Wire(UInt(blockWidth))
                when(!io.bus.master.ack_i){
                    offset := (ld_count - 1.U) << 2.U
                }.otherwise{
                    for(i <- 0 until 32){
                        blocks(cur_index)(cur_way_index)(((ld_count - 1.U) << 5.U) + i.U) := dat_i(i)
                    }
                    offset := wb_count << 2.U
                    ld_count := ld_count + 1.U
                }
                
                we_o := false.B
                adr_o := Cat(Seq(cur_tag, cur_index, offset))
                cyc_o := true.B
            }
        }

    }

}
