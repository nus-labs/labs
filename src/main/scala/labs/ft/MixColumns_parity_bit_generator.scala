package labs

import chisel3._
import chiffre._
import java.io.File

sealed class MuxColumnsParityBitGeneratorIo() extends Bundle{
	val in = Input(UInt(128.W))
	val in_parity = Input(UInt(16.W))
	val parity = Output(UInt(16.W))
	val round_ctr = Input(UInt(4.W)) // assume 4 bits
}

// 0, 0 | 1, 0 | 2, 0 | 3, 0 -> 0, 1, 2, 3

class MixColumnsParityBitGenerator() extends Module{
	val io = IO(new MuxColumnsParityBitGeneratorIo)

	val ins = VecInit(io.in.toBools)
	val parity_in = VecInit(io.in_parity.toBools)
	val parity_out = VecInit(io.parity.toBools)

	for(i <- 15 to 3 by -4){
		parity_out(i) := parity_in(i) ^ parity_in(i - 2) ^ parity_in(i - 3) ^ ins(((i + 1) * 8) - 1) ^ ins((i * 8) - 1)
		parity_out(i - 1) := parity_in(i) ^ parity_in(i - 1) ^ parity_in(i - 3) ^ ins((i * 8) - 1) ^ ins(((i - 1) * 8) - 1)
		parity_out(i - 2) := parity_in(i) ^ parity_in(i - 1) ^ parity_in(i - 2) ^ ins(((i - 1) * 8) - 1) ^ ins(((i - 2) * 8) - 1)
		parity_out(i - 3) := parity_in(i - 1) ^ parity_in(i - 2) ^ parity_in(i - 3) ^ ins(((i - 2) * 8) - 1) ^ ins((i * 8) + 8 - 1)
	}

	/*for(i <- 0 to 15 by 4){
		parity_out(i) := parity_in(i) ^ parity_in(i + 2) ^ parity_in(i + 3) ^ ins((i * 8) + 8 - 1) ^ ins((i * 8) + 16 - 1)
		parity_out(i + 1) := parity_in(i) ^ parity_in(i + 1) ^ parity_in(i + 3) ^ ins((i * 8) + 16 - 1) ^ ins((i * 8) + 24 - 1)
		parity_out(i + 2) := parity_in(i) ^ parity_in(i + 1) ^ parity_in(i + 2) ^ ins((i * 8) + 24 - 1) ^ ins((i * 8) + 32 - 1)
		parity_out(i + 3) := parity_in(i + 1) ^ parity_in(i + 2) ^ parity_in(i + 3) ^ ins((i * 8) + 32 - 1) ^ ins((i * 8) + 8 - 1)
	}*/
	
	io.parity := Mux(io.round_ctr === 10.U, io.in_parity, parity_out.asUInt)
}
