package systemoncat.devices

import chisel3._
import chisel3.util.HasBlackBoxResource
import systemoncat.sysbus.SysBusSlave

// class RAMBundle extends Bundle{
//     val addr = Output(UInt(20.W))
    
// }

// class RAM extends BlackBox {
//     val io = IO(new RAMBundle)
// }

class RAMSlave extends BlackBox with SysBusSlave with HasBlackBoxResource {
    setResource("/vsrc/RAMSlave.v")
}
