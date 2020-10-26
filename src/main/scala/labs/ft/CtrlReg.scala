// See LICENSE.txt for license details.
package labs

import chisel3._
import chiffre._
import chisel3.dontTouch
import java.io.File

object CtrlRegDriver extends App {
  val f = new File("CtrlReg.fir")
  chisel3.Driver.dumpFirrtl(chisel3.Driver.elaborate(() => new CtrlReg()), Option(f))
  chisel3.Driver.execute(args, () => new CtrlReg())
}

sealed class CtrlRegIo extends Bundle{
	val ready = Input(Bool())
	val start = Input(Bool())
	val finished = Output(Bool())
	val ctrl = Output(UInt(2.W))
}

class CtrlReg extends Module{
	val io = IO(new CtrlRegIo)
	val resetB = ~(reset.toBool)
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
