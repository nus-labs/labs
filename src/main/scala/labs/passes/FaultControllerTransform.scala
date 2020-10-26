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
  def transforms(compMap: Map[String, Seq[ComponentName]]): Seq[Transform] = Seq(
	new CompatibilityPass,
	new FaultControllerInstrumentation,
	InferTypes,
	ResolveKinds,
	InferTypes,
	CheckTypes,
	ResolveGenders
 )

  def execute(state: CircuitState): CircuitState = {
    	val myAnnos = state.annotations.collect { case a: ClockAnnotation => a 
												  case b: ResetAnnotation => b
												  case c: FaultControllerUDAnnotation => c
												  case d: FaultInjectionAnnotation => d}
		myAnnos match{
			case Nil => state
			case p =>
        val comp = mutable.HashMap[String, Seq[ComponentName]]()
        p.foreach {
          case ClockAnnotation(c) => comp("ClockAnnotation") = comp.getOrElse(name, Seq.empty) :+ (c) 
		  case ResetAnnotation(c) => comp("ResetAnnotation") = comp.getOrElse(name, Seq.empty) :+ (c)
		  case other => other
			}
        transforms(comp.toMap).foldLeft(state)((old, x) => x.runTransform(old))
          .copy(annotations = (state.annotations.toSet -- myAnnos.toSet).toSeq)
		}
  }
}
