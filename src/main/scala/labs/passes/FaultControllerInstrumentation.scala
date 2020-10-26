package labs.passes

import labs._
import chiffre.passes._
import chiffre.inject.Injector
import chiffre.util.removeZeroWidth
import chisel3.experimental.{annotate, ChiselAnnotation}
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

class FaultControllerInstrumentation() extends Transform {
  def inputForm: CircuitForm = HighForm
  def outputForm: CircuitForm = HighForm
  def run(c:Circuit, annos:List[Annotation]): Circuit ={
	var temp = c.modules
	var input_width = 0
	var faulty_width = 0
	var data_target = ""
	var number_of_fires = 0
	var affected_bits = List(1)
	annos.foreach{
		case FaultControllerUDAnnotation(target, dtarget, nfires, abits) =>{
			input_width = find_width(c.modules, target)
			data_target = dtarget
			number_of_fires = nfires
			affected_bits = abits
			temp = add_stmts(temp, target, input_width)
		}
		case FaultInjectionAnnotation(target, id, injector) =>{
			faulty_width = find_width(c.modules, target)
		}
	}
	if(input_width == 0){
		return c
	}
	//println("INFOOO")
	//println((input_width, data_target, number_of_fires, affected_bits, faulty_width))
	var elab = chisel3.Driver.toFirrtl(chisel3.Driver.elaborate(() => new FaultController(input_width, data_target, number_of_fires, affected_bits, faulty_width)))
	//println("ELAB")
	//println(elab)
	elab = ToWorkingIR.run(elab)
	temp = temp ++ elab.modules
	//println("TEMP")
	//println(temp)
	//val k = c.copy(modules = c.modules.map(splitM(_)))
	val k = c.copy(modules = temp)
	//println("END PASS")
	//println(k)
	k
}

  def find_width(modules: Seq[DefModule], target: ComponentName): Int = { // if mapport then print then return m will cause problems
	var module = modules.filter(_.name == target.module.name)
	println("FINDING")
	print(module)
	module(0).asInstanceOf[Module].body.asInstanceOf[Block].foreachStmt{
		case DefWire(info, name, tpe) => {
			if(name == target.name && tpe.isInstanceOf[UIntType]){
				return tpe.asInstanceOf[UIntType].width.asInstanceOf[IntWidth].width.toInt
			}
			else if(name == target.name && tpe.isInstanceOf[VectorType]) {
                input_type.tpe = "VectorType"
                return tpe.asInstanceOf[VectorType].tpe.asInstanceOf[UIntType].width.asInstanceOf[IntWidth].width.toInt // might not be correct
            }
		}
		
		case DefRegister(info, name, tpe, clock, reset, init) => {
			if(name == target.name && tpe.isInstanceOf[UIntType]){
				return tpe.asInstanceOf[UIntType].width.asInstanceOf[IntWidth].width.toInt 
			}
			else if(name == target.name && tpe.isInstanceOf[VectorType]) {
				input_type.tpe = "VectorType"	
				return tpe.asInstanceOf[VectorType].tpe.asInstanceOf[UIntType].width.asInstanceOf[IntWidth].width.toInt // might not be correct
			}
			else{
				throw new Exception(tpe.getClass + "not yet implemented")
			}
		}
		case DefNode(info, name, value) => {
			if(name == target.name)
				return value.tpe.asInstanceOf[UIntType].width.asInstanceOf[IntWidth].width.toInt
			
		}
		case other =>{println("OTHER"); println(other)}
	}
	throw new Exception(target + " not found")
}

  def add_stmts(modules: Seq[DefModule], target: ComponentName, width: Int): Seq[DefModule] = { // if mapport then print then return m will cause problems
	//println("FIND")
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
	
	//WRef(target.name, UIntType(IntWidth(width)), ExpKind, MALE)
	var block = Block(stmts)
	module = module.asInstanceOf[Module].copy(body = block)
	var ret = left_module :+ module
	//println("RET")
	ret
}

  def execute(state: CircuitState): CircuitState = {
	var annos = state.annotations.toList.filter(x => (x.isInstanceOf[FaultControllerUDAnnotation] || x.isInstanceOf[FaultInjectionAnnotation]))
	val ret = state.copy(circuit = run(state.circuit, annos))
	println("CTRL DONE")
	println(ret)
	ret
  }
}

