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

case class FaultTolerantDMRAnnotation(target: ComponentName) extends SingleTargetAnnotation[ComponentName]{
  def duplicate(x: ComponentName): FaultTolerantDMRAnnotation = this.copy(target = x)
}

case class FaultTolerantTMRAnnotation(target: ComponentName) extends SingleTargetAnnotation[ComponentName]{
  def duplicate(x: ComponentName): FaultTolerantTMRAnnotation = this.copy(target = x)
}

case class FaultTolerantTemporalAnnotation(target: ComponentName, ready_signal: String, start_signal: String) extends SingleTargetAnnotation[ComponentName]{
  def duplicate(x: ComponentName): FaultTolerantTemporalAnnotation = this.copy(target = x)
}

case class FaultControllerUDAnnotation(target: ComponentName, data_target: String, number_of_fires: Int, affected_bits: List[Int]) extends SingleTargetAnnotation[ComponentName] {
  def duplicate(x: ComponentName): FaultControllerUDAnnotation = this.copy(target = x)
}

case class FaultControllerProbAnnotation(target: ComponentName, data_target: String, number_of_fires: Int, probability: Int) extends SingleTargetAnnotation[ComponentName] {
  def duplicate(x: ComponentName): FaultControllerProbAnnotation = this.copy(target = x)
}

case class ClockAnnotation(target: ComponentName) extends
    SingleTargetAnnotation[ComponentName] {
  def duplicate(x: ComponentName): ClockAnnotation = this.copy(target = x)
}

case class ResetAnnotation(target: ComponentName) extends
    SingleTargetAnnotation[ComponentName] {
  def duplicate(x: ComponentName): ResetAnnotation = this.copy(target = x)
}
