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
    
    val r_tags = Mem(wayCount * (1 << indexWidth), UInt((32 - indexWidth - blockWidth).W))
    val r_valids = Mem(wayCount * (1 << indexWidth), Bool())
    val r_dirtys = Mem(wayCount * (1 << indexWidth), Bool())
    val r_blocks = Mem(wayCount * (1 << indexWidth), UInt(((8 << blockWidth)).W))
    

    def getWayIndex(_index: UInt, _tag: UInt) : UInt = {
        val _way_index = Wire(UInt(log2Up(wayCount).W))
        _way_index := 0.U
        for(i <- 0 until wayCount){
            when(r_tags(_index * wayCount.U + i.U) === _tag){
                _way_index := i.U
            }
        }
        _way_index
    }

    def isEntryValid(_index: UInt, _tag: UInt, _way_index: UInt) : UInt = {
        r_tags(_index * wayCount.U + way_index) === _tag && 
            r_valids(_index * wayCount.U + way_index)
    }

    val stb := io.bus.slave.stb_i
    val cyc := io.bus.slave.cyc_i
    val we := io.bus.slave.we_i
    val adr := io.bus.slave.adr_i
    val index = adr(tagStart - 1, indexStart)
    val tag = adr(31, tagStart)
    val offset = adr(indexStart - 1, 0) & ~3.U
    val way_index = getWayIndex(index, tag)
    val entry_valid = isEntryValid(_index, _tag, _way_index)
    val dat := io.bus.slave.dat_i
    val sel := io.bus.slave.sel_i


    val cur_adr = RegInit(UInt(32.W), 0.U)
    val cur_index = cur_adr(tagStart - 1, indexStart)
    val cur_tag = cur_adr(31, tagStart)
    val cur_offset = cur_adr(indexStart - 1, 0) & ~3.U
    val cur_way_index = getWayIndex(cur_index, cur_tag)    
    val cur_entry_valid = isEntryValid(cur_index, cur_tag, cur_way_index)
    val cur_dat = RegInit(UInt(32.W), 0.U)
    val cur_sel = RegInit(UInt(4.W), 0.U)


    val state = RegInit(UInt(4.W), STATE_IDLE)

    val ack = ((state === STATE_READ || state === STATE_WRITE) && cur_entry_valid) ||
        (state == STATE_IDLE && entry_valid)
    val stall = state != STATE_IDLE && !ack

    val next_victim = RegInit(UInt(log2Up(wayCount).W), 0.U)

    printf("VICLEN %d\n", log2Up(wayCount).U)

    val ld_count = RegInit(0.U)
    val wb_count = RegInit(0.U)

    val adr_o = Wire(UInt(32.W))
    val we_o = Wire(Bool())
    val cyc_o = Wire(Bool())
    val dat_o = Wire(UInt(32.W))

    val ans = Wire(UInt(32.W))
    ans := Mux(state == STATE_IDLE, 
        (r_blocks(index * wayCount.U + way_index) >> (offset << 3.U))(31, 0),
        (r_blocks(cur_index * wayCount.U + cur_way_index) >> (cur_offset << 3.U))(31, 0))


    adr_o := 0.U
    cyc_o := false.B
    we_o := false.B
    dat_o := 0.U

    val ack_i = RegInit(Bool(), false.B)
    val dat_i = RegInit(UInt(32.W), 0.U)
    ack_i := io.bus.master.ack_i
    dat_i := io.bus.master.dat_i


    io.bus.slave.ack_o := ack
    io.bus.slave.err_o := false.B
    io.bus.slave.rty_o := false.B
    io.bus.slave.stall_o := stall
    io.bus.slave.dat_o := ans

    printf("ld_count = %d, wb_count = %d, current_entry_valid = %d\n", ld_count, wb_count, cur_entry_valid)
    printf("cur_adr = %d, cur_index = %d, cur_tag = %d, dirty = %d\n", cur_adr, cur_index, cur_tag, r_dirtys(cur_index * wayCount.U + cur_way_index))


    def writeEntry(_index : UInt, _way_index : UInt, _offset : UInt, _dat : UInt, _sel : UInt){
        val new_data = Vec(r_blocks(_index * wayCount.U + _way_index).toBools)
        for(i <- 0 until 4){
            when(_sel(i)){
                for(j <- 0 until 8){
                    new_data(((_offset + i.U) << 3.U) + j.U) := _dat((i << 3) + j)
                }
            }
        }
        r_blocks(_index * wayCount.U + _way_index) := new_data.asUInt()
        r_dirtys(_index * wayCount.U + _way_index) := true.B 
    }

    when(cyc && stb && state == STATE_IDLE && !ack){
        when(!entry_valid){
            state := Mux(we, STATE_WRITE, STATE_READ)
            cur_adr := adr
            cur_dat := dat
            cur_sel := sel
            ld_count := 0.U
            wb_count := 0.U
        }.elsewhen(we){
            writeEntry(index, way_index, offset, dat, sel)
        }
    }.elsewhen(state === STATE_READ || state === STATE_WRITE){
        when(ack){
            when(state === STATE_WRITE){
                writeEntry(cur_index, cur_way_index, cur_offset, cur_dat, cur_sel)
            }
            state := STATE_IDLE
        }.elsewhen(ld_count === 0.U){
            // prepare
            val vacant_way_index = Wire(UInt(log2Up(wayCount).W))
            vacant_way_index := 0.U

            for(i <- 0 until wayCount){
                when(!r_valids(cur_index * wayCount.U + i.U)){
                    vacant_way_index := i.U
                }
            }
            when(!r_valids(cur_index * wayCount.U + vacant_way_index)){
                // happily, a vacant slot found
                r_tags(cur_index * wayCount.U + vacant_way_index) := cur_tag
                r_valids(cur_index * wayCount.U + vacant_way_index) := false.B
                ld_count := ld_count + 1.U

                we_o := false.B
                adr_o := Cat(Seq(cur_tag, cur_index, 0.U(blockWidth)))
                cyc_o := true.B                    
            }.elsewhen(!r_dirtys(cur_index * wayCount.U + next_victim) || (wb_count === blockSize.U && ack_i)){
                // we can safely remove this block
                printf("hoho %d %d %d\n", next_victim, r_tags(cur_index * wayCount.U + cur_way_index),
                    r_valids(cur_index * wayCount.U + cur_way_index))
                r_tags(cur_index * wayCount.U + next_victim) := cur_tag
                r_valids(cur_index * wayCount.U + next_victim) := false.B
                if(wayCount != 1){
                    next_victim := next_victim + 1.U
                }
                ld_count := ld_count + 1.U
                
                we_o := false.B
                adr_o := Cat(Seq(cur_tag, cur_index, 0.U(blockWidth.W)))
                cyc_o := true.B
            }.otherwise{
                printf("bad!\n");
                // write back
                val offset = Wire(UInt(blockWidth.W))
                when(wb_count > 0.U && !io.bus.master.ack_i){
                    offset := (wb_count - 1.U) << 2.U
                }.otherwise{
                    offset := wb_count << 2.U
                    wb_count := wb_count + 1.U
                }
                dat_o := (r_blocks(cur_index * wayCount.U + next_victim) >> (offset << 3.U))(31, 0)
                adr_o := Cat(Seq(r_tags(cur_index * wayCount.U + next_victim), cur_index, offset))
                we_o := true.B
                cyc_o := true.B
            }
        }.otherwise{
            val c_offset = Wire(UInt(blockWidth.W))
            when(!io.bus.master.ack_i){
                c_offset := (ld_count - 1.U) << 2.U
            }.otherwise{
                writeEntry(cur_index, cur_way_index, (ld_count - 1.U) << 
                val new_data = Vec(r_blocks(cur_index * wayCount.U + cur_way_index).toBools)
                for(i <- 0 until 32){
                    new_data(((ld_count - 1.U) << 5.U) + i.U) := dat_i(i)
                }
                r_blocks(cur_index * wayCount.U + cur_way_index) := new_data.asUInt
                c_offset := wb_count << 2.U
                ld_count := ld_count + 1.U
            }
            when(ld_count === blockSize.U && ack_i){
                r_valids(cur_index * wayCount.U + cur_way_index) := true.B
                r_dirtys(cur_index * wayCount.U + cur_way_index) := false.B
            }.otherwise{
                we_o := false.B
                adr_o := Cat(Seq(cur_tag, cur_index, c_offset))
                cyc_o := true.B
            }
        }
    }

    io.bus.master.cyc_o := cyc_o
    io.bus.master.stb_o := cyc_o
    io.bus.master.dat_o := dat_o
    io.bus.master.we_o := we_o
    io.bus.master.adr_o := adr_o
    io.bus.master.sel_o := 0xf.U


}
