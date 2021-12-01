package labs

import chisel3._
import chiffre._
import java.io.File

sealed class ShiftrowParityBitGeneratorIo() extends Bundle{
	val in = Input(UInt(16.W))
	val parity = Output(UInt(16.W))
}

// 0, 0 | 1, 0 | 2, 0 | 3, 0 -> 0, 1, 2, 3

class ShiftrowParityBitGenerator() extends Module{
	val io = IO(new ShiftrowParityBitGeneratorIo)
	val parity_out = Wire(Vec(16, UInt(1.W)))
	val ins = VecInit(io.in.toBools)

	parity_out(15) := ins(15)
	parity_out(14) := ins(10)
	parity_out(13) := ins(5)
	parity_out(12) := ins(0)
	parity_out(11) := ins(11)
	parity_out(10) := ins(6)
	parity_out(9) := ins(1)
	parity_out(8) := ins(12)
	parity_out(7) := ins(7)
	parity_out(6) := ins(2)
	parity_out(5) := ins(13)
	parity_out(4) := ins(8)
	parity_out(3) := ins(3)
	parity_out(2) := ins(14)
	parity_out(1) := ins(9)
	parity_out(0) := ins(4)


	/*parity_out(0) := ins(15)
	parity_out(1) := ins(10)
	parity_out(2) := ins(5)
	parity_out(3) := ins(0)
	parity_out(4) := ins(11)
	parity_out(5) := ins(6)
	parity_out(6) := ins(1)
	parity_out(7) := ins(12)
	parity_out(8) := ins(7)
	parity_out(9) := ins(2)
	parity_out(10) := ins(13)
	parity_out(11) := ins(8)
	parity_out(12) := ins(3)
	parity_out(13) := ins(14)
	parity_out(14) := ins(9)
	parity_out(15) := ins(4)*/

	/*parity_out(0) := ins(0)
	parity_out(1) := ins(5)
	parity_out(2) := ins(10)
	parity_out(3) := ins(15)
	parity_out(4) := ins(4)
	parity_out(5) := ins(9)
	parity_out(6) := ins(14)
	parity_out(7) := ins(3)
	parity_out(8) := ins(8)
	parity_out(9) := ins(13)
	parity_out(10) := ins(2)
	parity_out(11) := ins(7)
	parity_out(12) := ins(12)
	parity_out(13) := ins(1)
	parity_out(14) := ins(6)
	parity_out(15) := ins(11)*/
	io.parity := parity_out.asUInt
}
