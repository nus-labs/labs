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

class TMR {
  def run(c: Circuit, component: ComponentName, feedback: Int): Circuit ={
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

  def add_modules(k: Circuit, bitwidth: Int, feedback: Int): Circuit = {
	var design = chisel3.Driver.toFirrtl(chisel3.Driver.elaborate(() => new Voter(bitwidth, feedback))) 	
	var modules = Seq[DefModule]()
	var module = design.modules.filter(_.asInstanceOf[Module].name == "Voter").last
	var new_name = module.asInstanceOf[Module].name + bitwidth.toString + "_" + feedback.toString
	module = module.asInstanceOf[Module].copy(name=new_name)
	modules = design.modules.filter(_.asInstanceOf[Module].name != "Voter") :+ module.asInstanceOf[Module]
	
	design = design.copy(modules=modules)
	design = ToWorkingIR.run(design)
	var kk = k.copy(modules = k.modules ++ design.modules)
	kk
  }

  def is_input_output(expr: Expression, componentName: String): Boolean = {
	if(expr.isInstanceOf[WRef]){
		if(expr.asInstanceOf[WRef].name == componentName) return true
		else return false
	}
	else if(expr.isInstanceOf[DoPrim]){
		expr.foreachExpr( 
			x => if(is_input_output(x, componentName)) return true
		)
		return false
	}
	else{
		return false
	}
  }

  def traverseM(m: DefModule, component: ComponentName, feedback: Int): DefModule = { 
	if( component.module.name == m.name ){
		var input_lst = List[(String, Type)]()
		var output_lst = List[(String, Type)]()
		m.foreachPort{
			case Port(_, name, Input, tpe) => input_lst = input_lst :+ (name, tpe)
			case Port(_, name, Output, tpe) => output_lst = output_lst :+ (name, tpe)
		}
		if( component.name == "None" ){
			var mm = m.mapStmt(copyComponents(_, input_lst, output_lst, feedback)).asInstanceOf[Module]
			return mm
		}
		else{
			var m_stmts = m.asInstanceOf[Module].body.asInstanceOf[Block].stmts
			var input_stmt: Connect = null
			var output_stmt: Connect = null
			m_stmts.foreach{
				case Connect(info, ref1, ref2) =>{
					if(is_input_output(ref1, component.name)){
						input_stmt = Connect(info, ref1, ref2)
					}
					if(is_input_output(ref2, component.name)){
						output_stmt = Connect(info, ref1, ref2)
					}
				}
				case other => other
			}
			if(input_stmt == null) throw new Exception("The input port is not found")
			if(output_stmt == null) throw new Exception("The output port is not found")

			var mm = add_component(m.asInstanceOf[Module], component, input_stmt.expr, output_stmt.loc, feedback)
			mm.body.asInstanceOf[Block].stmts.foreach(x => {println(x.serialize)})
			return mm
		}
	}
	else{
		return m
	}
  }

  def add_component(m: Module, component: ComponentName, input_data: Expression, output_data: Expression, feedback: Int): Module = {
	var name = component.name
	var stmts = m.asInstanceOf[Module].body.asInstanceOf[Block].stmts.asInstanceOf[ArrayBuffer[Statement]]
	var allregs = stmts.filter(_.isInstanceOf[DefRegister])
	var allwires = stmts.filter(_.isInstanceOf[DefWire])
    var allregs_names = allregs.map(_.asInstanceOf[DefRegister].name)
    var allwires_names = allwires.map(_.asInstanceOf[DefWire].name)
    var target_component = stmts(0) // just to initialise
	if(allregs_names.contains(component.name)){	
		var reg = allregs.filter(_.asInstanceOf[DefRegister].name == component.name).last.asInstanceOf[DefRegister]
		var added_reg = reg.copy(name = newNameS(reg.name, 0))
		var added_reg2 = reg.copy(name = newNameS(reg.name, 1))
		var reg_instance = WRef(newNameS(component.name, 0), reg.tpe, ExpKind, UNKNOWNGENDER)
		var reg_instance2 = WRef(newNameS(component.name, 1), reg.tpe, ExpKind, UNKNOWNGENDER)
		var input_reg_instance = Connect(NoInfo, reg_instance, input_data)
		var input_reg_instance2 = Connect(NoInfo, reg_instance2, input_data)
		stmts = stmts :+ added_reg :+ added_reg2 :+ input_reg_instance :+ input_reg_instance2
		stmts = add_voter_component(stmts, component.name, reg.tpe, output_data, feedback)
		output_size.arr += ((reg.tpe, true))
	}
	else if (allwires_names.contains(component.name)){
        var wire = allwires.filter(_.asInstanceOf[DefRegister].name == component.name).last.asInstanceOf[DefWire]
        var added_wire = wire.copy(name = newNameS(wire.name, 0))
        var added_wire2 = wire.copy(name = newNameS(wire.name, 1))
        var wire_instance = WRef(newNameS(component.name, 0), wire.tpe, ExpKind, UNKNOWNGENDER)
        var wire_instance2 = WRef(newNameS(component.name, 1), wire.tpe, ExpKind, UNKNOWNGENDER)
        var input_wire_instance = Connect(NoInfo, wire_instance, input_data)
        var input_wire_instance2 = Connect(NoInfo, wire_instance2, input_data)
        stmts = stmts :+ added_wire :+ added_wire2 :+ input_wire_instance :+ input_wire_instance2
        stmts = add_voter_component(stmts, component.name, wire.tpe, output_data, feedback)
        output_size.arr += ((wire.tpe, true))

	}
	var mm = m.asInstanceOf[Module].copy(body = Block(stmts))
	mm
  }

  def transform_expr(expr: Expression, io_lst: List[String], i: Int): Expression = {
  	if(expr.isInstanceOf[WRef]){
		val expr_name = expr.serialize
		val expr_name_ft = if (io_lst.contains(expr_name)) expr_name else newNameS(expr_name, i)
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

  def copyComponents(components: Statement, input_lst: List[(String, Type)], output_lst: List[(String, Type)], feedback: Int): Statement = {
	var temp_block : Block = components.asInstanceOf[Block]
	var added_stmts = ArrayBuffer[Statement]()
	var temp = ArrayBuffer[Statement]()
	var io_lst = input_lst.map(_._1) ++ output_lst.map(_._1)
	var feed_to_output_map = scala.collection.mutable.Map[String, ArrayBuffer[Expression]]()
	var from_input_map = scala.collection.mutable.Map[Expression, Expression]()
	var del_lst = ArrayBuffer[Statement]()
	for(i <- 0 to 1){	
	temp_block.stmts.foreach{
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
			if(ref2.isInstanceOf[WRef] || ref2.isInstanceOf[DoPrim] || ref2.isInstanceOf[Mux] || ref2.isInstanceOf[WSubField]){
				val ref2_ft = transform_expr(ref2, io_lst, i)
				if(output_lst.map(_._1).contains(ref1)){
					feed_to_output_map = add_output_relationship(feed_to_output_map, ref1, ref2, ref2_ft)
					del_lst += DefNode(info, ref1, ref2)
				}
				else
					added_stmts += DefNode(info, ref1_ft, ref2_ft)
			}	
			else{
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
				else throw new Exception("This should not be executed!")
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
	}

	added_stmts = add_voter(added_stmts, feed_to_output_map, feedback)	
	var all_detect_signals: ArrayBuffer[Expression] = ArrayBuffer()
	added_stmts = (temp_block.stmts.toBuffer ++ added_stmts).asInstanceOf[ArrayBuffer[Statement]]
	added_stmts = added_stmts -- del_lst
	temp_block.copy(stmts=added_stmts)
  }

  def add_voter_component(added_stmts: ArrayBuffer[Statement], componentName: String, tpe:Type,  output_data: Expression, feedback: Int): ArrayBuffer[Statement] = {
	var count = 0
	var voter_count = 0
	var new_stmts = added_stmts
	var all_detect_signals = ArrayBuffer[Expression]()

	var reg_ori_instance = WRef(componentName, tpe, ExpKind, UNKNOWNGENDER)
	var reg_instance = WRef(newNameS(componentName, 0), tpe, ExpKind, UNKNOWNGENDER)
	var reg_instance2 = WRef(newNameS(componentName, 1), tpe, ExpKind, UNKNOWNGENDER)

	val name = "v" + count.toString
	val size = tpe
	val bitwidth = size.asInstanceOf[UIntType].width.asInstanceOf[IntWidth].width.toInt	
	val ports = ArrayBuffer(Field("clock", Flip, ClockType), Field("reset", Flip, UIntType(IntWidth(1))), Field("io", Default, BundleType(Vector(Field("in1", Flip, size), Field("in2", Flip, size), Field("in3", Flip, size), Field("out", Default, size)))))
	val wref = WRef(name, BundleType(ports), ExpKind, UNKNOWNGENDER)
	val inside_io = BundleType(Vector(Field("in1", Flip, size), Field("in2", Flip, size), Field("in3", Flip, size), Field("out", Default, size)))
	new_stmts += WDefInstance(NoInfo, name, "Voter" + bitwidth.toString + "_" + feedback.toString, UnknownType)
	new_stmts += Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "in1", size, FEMALE), reg_ori_instance)
	new_stmts += Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "in2", size, FEMALE), reg_instance)
	new_stmts += Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "in3", size, FEMALE), reg_instance2)
	new_stmts += Connect(NoInfo, WSubField(wref, "clock", ClockType, FEMALE), WRef("clock", ClockType, ExpKind, MALE))
	new_stmts += Connect(NoInfo, WSubField(wref, "reset", UIntType(IntWidth(1)), FEMALE), WRef("reset", UIntType(IntWidth(1)), ExpKind, MALE))
	new_stmts += Connect(NoInfo, output_data, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "out", size, MALE))
	count += 1
	new_stmts
  }


  def add_voter(added_stmts: ArrayBuffer[Statement], feed_to_output_map: scala.collection.mutable.Map[String, ArrayBuffer[Expression]], feedback: Int): ArrayBuffer[Statement] = {
	var count = 0
	var voter_count = 0
	var all_detect_signals = ArrayBuffer[Expression]()
	print(feed_to_output_map)
	feed_to_output_map.foreach{
		mapping =>{
			val name = "v" + count.toString
			val output = mapping._1
			val feed_to_output = mapping._2
			val size = feed_to_output(0).tpe
			val bitwidth = size.asInstanceOf[UIntType].width.asInstanceOf[IntWidth].width.toInt
			val ports = ArrayBuffer(Field("clock", Flip, ClockType), Field("reset", Flip, UIntType(IntWidth(1))), Field("io", Default, BundleType(Vector(Field("in1", Flip, size), Field("in2", Flip, size), Field("in3", Flip, size), Field("out", Default, size)))))
			val inside_io = BundleType(Vector(Field("in1", Flip, size), Field("in2", Flip, size), Field("in3", Flip, size), Field("out", Default, size)))
			val wref = WRef(name, BundleType(ports), ExpKind, UNKNOWNGENDER)
			added_stmts += WDefInstance(NoInfo, name, "Voter" + bitwidth.toString + "_" + feedback.toString, UnknownType)
			added_stmts += Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "in1", size, FEMALE), feed_to_output(0))
			added_stmts += 	Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "in2", size, FEMALE), feed_to_output(1))
			added_stmts += Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "in3", size, FEMALE), feed_to_output(2))
			added_stmts += Connect(NoInfo, WSubField(wref, "clock", ClockType, FEMALE), WRef("clock", ClockType, ExpKind, MALE))
			added_stmts += Connect(NoInfo, WSubField(wref, "reset", UIntType(IntWidth(1)), FEMALE), WRef("reset", UIntType(IntWidth(1)), ExpKind, MALE))
			added_stmts += Connect(NoInfo, WRef(output, size, ExpKind, FEMALE), WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "out", size, MALE))
			if (!output_size.arr.contains((size, feedback_target.arr.contains(output)))){
				output_size.arr += ((size, feedback_target.arr.contains(output)))
				voter_count += 1
			}
			count += 1
		}	
	}
	added_stmts
  }

}

