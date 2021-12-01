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

// Yosys generates FIRRTL with a not so friendly format :(
// Unused
// Implementation is not done
// Currently, reusing LowFirrtlOptimization helps

class RemoveYosysProc extends Transform{
  def inputForm: CircuitForm = HighForm
  def outputForm: CircuitForm = HighForm
  def execute(state: CircuitState): CircuitState = {
    val ret = state.copy(circuit = run(state.circuit))
    ret
  }
  def run(c:Circuit): Circuit ={
	var modules = c.modules
	modules = modules.map(traverseM(_))
    c.copy(modules=modules)
  }
// change reg name
// remove wire
// change all wire references to reg references
  def traverseM(m: DefModule): DefModule = {
	var stmts = m.asInstanceOf[Module].body.asInstanceOf[Block].stmts.asInstanceOf[ArrayBuffer[Statement]]
    var allregs = stmts.filter(_.isInstanceOf[DefRegister])
    var allwires = stmts.filter(_.isInstanceOf[DefWire])
	var allregs_names = allregs.map(_.asInstanceOf[DefRegister].name)
	var allwires_names = allwires.map(_.asInstanceOf[DefWire].name)
	var reg_wire_map = scala.collection.mutable.Map[String, String]() // (reg, wire)
	//stmts --= allregs
	//stmts --= allwires
	println(stmts)
	println(allregs_names)
	println(allwires_names)
	stmts.map( stmt => stmt match { // search only for direct connect
		case Connect(info, ref1, ref2) => {
			println(ref1, ref2)
			if(ref1.isInstanceOf[WRef] && (allregs_names.contains(ref1) || allwires_names.contains(ref1)) && ref2.isInstanceOf[DoPrim]){
				var doprim = ref2.asInstanceOf[DoPrim]
				var doprim_size = doprim.tpe
				var target = doprim.args.last.asInstanceOf[WRef]
				var target_size = target.tpe
				println("Here")
				if(doprim.op == PrimOps.Bits && target_size == doprim_size){
					println("Here2")
					reg_wire_map += (target.name -> ref1.asInstanceOf[WRef].name)
					EmptyStmt
				}
				Connect(info, ref1, ref2)
			}
			Connect(info, ref1, ref2)
		}
		case other => {other}
	})
	stmts.foreach(x => println(x.serialize))
	println(reg_wire_map)
	var mm = m.asInstanceOf[Module].copy(body = Block(stmts))
	mm
  }
}
