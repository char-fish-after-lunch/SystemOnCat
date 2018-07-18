package systemoncat.sysbus

import chisel3._


class SysBusFilterBundle extends Bundle{
    val master = new SysBusMasterBundle
    val slave = new SysBusSlaveBundle
}
