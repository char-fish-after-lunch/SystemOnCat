package systemoncat.devices

import scala.util.control.Breaks
import scala.collection.mutable.ListBuffer

import chisel3._
import systemoncat.sysbus._
import chisel3.util.{log2Up, Cat}

import java.io.{File, FileInputStream}

object ROM{
    private def loadData(dataFile : String) : List[Byte] = {
        val file = new File(dataFile)
        val in = new FileInputStream(file)

        val loop = new Breaks
        val data = new ListBuffer[Byte]

        loop.breakable{
            while(true){
                val ibyte = in.read()
                if(ibyte == -1)
                    loop.break
                data += ibyte.toByte
            }
        }

        in.close()

        data.toList
    }
}

class ROM(dataFile : String) extends SysBusSlave(new Bundle(){}){
    val data = ROM.loadData(dataFile)
    val len = data.length
    val adr_width = log2Up(len)

    val req = RegInit(Bool(), false.B)
    req := io.out.cyc_i & io.out.stb_i
    val dat_req = RegInit(UInt(32.W), 0.U)
    dat_req := io.out.dat_i
    val sel_req = RegInit(UInt(4.W), 0.U)
    sel_req := io.out.sel_i
    
    io.out.stall_o := false.B
    io.out.ack_o := req
    io.out.err_o := false.B
    io.out.rty_o := false.B

    val ans = Seq(Wire(UInt(8.W)),
        Wire(UInt(8.W)),
        Wire(UInt(8.W)),
        Wire(UInt(8.W)))

    for(i <- 0 until (1 << adr_width)){
        when((i >> 2).U === dat_req(adr_width - 1, 2)){
            // in this word
            for(j <- 0 until 4){
                ans(j) := Mux(sel_req(j), 
                    if((i | j) < len) data(i | j).U(8.W) else 0.U(8.W),
                    0.U(8.W))
            }
        }
    }

    io.out.dat_o := Cat(ans.reverse)
}
