package labs

import chisel3._
import chiffre._
import java.io.File

sealed class OutputRegIo(bitwidth: Int) extends Bundle{
	val in = Input(UInt(bitwidth.W))
	val ready = Input(Bool())
	val out = Output(UInt(bitwidth.W))
	val ctrl = Input(UInt(2.W))
	val detect = Output(Bool())
}

class OutputReg(bitwidth: Int) extends Module{
	val io = IO(new OutputRegIo(bitwidth))
	val resetB = ~reset.toBool
	val fault_detected = RegInit(0.U)
	val stored_output = RegInit(VecInit(Seq.fill(2)(0.U(bitwidth.W))))
	for (i <- 0 to 1){
		stored_output(i) := Mux(io.ctrl === (i + 1).U && io.ready, io.in, stored_output(i))
	}	
	val detector = Module(new Detector(bitwidth))
	detector.io.in1 := stored_output(0)
	detector.io.in2 := stored_output(1)
	io.detect := (detector.io.detect) & (io.ctrl =/= 2.U) & io.ready
	fault_detected := Mux(io.detect, 1.U, fault_detected)
	io.out := Mux(fault_detected === 1.U, 0.U, io.in)
}
