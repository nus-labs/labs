package labs

import chisel3._
import chiffre._
import java.io.File
import labs.passes._

sealed class ParityBitGeneratorIo(val bitWidth: Int) extends Bundle{
	val in = Input(UInt(bitWidth.W))
	val parity = Output(UInt(1.W))
}

class ParityBitGenerator(val bitWidth: Int) extends Module{
	val io = IO(new ParityBitGeneratorIo(bitWidth))
	io.parity := io.in.xorR
}

sealed class nByteParityBitGeneratorIo(val byteWidth: Int) extends Bundle{
	val in = Input(UInt((byteWidth * 8).W))
	val parity = Output(UInt(byteWidth.W))
}

class nByteParityBitGenerator(val byteWidth: Int) extends Module{
	val io = IO(new nByteParityBitGeneratorIo(byteWidth))
    val resetB = if(global_edge_reset.posedge_reset == 0) ~reset.toBool else reset.toBool
    withReset(resetB){

	val parity_bits = Wire(Vec(byteWidth, UInt(1.W)))
	val reg_output_parity = RegInit(0.U(byteWidth.W))
	val parity_generators = for(i <- 0 to byteWidth - 1) yield
	{
		val one_byte_parity_generator = Module(new ParityBitGenerator(8))
		one_byte_parity_generator
	}
	for(i <- 0 to byteWidth - 1){
		parity_generators(i).io.in := VecInit(io.in.toBools.slice(i * 8, (i * 8) + 8)).asUInt
		parity_bits(i) := parity_generators(i).io.parity
	}
	
	io.parity := parity_bits.asUInt
	}
}
