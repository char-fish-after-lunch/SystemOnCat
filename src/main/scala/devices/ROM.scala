package systemoncat.devices

import scala.util.control.Breaks
import scala.collection.mutable.ListBuffer

import chisel3._
import systemoncat.sysbus._
import chisel3.util.{log2Up, Cat}

import java.io.{File, FileInputStream}

object ROM{
    private def loadData(dataFile : String) : List[Short] = {
        val file = new File(dataFile)
        val in = new FileInputStream(file)

        val loop = new Breaks
        val data = new ListBuffer[Short]

        loop.breakable{
            while(true){
                val ibyte = in.read()
                if(ibyte == -1)
                    loop.break
                data += ibyte.toShort
            }
        }

        in.close()

        data.toList
    }
}

class DumbBundle extends Bundle{
    val dumb = Output(Bool()) // to make chisel happy
    // for chisel does not allow empty bundles
}

class ROM(dataFile : String) extends SysBusSlave(new DumbBundle){
    val db = Wire(new DumbBundle)
    io.in <> db

    val data = ROM.loadData(dataFile)
    val len = data.length
    println("Length of ROM firmware: " + len)

    val adr_width = log2Up(len)

    val req = RegInit(Bool(), false.B)
    req := io.out.cyc_i & io.out.stb_i
    val adr_req = RegInit(UInt(32.W), 0.U)
    adr_req := io.out.adr_i
    val sel_req = RegInit(UInt(4.W), 0.U)
    sel_req := io.out.sel_i
    
    io.out.stall_o := false.B
    io.out.ack_o := req
    io.out.err_o := false.B
    io.out.rty_o := false.B

    db.dumb := false.B // to make chisel happy

    val ans = Seq(Wire(UInt(8.W)),
        Wire(UInt(8.W)),
        Wire(UInt(8.W)),
        Wire(UInt(8.W)))
    for(i <- 0 until 4)
        ans(i) := 0.U
    for(i <- 0 until (1 << (adr_width - 2))){
        when(i.U === adr_req(adr_width - 1, 2)){
            // in this word
            for(j <- 0 until 4){
                ans(j) := Mux(sel_req(j), 
                    if(((i << 2) | j) < len) data((i << 2) | j).U(8.W) else 0.U(8.W),
                    0.U(8.W))
            }
        }
    }

    io.out.dat_o := Cat(ans.reverse)
}
