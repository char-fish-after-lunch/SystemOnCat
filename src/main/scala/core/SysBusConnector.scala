package systemoncat.core

import systemoncat.devices._
import systemoncat.sysbus._

class SysBusRequest extends Bundle {
    val addr = Input(UInt(32.W))
    val data_wr = Input(UInt(32.W))
    val sel = Input(UInt(4.W))
    val wen = Input(Bool())
    val ren = Input(Bool())
}

class SysBusResponse extends Bundle {
    val data_rd = Output(UInt(32.W))
    val locked = Output(Bool())
}

class SysBusBundle extends Bundle {
    val res = new SysBusResponse
    val req = new SysBusRequest
}

class SysBusConnectorIO extends Bundle {
    val imem = new SysBusBundle()
    val dmem = new SysBusBundle()
}

class SysBusConnector() extends Module {
    val io = IO(new SysBusConnectorIO())

    val ram_slave = new RAMSlave()
    val bus_map = Seq(
        BitPat.dontCare(32) -> 0
    )
    val bus_slaves = Seq(
        ram_slave
    )

    val bus = Module(new SysBusTranslator(bus_map, bus_slaves))

    // TODO: imem/dmem arbitor
    // bus.dat_i := 


// class SysBusSlaveBundle extends Bundle{
//     val dat_i = Input(UInt(32.W))
//     val dat_o = Output(UInt(32.W))
//     val ack_o = Output(Bool())
//     val adr_i = Input(UInt(32.W))
//     val cyc_i = Input(Bool())
//     val err_o = Output(Bool())
//     val rty_o = Output(Bool())
//     val sel_i = Input(UInt(4.W))
//     val stb_i = Input(Bool())
//     val we_i = Input(Bool())
// }

}
