package chiffre.inject

import chisel3._
import chiffre.ChiffreInjector
import chiffre.{InjectorInfo, ScanField, HasWidth}
import labs.passes._

case class condition(width: Int) extends ScanField

case class ConditionInjectorInfo(bitWidth: Int) extends InjectorInfo {
  val fields = Seq(condition(bitWidth))
}

class ConditionInjector(bitWidth: Int, faultType: String) extends Injector(bitWidth){
  val resetB = if(global_edge_reset.posedge_reset == 0) ~reset.toBool else reset.toBool
  lazy val info = ConditionInjectorInfo(bitWidth)

  val flipMask = Reg(UInt(bitWidth.W))
 
  withReset(resetB){
  val fire = enabled & ~io.scan.clk
  if(faultType == "bit-set")
  	io.out := Mux(fire, io.in | flipMask, io.in)
  else if(faultType == "bit-reset")
	io.out := Mux(fire, io.in & !flipMask, io.in)
  else
	io.out := Mux(fire, io.in ^ flipMask, io.in)
  
  flipMask := Mux(io.scan.clk, (io.scan.in ## flipMask) >> 1, flipMask)

  io.scan.out := flipMask(0)

  when(fire){
    printf(s"""|[info] $name enabled
           |[info]   - flipMask: 0x%x
           |[info]   - input: 0x%x
           |[info]   - output: 0x%x
               |""".stripMargin, flipMask, io.in, io.out)
  }
  }
}

// scalastyle:off magic.number
class ConditionInjectorCaller(bitWidth: Int, val scanId: String) extends ConditionInjector(bitWidth, "bit-flip") with ChiffreInjector
class ConditionInjectorCaller_bitset(bitWidth: Int, val scanId: String) extends ConditionInjector(bitWidth, "bit-set") with ChiffreInjector
class ConditionInjectorCaller_bitreset(bitWidth: Int, val scanId: String) extends ConditionInjector(bitWidth, "bit-reset") with ChiffreInjector

// scalastyle:on magic.number
