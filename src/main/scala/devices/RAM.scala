package systemoncat.devices

import chisel3._
import chisel3.util.HasBlackBoxResource
import systemoncat.sysbus._

// class RAMBundle extends Bundle{
//     val addr = Output(UInt(20.W))
    
// }

// class RAM extends BlackBox {
//     val io = IO(new RAMBundle)
// }

class RAMSlaveReflector extends Module with SysBusSlave {
    io.in <> io.out
}
