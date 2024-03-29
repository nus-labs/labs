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

class DMR {
  def run(c:Circuit, component:ComponentName, feedback: Int): Circuit ={
	var modules = c.modules
	modules = modules.map(traverseM(_, component, feedback))

	var k = c.copy(modules=modules)
	var size_lst = ArrayBuffer[Int]()

	output_size.arr.foreach{
		case (UIntType(width), whether_add_feedback) => { // Can rely on ordering from set?
			var bitwidth = width.asInstanceOf[IntWidth].width.toInt
			if(!size_lst.contains(bitwidth)){
				val feedback_determined = if (whether_add_feedback) feedback else 0
				k = add_modules(k, bitwidth, feedback_determined)
				size_lst = size_lst :+ bitwidth
			}
		}
		case p => throw new Exception("There is something wrong with the type " + p.getClass)
	}

	var new_modules = if(output_size.arr.size == 0) k.modules else remove_dup_modules(k.modules)
	k.copy(modules=new_modules)
}

  def traverseM(m: DefModule, component: ComponentName, feedback: Int): DefModule = { 
	if( component.module.name == m.name ){
		if( component.name == "None" ){ // whole module
			var io_lst = List[String]()
			var input_lst = List[(String, Type)]()
			var output_lst = List[(String, Type)]()
			m.foreachPort{
				case Port(_, name, Input, tpe) => input_lst = input_lst :+ (name, tpe)
				case Port(_, name, Output, tpe) => output_lst = output_lst :+ (name, tpe)
			}
			var ports = m.ports
			ports = ports :+ Port(NoInfo, "detect", Output, UIntType(IntWidth(1)))
			var mm = m.asInstanceOf[Module].copy(ports=ports)
			mm = mm.mapStmt(copyComponents(_, input_lst, output_lst)).asInstanceOf[Module] // expect Block
			return mm
		}
		else{ // specific component in a module
			var ports = m.ports
			ports = ports :+ Port(NoInfo, "detect", Output, UIntType(IntWidth(1)))
			var mm = m.asInstanceOf[Module].copy(ports=ports)
			mm = add_component(mm, component, feedback)
			return mm
		}
	}
	else{
		return m
	}
  }

  def copyComponents(components: Statement, input_lst: List[(String, Type)], output_lst: List[(String, Type)]): Statement = {
	var temp_block = components.asInstanceOf[Block]
	var added_stmts = ArrayBuffer[Statement]()
	var temp = ArrayBuffer[Statement]()
	var io_lst = input_lst.map(_._1) ++ output_lst.map(_._1)
	var feed_to_output_map = scala.collection.mutable.Map[String, ArrayBuffer[Expression]]()
	var from_input_map = scala.collection.mutable.Map[Expression, Expression]()
	var del_lst = ArrayBuffer[Statement]()
	var i = 0
	temp_block.stmts.foreach{ // TODO: Is it enough for low FIRRTL?
		case DefWire(info, name, tpe) => {
			added_stmts += DefWire(info, newNameS(name, i), tpe)
		}
		case DefRegister(info, name, tpe, doprim, a, init) => {
			added_stmts += DefRegister(info, newNameS(name, i), tpe, doprim, a, transform_expr(init, io_lst, i) )
		}
		case DefInstance(info, name, module) => {
			added_stmts += DefInstance(info, newNameS(name, i), module)
		}
		case WDefInstance(info, name, module, tpe) => {
			added_stmts += WDefInstance(info, newNameS(name, i), module, tpe)
		}
		case DefNode(info, ref1, ref2) => { // ref1 is a string
			val ref1_ft = if (io_lst.contains(ref1)) ref1 else newNameS(ref1, i)
			if(ref2.isInstanceOf[WRef] || ref2.isInstanceOf[DoPrim] || ref2.isInstanceOf[Mux]){
				val ref2_ft = transform_expr(ref2, io_lst, i)
				if(output_lst.map(_._1).contains(ref1)){
					feed_to_output_map = add_output_relationship(feed_to_output_map, ref1, ref2, ref2_ft)
					del_lst += DefNode(info, ref1, ref2)
				}
				else
					added_stmts += DefNode(info, ref1_ft, ref2_ft)
			}	
			else{
				// TODO: Is this too restricted?
				// Can the if above be removed?
				added_stmts += DefNode(info, ref1_ft, ref2)
			}
		}
		case Connect(info, ref1, ref2) => {
			val ref1_name = getName(ref1)
			val ref1_ft = transform_expr(ref1, io_lst, i)

			if(ref2.isInstanceOf[WRef] || ref2.isInstanceOf[DoPrim] || ref2.isInstanceOf[Mux] || ref2.isInstanceOf[WSubField]){
				val ref2_ft = transform_expr(ref2, io_lst, i)
				if(output_lst.map(_._1).contains(ref1_name)){
					feed_to_output_map = add_output_relationship(feed_to_output_map, ref1_name, ref2, ref2_ft)
					del_lst += Connect(info, ref1, ref2)
				}
				else
					added_stmts += Connect(info, ref1_ft, ref2_ft)
			}
			else if(ref2.isInstanceOf[UIntLiteral]){
				if(!output_lst.map(_._1).contains(ref1_name)) added_stmts += Connect(info, ref1_ft, ref2)
				else throw new Exception("This line should not be executed")
			}
			else{
				throw new Exception(ref2.getClass + " not yet implemented")
			}
		}
		case IsInvalid(info, ref1) =>{
			if(!output_lst.map(_._1).contains(getName(ref1))){
				val ref1_ft = transform_expr(ref1, io_lst, i)
				added_stmts += IsInvalid(info, ref1_ft)
			}
			else throw new Exception("IsInvalid not yet implemented")
		}
		case other => {
			throw new Exception(other.getClass + " not yet implemented")
		}
	}

	var tempp = temp_block
	added_stmts = add_detector(added_stmts, feed_to_output_map)
	added_stmts = add_preventer(added_stmts, feed_to_output_map)
	added_stmts = (temp_block.stmts.toBuffer ++ added_stmts).asInstanceOf[ArrayBuffer[Statement]]
	temp_block.copy(stmts=added_stmts)
  }

  def transform_expr(expr: Expression, io_lst: List[String], i: Int): Expression = {
  	if(expr.isInstanceOf[WRef]){
		val expr_name = expr.serialize
		val expr_name_ft = if (io_lst.contains(expr_name)) expr_name else newNameS(expr_name, i) // Keep port name otherwise give new name
		val ret = expr.asInstanceOf[WRef].copy(name=expr_name_ft)
		return ret 
	}
	else if(expr.isInstanceOf[Mux] || expr.isInstanceOf[ValidIf] || expr.isInstanceOf[DoPrim] || expr.isInstanceOf[UIntLiteral] || expr.isInstanceOf[WSubField]){
		expr.mapExpr(transform_expr(_, io_lst, i))
	}
	else{
		throw new Exception(expr.getClass + " not yet implemented")
	}
  }

  def add_modules(k: Circuit, bitwidth: Int, feedback: Int): Circuit = {
	var design = chisel3.Driver.toFirrtl(chisel3.Driver.elaborate(() => new Detector(bitwidth)))
	var modules = Seq[DefModule]()

	var module = design.modules.filter(_.asInstanceOf[Module].name == "Detector").last 	
	var new_name = module.asInstanceOf[Module].name + bitwidth.toString + "_" + feedback.toString
	module = module.asInstanceOf[Module].copy(name=new_name)
	modules = design.modules.filter(_.asInstanceOf[Module].name != "Detector") :+ module.asInstanceOf[Module] 			
		
	var preventer = chisel3.Driver.toFirrtl(chisel3.Driver.elaborate(() => new Preventer(bitwidth, feedback)))
	module = preventer.modules.filter(_.asInstanceOf[Module].name == "Preventer").last
	new_name = module.asInstanceOf[Module].name + bitwidth.toString + "_" + feedback.toString
	module = module.asInstanceOf[Module].copy(name=new_name)
	modules = modules :+ module.asInstanceOf[Module]
	modules = preventer.modules.filter(_.asInstanceOf[Module].name != "Preventer") ++ modules  

	design = design.copy(modules=modules)
	design = ToWorkingIR.run(design)
	var kk = k.copy(modules = k.modules ++ design.modules)
	kk
  }

  def add_component(m: Module, component: ComponentName, feedback: Int): Module = {
	var name = component.name
	var stmts = m.asInstanceOf[Module].body.asInstanceOf[Block].stmts.asInstanceOf[ArrayBuffer[Statement]]
	var allregs = stmts.filter(_.isInstanceOf[DefRegister])
	var allwires = stmts.filter(_.isInstanceOf[DefWire])
	var allregs_names = allregs.map(_.asInstanceOf[DefRegister].name)
	var allwires_names = allwires.map(_.asInstanceOf[DefWire].name)
	var target_component = stmts(0) // just to initialise
	if (allregs_names.contains(component.name)){
		target_component = allregs.filter(_.asInstanceOf[DefRegister].name == component.name).last
		target_component = target_component.asInstanceOf[DefRegister].copy(name = newNameS(target_component.asInstanceOf[DefRegister].name, 0))
		stmts += target_component
		stmts = add_preventer_component(stmts, component, target_component.asInstanceOf[DefRegister].tpe, feedback)
		stmts = add_detector_component(stmts, component, target_component.asInstanceOf[DefRegister].tpe, feedback)
		output_size.arr += ((target_component.asInstanceOf[DefRegister].tpe, true)) // always true
	}
	else if (allwires_names.contains(component.name)){
		target_component = allwires.filter(_.asInstanceOf[DefWire].name == component.name).last
		target_component = target_component.asInstanceOf[DefWire].copy(name = newNameS(target_component.asInstanceOf[DefWire].name, 0))
		stmts += target_component
		stmts = add_detector_component(stmts, component, target_component.asInstanceOf[DefWire].tpe, feedback)
		output_size.arr += ((target_component.asInstanceOf[DefWire].tpe, true)) // always true
	}
	else{
		throw new Exception(component.name + " not found")
	}
	var mm = m.asInstanceOf[Module].copy(body = Block(stmts))
	mm
  }

  def add_preventer(added_stmts: ArrayBuffer[Statement], feed_to_output_map: scala.collection.mutable.Map[String, ArrayBuffer[Expression]]): ArrayBuffer[Statement] = {
	var count = 0
	var all_detect_signals = ArrayBuffer[Expression]()
	feed_to_output_map.foreach{
		mapping =>{
			val name = "p" + count.toString
			val output = mapping._1
			val feed_to_output = mapping._2
			val size = feed_to_output(0).tpe
			val bitwidth = size.asInstanceOf[UIntType].width.asInstanceOf[IntWidth].width.toInt
			val inside_io = BundleType(Vector(Field("in", Flip, size), Field("out", Default, size), Field("detect", Flip, UIntType(IntWidth(1)))))
			val ports = ArrayBuffer(Field("clock", Flip, ClockType), Field("reset", Flip, UIntType(IntWidth(1))), Field("io", Default, inside_io))
			val wref = WRef(name, BundleType(ports), ExpKind, UNKNOWNGENDER)
			added_stmts += WDefInstance(NoInfo, name, "Preventer" + bitwidth.toString, UnknownType)
			added_stmts += Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "in", size, FEMALE), feed_to_output(0))
			added_stmts += Connect(NoInfo, WRef(output, size, ExpKind, FEMALE), WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "out", size, FEMALE))
			added_stmts += Connect(NoInfo, WSubField(wref, "clock", ClockType, FEMALE), WRef("clock", ClockType, ExpKind, MALE))
			added_stmts += Connect(NoInfo, WSubField(wref, "reset", UIntType(IntWidth(1)), FEMALE), WRef("reset", UIntType(IntWidth(1)), ExpKind, MALE))
			added_stmts += Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "detect", UIntType(IntWidth(1)), FEMALE), WRef("detect", UIntType(IntWidth(1)), ExpKind, MALE))
			count += 1
		}	
	}
	added_stmts
  }

  def add_detector(added_stmts: ArrayBuffer[Statement], feed_to_output_map: scala.collection.mutable.Map[String, ArrayBuffer[Expression]]): ArrayBuffer[Statement] = {
	var count = 0
	var all_detect_signals = ArrayBuffer[Expression]()
	feed_to_output_map.foreach{
		mapping =>{
			val name = "d" + count.toString
			val output = mapping._1
			val feed_to_output = mapping._2
			val size = feed_to_output(0).tpe
			val bitwidth = size.asInstanceOf[UIntType].width.asInstanceOf[IntWidth].width.toInt
			val inside_io = BundleType(Vector(Field("in1", Flip, size), Field("in2", Flip, size), Field("detect", Default, UIntType(IntWidth(1)))))
			val ports = ArrayBuffer(Field("clock", Flip, ClockType), Field("reset", Flip, UIntType(IntWidth(1))), Field("io", Default, inside_io))
			val wref = WRef(name, BundleType(ports), ExpKind, UNKNOWNGENDER)
			added_stmts += WDefInstance(NoInfo, name, "Detector" + bitwidth.toString, UnknownType)
			added_stmts += Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "in1", size, FEMALE), feed_to_output(0))
			added_stmts += Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "in2", size, FEMALE), feed_to_output(1))
			added_stmts += Connect(NoInfo, WSubField(wref, "clock", ClockType, FEMALE), WRef("clock", ClockType, ExpKind, MALE))
			added_stmts += Connect(NoInfo, WSubField(wref, "reset", UIntType(IntWidth(1)), FEMALE), WRef("reset", UIntType(IntWidth(1)), ExpKind, MALE))
			var each_detect_signal = WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "detect", UIntType(IntWidth(1)), FEMALE)
			all_detect_signals = all_detect_signals :+ each_detect_signal
			if (!output_size.arr.contains((size, feedback_target.arr.contains(output)))){
				output_size.arr += ((size, feedback_target.arr.contains(output)))
			}
			count += 1
		}	
	}
	var detect_output_signal = WRef("detect", UIntType(IntWidth(1)), ExpKind, FEMALE)
	var detect_expr = if (all_detect_signals.size == 1) all_detect_signals(0) else {
		var arr = DoPrim(PrimOps.Or, ArrayBuffer(all_detect_signals(0), all_detect_signals(1)), ArrayBuffer(), all_detect_signals(0).tpe)
		all_detect_signals.drop(2).foreach{
			signal =>{
				arr = DoPrim(PrimOps.Or, ArrayBuffer(signal, arr), ArrayBuffer(), signal.tpe)
			}
		}
		arr
	}
	added_stmts += Connect(NoInfo, detect_output_signal, detect_expr)
	added_stmts
  }

  def convert_all_targets(expr: Expression, component: ComponentName, preventer_name: String, preventer_size: Type, preventer_wref: WRef, preventer_inside_io: BundleType): Expression = {
	var output_port_from_preventer = WSubField(WSubField(preventer_wref, "io", preventer_inside_io, UNKNOWNGENDER), "out", preventer_size, MALE)
    if(expr.isInstanceOf[WRef]){
        val expr_name = expr.serialize
		if(expr_name == component.name){
			return output_port_from_preventer
		}
		else{
			return expr
		}
    }
    else if(expr.isInstanceOf[Mux] || expr.isInstanceOf[ValidIf] || expr.isInstanceOf[DoPrim] || expr.isInstanceOf[UIntLiteral] || expr.isInstanceOf[WSubField]){
        return expr.mapExpr(convert_all_targets(_, component, preventer_name, preventer_size, preventer_wref, preventer_inside_io))
    }
    else{
        throw new Exception(expr.getClass + " not yet implemented")
    }
  }

  def convert_all_connected_ports(added_stmts: ArrayBuffer[Statement], component: ComponentName, preventer_name: String, preventer_size: Type, preventer_wref: WRef, preventer_inside_io: BundleType): ArrayBuffer[Statement] ={
	var arr = ArrayBuffer[Statement]()
	added_stmts.map({
		case DefNode(info, ref1, ref2) => {
			DefNode(info, ref1, convert_all_targets(ref2, component, preventer_name, preventer_size, preventer_wref, preventer_inside_io))
		}
		case Connect(info, ref1, ref2) => {
			Connect(info, ref1, convert_all_targets(ref2, component, preventer_name, preventer_size, preventer_wref, preventer_inside_io))
		}
		case other => {other}
	})
  }

  def add_preventer_component(stmts: ArrayBuffer[Statement], component: ComponentName, tpe: Type, feedback: Int): ArrayBuffer[Statement] = {
	var count = 0
	var all_detect_signals = ArrayBuffer[Expression]()
	val name = "p" + count.toString
	val feed_to_output = component.name
	val size = tpe
	val bitwidth = size.asInstanceOf[UIntType].width.asInstanceOf[IntWidth].width.toInt
	val inside_io = BundleType(Vector(Field("in", Flip, size), Field("out", Default, size), Field("detect", Flip, UIntType(IntWidth(1)))))
	val ports = ArrayBuffer(Field("clock", Flip, ClockType), Field("reset", Flip, UIntType(IntWidth(1))), Field("io", Default, inside_io))
	val wref = WRef(name, BundleType(ports), ExpKind, UNKNOWNGENDER)
	var added_stmts = stmts 
	added_stmts.prepend(WDefInstance(NoInfo, name, "Preventer" + bitwidth.toString + "_" + feedback.toString, UnknownType))
	added_stmts = convert_all_connected_ports(added_stmts, component, name, size, wref, inside_io)
	added_stmts += Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "in", size, FEMALE), WRef(feed_to_output, tpe, ExpKind, MALE))
	added_stmts += Connect(NoInfo, WSubField(wref, "clock", ClockType, FEMALE), WRef("clock", ClockType, ExpKind, MALE))
	added_stmts += Connect(NoInfo, WSubField(wref, "reset", UIntType(IntWidth(1)), FEMALE), WRef("reset", UIntType(IntWidth(1)), ExpKind, MALE))
	added_stmts += Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "detect", UIntType(IntWidth(1)), FEMALE), WRef("detect", UIntType(IntWidth(1)), ExpKind, MALE))
	count += 1
	added_stmts
  }

  def add_detector_component(stmts: ArrayBuffer[Statement], component: ComponentName, tpe: Type, feedback: Int): ArrayBuffer[Statement] = {
	val name = "dc" + redundancy_number.count.toString
	redundancy_number.count += 1
	val bitwidth = tpe.asInstanceOf[UIntType].width.asInstanceOf[IntWidth].width.toInt
	val size = tpe
	val inside_io = BundleType(Vector(Field("in1", Flip, size), Field("in2", Flip, size), Field("detect", Default, UIntType(IntWidth(1)))))
	val ports = ArrayBuffer(Field("clock", Flip, ClockType), Field("reset", Flip, UIntType(IntWidth(1))), Field("io", Default, inside_io))
	val wref = WRef(name, BundleType(ports), ExpKind, UNKNOWNGENDER)
	var added_stmts = stmts :+ WDefInstance(NoInfo, name, "Detector" + bitwidth.toString + "_" + feedback.toString, UnknownType)
	added_stmts = added_stmts :+ Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "in1", size, FEMALE), WRef(component.name, tpe, ExpKind, MALE))
	added_stmts = added_stmts :+ Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "in2", size, FEMALE), WRef(newNameS(component.name, 0), tpe, ExpKind, MALE))
	added_stmts = added_stmts :+ Connect(NoInfo, WRef("detect", UIntType(IntWidth(1)), ExpKind, FEMALE), WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "detect", size, FEMALE))
	added_stmts = added_stmts :+ Connect(NoInfo, WSubField(wref, "clock", ClockType, FEMALE), WRef("clock", ClockType, ExpKind, MALE))
	added_stmts = added_stmts :+ Connect(NoInfo, WSubField(wref, "reset", UIntType(IntWidth(1)), FEMALE), WRef("reset", UIntType(IntWidth(1)), ExpKind, MALE))
	added_stmts		 
  }


}

