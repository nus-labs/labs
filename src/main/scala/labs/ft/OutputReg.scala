package labs

import chisel3._
import chiffre._
import java.io.File
import chisel3.experimental.chiselName
import labs.passes._

sealed class OutputRegIo(bitwidth: Int) extends Bundle{
	val in = Input(UInt(bitwidth.W))
	val ready = Input(Bool())
	val out = Output(UInt(bitwidth.W))
	val ctrl = Input(UInt(2.W))
	val detect = Output(Bool())
}

@chiselName
class OutputReg(bitwidth: Int, feedback: Int) extends Module{
	val io = IO(new OutputRegIo(bitwidth))
    val resetB = if(global_edge_reset.posedge_reset == 0) ~reset.toBool else reset.toBool
	withReset(resetB){
	//val fault_detected = RegInit(0.U)
	val stored_output = RegInit(VecInit(Seq.fill(2)(0.U(bitwidth.W))))
	for (i <- 0 to 1){
		stored_output(i) := Mux(io.ctrl === (i + 1).U && io.ready, io.in, stored_output(i))
	}	
	val detector = Module(new Detector(bitwidth))
	detector.io.in1 := stored_output(0)
	detector.io.in2 := stored_output(1)
	val detect = (detector.io.detect) & (io.ctrl =/= 2.U) & io.ready
	io.detect := detect
	if(feedback != 0){
		val preventer = Module(new Preventer(bitwidth, feedback))
		preventer.io.in := io.in
		preventer.io.detect := detect
		io.out := preventer.io.out
	}
	else{
		io.out := io.in
	}
	}
}
