package labs.passes

import labs._
import chiffre.passes._
import firrtl._
import firrtl.passes.{ToWorkingIR, InferTypes, Uniquify, ExpandWhens, CheckInitialization, ResolveKinds, ResolveGenders,
  CheckTypes, RemoveEmpty, RemoveAccesses}
import firrtl.passes.wiring.WiringTransform
import firrtl.annotations.{SingleTargetAnnotation, ComponentName}
import scala.collection.mutable
import scala.language.existentials

class FaultControllerTransform extends Transform {
  def inputForm: CircuitForm = HighForm
  def outputForm: CircuitForm = HighForm
  def transforms(): Seq[Transform] = Seq(
	new CompatibilityPass,
	new FaultControllerInstrumentation,
	InferTypes,
	ResolveKinds,
	InferTypes,
	CheckTypes,
	ResolveGenders
 )

  def execute(state: CircuitState): CircuitState = {
        transforms.foldLeft(state)((old, x) => x.runTransform(old))
  }
}
