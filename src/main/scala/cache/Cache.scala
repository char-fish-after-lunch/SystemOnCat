package systemoncat.cache

import systemoncat.sysbus.SysBusFilterBundle

import chisel3._
import chisel3.core.Module
import chisel3.util._


class CacheBundle extends Bundle{
    val bus = new SysBusFilterBundle
}

class Cache(blockWidth : Int, wayCount : Int, indexWidth : Int, notToCache: Seq[BitPat]) extends Module{
    private def STATE_IDLE = 0.U
    private def STATE_FETCH = 1.U
    private def STATE_WRITE_BACK = 2.U

    val io = IO(new CacheBundle)
    val indexStart = blockWidth
    val tagStart = blockWidth + indexWidth
    val blockSize = 1 << (blockWidth - 2)
    
    val r_tags = Mem(wayCount * (1 << indexWidth), UInt((32 - indexWidth - blockWidth).W))
    val r_valids = Mem(wayCount * (1 << indexWidth), Bool())
    val r_dirtys = Mem(wayCount * (1 << indexWidth), Bool())
    val r_blocks = Mem(wayCount * (1 << indexWidth), UInt(((8 << blockWidth)).W))

    def isValid(_index : UInt, _way_index : UInt) : Bool = r_valids(_index * wayCount.U + _way_index)
    def getTag(_index : UInt, _way_index: UInt) : UInt = r_tags(_index * wayCount.U + _way_index)
    def isDirty(_index : UInt, _way_index : UInt) : Bool = r_dirtys(_index * wayCount.U + _way_index)
    def getData(_index : UInt, _way_index : UInt, _bit_offset : UInt, _bit_count : Int) : UInt = {
        (r_blocks(_index * wayCount.U + _way_index) >> _bit_offset)(_bit_count - 1, 0)
    }

    def getWayIndex(_index: UInt, _tag: UInt) : UInt = {
        val _way_index = Wire(UInt(log2Up(wayCount).W))
        _way_index := 0.U
        for(i <- 0 until wayCount){
            when(getTag(_index, i.U) === _tag){
                _way_index := i.U
            }
        }
        _way_index
    }

    def isEntryValid(_index: UInt, _tag: UInt, _way_index: UInt) : Bool = {
        getTag(_index, _way_index) === _tag && 
            isValid(_index, _way_index)
    }

    def getVacantWay(_index: UInt) : UInt = {
        val vacant_way_index = Wire(UInt(log2Up(wayCount).W))
            vacant_way_index := 0.U

        for(i <- 0 until wayCount){
            when(!isValid(_index, i.U)){
                vacant_way_index := i.U
            }
        }
        vacant_way_index
    }



    val stb = io.bus.slave.stb_i
    val cyc = io.bus.slave.cyc_i
    val we = io.bus.slave.we_i
    val adr = io.bus.slave.adr_i
    val index = adr(tagStart - 1, indexStart)
    val tag = adr(31, tagStart)
    val offset = (adr(indexStart - 1, 0) & ~3.U)(indexStart - 1, 0)
    val way_index = getWayIndex(index, tag)
    val entry_valid = isEntryValid(index, tag, way_index)
    val dat = io.bus.slave.dat_i
    val sel = io.bus.slave.sel_i

    val cur_adr = RegInit(UInt(32.W), 0.U)
    val cur_index = cur_adr(tagStart - 1, indexStart)
    val cur_tag = cur_adr(31, tagStart)
    val cur_offset = (cur_adr(indexStart - 1, 0) & ~3.U)(indexStart - 1, 0)
    val cur_way_index = RegInit(UInt(log2Up(wayCount).W), 0.U)   
    val cur_entry_valid = isEntryValid(cur_index, cur_tag, cur_way_index)
    val cur_dat = RegInit(UInt(32.W), 0.U)
    val cur_sel = RegInit(UInt(4.W), 0.U)
    val cur_we = RegInit(Bool(), false.B)


    val state = RegInit(UInt(4.W), STATE_IDLE)

    val fetch_old = RegInit(Bool(), false.B)
    val fetch_done = Wire(Bool())
    fetch_done := false.B



    val next_victim = RegInit(UInt(log2Up(wayCount).W), 0.U)

    val adr_o = Wire(UInt(32.W))
    val we_o = Wire(Bool())
    val cyc_o = Wire(Bool())
    val dat_o = Wire(UInt(32.W))

    val op_counter = RegInit(UInt((blockWidth - 1).W), 0.U)

    val fetch_ans = RegInit(UInt(32.W), 0.U)

    adr_o := 0.U
    cyc_o := false.B
    we_o := false.B
    dat_o := 0.U



    printf("current_state = %d\n", state)
    printf("valid = %d, %x, %x, %d, %d, %d\n", entry_valid, tag, getTag(index, way_index),
        isDirty(index, way_index), index, way_index)
    printf("adr = %x\n", adr)

    val bus_stalled = RegInit(Bool(), false.B)
    bus_stalled := io.bus.master.stall_o
    val bus_in_dat = RegInit(UInt(32.W), 0.U)
    bus_in_dat := io.bus.master.dat_o

    def writeEntry(_index : UInt, _way_index : UInt, _word_offset : UInt, _dat : UInt, _sel : UInt){
        val new_data = Vec(r_blocks(_index * wayCount.U + _way_index).toBools)
        for(i <- 0 until 4){
            when(_sel(i)){
                for(j <- 0 until 8){
                    new_data((((_word_offset << 2.U) + i.U) << 3.U) + j.U) := _dat((i << 3) + j)
                }
            }
        }
        r_blocks(_index * wayCount.U + _way_index) := new_data.asUInt()
        r_dirtys(_index * wayCount.U + _way_index) := true.B 
    }

    def writeToBus(_index : UInt, _way_index : UInt, _byte_offset : UInt){
        dat_o := getData(_index, _way_index, _byte_offset << 3.U, 32)
        adr_o := Cat(Seq(getTag(_index, _way_index), _index, _byte_offset))
        we_o := true.B
        cyc_o := true.B
    }

    def readFromBus(_index : UInt, _tag : UInt, _byte_offset : UInt){
        adr_o := Cat(Seq(_tag, _index, _byte_offset))
        we_o := false.B
        cyc_o := true.B
    }

    def endBusTransaction(){
        we_o := false.B
        cyc_o := false.B
    }

    when(cyc && stb && state === STATE_IDLE){
        when(!entry_valid){
            // miss
            cur_adr := adr
            cur_dat := dat
            cur_sel := sel
            cur_we := we


            val vacant_way_index = getVacantWay(index)
            when(!isValid(index, vacant_way_index) || !isDirty(index, next_victim)){
                printf("good %d %d %d!\n", index, vacant_way_index, next_victim)
                val n_way_index = Mux(isValid(index, vacant_way_index), next_victim, vacant_way_index)

                // happily, a vacant slot found

                readFromBus(index, tag, 0.U(blockWidth.W))

                state := STATE_FETCH // good, you can directly go fetching the data
                cur_way_index := n_way_index

                if(wayCount != 1){
                    next_victim := Mux(isValid(index, vacant_way_index), next_victim + 1.U, next_victim)
                }

                isValid(index, n_way_index) := true.B
                isDirty(index, n_way_index) := false.B
                getTag(index, n_way_index) := tag

            }.otherwise{
                // writeback necessary
                writeToBus(cur_index, cur_way_index, 0.U(blockWidth - 1, 0))

                state := STATE_WRITE_BACK
                cur_way_index := next_victim

                if(wayCount != 1){
                    next_victim := next_victim + 1.U
                }

                isValid(index, next_victim) := true.B
                isDirty(index, next_victim) := false.B
                getTag(index, next_victim) := tag
            }
        }.elsewhen(we){
            writeEntry(index, way_index, offset, dat, sel)
        }
    }.elsewhen(state === STATE_FETCH){
        val next_counter = Mux(bus_stalled, op_counter, op_counter + 1.U)

        when(!bus_stalled){
            writeEntry(cur_index, cur_way_index, op_counter << 2.U, bus_in_dat, 0xf.U(4.W))
            when(Cat(op_counter, 0.U(2.W)) === Cat(cur_adr(31, 2), 0.U(2.W))(blockWidth, 0)){
                fetch_old := true.B
                fetch_ans := bus_in_dat
            }
        }

        when(next_counter < blockSize.U){
            readFromBus(cur_index, cur_way_index, Cat(next_counter, 0.U(2.W))(blockWidth - 1, 0))
            op_counter := next_counter
        }.otherwise{
            isDirty(cur_index, cur_way_index) := false.B
            op_counter := 0.U
            endBusTransaction()
            state := STATE_IDLE
            fetch_done := true.B
            fetch_old := false.B

            when(cur_we){
                writeEntry(cur_index, cur_way_index, Cat(cur_adr(31, 2), 0.U(2.W))(blockWidth - 1, 0), 
                    cur_dat, cur_sel)
            }
        }
    }.elsewhen(state === STATE_WRITE_BACK){
        val next_counter = Mux(bus_stalled, op_counter, op_counter + 1.U)

        when(next_counter < blockSize.U){
            writeToBus(cur_index, cur_way_index, Cat(next_counter, 0.U(2.W))(blockWidth - 1, 0))
            op_counter := next_counter
        }.otherwise{
            op_counter := 0.U
            readFromBus(cur_index, cur_way_index, 0.U(blockWidth.W))
            state := STATE_FETCH
        }
    }



    val ack = ((state === STATE_FETCH && fetch_done) ||
        (state === STATE_IDLE && entry_valid))


    io.bus.slave.ack_o := ack
    io.bus.slave.err_o := false.B
    io.bus.slave.rty_o := false.B
    io.bus.slave.stall_o := false.B
    io.bus.slave.dat_o := Mux(state === STATE_IDLE, 
        (r_blocks(index * wayCount.U + way_index) >> (offset << 3.U))(31, 0), 
        Mux(fetch_old, fetch_ans, bus_in_dat))
    //TODO: the last two bits in adr should be omitted 

    io.bus.master.cyc_i := cyc_o
    io.bus.master.stb_i := cyc_o
    io.bus.master.dat_i := dat_o
    io.bus.master.we_i := we_o
    io.bus.master.adr_i := adr_o
    io.bus.master.sel_i := 0xf.U
}
