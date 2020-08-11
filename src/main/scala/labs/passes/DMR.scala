// Copyright 2017 IBM
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
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
  def run(c:Circuit, component:ComponentName): Circuit ={
	var modules = c.modules
	modules = modules.map(splitM(_, component))

	var k = c.copy(modules=modules)
	var size_lst = ArrayBuffer[Int]()

	output_size.arr.foreach{
		case UIntType(width) => { // Can rely on ordering from set?
			var bitwidth = width.asInstanceOf[IntWidth].width.toInt
			if(!size_lst.contains(bitwidth)){
				k = add_modules(k, bitwidth)
				size_lst = size_lst :+ bitwidth
			}
		}
		case p => throw new Exception("There is something wrong with the type " + p.getClass)
	}

	var new_modules = if(output_size.arr.size == 0) k.modules else remove_dup_modules(k.modules)
	k.copy(modules=new_modules)
}

  def remove_dup_modules(modules: Seq[DefModule]): Seq[DefModule] = {
	var module_names: Set[String] = Set()
	var new_modules: Seq[DefModule] = Seq()
	modules.foreach{
		module => {
			if (!module_names.contains(module.asInstanceOf[Module].name)){
				module_names = module_names + module.asInstanceOf[Module].name
				new_modules = new_modules :+ module
			}
		}
	}
	return new_modules
  }

  def add_modules(k: Circuit, bitwidth: Int): Circuit = {
	var design = chisel3.Driver.toFirrtl(chisel3.Driver.elaborate(() => new Detector(bitwidth)))
	var modules = Seq[DefModule]()

	var module = design.modules.filter(_.asInstanceOf[Module].name == "Detector").last 	
	var new_name = module.asInstanceOf[Module].name + bitwidth.toString
	module = module.asInstanceOf[Module].copy(name=new_name)
	modules = design.modules.filter(_.asInstanceOf[Module].name != "Detector") :+ module.asInstanceOf[Module] 			
		
	var preventer = chisel3.Driver.toFirrtl(chisel3.Driver.elaborate(() => new Preventer(bitwidth, true)))
	module = preventer.modules.filter(_.asInstanceOf[Module].name == "Preventer").last
	new_name = module.asInstanceOf[Module].name + bitwidth.toString
	module = module.asInstanceOf[Module].copy(name=new_name)
	modules = modules :+ module.asInstanceOf[Module]
		
	design = design.copy(modules=modules)
	design = ToWorkingIR.run(design)
	var kk = k.copy(modules = k.modules ++ design.modules)
	kk
  }

  def splitM(m: DefModule, component: ComponentName): DefModule = { 
	if( component.module.name == m.name ){
		if( component.name == "None" ){
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
			var ports = m.ports
			ports = ports :+ Port(NoInfo, "detect", Output, UIntType(IntWidth(1)))
			var mm = m.asInstanceOf[Module].copy(ports=ports)
			mm = add_component(mm, component)
			return mm
		}
	}
	else{
		return m
	}
  }

  def newNameS(name: String, i: Int): String = name + "_ft" + i.toString

  def add_component(m: Module, component: ComponentName): Module = {
	var name = component.name
	var stmts = m.asInstanceOf[Module].body.asInstanceOf[Block].stmts
	var allregs = stmts.filter(_.isInstanceOf[DefRegister])
	var allwire = stmts.filter(_.isInstanceOf[DefWire])
	var reg = allregs.filter(_.asInstanceOf[DefRegister].name == component.name).last.asInstanceOf[DefRegister]
	var added_reg = reg.copy(name = newNameS(reg.name, 0))
	stmts = stmts :+ added_reg
	stmts = add_detector_component(stmts, component, reg.tpe)
	output_size.arr += reg.tpe
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
	var temp_block = components.asInstanceOf[Block]
	var added_stmts = ArrayBuffer[Statement]()
	var temp = ArrayBuffer[Statement]()
	var io_lst = input_lst.map(_._1) ++ output_lst.map(_._1)
	var feed_to_output_map = scala.collection.mutable.Map[String, ArrayBuffer[Expression]]()
	var from_input_map = scala.collection.mutable.Map[Expression, Expression]()
	var del_lst = ArrayBuffer[Statement]()
	var i = 0
	temp_block.stmts.foreach{
		case DefWire(info, name, tpe) => {
			added_stmts += DefWire(info, newNameS(name, i), tpe)
		}
		case DefRegister(info, name, tpe, doprim, a, init) => {
			added_stmts += DefRegister(info, newNameS(name, i), tpe, doprim, a, transform_expr(init, io_lst, i).asInstanceOf[Expression] )
		}
		case DefInstance(info, name, module) => {
			added_stmts += DefInstance(info, newNameS(name, i), module)
		}
		case WDefInstance(info, name, module, tpe) => {
			added_stmts += WDefInstance(info, newNameS(name, i), module, tpe)
		}
		case DefNode(info, ref1, ref2) => { // ref1 is a string
			val ref1_ft = if (io_lst.contains(ref1)) ref1 else newNameS(ref1, i)
			if(ref2.isInstanceOf[Reference] || ref2.isInstanceOf[DoPrim] || ref2.isInstanceOf[Mux]){
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

			if(ref2.isInstanceOf[Reference] || ref2.isInstanceOf[WRef] || ref2.isInstanceOf[DoPrim] || ref2.isInstanceOf[Mux] || ref2.isInstanceOf[WSubField]){
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
				// must check
				else throw new Exception("Something Weird")
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
			if (!output_size.arr.contains(size)){
				output_size.arr += size
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

  def add_detector_component(stmts: Seq[Statement], component: ComponentName, tpe: Type): Seq[Statement] = {
	val name = "dc" + redundancy_number.count.toString
	redundancy_number.count += 1
	val bitwidth = tpe.asInstanceOf[UIntType].width.asInstanceOf[IntWidth].width.toInt
	val size = tpe
	val inside_io = BundleType(Vector(Field("in1", Flip, size), Field("in2", Flip, size), Field("detect", Default, UIntType(IntWidth(1)))))
	val ports = ArrayBuffer(Field("clock", Flip, ClockType), Field("reset", Flip, UIntType(IntWidth(1))), Field("io", Default, inside_io))
	val wref = WRef(name, BundleType(ports), ExpKind, UNKNOWNGENDER)
	var added_stmts = stmts :+ WDefInstance(NoInfo, name, "Detector" + bitwidth.toString, UnknownType)
	added_stmts = added_stmts :+ Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "in1", size, FEMALE), WRef(component.name, tpe, ExpKind, MALE))
	added_stmts = added_stmts :+ Connect(NoInfo, WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "in2", size, FEMALE), WRef(newNameS(component.name, 0), tpe, ExpKind, MALE))
	added_stmts = added_stmts :+ Connect(NoInfo, WRef("detect", UIntType(IntWidth(1)), ExpKind, FEMALE), WSubField(WSubField(wref, "io", inside_io, UNKNOWNGENDER), "detect", size, FEMALE))
	added_stmts = added_stmts :+ Connect(NoInfo, WSubField(wref, "clock", ClockType, FEMALE), WRef("clock", ClockType, ExpKind, MALE))
	added_stmts = added_stmts :+ Connect(NoInfo, WSubField(wref, "reset", UIntType(IntWidth(1)), FEMALE), WRef("reset", UIntType(IntWidth(1)), ExpKind, MALE))
	added_stmts		 
  }


}

