package labs.passes

import labs._
import chiffre.inject.Injector
import chiffre.util.removeZeroWidth
import chisel3.experimental.{annotate, ChiselAnnotation}
import firrtl._
import firrtl.ir._
import firrtl.passes.{PassException, ToWorkingIR}
import firrtl.passes.wiring.SinkAnnotation
import firrtl.annotations.{Annotation, ComponentName, ModuleName, CircuitName, SingleTargetAnnotation, MultiTargetAnnotation, Target}
import firrtl.annotations.AnnotationUtils._
import scala.collection.mutable

case class FaultTolerantDMRAnnotation(target: ComponentName, feedback: Int, feedback_target: List[String]) extends SingleTargetAnnotation[ComponentName]{
  def duplicate(x: ComponentName): FaultTolerantDMRAnnotation = this.copy(target = x)
}

case class FaultTolerantTMRAnnotation(target: ComponentName, feedback: Int, feedback_target: List[String]) extends SingleTargetAnnotation[ComponentName]{
  def duplicate(x: ComponentName): FaultTolerantTMRAnnotation = this.copy(target = x)
}

case class FaultTolerantTemporalAnnotation(target: ComponentName, ready_signal: String, start_signal: String, feedback: Int, feedback_target: List[String]) extends SingleTargetAnnotation[ComponentName]{
  def duplicate(x: ComponentName): FaultTolerantTemporalAnnotation = this.copy(target = x)
}

case class FaultTolerantInformationAnnotation(target: ComponentName, roundkey: ComponentName, tosbox_before: ComponentName, tosbox_after: ComponentName, tosbox_write: ComponentName, tomixcolumns: ComponentName, round_ctr: ComponentName, next_round: ComponentName, feedback_mechanism: Int, feedback_target: List[String]) extends SingleTargetAnnotation[ComponentName]{
  def duplicate(x: ComponentName): FaultTolerantInformationAnnotation = this.copy(target = x)
}

case class FaultControllerUDAnnotation(target: List[ComponentName], data_target: List[String], affected_bits: List[List[Int]], durations: List[Int]) extends Annotation{ 
  def duplicate(x: List[ComponentName]): FaultControllerUDAnnotation = this.copy(target = x)
  def update(renames: RenameMap): Seq[Annotation] = Seq(this) // hack
}

case class FaultControllerProbAnnotation(target: List[ComponentName], data_target: List[String], number_of_fires: Int, probability: Int) extends Annotation {
  def duplicate(x: List[ComponentName]): FaultControllerProbAnnotation = this.copy(target = x)
  def update(renames: RenameMap): Seq[Annotation] = Seq(this) // hack
}

case class ClockAnnotation(target: ComponentName) extends
    SingleTargetAnnotation[ComponentName] {
  def duplicate(x: ComponentName): ClockAnnotation = this.copy(target = x)
}

case class ResetAnnotation(target: ComponentName, posedge_reset: Int) extends
    SingleTargetAnnotation[ComponentName] {
  def duplicate(x: ComponentName): ResetAnnotation = this.copy(target = x)
}
