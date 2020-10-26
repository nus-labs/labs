package labs.passes

import labs._
import chiffre.inject.Injector
import chiffre.util.removeZeroWidth
import chisel3.experimental.{annotate, ChiselAnnotation}
import firrtl._
import firrtl.ir._
import firrtl.passes.{PassException, ToWorkingIR}
import firrtl.passes.wiring.SinkAnnotation
import firrtl.annotations.{Annotation, ComponentName, ModuleName, CircuitName, SingleTargetAnnotation}
import firrtl.annotations.AnnotationUtils._
import scala.collection.mutable

class CompatibilityPass() extends Transform {
  def inputForm: CircuitForm = HighForm
  def outputForm: CircuitForm = HighForm
  def run(c:Circuit, myAnnos:List[Annotation]): Circuit ={
	if(myAnnos.length == 0) return c
	var clockname = ""
	var resetname = ""
	myAnnos.foreach{
		anno => anno match{
			case ClockAnnotation(c) => clockname = c.name
			case ResetAnnotation(c) => resetname = c.name
		}
	}
	val k = c.copy(modules = c.modules.map(splitM(_, clockname, resetname)))
	k
}
  def splitM(m: DefModule, clockname: String, resetname: String): DefModule = { // if mapport then print then return m will cause problems
	val k = m.mapPort(splitP(_, clockname, resetname))
	k.mapStmt(splitS(_, clockname, resetname))
}

  def splitP(p: Port, clockname: String, resetname: String): Port ={
	p.name match{
		case `clockname` => { // needs ` not ' if not used will always pass
			val k = p.copy(name="clock", tpe=ClockType)
			k
		}
		case "clock" => { // needs ` not ' if not used will always pass
			val k = p.copy(name="clock", tpe=ClockType)
			k
		}
		case `resetname` => {
			val k = p.copy(name="reset")
			k
		}
		case _ => p
	}
}  

  def splitType(t: Type, clockname: String, resetname: String): Type = {
	t match{
		case BundleType(field) => BundleType(field.map(changeField(_, clockname, resetname)))
		case other => other
	}
  }
	
  def splitS(s: Statement, clockname: String, resetname: String): Statement = {
	var ss = s.mapStmt{
		case stmt => {
			var temp = stmt.mapExpr(splitE(_, clockname, resetname))
			temp = temp.mapType(splitType(_, clockname, resetname))
			temp
		}
	}

	ss
  }

  def changeField(f: Field, clockname: String, resetname: String): Field={
	f match{
		case Field(name, flip, tpe) => {
			name match {
				case `clockname` => Field("clock", flip, ClockType)
				case "clock" => Field("clock", flip, ClockType)
				case `resetname` => Field("reset", flip, tpe)
				case other => Field(name, flip, tpe)
			}
		}
		case other => throw new Exception(f.getClass + " not yet implemented")
	}
  }

  def changeName(name: String, clockname: String, resetname: String): String = if (name == clockname) "clock" else if (name == resetname) "reset" else name

  def splitE(e: Expression, clockname: String, resetname: String): Expression={
	val ret = e match{
		case WRef(name, tpe, port, gender) => {
			name match{
				case `clockname` => WRef("clock", ClockType, port, gender)
				case "clock" => WRef("clock", ClockType, port, gender)
				case `resetname` => WRef("reset", tpe, port, gender)
				case other => {
					if(tpe.isInstanceOf[BundleType]){
						val modified_tpe = BundleType(tpe.asInstanceOf[BundleType].fields.map(changeField(_, clockname, resetname)))
						WRef(name, modified_tpe, port, gender)
					}
					else WRef(name, tpe, port, gender)
				}
			}
		}
		case WSubField(expr, name, tpe, flow) => {
			WSubField(splitE(expr, clockname, resetname), changeName(name, clockname, resetname), tpe, flow)
		}
		case e =>{
			var temp = e.mapExpr(splitE(_, clockname, resetname))
			temp = temp.mapType(splitType(_, clockname, resetname))
			temp
		}
	}
	ret
}

  def execute(state: CircuitState): CircuitState = {
    val myAnnos = state.annotations.collect { case a: ClockAnnotation => a
                                              case b: ResetAnnotation => b }.toList
	state.copy(circuit = run(state.circuit, myAnnos))
  }
}

