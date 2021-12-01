package labs

import chisel3._
import chisel3.util._
import chiffre._
import java.io.File
import labs.passes._

sealed class nByteParityBitGeneratorSBoxIo(val byteWidth: Int) extends Bundle{
    val in_after_sbox = Input(UInt((byteWidth * 8).W))
	val in_before_sbox = Input(UInt((byteWidth * 8).W))
	val in_parity = Input(UInt(16.W)) // from addroundkey generator
	val write_sbox = Input(UInt(1.W)) // update sbox
	val write = Input(UInt(1.W)) // next round
    val parity = Output(UInt(16.W))
}

class nByteParityBitGeneratorSBox(val byteWidth: Int) extends Module{
	val io = IO(new nByteParityBitGeneratorSBoxIo(byteWidth))
    val resetB = if(global_edge_reset.posedge_reset == 0) ~reset.toBool else reset.toBool
	withReset(resetB){

	val parity_generators_after_sbox = for(i <- 0 to 16 - 1) yield
	{
		val one_byte_parity_generator = Module(new ParityBitGenerator(8))
		one_byte_parity_generator
	}

	val parity_generators_before_sbox = for(i <- 0 to 16 - 1) yield
	{
		val one_byte_parity_generator = Module(new ParityBitGenerator(8))
		one_byte_parity_generator
	}

	val counter = RegInit(0.U(((128/byteWidth/8) - 1).U.getWidth.W))
	counter := Mux(io.write_sbox === 1.U, counter + 1.U, counter)

	val result_after_sbox = RegInit(VecInit(Seq.fill(128/byteWidth/8)(0.U((byteWidth * 8).W)))) // assume overflow
	for(i<-0 to (128/byteWidth/8) - 1){
		result_after_sbox((128/byteWidth/8) - 1 - i) := Mux(io.write_sbox === 1.U && counter === i.U, io.in_after_sbox, result_after_sbox((128/byteWidth/8) - 1 - i))
	}

	val result_before_sbox = RegInit(VecInit(Seq.fill(128/byteWidth/8)(0.U((byteWidth * 8).W)))) // assume overflow
	for(i<-0 to (128/byteWidth/8) - 1){
		result_before_sbox((128/byteWidth/8) - 1 - i) := Mux(io.write_sbox === 1.U && counter === i.U, io.in_before_sbox, result_before_sbox((128/byteWidth/8) - 1 - i))
	}

	val result_after_sbox_bytes = Wire(Vec(16, (UInt(8.W))))
	val result_before_sbox_bytes = Wire(Vec(16, (UInt(8.W))))
	for(i<-0 to 15){
		result_after_sbox_bytes(i) := VecInit(result_after_sbox.asUInt.toBools.slice(i * 8, (i+1) * 8)).asUInt
		result_before_sbox_bytes(i) := VecInit(result_before_sbox.asUInt.toBools.slice(i * 8, (i+1) * 8)).asUInt
	}

	for(i <- 0 to 15){
		parity_generators_after_sbox(i).io.in := result_after_sbox_bytes(i)
		parity_generators_before_sbox(i).io.in := result_before_sbox_bytes(i)
	}

	val last_round_parity_reg = RegInit(0.U(16.W))
	last_round_parity_reg := Mux(io.write === 1.U, io.in_parity, last_round_parity_reg)

	val last_round_parity_bits = VecInit(last_round_parity_reg.toBools)
	val fault_detection_result = Wire(Vec(16, UInt(1.W)))
	for(i<-0 to 15){
		fault_detection_result(i) :=  parity_generators_before_sbox(i).io.parity ^ last_round_parity_bits(i)
	}

	// care fault_detected only at the end of each round
	val fault_detected = Wire(UInt(1.W))
	fault_detected := fault_detection_result.asUInt.orR && io.write === 1.U

	val parity_generators_after_sbox_output_bits = Wire(Vec(16,UInt(1.W)))
	for(i <- 0 to 15){
		parity_generators_after_sbox_output_bits(i) := parity_generators_after_sbox(i).io.parity
	}
	io.parity := Mux(fault_detected === 1.U, !parity_generators_after_sbox_output_bits.asUInt, parity_generators_after_sbox_output_bits.asUInt)
	}
}
