package systemoncat.devices

import chisel3._
import systemoncat.sysbus._

class SerialPortSlaveReflector extends Module with SysBusSlave {
    io.in <> io.out
}
