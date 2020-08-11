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
		println("MYANNOS")
		println(myAnnos)
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
