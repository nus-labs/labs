package labs

import chisel3._
import chiffre._
import java.io.File
import perfect.random._
import chisel3.util.random._
import labs.passes._

sealed class PreventerIo(val bitWidth: Int) extends Bundle{
	val in = Input(UInt(bitWidth.W))
	val out = Output(UInt(bitWidth.W))
	val detect = Input(Bool())
}

class Preventer(var bitWidth: Int, var feedback_mechanism: Int) extends Module{
  val io = IO(new PreventerIo(bitWidth))
  val resetB = if(global_edge_reset.posedge_reset == 0) ~reset.toBool else reset.toBool  
  withReset(resetB){ 
  val fault_detected = RegInit(0.U)
  fault_detected := Mux(io.detect, 1.U, fault_detected)
  if(feedback_mechanism == 1) io.out := Mux(fault_detected === 1.U, 0.U, io.in)
  else if(feedback_mechanism == 2){
	val valid_signal = RegInit(1.U(bitWidth.W))
	valid_signal := Mux(valid_signal === 1.U, 0.U, valid_signal)
	val lfsrs = for(i <- 0 to ((bitWidth / 32.0).ceil.toInt) - 1) yield
    {
        val lfsr = FibonacciLFSR(32, Set(31, 6, 5, 1))
        lfsr
    }
	io.out := Mux(fault_detected === 1.U, VecInit(lfsrs).asUInt, io.in)
  }
  else io.out := io.in
  }
}
