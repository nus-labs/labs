package labs

import chisel3._
import chiffre._
import java.io.File
import labs.passes._

sealed class AddRoundKeyParityBitGeneratorIo(byteWidth:Int) extends Bundle{
	val parity_in = Input(UInt(byteWidth.W))
	val roundkey = Input(UInt(128.W))
	val mixcolumns_in = Input(UInt(byteWidth.W))
	val parity_out = Output(UInt(byteWidth.W))
	val round_ctr = Input(UInt(4.W)) // assume 4 bits
	val write = Input(UInt(1.W))
}

class AddRoundKeyParityBitGenerator(byteWidth:Int) extends Module{
    val io = IO(new AddRoundKeyParityBitGeneratorIo(byteWidth))	
    val resetB = if(global_edge_reset.posedge_reset == 0) ~reset.toBool else reset.toBool
	withReset(resetB){

	val parity_bit_generators = Module(new nByteParityBitGenerator(byteWidth))
	parity_bit_generators.io.in := io.roundkey

	val parity_in_bits = VecInit(io.parity_in.toBools)
	val mixcolumns_in_bits = VecInit(io.mixcolumns_in.toBools)
	val parity_bit_generators_out = VecInit(parity_bit_generators.io.parity.toBools)
	val parity_out_bits = VecInit(io.parity_out.toBools)

	for(i <- 0 to byteWidth - 1){
		parity_out_bits(i) := Mux(io.round_ctr === 0.U, 
								parity_in_bits(i),
								mixcolumns_in_bits(i) ^ parity_bit_generators_out(i))
	}

	io.parity_out := parity_out_bits.asUInt
	}
}
