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
import labs.passes._
import chiffre.{ScanField, InjectorInfo, ProbabilityBind}

case class Seed(width: Int) extends ScanField
case class Difficulty(width: Int) extends ScanField with ProbabilityBind

case class LfsrInjectorInfo(bitWidth: Int, lfsrWidth: Int) extends InjectorInfo {
  val fields = Seq.fill(bitWidth)(Seq(Seed(lfsrWidth), Difficulty(lfsrWidth))).flatten
}

sealed class LfsrInjector(val lfsrWidth: Int, val faultType: String) extends Injector(1) {
  lazy val info = LfsrInjectorInfo(1, lfsrWidth)
  val resetB = if(global_edge_reset.posedge_reset == 0) ~reset.toBool else reset.toBool
  withReset(resetB){

  val difficulty = RegInit(0.U(lfsrWidth.W))
  val seed = RegInit(1.U(lfsrWidth.W))

  val is_inited = RegInit(0.U(1.W))
  val lfsr = Module(new perfect.random.Lfsr(lfsrWidth))
  lfsr.io.seed.valid := Mux(is_inited === 1.U, 0.U, io.scan.en)
  lfsr.io.seed.bits := seed
  is_inited := Mux(is_inited === 1.U, 1.U, io.scan.en)

  val fire = enabled && (lfsr.io.y > difficulty)

  if(faultType == "bit-set")
  	io.out := Mux(fire, 1.U, io.in)
  else if(faultType == "bit-reset")
	io.out := Mux(fire, 0.U, io.in)
  else
	io.out := Mux(fire, ~io.in, io.in)
 
  when (io.scan.clk) {
    enabled := false.B
    seed := (io.scan.in ## seed) >> 1
    difficulty := (seed(0) ## difficulty) >> 1
  }

  io.scan.out := difficulty(0)

  when(enabled){
    printf(s"""|[info] $name enabled
               |[info]   - seed: 0x%x
               |[info]   - difficulty: 0x%x
	       |[info]   - input: 0x%x
	       |[info]   - output: 0x%x
               |""".stripMargin, seed, difficulty, io.in, io.out)
  }
  }
}

class LfsrInjectorN(bitWidth: Int, val lfsrWidth: Int, val scanId: String, val faultType: String) extends
    InjectorBitwise(bitWidth, new LfsrInjector(lfsrWidth, faultType)) with ChiffreInjector {
  lazy val info = LfsrInjectorInfo(bitWidth, lfsrWidth)
}

class LfsrInjector32(bitWidth: Int, scanId: String) extends LfsrInjectorN(bitWidth, 32, scanId, "bit-flip") // scalastyle:ignore

class LfsrInjector32_bitset(bitWidth: Int, scanId: String) extends LfsrInjectorN(bitWidth, 32, scanId, "bit-set") // scalastyle:ignore

class LfsrInjector32_bitreset(bitWidth: Int, scanId: String) extends LfsrInjectorN(bitWidth, 32, scanId, "bit-reset") // scalastyle:ignore


