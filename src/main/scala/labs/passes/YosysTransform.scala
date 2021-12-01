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

// Yosys provides a bad FIRRTL format
// LowFirrtlOptimization helps!

class YosysTransform extends Transform {
  def inputForm: CircuitForm = LowForm
  def outputForm: CircuitForm = LowForm
  def transforms(): Seq[Transform] = Seq(
	InferTypes,
	ResolveKinds,
	InferTypes,
	CheckTypes,
	ResolveGenders,
	RemoveEmpty,
	new LowFirrtlOptimization,
	RemoveEmpty,
 )

  def execute(state: CircuitState): CircuitState = {
        transforms.foldLeft(state)((old, x) => x.runTransform(old))
  }
}
