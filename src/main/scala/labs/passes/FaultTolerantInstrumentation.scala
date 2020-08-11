// Copyright 2017 IBM
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
package labs.passes

import labs._
import chiffre.inject.Injector
import chiffre.util.removeZeroWidth
import chisel3.experimental.{annotate, ChiselAnnotation}
import firrtl._
import firrtl.ResolveAndCheck
import firrtl.ir._
import firrtl.passes.{PassException, ToWorkingIR, ResolveKinds, InferTypes, CheckTypes, Uniquify, ResolveGenders, CheckGenders, InferWidths, CheckWidths}
import firrtl.passes.wiring.SinkAnnotation
import firrtl.annotations.{SingleTargetAnnotation, Annotation, ComponentName, ModuleName, CircuitName, ReferenceTarget, TargetToken}
import firrtl.annotations.TargetToken.{OfModule, Instance}
import firrtl.transforms.DontTouchAnnotation
import firrtl.annotations.AnnotationUtils._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import java.io.File

object output_size{
  var arr: Set[Type] = Set()
}

object redundancy_number{
  var mode: String = "DMR"
  var n: Int = 2
  var count: Int = 0
}

object signals{
  var ready_signal = ""
  var start_signal = ""
}

// NEED FIX BUG OUTPUT_SIZE.ARR CAN BE FOR DIFFERENT TECHNIQUES

class FaultTolerantInstrumentation extends Transform {
 def inputForm: CircuitForm = HighForm
  def outputForm: CircuitForm = HighForm
  def run(c:Circuit, annos:List[Annotation]): Circuit ={
	var dmr = new DMR
	var tmr = new TMR
	var temporal = new Temporal
	var cc = c
	annos.foreach{
		case FaultTolerantAnnotation(z, mode, ready_signal, start_signal) => {
			output_size.arr = Set()
			redundancy_number.count = 0
			mode match{
				case "DMR" => {redundancy_number.n = 2; redundancy_number.mode = "DMR"; cc = dmr.run(c, z)}
				case "TMR" => {redundancy_number.n = 3; redundancy_number.mode = "TMR"; cc = tmr.run(c, z)}
				case "Temporal" => {redundancy_number.n = 2; redundancy_number.mode = "Temporal"; signals.ready_signal = ready_signal; signals.start_signal = start_signal; cc = temporal.run(c, z)}
				case other => throw new Exception(mode + " Incorrect Configuration")
			}
		}
	}
	return cc
}

  def execute(state: CircuitState): CircuitState = {
	var annos = state.annotations.toList.filter(_.isInstanceOf[FaultTolerantAnnotation])
	state.copy(circuit = run(state.circuit, annos))
  }
}

