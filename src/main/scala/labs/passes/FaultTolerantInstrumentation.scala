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
  var arr: Set[(Type, Boolean)] = Set()
}

object feedback_target{
  var arr: List[String] = List()
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

class FaultTolerantInstrumentation extends Transform {
 def inputForm: CircuitForm = HighForm
  def outputForm: CircuitForm = HighForm
  def run(c:Circuit, annos:List[Annotation]): Circuit ={
	var dmr = new DMR
	var tmr = new TMR
	var temporal = new Temporal
	var ir = new IR
	var cc = c
	annos.foreach{
		case FaultTolerantDMRAnnotation(z, feedback, feedback_target_list) => {
			output_size.arr = Set()
			redundancy_number.count = 0
			redundancy_number.n = 2; 
			redundancy_number.mode = "DMR" 
			feedback_target.arr = feedback_target_list
			cc = dmr.run(c, z, feedback)
		}
		case FaultTolerantTMRAnnotation(z, feedback, feedback_target_list) => {
			output_size.arr = Set()
			redundancy_number.count = 0
			redundancy_number.n = 3
			redundancy_number.mode = "TMR"
			feedback_target.arr = feedback_target_list
			cc = tmr.run(c, z, feedback)
		}
		case FaultTolerantTemporalAnnotation(z, ready_signal, start_signal, feedback, feedback_target_list) => {
			redundancy_number.n = 2
			redundancy_number.mode = "Temporal" 
			signals.ready_signal = ready_signal 
			signals.start_signal = start_signal 
			feedback_target.arr = feedback_target_list
			cc = temporal.run(c, z, feedback)
		}
		case FaultTolerantInformationAnnotation(z, roundkey, tosbox_before, tosbox_after, tosbox_write, tomixcolumns, round_ctr, next_round, feedback_mechanism, feedback_target_list) => {
			cc = ir.run(c, z, roundkey, tosbox_before, tosbox_after, tosbox_write, tomixcolumns, round_ctr, next_round, feedback_mechanism, feedback_target_list)
		}
		case other => {}
	}
	return cc
  }

  def execute(state: CircuitState): CircuitState = {
	var annos = state.annotations.toList
	state.copy(circuit = run(state.circuit, annos))
  }
}

