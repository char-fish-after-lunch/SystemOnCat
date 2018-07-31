package systemoncat.cache

import systemoncat.sysbus.SysBusFilterBundle

import chisel3._
import chisel3.core.Module
import chisel3.util._


class CacheBundle(blockWidth : Int) extends Bundle{
    val bus = new SysBusFilterBundle
    val snooper = new CacheSnooperBundle(blockWidth)
    val broadcaster = Flipped(new CacheSnooperBundle(blockWidth))
    val my_turn = Input(Bool())
}

class Cache(blockWidth : Int, wayCount : Int, indexWidth : Int, notToCache: Seq[(BitPat, Bool)]) extends Module{
    private def STATE_IDLE = 0.U
    private def STATE_FETCH = 1.U
    private def STATE_WRITE_BACK = 2.U
    private def STATE_DIRECT_ACCESS = 3.U
    private def STATE_MODIFY = 4.U

    val io = IO(new CacheBundle(blockWidth))
    val indexStart = blockWidth
    val tagStart = blockWidth + indexWidth
    val blockSize = 1 << (blockWidth - 2)

    val r_tags = RegInit(Vec(Seq.fill(wayCount * (1 << indexWidth))(0.U((32 - indexWidth - blockWidth).W))))
    val r_valids = RegInit(Vec(Seq.fill(wayCount * (1 << indexWidth))(false.B)))
    val r_dirtys = RegInit(Vec(Seq.fill(wayCount * (1 << indexWidth))(false.B)))
    val r_blocks = RegInit(Vec(Seq.fill(wayCount * (1 << (indexWidth + blockWidth)))(0.U(8.W))))

    val c_blocks = Wire(Vec(wayCount * (1 << (indexWidth + blockWidth)), UInt(8.W)))
    for(i <- 0 until (wayCount * (1 << (indexWidth + blockWidth)))){
        c_blocks(i) := r_blocks(i)
    }

    def computeTag(_adr : UInt) : UInt = _adr(31, tagStart)
    def computeIndex(_adr : UInt) : UInt = _adr(tagStart - 1, indexStart)
    def computeOffset(_adr : UInt) : UInt = (_adr(indexStart - 1, 0) & ~3.U(indexStart.W))(indexStart - 1, 0)

    def isValid(_index : UInt, _way_index : UInt) : Bool = r_valids(_index * wayCount.U + _way_index)
    def getTag(_index : UInt, _way_index: UInt) : UInt = r_tags(_index * wayCount.U + _way_index)
    def isDirty(_index : UInt, _way_index : UInt) : Bool = r_dirtys(_index * wayCount.U + _way_index)
    def getByte(_index : UInt, _way_index : UInt, _byte_offset : UInt) : UInt = {
        c_blocks(((_index * wayCount.U + _way_index) << blockWidth.U) | _byte_offset)
    }
    def getWord(_index : UInt, _way_index : UInt, _word_offset : UInt) : UInt = {
        Cat(Seq(getByte(_index, _way_index, (_word_offset << 2.U) | 3.U), 
            getByte(_index, _way_index, (_word_offset << 2.U) | 2.U),
            getByte(_index, _way_index, (_word_offset << 2.U) | 1.U),
            getByte(_index, _way_index, _word_offset << 2.U)))
    }
    def getBlock(_index : UInt, _way_index : UInt) : UInt = {
        Cat((for(i <- 0 until blockSize) yield getWord(_index, _way_index, i.U)).reverse)
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


    // -----------------------------

    val cache_stalled = io.broadcaster.response_type === CacheCoherence.RE_STALL
    val cache_prev_stalled = RegInit(Bool(), false.B)
    cache_prev_stalled := cache_stalled

    // Slave Input

    val prev_stb = RegInit(Bool(), false.B)
    val prev_cyc = RegInit(Bool(), false.B)
    val prev_we = RegInit(Bool(), false.B)
    val prev_adr = RegInit(UInt(32.W), 0.U)
    val prev_dat = RegInit(UInt(32.W), 0.U)
    val prev_sel = RegInit(UInt(4.W), 0.U)

    val stb = Mux(cache_prev_stalled, prev_stb, io.bus.slave.stb_i)
    val cyc = Mux(cache_prev_stalled, prev_cyc, io.bus.slave.cyc_i)
    val we = Mux(cache_prev_stalled, prev_we, io.bus.slave.we_i)
    val adr = Mux(cache_prev_stalled, prev_adr, io.bus.slave.adr_i)
    val index = computeIndex(adr)
    val tag = computeTag(adr)
    val offset = computeOffset(adr)
    val way_index = getWayIndex(index, tag)
    val entry_valid = isEntryValid(index, tag, way_index)
    val dat = Mux(cache_prev_stalled, prev_dat, io.bus.slave.dat_i)
    val sel = Mux(cache_prev_stalled, prev_sel, io.bus.slave.sel_i)

    prev_stb := stb
    prev_cyc := cyc
    prev_we := we
    prev_adr := adr
    prev_dat := dat
    prev_sel := sel

    // ----------------------------

    val cur_adr = RegInit(UInt(32.W), 0.U)
    val cur_index = computeIndex(cur_adr)
    val cur_tag = computeTag(cur_adr)
    val cur_offset = computeOffset(cur_adr)
    val cur_way_index = RegInit(UInt(log2Up(wayCount).W), 0.U)
    val cur_dat = RegInit(UInt(32.W), 0.U)
    val cur_sel = RegInit(UInt(4.W), 0.U)
    val cur_we = RegInit(Bool(), false.B)

    // ------------------------------
    
    val partner_adr = io.snooper.broadcast_adr
    val partner_index = computeIndex(partner_adr)
    val partner_tag = computeTag(partner_adr)
    val partner_offset = computeTag(partner_adr)
    val partner_dat = io.snooper.broadcast_dat
    val partner_sel = io.snooper.broadcast_sel
    val partner_we = io.snooper.broadcast_type === CacheCoherence.BR_MODIFY
    val partner_way_index = getWayIndex(partner_index, partner_tag)
    val partner_entry_valid = isEntryValid(partner_index, partner_tag, partner_way_index)



    val state = RegInit(UInt(4.W), STATE_IDLE)

    val job_done = Wire(Bool())
    job_done := false.B

    val direct_access = Lookup(adr, false.B, notToCache)

    val next_victim = RegInit(UInt(log2Up(wayCount).W), 0.U)


    // Master Output
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

    // from bus
    val bus_stalled = io.bus.master.stall_o
    val bus_in_dat = io.bus.master.dat_o
    val bus_ack = io.bus.master.ack_o

    // Snooper
    io.snooper.response_type := CacheCoherence.RE_NO_MSG
    io.snooper.response_dat := 0.U

    def sendResponse(_type : UInt, _dat : UInt){
        io.snooper.response_type := _type
        io.snooper.response_dat := _dat
    }

    def sendResponse(_type : UInt){
        io.snooper.response_type := _type
    }

    // Broadcaster
    io.broadcaster.broadcast_type := CacheCoherence.BR_NO_MSG
    io.broadcaster.broadcast_adr := 0.U
    io.broadcaster.broadcast_dat := 0.U

    def sendBroadcast(_type : UInt, _adr : UInt, _dat : UInt, _sel : UInt){
        io.broadcaster.broadcast_type := _type
        io.broadcaster.broadcast_adr := _adr
        io.broadcaster.broadcast_dat := _dat
        io.broadcaster.broadcast_sel := _sel
    }

    def sendBroadcast(_type : UInt){
        io.broadcaster.broadcast_type := _type
    }

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
    }

    def fillEntry(_index : UInt, _way_index : UInt, _dat : UInt){
        for(i <- 0 until blockSize){
            writeEntry(_index, _way_index, i.U, _dat(((i + 1) << 5) - 1, i << 5), 0xf.U(4.W))
        }
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


    def finishFetch(_index : UInt, _way_index : UInt, _current : Boolean){
        val _we = (if(_current) cur_we else we)
        fetched_index := _index
        fetched_way_index := _way_index
        when(_we){
            modifyEntry(_index, _way_index, _current)
            //TODO: state not hold
        }.otherwise{
            state := STATE_IDLE
            op_counter := 0.U
            job_done := true.B
        }
    }

    val fetched_index = Wire(UInt(indexWidth.W))
    val fetched_way_index = Wire(UInt(log2Up(wayCount).W))

    fetched_index := 0.U
    fetched_way_index := 0.U

    def commenceFetch(_index : UInt, _way_index : UInt, _current : Boolean) : Bool = {
        // broadcast the message that we would fetch the data
                    
        sendBroadcast(CacheCoherence.BR_FETCH, adr, 0.U, 0.U)
        switch(io.broadcaster.response_type){
            is(CacheCoherence.RE_CLEAN_FOUND){
                isDirty(_index, _way_index) := false.B
            }
            is(CacheCoherence.RE_DIRTY_FOUND){
                isDirty(_index, _way_index) := true.B
            }
            is(CacheCoherence.RE_NO_MSG){
                readFromBus(_index, (if(_current) cur_tag else tag), 0.U(blockWidth.W))

                state := STATE_FETCH // good, you can directly go fetching the data
                cur_way_index := _way_index

                op_counter := Mux(bus_stalled, 0.U, 1.U)
                isDirty(_index, _way_index) := false.B
            }
        }

        when(io.broadcaster.response_type === CacheCoherence.RE_CLEAN_FOUND ||
            io.broadcaster.response_type === CacheCoherence.RE_DIRTY_FOUND){
            fillEntry(_index, _way_index, io.broadcaster.response_dat)
            finishFetch(_index, _way_index, _current)
        }

        when(io.broadcaster.response_type =/= CacheCoherence.RE_STALL){
            getTag(_index, _way_index) := (if(_current) cur_tag else tag)
            isValid(_index, _way_index) := true.B
        }

        io.broadcaster.response_type =/= CacheCoherence.RE_STALL
    }


    def commenceWriteBack(_index : UInt, _way_index : UInt) : Bool = {
        sendBroadcast(CacheCoherence.BR_WRITE_BACK, adr, 0.U, 0.U)
        switch(io.broadcaster.response_type){
            is(CacheCoherence.RE_NO_MSG){
                writeToBus(_index, _way_index, 0.U(blockWidth.W))

                state := STATE_WRITE_BACK
                cur_way_index := _way_index
                op_counter := Mux(bus_stalled, 0.U, 1.U)
            }
            is(CacheCoherence.RE_NO_WRITE_BACK){
                // finish write back
                // TODO: the state might not hold if this is stalled
                state := STATE_WRITE_BACK
                op_counter := blockSize.U
            }
        }
        assert(io.broadcaster.response_type =/= CacheCoherence.RE_CLEAN_FOUND &&
            io.broadcaster.response_type =/= CacheCoherence.RE_DIRTY_FOUND)

        io.broadcaster.response_type =/= CacheCoherence.RE_STALL
    }

    def modifyEntry(_index : UInt, _way_index : UInt, _current : Boolean) : Bool = {
        if(_current){
            //TODO: the state might not hold
            sendBroadcast(CacheCoherence.BR_MODIFY, adr, cur_dat, cur_sel)
            when(io.broadcaster.response_type =/= CacheCoherence.RE_STALL){
                writeEntry(_index, _way_index, cur_offset, cur_dat, cur_sel)
                isDirty(_index, _way_index) := true.B
                state := STATE_IDLE
                op_counter := 0.U
                job_done := true.B
            }.otherwise{
                state := STATE_MODIFY
                cur_adr := adr
                cur_dat := dat
                cur_sel := sel
                cur_we := we

                cur_way_index := _way_index
            }
        } else{
            sendBroadcast(CacheCoherence.BR_MODIFY, adr, dat, sel)
            when(io.broadcaster.response_type =/= CacheCoherence.RE_STALL){
                writeEntry(_index, _way_index, offset, dat, sel)
                isDirty(_index, _way_index) := true.B
                state := STATE_IDLE
                op_counter := 0.U
                job_done := true.B
            }.otherwise{
                state := STATE_MODIFY
                cur_way_index := _way_index
            }
        }

        assert(io.broadcaster.response_type === CacheCoherence.RE_NO_MSG)
        io.broadcaster.response_type =/= CacheCoherence.RE_STALL
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

                    when(commenceFetch(index, n_way_index, false)){
                        if(wayCount != 1){
                            next_victim := Mux(isValid(index, vacant_way_index), next_victim + 1.U, next_victim)
                        }
                    }

                }.otherwise{
                    // writeback necessary
                    when(commenceWriteBack(index, next_victim)){
                        if(wayCount != 1){
                            next_victim := next_victim + 1.U
                        }
                    }
                }
            }
        }.elsewhen(we){
            modifyEntry(index, way_index, false)
        }
    }.elsewhen(state === STATE_FETCH){
        val next_counter = Mux(bus_stalled, op_counter, op_counter + 1.U)

        printf("Fetch step %d, %d\n", op_counter, bus_in_dat)

        when(bus_ack && op_counter > 0.U){
            writeEntry(cur_index, cur_way_index, op_counter - 1.U, bus_in_dat, 0xf.U(4.W))
        }
        when(op_counter < blockSize.U){
            readFromBus(cur_index, cur_tag, Cat(op_counter, 0.U(2.W))(blockWidth - 1, 0))
            op_counter := next_counter
        }.otherwise{
            endBusTransaction()
            when(bus_ack){
                finishFetch(cur_index, cur_way_index, true)
            }
        }
    }.elsewhen(state === STATE_WRITE_BACK){
        val next_counter = Mux(bus_stalled, op_counter, op_counter + 1.U)

        when(op_counter < blockSize.U){
            writeToBus(cur_index, cur_way_index, Cat(op_counter, 0.U(2.W))(blockWidth - 1, 0))
            op_counter := next_counter
        }.otherwise{
            commenceFetch(cur_index, cur_way_index, true)
        }
    }.elsewhen(state === STATE_DIRECT_ACCESS){
        val next_counter = Mux(bus_stalled, op_counter, op_counter + 1.U)

        when(op_counter === 0.U){
            directlyAccessBus(cur_adr, cur_dat, cur_we, cur_sel)
            op_counter := next_counter
        }.otherwise{
            endBusTransaction()
            when(bus_ack){
                job_done := true.B
                state := STATE_IDLE
                op_counter := 0.U
            }
        }
    }.elsewhen(state === STATE_MODIFY){
        modifyEntry(cur_index, cur_way_index, true)
    }

    val i_have_something_to_do = io.broadcaster.broadcast_type =/= CacheCoherence.BR_NO_MSG
    val partner_has_something_to_do = io.snooper.broadcast_type =/= CacheCoherence.BR_NO_MSG

    def inSameBlock(_adr1 : UInt, _adr2 : UInt) : Bool = {
        computeIndex(_adr1) === computeIndex(_adr2) && computeTag(_adr1) === computeTag(_adr2)
    }

    def tasksCollide : Bool = {
        i_have_something_to_do && partner_has_something_to_do &&
            inSameBlock(io.broadcaster.broadcast_adr, io.snooper.broadcast_adr)
    }

    def tasksTransitionCollide : Bool = {
        (state =/= STATE_IDLE && state =/= STATE_DIRECT_ACCESS) && partner_has_something_to_do && 
            inSameBlock(io.broadcaster.broadcast_adr, io.snooper.broadcast_adr)
    }

    val partner_stalled = Wire(Bool())
    partner_stalled := false.B

    when((tasksCollide && io.my_turn) || 
        (!i_have_something_to_do && tasksTransitionCollide)){
        // in collision, if it is my turn, tell partner to wait
        sendResponse(CacheCoherence.RE_STALL)
        partner_stalled := true.B
    }

    when(!partner_stalled && partner_has_something_to_do && partner_entry_valid){
        // partner will do its job
        // so i have to send a serious response
        switch(io.snooper.broadcast_type){
            is(CacheCoherence.BR_FETCH){
                sendResponse(Mux(isDirty(partner_index, partner_way_index), 
                    CacheCoherence.RE_DIRTY_FOUND, CacheCoherence.RE_CLEAN_FOUND),
                    getBlock(partner_index, partner_way_index))
            }
            is(CacheCoherence.BR_MODIFY){
                writeEntry(partner_index, partner_way_index,
                    partner_offset >> 2.U, partner_dat, partner_sel)
                isDirty(partner_index, partner_way_index) := true.B
                // no need to send any response in this case
            }
            is(CacheCoherence.BR_WRITE_BACK){
                sendResponse(CacheCoherence.RE_NO_WRITE_BACK)
                assert(isDirty(partner_index, partner_way_index))
            }
        }
    }


    io.bus.slave.ack_o := job_done || (state === STATE_IDLE && we && entry_valid)
    io.bus.slave.err_o := false.B
    io.bus.slave.rty_o := false.B
    io.bus.slave.stall_o := false.B
    io.bus.slave.dat_o := Mux(state === STATE_IDLE,
        Mux(entry_valid, getWord(index, way_index, offset >> 2.U), 
            getWord(fetched_index, fetched_way_index, offset >> 2.U)),
        getWord(fetched_index, fetched_way_index, cur_offset >> 2.U))
    printf("D GET %d, %d, %d, %d, %d, %d\n", index, way_index, offset >> 2.U, offset, adr(indexStart - 1, 0), adr(indexStart - 1, 0) & ~3.U)

    for(i <- 0 until (wayCount * (1 << (indexWidth + blockWidth)))){
        r_blocks(i) := c_blocks(i)
    }

    io.bus.master.cyc_i := cyc_o
    io.bus.master.stb_i := cyc_o
    io.bus.master.dat_i := dat_o
    io.bus.master.we_i := we_o
    io.bus.master.adr_i := adr_o
    io.bus.master.sel_i := sel_o
}
