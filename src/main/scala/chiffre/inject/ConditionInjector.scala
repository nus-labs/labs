// Copyright 2018 IBM
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package chiffre.inject

import chisel3._
import chiffre.ChiffreInjector

import chiffre.{InjectorInfo, ScanField, HasWidth}

case class condition(width: Int) extends ScanField
//case class conditionInject(width: Int) extends ScanField

case class ConditionInjectorInfo(bitWidth: Int) extends InjectorInfo {
  val fields = Seq(condition(bitWidth))
}

class ConditionInjector(bitWidth: Int, faultType: String) extends Injector(bitWidth){
//  val flipMask = RegInit(0.U(bitWidth.W))
  val resetB = ~reset.toBool
  lazy val info = ConditionInjectorInfo(bitWidth)

  //withReset (resetB){
  val flipMask = Reg(UInt(bitWidth.W))
 
  val fire = enabled & ~io.scan.clk
  if(faultType == "bit-set")
  	io.out := Mux(fire, io.in | flipMask, io.in)
  else if(faultType == "bit-reset")
	io.out := Mux(fire, io.in & !flipMask, io.in)
  else
	io.out := Mux(fire, io.in ^ flipMask, io.in)
  
  flipMask := Mux(io.scan.clk, (io.scan.in ## flipMask) >> 1, flipMask)
//  when (io.scan.clk) {
//    enabled := false.B
//    flipMask := (io.scan.in ## flipMask) >> 1
//  }

  io.scan.out := flipMask(0)

  when(fire){
    printf(s"""|[info] $name enabled
           |[info]   - flipMask: 0x%x
           |[info]   - input: 0x%x
           |[info]   - output: 0x%x
               |""".stripMargin, flipMask, io.in, io.out)
  }
  //}
}

// scalastyle:off magic.number
class ConditionInjectorCaller(bitWidth: Int, val scanId: String) extends ConditionInjector(bitWidth, "bit-flip") with ChiffreInjector
class ConditionInjectorCaller_bitset(bitWidth: Int, val scanId: String) extends ConditionInjector(bitWidth, "bit-set") with ChiffreInjector
class ConditionInjectorCaller_bitreset(bitWidth: Int, val scanId: String) extends ConditionInjector(bitWidth, "bit-reset") with ChiffreInjector

// scalastyle:on magic.number
