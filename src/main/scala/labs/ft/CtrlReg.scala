package labs

import chisel3._
import chiffre._
import chisel3.dontTouch
import java.io.File
import chisel3.experimental.chiselName
import labs.passes._

sealed class CtrlRegIo extends Bundle{
	val ready = Input(Bool())
	val start = Input(Bool())
	val finished = Output(Bool())
	val ctrl = Output(UInt(2.W))
}

@chiselName
class CtrlReg extends Module{
	val io = IO(new CtrlRegIo)
    val resetB = if(global_edge_reset.posedge_reset == 0) ~reset.toBool else reset.toBool	
	withReset(resetB){

	val counter = RegInit(0.U(2.W))
	when ((counter === 0.U) & io.start){
		counter := 1.U
	}
	.elsewhen ((counter === 1.U) & io.ready){
		counter := 2.U
	}
	.elsewhen ((counter === 2.U) & io.ready & !io.start){
		counter := 0.U
	}
	.elsewhen ((counter === 2.U) & io.ready & io.start){
		counter := 1.U
	}
	
	io.ctrl := counter
	io.finished := io.ready & (counter === 2.U || counter === 0.U)
	}
}
