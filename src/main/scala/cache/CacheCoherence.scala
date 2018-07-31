package systemoncat.cache

import chisel3._
import chisel3.core.Module
import chisel3.util._

/***

WRITE UPDATE

Entry states:
	dirty
	clean
	invalid

Broadcasted Message fields:
	type (modify, write-back, fetch, no-message)
	adr
	dat
	
Response Message fields:
	type (no-message, clean-found, dirty-found)
	dat

---------------- State Transfer on Broadcasting and Receiving Response-----------

Fetch: invalid -> clean/dirty
    Dirty-found: no need to fetch data from bus -> dirty
    Clean-found: no need to fetch data from bus -> clean
    No-message: from bus -> clean

Modify: clean/dirty -> dirty
    No-message is the only possible response

Write-back: dirty -> invalid
    Clean-found (actually impossible)/Dirty-found: no need to write back to bus
    No-message: write back necessary

---------------- State Transfer on Detecting Broadcast Messages -----------

Fetch:
    Invalid: no-message
    Dirty: dirty-found, with data
    Clean: clean-found, with data

Modify:
    Invalid: no-message
    Dirty: no-message, update data
    Clean: no-message, update data -> dirty

Write-back:
    Invalid: no-message
    Dirty: dirty-found
    Clean: (fault, impossible)


---------------- Responding to Broadcast Messages ------
A possible compromise: respond with STALL whenever in transitional states, including FETCH, MODIFY and WRITE_BACK


IDLE:
    Fetch:
        Hit: return entry -> dirty-found/clean-found
        Miss: do nothing -> no-message
    Modify:
        Hit: modify the entry 
            (note that it could coincides with the request) -> no-message
        Miss: do nothing -> no-message
    Write-back
        Hit: return no-write-back
        Miss: do nothing

FETCH:
    Fetch:
        Hit:
            Colliding: -> STALL
            Separate: -> return entry -> dirty-found/clean-found
        Miss: do nothing
    Modify:
        Hit:
            Colliding: -> STALL
            Separate: modify the entry
        Miss: do nothing
    Write-back:
        Hit:
            Colliding: (this seems impossible)
            Separate: no-message
        Miss: do nothing

WRITE_BACK:
    Fetch:
        Hit:
            Colliding: -> STALL
            Separate: return entry -> clean-found/dirty-found
        Miss: do nothing
    Modify:
        Hit, Separate: update
    Write-back:
        Hit, Separate: no-message


--------------------- Broadcast Collisions ----------------
If messages of the same type and targeting the same block entry are
issued within the same period, break the tie by inspecting the signal
sent from CacheArbiter.
*/

object CacheCoherence{
    def RE_NO_MSG = 0.U(3.W)
    def RE_CLEAN_FOUND = 1.U(3.W)
    def RE_DIRTY_FOUND = 2.U(3.W)
    def RE_NO_WRITE_BACK = 3.U(3.W)
    def RE_STALL = 4.U(3.W)
    
    def BR_NO_MSG = 0.U(2.W)
    def BR_FETCH = 1.U(2.W)
    def BR_MODIFY = 2.U(2.W)
    def BR_WRITE_BACK = 3.U(2.W)
}

class CacheSnooperBundle(blockWidth : Int) extends Bundle {
    val broadcast_type = Input(UInt(2.W))
    val broadcast_adr = Input(UInt(32.W))
    val broadcast_dat = Input(UInt(32.W))
    val broadcast_sel = Input(UInt(4.W))

    val response_type = Output(UInt(3.W))
    val response_dat = Output(UInt((8 << blockWidth).W))
}
