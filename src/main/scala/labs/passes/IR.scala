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

class IR {

  private var inside_io_mapper = Map( // should be dinamically generated instead
	"AddRoundKeyParityBitGenerator" -> BundleType(Vector(Field("parity_in", Flip, UIntType(IntWidth(16))), Field("roundkey", Flip, UIntType(IntWidth(128))), Field("mixcolumns_in", Flip, UIntType(IntWidth(16))), Field("parity_out", Default, UIntType(IntWidth(16))), Field("round_ctr", Flip, UIntType(IntWidth(4))), Field("write", Flip, UIntType(IntWidth(1))) )),
	"MixColumnsParityBitGenerator" -> BundleType(Vector(Field("in", Flip, UIntType(IntWidth(128))), Field("in_parity", Flip, UIntType(IntWidth(16))), Field("parity", Default, UIntType(IntWidth(16))), Field("round_ctr", Flip, UIntType(IntWidth(4))))),
	newName("nByteParityBitGenerator", 128) -> BundleType(Vector(Field("in", Flip, UIntType(IntWidth(128))), Field("in_parity", Flip, UIntType(IntWidth(16))), Field("parity", Default, UIntType(IntWidth(16))))),
	"ShiftrowParityBitGenerator" -> BundleType(Vector(Field("in", Flip, UIntType(IntWidth(16))), Field("parity", Default, UIntType(IntWidth(16))))),
	newName("nByteParityBitGeneratorSBox", 32) -> BundleType(Vector(Field("in_after_sbox", Flip, UIntType(IntWidth(32))), Field("in_before_sbox", Flip, UIntType(IntWidth(32))), Field("in_parity", Flip, UIntType(IntWidth(16))), Field("write_sbox", Flip, UIntType(IntWidth(1))), Field("write", Flip, UIntType(IntWidth(1))), Field("parity", Default, UIntType(IntWidth(16))))),
	newName("Detector_with_enable", 16) -> BundleType(Vector(Field("in1", Flip, UIntType(IntWidth(16))), Field("in2", Flip, UIntType(IntWidth(16))), Field("enable", Flip, UIntType(IntWidth(1))), Field("detect", Default, UIntType(IntWidth(1))))),
  	newName("Preventer", 128) -> BundleType(Vector(Field("in", Flip, UIntType(IntWidth(128))), Field("detect", Flip, UIntType(IntWidth(1))), Field("out", Default, UIntType(IntWidth(128))))),

  )

// make sure to instantiate a module before using it

  def run(c: Circuit, toparitygen: ComponentName, roundkey: ComponentName, tosbox_before: ComponentName, tosbox_after: ComponentName, tosbox_write: ComponentName, tomixcolumns: ComponentName, round_ctr: ComponentName, write:ComponentName, feedback_mechanism: Int, feedback_target: List[String]): Circuit ={
	var cc = add_parity_generator(c, 16)
	cc = connect_parity_generator(cc, toparitygen, 16)
	cc = add_addroundkey_parity_generator(cc, 16)
	cc = connect_parity_generator_with_addroundkey(cc, roundkey)
	cc = connect_addroundkey_for_design(cc, round_ctr, roundkey, write)
	cc = add_sbox_parity_generator(cc, 4)
	cc = connect_sbox_parity_generator(cc, tosbox_before, tosbox_after, tosbox_write, write, 4)
	cc = add_shiftrow_parity_generator(cc)
	cc = connect_sbox_with_shiftrow(cc, tosbox_after)
	cc = add_mixcolumns_parity_generator(cc)
	cc = connect_shiftrow_with_mixcolumns(cc, tomixcolumns)
	cc = connect_mixcolumns_with_addroundkey(cc, roundkey)
	cc = connect_mixcolumns_for_design(cc, round_ctr)
	cc = add_detector(cc, 16)
	cc = connect_detector(cc, write)
	if(feedback_mechanism == 0){
		cc = add_detect_port(cc, toparitygen)
		cc = connect_detector_with_detect_port(cc, toparitygen)
	}
	else{
		cc = add_preventer(cc, 128, feedback_mechanism)
		cc = connect_detector_with_preventer(cc, toparitygen)
		cc = connect_preventer_with_output_and_input(cc, toparitygen, feedback_target)

	}
	cc = remove_all_duplicates(cc)
	cc
  }

  def newName(name: String, size: BigInt): String = name + "_" + size.toString

  def newName_module_level(name: String, bitwidth: Int, modules: Seq[DefModule]): Seq[DefModule] = {
	modules.map(
		x => {
			if(x.name == name){
				x.asInstanceOf[Module].copy(name = newName(name, bitwidth))
			}
			else{
				x
			}
		}
	)
  }

  def generate_instance(module_name: String, name:String, inside_io: BundleType): Statement = {
	val ports = ArrayBuffer(Field("clock", Flip, ClockType), Field("reset", Flip, UIntType(IntWidth(1))), Field("io", Default, inside_io))
	WDefInstance(NoInfo, module_name, name, UnknownType)
  }

  def generate_clock_connection(instance_name: String, inside_io: BundleType): Statement = {
	val ports = ArrayBuffer(Field("clock", Flip, ClockType), Field("reset", Flip, UIntType(IntWidth(1))), Field("io", Default, inside_io))
	val instance_wref = WRef(instance_name, BundleType(ports), ExpKind, UNKNOWNGENDER)
	Connect(NoInfo, WSubField(instance_wref, "clock", ClockType, FEMALE), WRef("clock", ClockType, ExpKind, MALE))
  }

  def generate_reset_connection(instance_name: String, inside_io: BundleType): Statement = {
	val ports = ArrayBuffer(Field("clock", Flip, ClockType), Field("reset", Flip, UIntType(IntWidth(1))), Field("io", Default, inside_io))
	val instance_wref = WRef(instance_name, BundleType(ports), ExpKind, UNKNOWNGENDER)
	Connect(NoInfo, WSubField(instance_wref, "reset", UIntType(IntWidth(1)), FEMALE), WRef("reset", UIntType(IntWidth(1)), ExpKind, MALE))
  }

  def generate_in_connection(instance_name: String, connect_port_name: String, connect_port_size: Type, output_port_name: String, output_port_size: Type, inside_io: BundleType): Statement = {
	val ports = ArrayBuffer(Field("clock", Flip, ClockType), Field("reset", Flip, UIntType(IntWidth(1))), Field("io", Default, inside_io))
	val instance_wref = WRef(instance_name, BundleType(ports), ExpKind, UNKNOWNGENDER)
	val output_port = WRef(output_port_name, output_port_size, ExpKind, MALE)
	Connect(NoInfo, WSubField(WSubField(instance_wref, "io", inside_io, UNKNOWNGENDER),connect_port_name, connect_port_size, FEMALE), output_port)
  }

  def generate_out_connection(target_port_name: String, target_port_size: Type, instance_name: String, output_port_name: String, output_port_size: Type, inside_io: BundleType): Statement = {
	val ports = ArrayBuffer(Field("clock", Flip, ClockType), Field("reset", Flip, UIntType(IntWidth(1))), Field("io", Default, inside_io))
	val instance_wref = WRef(instance_name, BundleType(ports), ExpKind, UNKNOWNGENDER)
	val input_port = WRef(target_port_name, target_port_size, ExpKind, FEMALE)
	Connect(NoInfo, input_port, WSubField(WSubField(instance_wref, "io", inside_io, UNKNOWNGENDER), output_port_name, output_port_size, MALE))
  }

  def generate_between_connection(in_instance_name: String, input_port_name: String, input_port_size: Type, out_instance_name: String, output_port_name: String, output_port_size: Type, in_inside_io: BundleType, out_inside_io: BundleType): Statement = {
	val in_ports = ArrayBuffer(Field("clock", Flip, ClockType), Field("reset", Flip, UIntType(IntWidth(1))), Field("io", Default, in_inside_io))
	val out_ports = ArrayBuffer(Field("clock", Flip, ClockType), Field("reset", Flip, UIntType(IntWidth(1))), Field("io", Default, out_inside_io))
	val in_instance_wref = WRef(in_instance_name, BundleType(in_ports), ExpKind, UNKNOWNGENDER)
	val out_instance_wref = WRef(out_instance_name, BundleType(out_ports), ExpKind, UNKNOWNGENDER)
	val input_port = WRef(input_port_name, input_port_size, ExpKind, FEMALE)
	val output_port = WRef(output_port_name, output_port_size, ExpKind, MALE)
	val in_subfield = WSubField(WSubField(in_instance_wref, "io", in_inside_io, UNKNOWNGENDER), input_port_name, input_port_size, FEMALE)
	val out_subfield = WSubField(WSubField(out_instance_wref, "io", in_inside_io, UNKNOWNGENDER), output_port_name, output_port_size, MALE)
	Connect(NoInfo, in_subfield, out_subfield)
  }

  def add_detect_port(k: Circuit, toparitygen:ComponentName): Circuit = {
	var modules = k.modules
	modules = modules.map(
		x => {
			if(x.name == toparitygen.module.name){
				var ports = x.ports
				ports = ports :+ Port(NoInfo, "detect", Output, UIntType(IntWidth(1)))
				//println("Found")
				x.asInstanceOf[Module].copy(ports = ports)
			}
			else{	
				x
			}		
		}
	)
	k.copy(modules = modules)
  }

  def add_parity_generator(k: Circuit, bytewidth: Int): Circuit = {
	var design = chisel3.Driver.toFirrtl(chisel3.Driver.elaborate(() => new nByteParityBitGenerator(bytewidth)))
	design = ToWorkingIR.run(design)
	var design_modules = newName_module_level("nByteParityBitGenerator", bytewidth * 8, design.modules)
	design = design.copy(modules = design_modules)
	k.copy(modules = design.modules ++ k.modules)
  }

  def add_sbox_parity_generator(k: Circuit, bytewidth: Int): Circuit = {
	var design = chisel3.Driver.toFirrtl(chisel3.Driver.elaborate(() => new nByteParityBitGeneratorSBox(bytewidth)))
	design = ToWorkingIR.run(design)
	var design_modules = newName_module_level("nByteParityBitGeneratorSBox", bytewidth * 8, design.modules)
	design = design.copy(modules = design_modules)
	k.copy(modules = design.modules ++ k.modules)
  }

  def add_addroundkey_parity_generator(k: Circuit, bytewidth: Int): Circuit = {
	var design = chisel3.Driver.toFirrtl(chisel3.Driver.elaborate(() => new AddRoundKeyParityBitGenerator(bytewidth)))
	design = ToWorkingIR.run(design)
	k.copy(modules = design.modules ++ k.modules)
  }

  def add_shiftrow_parity_generator(k: Circuit): Circuit = {
	var design = chisel3.Driver.toFirrtl(chisel3.Driver.elaborate(() => new ShiftrowParityBitGenerator()))
	design = ToWorkingIR.run(design)
	k.copy(modules = design.modules ++ k.modules)
  }

  def add_mixcolumns_parity_generator(k: Circuit): Circuit = {
	var design = chisel3.Driver.toFirrtl(chisel3.Driver.elaborate(() => new MixColumnsParityBitGenerator))
	design = ToWorkingIR.run(design)
	k.copy(modules = design.modules ++ k.modules)
  }

  def add_detector(k: Circuit, bitWidth:Int): Circuit = {
	var design = chisel3.Driver.toFirrtl(chisel3.Driver.elaborate(() => new Detector_with_enable(bitWidth)))
	design = ToWorkingIR.run(design)
	k.copy(modules = design.modules ++ k.modules)
  }

  def add_preventer(k: Circuit, bitWidth:Int, feedback:Int): Circuit = {
	var design = chisel3.Driver.toFirrtl(chisel3.Driver.elaborate(() => new Preventer(bitWidth, feedback)))
	design = ToWorkingIR.run(design)
	k.copy(modules = design.modules ++ k.modules)
  }

  def connect_parity_generator(k: Circuit, toparitygen: ComponentName, size: BigInt): Circuit = { // size in byte
	var modules = k.modules
	val inside_io = BundleType(Vector(Field("in", Flip, UIntType(IntWidth(size * 8))), Field("parity", Default, UIntType(IntWidth(size)))))
	modules = modules.map(
		x => {
				if(x.name == toparitygen.module.name){
					var stmts = x.asInstanceOf[Module].body.asInstanceOf[Block].stmts
					val module_name = newName("nByteParityBitGenerator", size * 8)
					stmts = stmts :+ generate_instance(module_name, module_name, inside_io)
					stmts = stmts :+ generate_in_connection(module_name, "in", UIntType(IntWidth(size * 8)), toparitygen.name, UIntType(IntWidth(size * 8)), inside_io)
					stmts = stmts :+ generate_clock_connection(module_name, inside_io)
					stmts = stmts :+ generate_reset_connection(module_name, inside_io)
					x.asInstanceOf[Module].copy(body = Block(stmts))
				}
				else{
					x
				}
			}
	)
	k.copy(modules = modules)
  }

  def connect_sbox_parity_generator(k: Circuit, tosbox_before:ComponentName, toparitygen: ComponentName, write_sbox: ComponentName, write: ComponentName, size: BigInt): Circuit = { // size in byte
	var modules = k.modules
	val module_name = newName("nByteParityBitGeneratorSBox", size * 8)
	val inside_io = inside_io_mapper(module_name)
	val parity_inside_io = BundleType(Vector(Field("in", Flip, UIntType(IntWidth(size * 8))), Field("parity", Default, UIntType(IntWidth(size)))))
	modules = modules.map(
		x => {
				if(x.name == toparitygen.module.name){
					var stmts = x.asInstanceOf[Module].body.asInstanceOf[Block].stmts
					val parity_generator_name = "AddRoundKeyParityBitGenerator"
					stmts = stmts :+ generate_instance(module_name, module_name, inside_io)
					stmts = stmts :+ generate_in_connection(module_name, "in_after_sbox", UIntType(IntWidth(size * 8)), toparitygen.name, UIntType(IntWidth(size * 8)), inside_io)
					stmts = stmts :+ generate_in_connection(module_name, "in_before_sbox", UIntType(IntWidth(128)), tosbox_before.name, UIntType(IntWidth(128)), inside_io)
					stmts = stmts :+ generate_in_connection(module_name, "write_sbox", UIntType(IntWidth(1)), write_sbox.name, UIntType(IntWidth(1)), inside_io)
					stmts = stmts :+ generate_in_connection(module_name, "write", UIntType(IntWidth(1)), write.name, UIntType(IntWidth(1)), inside_io)
					stmts = stmts :+ generate_between_connection(module_name, "in_parity", UIntType(IntWidth(16)), parity_generator_name, "parity_out", UIntType(IntWidth(16)), inside_io, parity_inside_io)
					stmts = stmts :+ generate_clock_connection(module_name, inside_io)
					stmts = stmts :+ generate_reset_connection(module_name, inside_io)
					x.asInstanceOf[Module].copy(body = Block(stmts))
				}
				else{
					x
				}
			}
	)
	k.copy(modules = modules)
  }

  def connect_parity_generator_with_addroundkey(k: Circuit, roundkey: ComponentName): Circuit = {
	var modules = k.modules
	val addroundkey_module_name = "AddRoundKeyParityBitGenerator"
	val parity_module_name = newName("nByteParityBitGenerator", 128)
	val in_inside_io = inside_io_mapper(addroundkey_module_name)
	val out_inside_io = inside_io_mapper(parity_module_name)
	modules = modules.map(
		x => {
				if(x.name == roundkey.module.name){
					var stmts = x.asInstanceOf[Module].body.asInstanceOf[Block].stmts
					stmts = stmts :+ generate_instance(addroundkey_module_name, addroundkey_module_name, in_inside_io)
					stmts = stmts :+ generate_between_connection(addroundkey_module_name, "parity_in", UIntType(IntWidth(16)), parity_module_name, "parity", UIntType(IntWidth(16)), in_inside_io, out_inside_io)
					stmts = stmts :+ generate_clock_connection(addroundkey_module_name, in_inside_io)
					stmts = stmts :+ generate_reset_connection(addroundkey_module_name, in_inside_io)
					x.asInstanceOf[Module].copy(body = Block(stmts))
				}
				else{
					x
				}
			}
	)
	k.copy(modules = modules)
  }

  def connect_sbox_with_shiftrow(k: Circuit, sbox: ComponentName): Circuit = {
	var modules = k.modules
	val sbox_module_name = newName("nByteParityBitGeneratorSBox", 32)
	val shiftrow_module_name = "ShiftrowParityBitGenerator"
	val in_inside_io = inside_io_mapper(shiftrow_module_name)
	val out_inside_io = inside_io_mapper(sbox_module_name)
	modules = modules.map(
		x => {
				if(x.name == sbox.module.name){
					var stmts = x.asInstanceOf[Module].body.asInstanceOf[Block].stmts
					stmts = stmts :+ generate_instance(shiftrow_module_name, shiftrow_module_name, in_inside_io)
					stmts = stmts :+ generate_between_connection(shiftrow_module_name, "in", UIntType(IntWidth(16)), sbox_module_name, "parity", UIntType(IntWidth(16)), in_inside_io, out_inside_io)
					stmts = stmts :+ generate_clock_connection(shiftrow_module_name, in_inside_io)
                    stmts = stmts :+ generate_reset_connection(shiftrow_module_name, in_inside_io)
					x.asInstanceOf[Module].copy(body = Block(stmts))
				}
				else{
					x
				}
		}
	)
	k.copy(modules = modules)
  }

  def connect_shiftrow_with_mixcolumns(k: Circuit, tomixcolumns: ComponentName): Circuit = {
	var modules = k.modules
	val shiftrow_module_name = "ShiftrowParityBitGenerator"
	val mixcolumns_module_name = "MixColumnsParityBitGenerator"
	val in_inside_io = inside_io_mapper(mixcolumns_module_name)
	val out_inside_io = inside_io_mapper(shiftrow_module_name)	
	modules = modules.map(
		x => {
				if(x.name == tomixcolumns.module.name){
					var stmts = x.asInstanceOf[Module].body.asInstanceOf[Block].stmts
					stmts = stmts :+ generate_instance(mixcolumns_module_name, mixcolumns_module_name, in_inside_io)
					stmts = stmts :+ generate_between_connection(mixcolumns_module_name, "in_parity", UIntType(IntWidth(16)), shiftrow_module_name, "parity", UIntType(IntWidth(16)), in_inside_io, out_inside_io)
					stmts = stmts :+ generate_in_connection(mixcolumns_module_name, "in", UIntType(IntWidth(128)), tomixcolumns.name, UIntType(IntWidth(128)), in_inside_io)
					stmts = stmts :+ generate_clock_connection(mixcolumns_module_name, in_inside_io)
					stmts = stmts :+ generate_reset_connection(mixcolumns_module_name, in_inside_io)
					x.asInstanceOf[Module].copy(body = Block(stmts))
				}
				else{
					x
				}
		}
	)
	k.copy(modules = modules)
  }

  def connect_mixcolumns_with_addroundkey(k: Circuit, roundkey: ComponentName): Circuit = {
	var modules = k.modules
	val mixcolumns_module_name = "MixColumnsParityBitGenerator"
	val addroundkey_module_name = "AddRoundKeyParityBitGenerator"
	val in_inside_io = inside_io_mapper(addroundkey_module_name)
	val out_inside_io = inside_io_mapper(mixcolumns_module_name)
	modules = modules.map(
		x => {
				if(x.name == roundkey.module.name){
					var stmts = x.asInstanceOf[Module].body.asInstanceOf[Block].stmts
					stmts = stmts :+ generate_between_connection(addroundkey_module_name, "mixcolumns_in", UIntType(IntWidth(16)), mixcolumns_module_name, "parity", UIntType(IntWidth(16)), in_inside_io, out_inside_io)
					x.asInstanceOf[Module].copy(body = Block(stmts))
				}
				else{
					x
				}
		}
	)
	k.copy(modules = modules)
  }

  def connect_mixcolumns_for_design(k: Circuit, round_ctr: ComponentName): Circuit = {
	var modules = k.modules
	val mixcolumns_module_name = "MixColumnsParityBitGenerator"
	val inside_io = inside_io_mapper(mixcolumns_module_name)
	modules = modules.map(
		x => {
				if(x.name == round_ctr.module.name){
					var stmts = x.asInstanceOf[Module].body.asInstanceOf[Block].stmts
					stmts = stmts :+ generate_in_connection(mixcolumns_module_name, "round_ctr", UIntType(IntWidth(4)), round_ctr.name, UIntType(IntWidth(4)), inside_io)
					x.asInstanceOf[Module].copy(body = Block(stmts))
				}
				else{
					x
				}
		}
	)
	k.copy(modules = modules)
  }

  def connect_addroundkey_for_design(k: Circuit, round_ctr: ComponentName, roundkey: ComponentName, write: ComponentName): Circuit = {
	var modules = k.modules
	val addroundkey_module_name = "AddRoundKeyParityBitGenerator"
	val inside_io = inside_io_mapper(addroundkey_module_name)
	modules = modules.map(
		x => {
				if(x.name == round_ctr.module.name){
					var stmts = x.asInstanceOf[Module].body.asInstanceOf[Block].stmts
					stmts = stmts :+ generate_in_connection(addroundkey_module_name, "round_ctr", UIntType(IntWidth(4)), round_ctr.name, UIntType(IntWidth(4)), inside_io)
					stmts = stmts :+ generate_in_connection(addroundkey_module_name, "roundkey", UIntType(IntWidth(128)), roundkey.name, UIntType(IntWidth(128)), inside_io)
					stmts = stmts :+ generate_in_connection(addroundkey_module_name, "write", UIntType(IntWidth(1)), write.name, UIntType(IntWidth(1)), inside_io)
					x.asInstanceOf[Module].copy(body = Block(stmts))
				}
				else{
					x
				}
		}
	)
	k.copy(modules = modules)
  }

  def connect_detector(k: Circuit, write: ComponentName): Circuit = {
	var modules = k.modules
	val detector_module_name = newName("Detector_with_enable", 16)
	val parity_bit_module_name = newName("nByteParityBitGenerator", 128)
	val addroundkey_bit_module_name = "AddRoundKeyParityBitGenerator"
	val in_inside_io = inside_io_mapper(detector_module_name)
	val parity_inside_io = inside_io_mapper(parity_bit_module_name)
	val addroundkey_inside_io = inside_io_mapper(addroundkey_bit_module_name)
	modules = modules.map(
		x => {
				if(x.name == write.module.name){
					var stmts = x.asInstanceOf[Module].body.asInstanceOf[Block].stmts
					stmts = stmts :+ generate_instance(detector_module_name, "Detector_with_enable", in_inside_io)
					stmts = stmts :+ generate_between_connection(detector_module_name, "in1", UIntType(IntWidth(16)), parity_bit_module_name, "parity", UIntType(IntWidth(16)), in_inside_io, parity_inside_io)
					stmts = stmts :+ generate_between_connection(detector_module_name, "in2", UIntType(IntWidth(16)), addroundkey_bit_module_name, "parity_out", UIntType(IntWidth(16)), in_inside_io, addroundkey_inside_io)
					stmts = stmts :+ generate_in_connection(detector_module_name, "enable", UIntType(IntWidth(1)), write.name, UIntType(IntWidth(1)), in_inside_io)
					stmts = stmts :+ generate_clock_connection(detector_module_name, in_inside_io)
					stmts = stmts :+ generate_reset_connection(detector_module_name, in_inside_io)
					x.asInstanceOf[Module].copy(body = Block(stmts))
				}
				else{
					x
				}
		}
	)
	k.copy(modules = modules)
  }

  def connect_detector_with_detect_port(k: Circuit, toparitygen: ComponentName): Circuit = {
	var modules = k.modules
	val detector_module_name = newName("Detector_with_enable", 16)
	val inside_io = inside_io_mapper(detector_module_name)
	modules = modules.map(
		x => {
				if(x.name == toparitygen.module.name){
					var stmts = x.asInstanceOf[Module].body.asInstanceOf[Block].stmts
					stmts = stmts :+ generate_out_connection("detect", UIntType(IntWidth(1)), detector_module_name, "detect", UIntType(IntWidth(1)), inside_io)
					x.asInstanceOf[Module].copy(body = Block(stmts))
				}
				else{
					x
				}
		}
	)
	k.copy(modules = modules)
  }

  def connect_detector_with_preventer(k: Circuit, toparitygen: ComponentName): Circuit = {
	var modules = k.modules
	val detector_module_name = newName("Detector_with_enable", 16)
	val preventer_module_name = newName("Preventer", 128)
	val in_inside_io = inside_io_mapper(preventer_module_name)
	val out_inside_io = inside_io_mapper(detector_module_name)
	modules = modules.map(
		x => {
				if(x.name == toparitygen.module.name){
					var stmts = x.asInstanceOf[Module].body.asInstanceOf[Block].stmts
					stmts = stmts :+ generate_instance(preventer_module_name, "Preventer", out_inside_io)
					stmts = stmts :+ generate_between_connection(preventer_module_name, "detect", UIntType(IntWidth(128)), detector_module_name, "detect", UIntType(IntWidth(1)), in_inside_io, out_inside_io)
					stmts = stmts :+ generate_clock_connection(preventer_module_name, in_inside_io)
					stmts = stmts :+ generate_reset_connection(preventer_module_name, in_inside_io)
					x.asInstanceOf[Module].copy(body = Block(stmts))
				}
				else{
					x
				}
		}
	)
	k.copy(modules = modules)
  }

  def connect_preventer_with_output_and_input(k: Circuit, toparitygen: ComponentName, feedback_target: List[String]): Circuit = {
	var modules = k.modules
	val preventer_module_name = newName("Preventer", 128)
	val inside_io = inside_io_mapper(preventer_module_name)
	modules = modules.map(
		x => {
				if(x.name == toparitygen.module.name){
					var stmts = x.asInstanceOf[Module].body.asInstanceOf[Block].stmts
					for(i <- 0 until feedback_target.length){ // TODO now supports only one target having 128 bits
						val temp_name = "temp" + i.toString
						stmts = change_target_port(stmts, feedback_target(i), temp_name)
						stmts = stmts :+ generate_in_connection(preventer_module_name, "in", UIntType(IntWidth(128)), temp_name, UIntType(IntWidth(128)), inside_io) 
						stmts = stmts :+ generate_out_connection(feedback_target(i), UIntType(IntWidth(128)), preventer_module_name, "out", UIntType(IntWidth(128)), inside_io)
						//println(generate_out_connection(feedback_target(i), UIntType(IntWidth(128)), preventer_module_name, "out", UIntType(IntWidth(128)), inside_io))
						//stmts.foreach(println(_))
					}
					x.asInstanceOf[Module].copy(body = Block(stmts))
				}
				else{
					x
				}
		}
	)
	k.copy(modules = modules)
  }

  def change_target_port(stmts: Seq[Statement], feedback_target: String, change_to_name: String): Seq[Statement] = {
	var new_stmts = stmts
	var stmts_to_be_added = List[Statement]()
	var temp_type = UIntType(IntWidth(1)).asInstanceOf[Type] //just to instantiate this
	stmts.foreach(
		x =>{
			if(x.isInstanceOf[Connect]){
				val xx = x.asInstanceOf[Connect]
				//assume left must be wref
				val left = xx.loc
				if(left.isInstanceOf[WRef]){
					var left_wref = left.asInstanceOf[WRef]
					if(left_wref.name == feedback_target){
						stmts_to_be_added = stmts_to_be_added :+ xx.copy(loc=left_wref.copy(name=change_to_name)).asInstanceOf[Statement]
						temp_type = left_wref.tpe
					}
				}
			}
		}
    )
	var temp_wire = DefWire(NoInfo, change_to_name, temp_type)
	new_stmts = new_stmts :+ temp_wire.asInstanceOf[Statement]	
	new_stmts = new_stmts ++ stmts_to_be_added
	new_stmts
  }

  def remove_all_duplicates(k: Circuit): Circuit = {
	var modules = k.modules
	var unique_modules = List[DefModule]()
	var module_set = Set[DefModule]()
	modules.foreach(
		x => {
			if(!module_set.contains(x)){
				unique_modules = unique_modules :+ x
				module_set += x
			}
		}
	)
	k.copy(modules=unique_modules)
  }
}
