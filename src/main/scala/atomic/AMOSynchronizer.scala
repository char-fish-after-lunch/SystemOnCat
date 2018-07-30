package systemoncat.atomic

import chisel3._
import chisel3.util._
import systemoncat.core._

class AMOSynchronizerCoreIO extends Bundle {
    val req = Input(Bool())
    val locked = Input(Bool())
    val pending = Output(Bool())
}

class AMOSynchronizerIO extends Bundle {
    val core0 = new AMOSynchronizerCoreIO
    val core1 = new AMOSynchronizerCoreIO
}

class AMOSynchronizer extends Module {
    val io = IO(new AMOSynchronizerIO)
    val sIDLE :: sCORE0 :: sCORE1 :: Nil = Enum(3)

    val state = RegInit(sIDLE)
    io.core0.pending := state === sCORE1
    io.core1.pending := state === sCORE0

    switch (state) {
        is(sIDLE) {
            when (io.core0.req) {
                state := sCORE0 // core 0 is prior to core 1.
                // atomic operations might be rather rare in real world, 
                // so it's ok with a little inequality
            }
            .elsewhen (io.core1.req) {
                state := sCORE1
            }
        }
        is(sCORE0) {
            when (!io.core0.locked) {
                when (io.core1.locked || io.core1.req) { 
                    // core 0 operation end & core 1 request start immediately
                    state := sCORE1
                }
                .elsewhen (io.core0.req) {
                    // another request comes
                    state := sCORE0
                }
                .otherwise {
                    state := sIDLE
                }
            }
        }
        is(sCORE1) {
            when (!io.core1.locked) {
                when (io.core0.locked || io.core0.req) { 
                    // core 1 operation end & core 0 request start immediately
                    state := sCORE0
                }
                .elsewhen (io.core1.req) {
                    // another request comes
                    state := sCORE1
                }
                .otherwise {
                    state := sIDLE
                }
            }
        }
    }
}