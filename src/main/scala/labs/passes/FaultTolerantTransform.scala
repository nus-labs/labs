package labs.passes

import labs._
import chiffre.passes._
import firrtl._
import firrtl.passes.{ToWorkingIR, InferTypes, Uniquify, ExpandWhens, CheckInitialization, ResolveKinds, ResolveGenders,
  CheckTypes, RemoveEmpty, RemoveAccesses, InferWidths, RemoveValidIf}
import firrtl.transforms.InferResets
import firrtl.passes.wiring.WiringTransform
import firrtl.annotations.{SingleTargetAnnotation, ComponentName}
import scala.collection.mutable
import scala.language.existentials

class FaultTolerantTransform extends Transform {
  def inputForm: CircuitForm = LowForm
  def outputForm: CircuitForm = HighForm
  def transforms(): Seq[Transform] = Seq(
	new CompatibilityPass,
	RemoveEmpty,
	new FaultTolerantInstrumentation,
	InferTypes,
	ResolveKinds,
	InferTypes,
	CheckTypes,
	ResolveGenders,
	RemoveValidIf,
	Uniquify,
	new firrtl.transforms.InferResets
 )

  def execute(state: CircuitState): CircuitState = {
        transforms.foldLeft(state)((old, x) => x.runTransform(old))
    	
  }
}
