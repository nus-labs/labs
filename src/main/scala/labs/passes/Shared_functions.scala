package labs

import firrtl._
import firrtl.ir._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

// shared functions
package object passes {
  def newNameS(name: String, i: Int): String = name + "_ft" + i.toString

  def newNameT(name: String): String = if (name != "clock" && name != "reset") name + "_temporal" else name

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

}
