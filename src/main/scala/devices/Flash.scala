package systemoncat.devices

import chisel3._
import systemoncat.sysbus._

class FlashSlaveReflector extends SysBusSlave(Flipped(new SysBusSlaveBundle)) {
    io.in <> io.out
}
