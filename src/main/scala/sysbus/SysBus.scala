package systemoncat.sysbus

object SysBus{
    def connect(master : SysBusMaster, slave : SysBusSlave){
        // master.io.dat_i := slave.io.dat_o
        // master.io.tgd_i := slave.io.tgd_o
        // master.io.ack_i := slave.io.ack_o
        // master.io.stall_i := slave.io.stall_o
        // master.io.err_i := slave.io.err_o
        // master.io.rty_i := slave.io.rty_i

        // slave.io.dat_i := master.io.dat_o
        // slave.io.tgd_i := master.io.tgd_o
        // slave.io.adr_i := master.io.adr_o
        // slave.io.cyc_i := master.io.cyc_o
        //TODO
    }
}
