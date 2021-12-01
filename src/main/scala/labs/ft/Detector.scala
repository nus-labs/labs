package labs

import chisel3._
import chiffre._
import java.io.File
import labs.passes._

sealed class DetectorIo(val bitWidth: Int) extends Bundle{
	val in1 = Input(UInt(bitWidth.W))
	val in2 = Input(UInt(bitWidth.W))
	val detect = Output(UInt(1.W))
}

class Detector(bitWidth: Int) extends Module{
	val io = IO(new DetectorIo(bitWidth))
    val resetB = if(global_edge_reset.posedge_reset == 0) ~reset.toBool else reset.toBool
    withReset(resetB){	
	val xored = Wire(UInt())
	xored := io.in1 ^ io.in2
	io.detect := xored.orR
	}
}
