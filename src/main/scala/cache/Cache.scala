package systemoncat.cache

import systemoncat.sysbus.SysBusFilterBundle

import chisel3._
import chisel3.core.Module
import chisel3.util._


class CacheBundle extends Bundle{
    val bus = new SysBusFilterBundle
}

class Cache(blockWidth : Int, wayCount : Int, indexWidth : Int, notToCache: Seq[(BitPat, Bool)]) extends Module{
    private def STATE_IDLE = 0.U
    private def STATE_FETCH = 1.U
    private def STATE_WRITE_BACK = 2.U
    private def STATE_DIRECT_ACCESS = 3.U

    val io = IO(new CacheBundle)
    val indexStart = blockWidth
    val tagStart = blockWidth + indexWidth
    val blockSize = 1 << (blockWidth - 2)

    val r_tags = RegInit(Vec(Seq.fill(wayCount * (1 << indexWidth))(0.U((32 - indexWidth - blockWidth).W))))
    val r_valids = RegInit(Vec(Seq.fill(wayCount * (1 << indexWidth))(false.B)))
    val r_dirtys = RegInit(Vec(Seq.fill(wayCount * (1 << indexWidth))(false.B)))
    val r_blocks = RegInit(Vec(Seq.fill(wayCount * (1 << (indexWidth + blockWidth)))(0.U(8.W))))

    def isValid(_index : UInt, _way_index : UInt) : Bool = r_valids(_index * wayCount.U + _way_index)
    def getTag(_index : UInt, _way_index: UInt) : UInt = r_tags(_index * wayCount.U + _way_index)
    def isDirty(_index : UInt, _way_index : UInt) : Bool = r_dirtys(_index * wayCount.U + _way_index)
    def getByte(_index : UInt, _way_index : UInt, _byte_offset : UInt) : UInt = {
        r_blocks(((_index * wayCount.U + _way_index) << blockWidth.U) | _byte_offset)
    }
    def getWord(_index : UInt, _way_index : UInt, _word_offset : UInt) : UInt = {
        Cat(Seq(getByte(_index, _way_index, (_word_offset << 2.U) | 3.U), 
            getByte(_index, _way_index, (_word_offset << 2.U) | 2.U),
            getByte(_index, _way_index, (_word_offset << 2.U) | 1.U),
            getByte(_index, _way_index, _word_offset << 2.U)))
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
    val offset = (adr(indexStart - 1, 0) & ~3.U(indexStart.W))(indexStart - 1, 0)
    val way_index = getWayIndex(index, tag)
    val entry_valid = isEntryValid(index, tag, way_index)
    val dat = io.bus.slave.dat_i
    val sel = io.bus.slave.sel_i

    val cur_adr = RegInit(UInt(32.W), 0.U)
    val cur_index = cur_adr(tagStart - 1, indexStart)
    val cur_tag = cur_adr(31, tagStart)
    val cur_offset = (cur_adr(indexStart - 1, 0) & ~3.U(indexStart.W))(indexStart - 1, 0)
    val cur_way_index = RegInit(UInt(log2Up(wayCount).W), 0.U)   
    val cur_dat = RegInit(UInt(32.W), 0.U)
    val cur_sel = RegInit(UInt(4.W), 0.U)
    val cur_we = RegInit(Bool(), false.B)


    val state = RegInit(UInt(4.W), STATE_IDLE)

    val fetch_done = Wire(Bool())
    fetch_done := false.B

    val direct_access = Lookup(adr, false.B, notToCache)

    val next_victim = RegInit(UInt(log2Up(wayCount).W), 0.U)

    val adr_o = Wire(UInt(32.W))
    val we_o = Wire(Bool())
    val cyc_o = Wire(Bool())
    val dat_o = Wire(UInt(32.W))
    val sel_o = Wire(UInt(4.W))

    val op_counter = RegInit(UInt((blockWidth).W), 0.U)

    adr_o := 0.U
    cyc_o := false.B
    we_o := false.B
    dat_o := 0.U
    sel_o := 0.U

    val bus_stalled = io.bus.master.stall_o
    val bus_in_dat = io.bus.master.dat_o
    val bus_ack = io.bus.master.ack_o

    printf("current_state = %d\n", state)
    printf("valid = %d, %x, %x, %d, %d, %d, %d, %d, %d, %d\n", entry_valid, tag, getTag(index, way_index),
        isDirty(index, way_index), index, way_index, bus_stalled, blockSize.U, op_counter, bus_in_dat)
    printf("adr = %x\n", adr)

    def writeEntry(_index : UInt, _way_index : UInt, _word_offset : UInt, _dat : UInt, _sel : UInt){
        printf("Write Entry : %d, %d, %d, %d, %d\n", _index, _way_index, _word_offset, _dat, _sel)
        assert(_sel.getWidth == 4)
        assert(_dat.getWidth == 32)
        for(i <- 0 until 4){
            when(_sel(i)){
                getByte(_index, _way_index, (_word_offset << 2.U) | i.U) := _dat(((i + 1) << 3) - 1, i << 3)
            }
        }
        isDirty(_index, _way_index) := true.B
    }

    def writeToBus(_index : UInt, _way_index : UInt, _byte_offset : UInt){
        assert(_byte_offset.getWidth == blockWidth)
        dat_o := getWord(_index, _way_index, _byte_offset >> 2.U)
        adr_o := Cat(Seq(getTag(_index, _way_index), _index, _byte_offset))
        assert(Cat(Seq(getTag(_index, _way_index), _index, _byte_offset)).getWidth == 32)

        we_o := true.B
        cyc_o := true.B
        sel_o := 0xf.U(4.W)
    }

    def readFromBus(_index : UInt, _tag : UInt, _byte_offset : UInt){
        assert(Cat(Seq(_tag, _index, _byte_offset)).getWidth == 32)
        adr_o := Cat(Seq(_tag, _index, _byte_offset))
        we_o := false.B
        cyc_o := true.B
        sel_o := 0xf.U(4.W)
    }

    def endBusTransaction(){
        we_o := false.B
        cyc_o := false.B
    }

    def directlyAccessBus(_adr : UInt, _dat : UInt, _we : Bool, _sel : UInt){
        cyc_o := true.B
        we_o := _we
        sel_o := _sel
        dat_o := _dat
        adr_o := _adr
    }

    when(cyc && stb && state === STATE_IDLE){
        when(!entry_valid){
            // miss
            cur_adr := adr
            cur_dat := dat
            cur_sel := sel
            cur_we := we


            when(direct_access){
                directlyAccessBus(adr, dat, we, sel)
                state := STATE_DIRECT_ACCESS
                op_counter := Mux(bus_stalled, 0.U, 1.U)
            }.otherwise{
                val vacant_way_index = getVacantWay(index)
                when(!isValid(index, vacant_way_index) || !isDirty(index, next_victim)){
                    printf("good %d %d %d %d!\n", index, vacant_way_index, next_victim, isDirty(index, next_victim))
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
                    op_counter := Mux(bus_stalled, 0.U, 1.U)

                }.otherwise{
                    // writeback necessary
                    writeToBus(index, next_victim, 0.U(blockWidth.W))

                    state := STATE_WRITE_BACK
                    cur_way_index := next_victim

                    if(wayCount != 1){
                        next_victim := next_victim + 1.U
                    }
                    op_counter := Mux(bus_stalled, 0.U, 1.U)
                }
            }
        }.elsewhen(we){
            writeEntry(index, way_index, offset >> 2.U, dat, sel)
        }
    }.elsewhen(state === STATE_FETCH){
        val next_counter = Mux(bus_stalled, op_counter, op_counter + 1.U)

        printf("Fetch step %d, %d\n", op_counter, bus_in_dat)
        when(op_counter < blockSize.U){
            when(bus_ack && op_counter > 0.U){
                writeEntry(cur_index, cur_way_index, op_counter - 1.U, bus_in_dat, 0xf.U(4.W))
            }
            readFromBus(cur_index, cur_tag, Cat(op_counter, 0.U(2.W))(blockWidth - 1, 0))
            op_counter := next_counter
        }.otherwise{
            endBusTransaction()
            when(bus_ack){
                writeEntry(cur_index, cur_way_index, op_counter - 1.U, bus_in_dat, 0xf.U(4.W))

                state := STATE_IDLE
                fetch_done := true.B
                isDirty(cur_index, cur_way_index) := false.B
                op_counter := 0.U
                when(cur_we){
                    printf("hoola!\n")
                    writeEntry(cur_index, cur_way_index,
                        cur_offset >> 2.U, cur_dat, cur_sel)
                }
            }
        }
    }.elsewhen(state === STATE_WRITE_BACK){
        val next_counter = Mux(bus_stalled, op_counter, op_counter + 1.U)

        when(op_counter < blockSize.U){
            writeToBus(cur_index, cur_way_index, Cat(op_counter, 0.U(2.W))(blockWidth - 1, 0))
            op_counter := next_counter
        }.otherwise{
            isValid(cur_index, cur_way_index) := true.B
            getTag(cur_index, cur_way_index) := cur_tag
            readFromBus(cur_index, cur_tag, 0.U(blockWidth.W))
            state := STATE_FETCH
            op_counter := Mux(bus_stalled, 0.U, 1.U)
        }
    }.elsewhen(state === STATE_DIRECT_ACCESS){
        val next_counter = Mux(bus_stalled, op_counter, op_counter + 1.U)

        when(op_counter === 0.U){
            directlyAccessBus(cur_adr, cur_dat, cur_we, cur_sel)
            op_counter := next_counter
        }.otherwise{
            endBusTransaction()
            when(bus_ack){
                fetch_done := true.B
                state := STATE_IDLE
                op_counter := 0.U
            }
        }
    }



    val ack = (((state === STATE_FETCH || state === STATE_DIRECT_ACCESS) && fetch_done) ||
        (state === STATE_IDLE && entry_valid))


    io.bus.slave.ack_o := ack
    io.bus.slave.err_o := false.B
    io.bus.slave.rty_o := false.B
    io.bus.slave.stall_o := false.B
    io.bus.slave.dat_o := Mux(state === STATE_IDLE, 
        getWord(index, way_index, offset >> 2.U),
        Mux((cur_offset + 4.U)(blockWidth - 1, 0) === 0.U || state === STATE_DIRECT_ACCESS, 
            bus_in_dat,
            getWord(cur_index, cur_way_index, cur_offset >> 2.U),
            ))
    printf("D GET %d, %d, %d, %d, %d, %d\n", index, way_index, offset >> 2.U, offset, adr(indexStart - 1, 0), adr(indexStart - 1, 0) & ~3.U)
    //TODO: the last two bits in adr should be omitted 

    io.bus.master.cyc_i := cyc_o
    io.bus.master.stb_i := cyc_o
    io.bus.master.dat_i := dat_o
    io.bus.master.we_i := we_o
    io.bus.master.adr_i := adr_o
    io.bus.master.sel_i := sel_o
}
