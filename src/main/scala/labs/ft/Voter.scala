package labs

import chisel3._
import chiffre._
import java.io.File
import labs.passes._

object MUXDriver6 extends App {
    chisel3.Driver.execute(args, () => new Voter(4, 1))
}

class One_Bit_Voter() extends Module{
    val io = IO(new VoterIo(1))
    val x = Wire(Bool())
    val y = Wire(Bool())
    val z = Wire(Bool())
    x := !(io.in1 & io.in2)
    y := !(io.in2 & io.in3)
    z := !(io.in1 & io.in3)
    io.out := !(x & y & z)
}

sealed class VoterIo(val bitWidth: Int) extends Bundle{
	val in1 = Input(UInt(bitWidth.W))
	val in2 = Input(UInt(bitWidth.W))
	val in3 = Input(UInt(bitWidth.W))
	val out = Output(UInt(bitWidth.W))
}

class Voter(bitWidth: Int, feedback: Int) extends Module{
	val io = IO(new VoterIo(bitWidth))
    val resetB = if(global_edge_reset.posedge_reset == 0) ~reset.toBool else reset.toBool
    withReset(resetB){
	val case1 = io.in1 === io.in2
	val case2 = io.in1 === io.in3
	val case3 = io.in2 === io.in3
	val all_not_equal = ~case1 & ~case2 & ~case3
	val vote = Mux(case1, io.in1,
			   Mux(case2, io.in1,
			   Mux(case3, io.in2, io.in1)))
	if(feedback != 0){
		val preventer = Module(new Preventer(bitWidth, feedback))
		preventer.io.detect := all_not_equal
		preventer.io.in := vote
		io.out := preventer.io.out
	}
	else{
		io.out := vote
	}
	}
}
