package labs.passes

import labs._
import chiffre.passes._
import chiffre.inject.Injector
import firrtl._
import firrtl.passes.{ToWorkingIR, InferTypes, Uniquify, ExpandWhens, CheckInitialization, ResolveKinds, ResolveGenders,
  CheckTypes, RemoveEmpty, RemoveAccesses, DeadCodeElimination, VerilogPrep, ExpandConnects, LowerTypes, RemoveValidIf}
import firrtl.transforms._
import firrtl.passes.wiring.WiringTransform
import firrtl.annotations.{SingleTargetAnnotation, ComponentName}
import scala.collection.mutable
import scala.language.existentials

// Use MidForm to avoid the uniquify pass called to convert HighForm to MidForm
// For that case, uniquify pass can be a problem
class FaultInjectionTransform extends Transform {
  def inputForm: CircuitForm = MidForm
  def outputForm: CircuitForm = HighForm // Must be high otherwise some ports disappear
  def transforms: Seq[Transform] = Seq(
	ToWorkingIR,
	LowerTypes,
	new CompatibilityPass,
	new FaultControllerInstrumentation,
	InferTypes,
	ResolveKinds,
	InferTypes,
	CheckTypes,
	ResolveGenders,
	new FaultInstrumentationTransform,
    InferTypes,
    ResolveKinds,
	InferTypes,
    ResolveGenders,
	InferTypes,
	RemoveValidIf,
	Uniquify,
 )

  def execute(state: CircuitState): CircuitState = {
        transforms.foldLeft(state)((old, x) => {
			val temp = x.runTransform(old)
			temp
		})
  }
}
