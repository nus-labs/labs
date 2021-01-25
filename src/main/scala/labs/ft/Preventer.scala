package labs

import chisel3._
import chiffre._
import java.io.File

sealed class PreventerIo(val bitWidth: Int) extends Bundle{
	val in = Input(UInt(bitWidth.W))
	val out = Output(UInt(bitWidth.W))
	val detect = Input(Bool())
}

class Preventer(var bitWidth: Int, var enabled: Boolean) extends Module{
  val io = IO(new PreventerIo(bitWidth))
  val resetB = ~reset.asBool
  val fault_detected = RegInit(0.U)
  fault_detected := Mux(io.detect, 1.U, fault_detected)
  if(enabled) io.out := Mux(fault_detected === 1.U, 0.U, io.in)
  else io.out := io.in
  
}
