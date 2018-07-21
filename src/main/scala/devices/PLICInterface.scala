package systemoncat.devices

import chisel3._

class PLICInterface extends Bundle{
    val serial_irq_r = Output(Bool())
    val serial_permission = Input(Bool())

    val keyboard_irq_r = Output(Bool())
    val keyboard_permission = Input(Bool())

    val net_irq_r = Output(Bool())
    val net_permission = Input(Bool())

    val reserved_irq_r = Output(Bool())
    val reserved_permission = Input(Bool())
}
