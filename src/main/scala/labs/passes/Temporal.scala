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

class Temporal {
  def run(c:Circuit, component:ComponentName, feedback: Int): Circuit ={
    var modules = c.modules	
	modules = modules.map(splitM(_,component))
	
	var k = c.copy(modules=modules)
	var size_lst = ArrayBuffer[(Int, Int)]()

	output_size.arr.foreach{
		case (UIntType(width), whether_add_feedback) => { // Can rely on ordering from set?
			var bitwidth = width.asInstanceOf[IntWidth].width.toInt
			println("OUTPUT")
			println(width, whether_add_feedback)
			println(size_lst)
			val feedback_determined = if (whether_add_feedback) feedback else 0
			if(!size_lst.contains((bitwidth, feedback_determined))){
				k = add_modules(k, bitwidth, feedback_determined)
				size_lst = size_lst :+ ((bitwidth, feedback_determined))
			}
		}
		case p => throw new Exception("There is something wrong with the type " + p.getClass)
	}
	var new_modules = if(output_size.arr.size == 0) k.modules else remove_dup_modules(k.modules)
	k.copy(modules=new_modules)
}

  def add_modules(k: Circuit, bitwidth: Int, feedback: Int): Circuit = {
		var design = {
			var arr = ArrayBuffer[DefModule]()
			var inputreg = chisel3.Driver.toFirrtl(chisel3.Driver.elaborate(() => new InputReg(bitwidth)))
			var outputreg = chisel3.Driver.toFirrtl(chisel3.Driver.elaborate(() => new OutputReg(bitwidth, feedback)))
			arr = arr ++ inputreg.modules ++ outputreg.modules
			Circuit(NoInfo, arr, "temporal")
		}
		var modules = Seq[DefModule]()
		var detector = design.modules.filter(x => (x.asInstanceOf[Module].name == "Detector" || x.asInstanceOf[Module].name == "Preventer")) // part of outreg
		modules = design.modules.filter(x => (x.asInstanceOf[Module].name != "Detector" || x.asInstanceOf[Module].name != "Preventer"))
		modules = modules.map(_.asInstanceOf[Module].mapStmt(_.mapStmt
		{
			case DefInstance(info, name, module) =>{		
				if (module == "Detector"){
					val ret = DefInstance(info, name, module + bitwidth.toString)
					ret 
				}
				else if (module == "Preventer"){
					val ret = DefInstance(info, name, module + bitwidth.toString + "f" + feedback.toString)
					ret
				}
				else DefInstance(info, name, module + bitwidth.toString)
			}
			case other =>{other}
		}))
		modules = modules ++ detector
		modules = modules.map{
			case Module(info, name, ports, body) => {
				if(name == "Preventer") Module(info, name + bitwidth.toString + "f" + feedback.toString, ports, body)
				else Module(info, name + bitwidth.toString, ports, body)
			}
			case other => throw new Exception("There should be something wrong")
		}
		var templst = k.modules.map(_.name)
		if(!templst.contains("CtrlReg")){
			var ctrlreg = chisel3.Driver.toFirrtl(chisel3.Driver.elaborate(() => new CtrlReg))
			modules = modules ++ ctrlreg.modules
		}
			
		design = design.copy(modules=modules)
		design = ToWorkingIR.run(design)
		var kk = k.copy(modules = k.modules ++ design.modules)
		kk
  }

  def splitM(m: DefModule, component: ComponentName): DefModule = { 
	if( component.module.name == m.name ){
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
	else{
		return m
	}
  }

  def modify_doprim_temporal(expr: Any, input_lst: List[(String, Type)], output_lst: List[(String, Type)]): Any = {
  	if(expr.isInstanceOf[WRef]){
		val expr_name = expr.asInstanceOf[WRef].name
		val expr_name_temporal = if (input_lst.map(_._1).contains(expr_name) || output_lst.map(_._1).contains(expr_name)) newNameT(expr_name) else expr_name
		val ret = expr.asInstanceOf[WRef].copy(name=expr_name_temporal).asInstanceOf[Expression]
		return ret 
	}
	else if(expr.isInstanceOf[WSubField]){
		return expr
	}
	else if(expr.isInstanceOf[UIntLiteral]){
		return expr
	}
	else if(expr.isInstanceOf[Mux]){
		return expr.asInstanceOf[Mux].mapExpr{
			ref => {
				modify_doprim_temporal(ref, input_lst, output_lst).asInstanceOf[Expression]
			}
		}
	}
	else if(expr.isInstanceOf[DoPrim]){
		var temp = expr.asInstanceOf[DoPrim]
		var temp2 = temp.args.map(modify_doprim_temporal(_, input_lst, output_lst))
		val ret = temp.copy(args=temp2.asInstanceOf[ArrayBuffer[Expression]])
		return ret
	}
	else if(expr.isInstanceOf[ValidIf]){
		val ret = expr.asInstanceOf[ValidIf].mapExpr{
			case exp => modify_doprim_temporal(exp, input_lst, output_lst).asInstanceOf[Expression]
		}
		return ret
	}
	else{
		throw new Exception(expr.getClass + " not yet implemented")
	}
	
  }

  def getName(ref: Expression): String = ref.serialize
  

  def add_output_relationship(feed_to_output_map: scala.collection.mutable.Map[String, ArrayBuffer[Expression]], ref1: String, ref2: Expression, ref2_ft: Expression): scala.collection.mutable.Map[String, ArrayBuffer[Expression]] = {
	if (feed_to_output_map.contains(ref1)) feed_to_output_map(ref1) += ref2_ft
	else feed_to_output_map += ref1 -> ArrayBuffer(ref2, ref2_ft)
	feed_to_output_map
  }

  def add_input_relationship(from_input_map: scala.collection.mutable.Map[Expression, Expression], ref1: Expression, ref2: Expression): scala.collection.mutable.Map[Expression, Expression] = {
	from_input_map += ref2 -> ref1
	from_input_map
  }

  def copyComponents(components: Statement, input_lst: List[(String, Type)], output_lst: List[(String, Type)]): Statement = {
	var temp_block : Block = components.asInstanceOf[Block]
	var added_stmts = ArrayBuffer[Statement]()
	var temp = ArrayBuffer[Statement]()
	var io_lst = input_lst.map(_._1) ++ output_lst.map(_._1)
	var feed_to_output_map = scala.collection.mutable.Map[String, ArrayBuffer[Expression]]()
	var from_input_map = scala.collection.mutable.Map[Expression, Expression]()
	var del_lst = ArrayBuffer[Statement]()

	temp = temp_block.stmts.to[ArrayBuffer].map{
		case DefWire(info, name, tpe) => {
			DefWire(info, name, tpe)
		}
		case DefRegister(info, name, tpe, doprim, a, init) => {
			DefRegister(info, name, tpe, doprim, a, init)
		}
		case DefInstance(info, name, module) => {
			DefInstance(info, name, module)
		}
		case WDefInstance(info, name, module, tpe) => {
			WDefInstance(info, name, module, tpe)
		}
		case IsInvalid(info, ref1) => {
			IsInvalid(info, ref1)
		}
		case DefNode(info, ref1, ref2) => {
			DefNode(info, ref1, modify_doprim_temporal(ref2, input_lst, output_lst).asInstanceOf[Expression])
		}
		case Connect(info, ref1, ref2) => {
			Connect(info, modify_doprim_temporal(ref1, input_lst, output_lst).asInstanceOf[Expression], modify_doprim_temporal(ref2, input_lst, output_lst).asInstanceOf[Expression])
		}
		case other => throw new Exception(other.getClass + " not yet implemented")
	}

	var tempp = temp_block
	added_stmts = add_temporal(added_stmts, input_lst, output_lst)
	
	input_lst.foreach{
		case ("clock", tpe) => ;
		case ("reset", tpe) => ;
		case (name, tpe) => {
			added_stmts prepend DefWire(NoInfo, newNameT(name), tpe)
		}
	}
	output_lst.foreach{
		case ("clock", tpe) => ;
		case ("reset", tpe) => ;
		case (name, tpe) => {
			added_stmts prepend DefWire(NoInfo, newNameT(name), tpe)
		}
	}
	temp_block.copy(stmts=added_stmts ++ temp)
  }

  def add_temporal(added_stmts: ArrayBuffer[Statement], input_lst: List[(String, Type)], output_lst: List[(String, Type)]): ArrayBuffer[Statement] = {
	var count = 0
	var voter_count = 0
	var all_detect_signals = ArrayBuffer[Expression]()

	val ctrl_name = "c"
	val ctrl_size = UIntType(IntWidth(2))
	val ctrl_ports = ArrayBuffer(Field("clock", Flip, ClockType), Field("reset", Flip, UIntType(IntWidth(1))), Field("io", Default, BundleType(Vector(Field("ready", Flip, UIntType(IntWidth(1))), Field("start", Flip, UIntType(IntWidth(1))), Field("finished", Default, UIntType(IntWidth(1))), Field("ctrl", Default, ctrl_size)))))
	val ctrl_inside_io = BundleType(Vector(Field("ready", Flip, UIntType(IntWidth(1))), Field("start", Flip, UIntType(IntWidth(1))), Field("finished", Default, UIntType(IntWidth(1))), Field("ctrl", Default, ctrl_size)))
	val ctrl_wref = WRef(ctrl_name, BundleType(ctrl_ports), ExpKind, UNKNOWNGENDER)
	added_stmts += WDefInstance(NoInfo, ctrl_name, "CtrlReg", UnknownType)	
	added_stmts += Connect(NoInfo, WSubField(ctrl_wref, "clock", ClockType, FEMALE), WRef("clock", ClockType, ExpKind, MALE))
	added_stmts += Connect(NoInfo, WSubField(ctrl_wref, "reset", UIntType(IntWidth(1)), FEMALE), WRef("reset", UIntType(IntWidth(1)), ExpKind, MALE))
	added_stmts += Connect(NoInfo, WSubField(WSubField(ctrl_wref, "io", ctrl_inside_io, UNKNOWNGENDER), "ready", UIntType(IntWidth(1)), FEMALE), WRef(newNameT(signals.ready_signal), UIntType(IntWidth(1)), ExpKind, UNKNOWNGENDER))
	added_stmts += Connect(NoInfo, WSubField(WSubField(ctrl_wref, "io", ctrl_inside_io, UNKNOWNGENDER), "start", UIntType(IntWidth(1)), FEMALE), WRef(newNameT(signals.start_signal), UIntType(IntWidth(1)), ExpKind, UNKNOWNGENDER))

	added_stmts += Connect(NoInfo, WRef(signals.ready_signal, UIntType(IntWidth(1)), ExpKind, UNKNOWNGENDER), WSubField(WSubField(ctrl_wref, "io", ctrl_inside_io, UNKNOWNGENDER), "finished", UIntType(IntWidth(1)), MALE) )
	
	// need to connect ctrl!
	input_lst.filter(input => input._1 != "clock").filter(input => input._1 != "reset").foreach{
		input =>{
			val name = "ipr" + count.toString
			val target = input._1
			val size = input._2
			val bitwidth = size.asInstanceOf[UIntType].width.asInstanceOf[IntWidth].width.toInt
			val ports = ArrayBuffer(Field("clock", Flip, ClockType), Field("reset", Flip, UIntType(IntWidth(1))), Field("io", Default, BundleType(Vector(Field("in", Flip, size), Field("out", Default, size), Field("ctrl", Flip, UIntType(IntWidth(2)))))))
			val inside_io = BundleType(Vector(Field("in", Flip, size), Field("out", Default, size), Field("ctrl", Flip, ctrl_size)))
			val wref = WRef(name, BundleType(ports), ExpKind, UNKNOWNGENDER)
			added_stmts += WDefInstance(NoInfo, name, "InputReg" + bitwidth.toString, UnknownType)
			added_stmts += Connect(NoInfo, WSubField(wref, "clock", ClockType, FEMALE), WRef("clock", ClockType, ExpKind, MALE))
			added_stmts += Connect(NoInfo, WSubField(wref, "reset", UIntType(IntWidth(1)), FEMALE), WRef("reset", UIntType(IntWidth(1)), ExpKind, MALE))
			added_stmts += Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "in", size, FEMALE), WRef(target, size, ExpKind, UNKNOWNGENDER))
			added_stmts += Connect(NoInfo, WRef(newNameT(target), size, ExpKind, UNKNOWNGENDER), WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "out", size, UNKNOWNGENDER))
			added_stmts += Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "ctrl", ctrl_size, FEMALE), WSubField(WSubField(ctrl_wref, "io", ctrl_inside_io, UNKNOWNGENDER), "ctrl", ctrl_size, MALE))
			added_stmts += Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "ready", UIntType(IntWidth(1)), FEMALE), WRef(newNameT(signals.ready_signal), UIntType(IntWidth(1)), ExpKind, UNKNOWNGENDER))

			if (!output_size.arr.contains((size, false))){
				output_size.arr += ((size, false))
			}
			count += 1
		  }
	}
	
	count = 0

	output_lst.foreach{
		output =>{
			if(output._1 != signals.ready_signal){
			val name = "opr" + count.toString
			val target = output._1
			val size = output._2
			val bitwidth = size.asInstanceOf[UIntType].width.asInstanceOf[IntWidth].width.toInt
			val ports = ArrayBuffer(Field("clock", Flip, ClockType), Field("reset", Flip, UIntType(IntWidth(1))), Field("io", Default, BundleType(Vector(Field("in", Flip, size), Field("ready", Flip, UIntType(IntWidth(1))), Field("out", Default, size), Field("ctrl", Flip, UIntType(IntWidth(2))), Field("detect", Default, UIntType(IntWidth(1)))))))
			val inside_io = BundleType(Vector(Field("in", Flip, size), Field("ready", Flip, UIntType(IntWidth(1))), Field("out", Default, size), Field("ctrl", Flip, UIntType(IntWidth(2))), Field("detect", Default, UIntType(IntWidth(1)))))
			val wref = WRef(name, BundleType(ports), ExpKind, UNKNOWNGENDER)
			added_stmts += WDefInstance(NoInfo, name, "OutputReg" + bitwidth.toString, UnknownType)
			added_stmts += Connect(NoInfo, WSubField(wref, "clock", ClockType, FEMALE), WRef("clock", ClockType, ExpKind, MALE))
			added_stmts += Connect(NoInfo, WSubField(wref, "reset", UIntType(IntWidth(1)), FEMALE), WRef("reset", UIntType(IntWidth(1)), ExpKind, MALE))
			added_stmts += Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "in", size, FEMALE), WRef(newNameT(target), size, ExpKind, UNKNOWNGENDER))
			added_stmts += Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "ready", UIntType(IntWidth(1)), FEMALE), WRef(newNameT(signals.ready_signal), UIntType(IntWidth(1)), ExpKind, UNKNOWNGENDER))
			added_stmts += Connect(NoInfo, WRef(target, size, ExpKind, UNKNOWNGENDER), WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "out", size, MALE))
			added_stmts += Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "ctrl", ctrl_size, FEMALE), WSubField(WSubField(ctrl_wref, "io", ctrl_inside_io, UNKNOWNGENDER), "ctrl", ctrl_size, MALE))
			var each_detect_signal = WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "detect", UIntType(IntWidth(1)), FEMALE)
			all_detect_signals = all_detect_signals :+ each_detect_signal
			println(feedback_target.arr.contains(target), target)
			if (!output_size.arr.contains((size, feedback_target.arr.contains(target)))){
				println("ADDED", (size, feedback_target.arr.contains(target)))
				output_size.arr += ((size, feedback_target.arr.contains(target)))
			}
			count += 1	
			}
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
}

