// See LICENSE.txt for license details.
package labs

import chisel3._
import chiffre._
import java.io.File

sealed class VoterIo(val bitWidth: Int) extends Bundle{
	val in1 = Input(UInt(bitWidth.W))
	val in2 = Input(UInt(bitWidth.W))
	val in3 = Input(UInt(bitWidth.W))
	val out = Output(UInt(bitWidth.W))
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

class Voter(bitWidth: Int) extends Module{
	val io = IO(new VoterIo(bitWidth))
	val voter = Vec(bitWidth, Module(new One_Bit_Voter()).io)
	for(i <- 0 to bitWidth - 1){
		voter(i).in1 := io.in1(i)
		voter(i).in2 := io.in2(i)
		voter(i).in3 := io.in3(i)
	}
	for(i <- 0 to bitWidth - 1){
		io.out(i) := voter(i).out
	}
}
