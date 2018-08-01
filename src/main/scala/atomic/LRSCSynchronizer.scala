package systemoncat.atomic

import chisel3._
import chisel3.util._
import systemoncat.core._


object AtomicConsts {
    def LoadReservationCycles = 126.U(7.W) 
    // The static code for the LR/SC sequence plus the code to retry the sequence in case
    // of failure must comprise at most 16 integer instructions placed sequentially in memory. -- riscv-spec-v2.2, p41
    // However, TLB refill & cache refill costs taken into consideration, a much larger limit is needed.

    def NCPUs = 2
    // However, this module doesn't, and won't apply to more than 2 CPUs.
}

import AtomicConsts._

class LRSCSynchronizerCoreIO extends Bundle {
    val sc_en = Input(Bool())
    val lr_en = Input(Bool())
    val modify_en = Input(Bool())
    val in_amo = Input(Bool())
    val addr = Input(UInt(32.W))
    val sc_valid = Output(Bool()) // if this Store Conditional is allowed
}

class LRSCSynchronizerIO extends Bundle {
    val core0 = new LRSCSynchronizerCoreIO
    val core1 = new LRSCSynchronizerCoreIO
}

class LRSCSynchronizer extends Module {
    val io = IO(new LRSCSynchronizerIO)

    val lr_valid_counter = IndexedSeq(
        RegInit(UInt(), 0.U(LoadReservationCycles.getWidth.W)), 
        RegInit(UInt(), 0.U(LoadReservationCycles.getWidth.W))
    )
    // lr_valid_counter == 0: no addr is reserved
    // else: a LR has been waiting for x cycles
    // when reserved addr is writen, this is reset to 0.

    val lr_reserved_addr = IndexedSeq(RegInit(UInt(), 0.U(32.W)), RegInit(UInt(), 0.U(32.W)))

    val lr_valid = Wire(Vec(NCPUs, Bool()))
    val reservation_broke = Wire(Vec(NCPUs, Bool()))
    val core = IndexedSeq(io.core0, io.core1)

    val sc_conflict = core(0).sc_en && core(1).sc_en && 
        lr_reserved_addr(0) === lr_reserved_addr(1) && lr_valid(0) && lr_valid(1)
    // this is why this module even exists. when 2 cores try Store Conditional on the same address, 
    // only one of them is allowed to continue.

    for (i <- 0 until NCPUs) {
        lr_valid(i) := (lr_valid_counter(i) =/= 0.U)

        when (core(i).lr_en) {
            lr_reserved_addr(i) := core(i).addr
        }

        reservation_broke(i) := core(i).modify_en && core(i).addr === lr_reserved_addr(i)
        lr_valid_counter(i) := Mux(reservation_broke(i), 0.U,
            Mux(core(i).lr_en, Mux(((core(i).addr === core(NCPUs-1-i).addr) && core(NCPUs-1-i).modify_en) || core(NCPUs-1-i).in_amo, 0.U, 1.U), 
                // if core 0 LR & core 1 STORE happens at the same time, core 0 LR is invalid
            Mux(core(NCPUs-1-i).modify_en && core(NCPUs-1-i).addr === lr_reserved_addr(i), 0.U,
            Mux(lr_valid_counter(i) === LoadReservationCycles, 0.U,
            Mux(lr_valid_counter(i) === 0.U, 0.U, 
            Mux(core(NCPUs-1-i).in_amo, 0.U, lr_valid_counter(i) + 1.U))))))

        core(i).sc_valid := Mux(sc_conflict, i.U === 0.U,
            core(i).sc_en && lr_valid(i) && lr_reserved_addr(i) === core(i).addr && (!core(NCPUs-1-i).in_amo)
            !(core(NCPUs-1-i).modify_en && core(NCPUs-1-i).addr === lr_reserved_addr(i)))
            // When a store and a SC happens at the same time, as we don't know which one will execute first, 
            // we assume that SC fails.
    }
}
