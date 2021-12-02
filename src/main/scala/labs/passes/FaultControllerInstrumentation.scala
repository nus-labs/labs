package labs.passes

import labs._
import chiffre.inject.Injector
import chiffre.util.removeZeroWidth
import chisel3.experimental.{annotate, ChiselAnnotation}
import chiffre.passes._
import firrtl._
import firrtl.ir._
import firrtl.passes.{PassException, ToWorkingIR}
import firrtl.passes.wiring.SinkAnnotation
import firrtl.annotations.{Annotation, SingleTargetAnnotation, ComponentName, ModuleName, CircuitName}
import firrtl.annotations.AnnotationUtils._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object input_type{
	var tpe = "UIntType"
}

object global_edge_reset{
	var posedge_reset = 1
}

class FaultControllerInstrumentation() extends Transform {
  def inputForm: CircuitForm = HighForm
  def outputForm: CircuitForm = HighForm
  def run(c:Circuit, annos:List[Annotation]): Circuit ={
	var temp = c.modules
	var input_width = List[Int]()
	var faulty_width = 0
	var data_target = List[String]()
	var number_of_fires = 0
	var probabilistic = false
	var affected_bits = List(List(1))
	var probability = 0
	var edge_reset = 1
	var delays = List(1)
	annos.foreach{
		case FaultControllerUDAnnotation(target, dtarget, abits, delays_in) =>{
			input_width = target.map(find_width(c.modules, _))
			data_target = dtarget
			affected_bits = abits
			temp = add_stmtsUD(temp, target, input_width)
			delays = delays_in
		}
		case FaultControllerProbAnnotation(target, dtarget, nfires, prob) =>{
			input_width = target.map(find_width(c.modules, _))
			data_target = dtarget
			number_of_fires = nfires
			probability = prob
			temp = add_stmtsUD(temp, target, input_width)
			probabilistic = true
		}
		case FaultInjectionAnnotation(target, id, injector) =>{
			faulty_width = find_width(c.modules, target)
		}
		case ResetAnnotation(target, posedge_reset) => {
			global_edge_reset.posedge_reset = posedge_reset
		}
		case other => {other}
	}
	if(input_width == 0){
		return c
	}
	var elab = chisel3.Driver.toFirrtl(chisel3.Driver.elaborate(() => new FaultController(input_width, data_target, affected_bits, faulty_width, probability, probabilistic, delays)))
	elab = ToWorkingIR.run(elab)
	temp = temp ++ elab.modules
	val k = c.copy(modules = temp)
	k
}

  def find_width_recursive(stmt: Statement, target: ComponentName): Int = {
	stmt match{
		case DefWire(info, name, tpe) => {
			if(name == target.name && tpe.isInstanceOf[UIntType]){
				return tpe.asInstanceOf[UIntType].width.asInstanceOf[IntWidth].width.toInt
			}
			else if(name == target.name && tpe.isInstanceOf[VectorType]) {
                input_type.tpe = "VectorType"
                return tpe.asInstanceOf[VectorType].tpe.asInstanceOf[UIntType].width.asInstanceOf[IntWidth].width.toInt // TODO might not be correct
            }
			else{
                return 0
			}
		}
		
		case DefRegister(info, name, tpe, clock, reset, init) => {
			if(name == target.name && tpe.isInstanceOf[UIntType]){
				return tpe.asInstanceOf[UIntType].width.asInstanceOf[IntWidth].width.toInt 
			}
			else if(name == target.name && tpe.isInstanceOf[VectorType]) {
				input_type.tpe = "VectorType"	
				return tpe.asInstanceOf[VectorType].tpe.asInstanceOf[UIntType].width.asInstanceOf[IntWidth].width.toInt // TODO might not be correct
			}
			else{
				return 0
			}
		}
		case DefNode(info, name, value) => {
			if(name == target.name){
				return value.tpe.asInstanceOf[UIntType].width.asInstanceOf[IntWidth].width.toInt
			}
			else{
                return 0
			}
		}
		case Block(stmts) => {
			stmts.foreach(stmt => {
					val result = find_width_recursive(stmt, target)
					if(result != 0) return result
				}
			)
			return 0
		}
		case other =>{return 0}
	}
  }

  def find_width(modules: Seq[DefModule], target: ComponentName): Int = {
	var module = modules.filter(_.name == target.module.name)
	module(0).asInstanceOf[Module].body.asInstanceOf[Block].stmts(0).foreachStmt(stmt =>{
			val result = find_width_recursive(stmt, target)
			if(result != 0) return result
		}
	)
	throw new Exception(target + " not found")
}

  def add_stmtsUD(modules: Seq[DefModule], target: List[ComponentName], width: List[Int]): Seq[DefModule] = { 
	var module = modules.filter(_.name == target(0).module.name)(0)
	var left_module = modules.filter(_.name != target(0).module.name)
	var stmts = module.asInstanceOf[Module].body.asInstanceOf[Block].stmts
	var data_ins = List[Field]()
	var data_in_name = "data_in"
    val inside_io = BundleType(Vector(Field("data_in", Flip, VectorType(UIntType(IntWidth(width(0))), width.length) )))
    val ports = ArrayBuffer(Field("clock", Flip, ClockType), Field("reset", Flip, UIntType(IntWidth(1))), Field("io", Default, inside_io))
	val wref = WRef("controller", BundleType(ports), ExpKind, UNKNOWNGENDER)
	stmts = stmts :+ WDefInstance(NoInfo, "controller", "FaultController", UnknownType)
	stmts = stmts :+ Connect(NoInfo, WSubField(wref, "clock", ClockType, FEMALE), WRef("clock", ClockType, ExpKind, MALE))
	stmts = stmts :+ Connect(NoInfo, WSubField(wref, "reset", UIntType(IntWidth(1)), FEMALE), WRef("reset", UIntType(IntWidth(1)), ExpKind, MALE))
	for(i <- 0 to width.length - 1){
		if (input_type.tpe == "UIntType"){
			stmts = stmts :+ Connect(NoInfo, WSubIndex(WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "data_in", UnknownType, FEMALE), i, UnknownType, UnknownFlow), WRef(target(i).name, UnknownType, ExpKind, MALE))
		}
		else{
			stmts = stmts :+ Connect(NoInfo, WSubIndex(WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "data_in", UnknownType, FEMALE), i, UnknownType, UnknownFlow), WSubIndex(WRef(target(i).name, UnknownType, ExpKind, MALE), 0, UnknownType, MALE))
		}
	}
	var block = Block(stmts)
	module = module.asInstanceOf[Module].copy(body = block)
	var ret = left_module :+ module
	ret
}

  def add_stmts(modules: Seq[DefModule], target: ComponentName, width: Int): Seq[DefModule] = { 
	var module = modules.filter(_.name == target.module.name)(0)
	var left_module = modules.filter(_.name != target.module.name)
	var stmts = module.asInstanceOf[Module].body.asInstanceOf[Block].stmts
    val inside_io = BundleType(Vector(Field("data_in", Flip, UIntType(IntWidth(width)))))
    val ports = ArrayBuffer(Field("clock", Flip, ClockType), Field("reset", Flip, UIntType(IntWidth(1))), Field("io", Default, inside_io))
	val wref = WRef("controller", BundleType(ports), ExpKind, UNKNOWNGENDER)
	stmts = stmts :+ WDefInstance(NoInfo, "controller", "FaultController", UnknownType)
	stmts = stmts :+ Connect(NoInfo, WSubField(wref, "clock", ClockType, FEMALE), WRef("clock", ClockType, ExpKind, MALE))
	stmts = stmts :+ Connect(NoInfo, WSubField(wref, "reset", UIntType(IntWidth(1)), FEMALE), WRef("reset", UIntType(IntWidth(1)), ExpKind, MALE))
	if (input_type.tpe == "UIntType"){
	stmts = stmts :+ Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "data_in", UnknownType, FEMALE), WRef(target.name, UnknownType, ExpKind, MALE))}
	else{
	stmts = stmts :+ Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "data_in", UnknownType, FEMALE), WSubIndex(WRef(target.name, UnknownType, ExpKind, MALE), 0, UnknownType, MALE))
	}
	
	var block = Block(stmts)
	module = module.asInstanceOf[Module].copy(body = block)
	var ret = left_module :+ module
	ret
}

  def execute(state: CircuitState): CircuitState = {
	var annos = state.annotations.toList
	val ret = state.copy(circuit = run(state.circuit, annos))
	ret
  }
}
