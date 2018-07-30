package systemoncat.sysbus

import chisel3._


class SysBusFilterBundle extends Bundle{
    val master = Flipped(new SysBusSlaveBundle)
    val slave = new SysBusSlaveBundle
}
