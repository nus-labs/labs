package labs

import chisel3._
import chiffre._
import labs.passes._
import java.io.File

sealed class Detector_with_enable_Io(val bitWidth: Int) extends Bundle{
    val in1 = Input(UInt(bitWidth.W))
    val in2 = Input(UInt(bitWidth.W))
	val enable = Input(UInt(1.W))
    val detect = Output(UInt(1.W))
}

class Detector_with_enable(bitWidth: Int) extends Module{
	val io = IO(new Detector_with_enable_Io(bitWidth))
    val resetB = if(global_edge_reset.posedge_reset == 0) ~reset.toBool else reset.toBool
    withReset(resetB){
	val xored = Wire(UInt())
	xored := io.in1 ^ io.in2
	io.detect := Mux(io.enable === 1.U, xored.orR, 0.U)
	}
}
