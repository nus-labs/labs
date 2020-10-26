// See LICENSE.txt for license details.
package labs

import chisel3._
import chiffre._
import java.io.File

object MUXDriver extends App {
	chisel3.Driver.execute(args, () => new Voter(2))
}

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
	val voter = for(i <- 0 to bitWidth - 1) yield
	{
		val one_bit_voter = Module(new One_Bit_Voter())
		one_bit_voter
	}	
	val bools = VecInit(io.out.asBools)
	val voter_io = voter.map(_.io)
	for(i <- 0 to bitWidth - 1){
		voter_io(i).in1 := io.in1(i)
		voter_io(i).in2 := io.in2(i)
		voter_io(i).in3 := io.in3(i)
	}
	for(i <- 0 to bitWidth - 1){
		bools(i) := voter_io(i).out
	}
	io.out := bools.asUInt
}
