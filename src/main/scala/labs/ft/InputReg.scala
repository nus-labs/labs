// See LICENSE.txt for license details.
package labs

import chisel3._
import chiffre._
import java.io.File

object InputRegDriver extends App {
  val f = new File("InputReg.fir")
  chisel3.Driver.dumpFirrtl(chisel3.Driver.elaborate(() => new InputReg(4)), Option(f))
  chisel3.Driver.execute(args, () => new InputReg(4))
}

sealed class InputRegIo(bitwidth: Int) extends Bundle{
	val in = Input(UInt(bitwidth.W))
	val out = Output(UInt(bitwidth.W))
	val ctrl = Input(UInt(2.W))
	val ready = Input(UInt(1.W))
}

class InputReg(bitwidth: Int) extends Module{
	val io = IO(new InputRegIo(bitwidth))
	val resetB = ~reset.toBool
	val stored_input = RegInit(0.U(bitwidth.W))
	stored_input := Mux(io.ctrl === 1.U, stored_input, io.in)
	io.out := Mux(io.ctrl === 1.U && io.ready === 1.U, stored_input, io.in)
}
