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
import firrtl.ir._
import firrtl.passes.{PassException, ToWorkingIR}
import firrtl.passes.wiring.SinkAnnotation
import firrtl.annotations.{Annotation, ComponentName, ModuleName, CircuitName, SingleTargetAnnotation}
import firrtl.annotations.AnnotationUtils._
import scala.collection.mutable

case class FaultTolerantAnnotation(target: ComponentName, mode: String, ready_signal: String, start_signal: String) extends SingleTargetAnnotation[ComponentName]{
  def duplicate(x: ComponentName): FaultTolerantAnnotation = this.copy(target = x)
}

case class FaultControllerUDAnnotation(target: ComponentName, data_target: String, number_of_fires: Int, affected_bits: List[Int]) extends SingleTargetAnnotation[ComponentName] {
  def duplicate(x: ComponentName): FaultControllerUDAnnotation = this.copy(target = x)
}

case class ClockAnnotation(target: ComponentName) extends
    SingleTargetAnnotation[ComponentName] {
  def duplicate(x: ComponentName): ClockAnnotation = this.copy(target = x)
}

case class ResetAnnotation(target: ComponentName) extends
    SingleTargetAnnotation[ComponentName] {
  def duplicate(x: ComponentName): ResetAnnotation = this.copy(target = x)
}
