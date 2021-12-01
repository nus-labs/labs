package labs

import chisel3._
import chiffre._
import chisel3.experimental.chiselName
import labs.passes._

@chiselName
class FaultController(var input_width: List[Int], var datatarget: List[String], var affected_bits: List[List[Int]], var faulty_width: Int, var probability: Int, var probabilistic: Boolean, delays: List[Int]) extends Module with chiffre.ChiffreController {
        lazy val scanId = "main"
        val io = IO(new Bundle{
                val data_in = Input(Vec(input_width.length, UInt(input_width.max.W)))
				
        })

		val r = scala.util.Random

    def random_config( faulty_width: Int) : String = { // assume Lfsr16
		val arr = Seq("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F")
		val length = faulty_width * 2
		var out = ""
		val r = scala.util.Random
		
		for(i <- 0 to length - 1){
			if(i % 2 == 0){ // difficulty
				out = "7FFF" + out
			}
			else{ // seed
				var seed = ""
				for(j <- 0 to 3){
					val randnumber = r.nextInt(16)
					seed = seed + arr(randnumber)
				}
				out = seed + out
			}
		}
		out = "h_" + out
		return out
	}

    def probabilistic_config(probability: Int) : String = { // assume Lfsr16
		val arr = Seq("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F")
		val length = faulty_width * 2
		var out = ""
		val r = scala.util.Random
		val scaled_probability = 655.35 * probability
		var scaled_probability_int = scaled_probability.toInt
		for(i <- 0 to length - 1){
			if(i % 2 == 0){ // difficulty
				var hex_value = "%04X".format(scaled_probability_int)
				out = hex_value + out
			}
			else{ // seed
				var seed = ""
				for(j <- 0 to 3){
					val randnumber = r.nextInt(16)
					seed = seed + arr(randnumber)
				}
				out = seed + out
			}
		}
		out = "h_" + out
		return out
	}


	def choose_bit( affected_bits: List[Int], width:Int ) : String = {
		var out = ""
		for(i <- 0 to width - 1){
			if (affected_bits.contains(i)){
				out = "1" + out
			}
			else{
				out = "0" + out
			}
		}	
		out = "b" + out
		return out	
	}

	if(affected_bits.length != delays.length) new Exception("affected_bits and delays must have the same length")

	val resetB = if (global_edge_reset.posedge_reset == 0) ~reset.asBool else reset.asBool
	withReset(resetB){
	val lfsr_length = 16

  	val datalength = if(probabilistic) faulty_width * 2 * 16 else if (affected_bits.size != 0) faulty_width else faulty_width * 2 * 16
	val configuration = if(probabilistic) probabilistic_config(probability).U else if (affected_bits.size != 0) affected_bits.map(choose_bit(_, faulty_width).U) else random_config(faulty_width).U     

    // put logics here
	val max_delay_width = delays.max.U.getWidth.W
	val delay_temp = for(i <- 0 to delays.length - 1) yield {
		val reg = RegInit(delays(i).U)
		reg
	}
	val delay_vec = VecInit(delay_temp)
	val delay_count = RegInit(0.U(max_delay_width))
	val delay_idx = RegInit(0.U(delays.length.U.getWidth.W + 1.W))
	val move_delay_idx = delay_count === delay_vec(delay_idx)
	delay_idx := Mux(move_delay_idx, delay_idx + 1.U, delay_idx)

    val count = RegInit(0.U(datalength.U.getWidth.W)) // used to send configuration information to an injector
	val check = count =/= datalength.U
	val delay_check = delay_count =/= delay_vec(delay_idx)

	val data_in_check = for(i <- 0 to input_width.length - 1) yield {
		val check_temp = io.data_in(i) === datatarget(i).U
		check_temp
	}
	val result_data_in_check = VecInit(data_in_check).reduce(_ && _)

   	//val fire = (check === false.B) & (io.data_in === datatarget.U) & (delay_check)
	val fire = (check === false.B) & (result_data_in_check === true.B) & (delay_check)

	count := Mux(move_delay_idx, 0.U,
				Mux(count === datalength.U, count, count + 1.U))
	delay_count := Mux(fire, delay_count + 1.U,
					Mux(move_delay_idx, 0.U, delay_count))

	val number_of_fires = affected_bits.length
    val count_fires = RegInit(0.U(number_of_fires.U.getWidth.W)) // used to count the number of fires
    count_fires := Mux(count_fires === number_of_fires.U, count_fires, count_fires + move_delay_idx)

    val stop_fire = Wire(Bool())
    stop_fire := Mux(check, 0.U,
                    Mux(count_fires === number_of_fires.U, 1.U, 0.U))

    if(affected_bits.size == 0 || probabilistic){
		val conf = RegInit(configuration.asInstanceOf[UInt])
		conf := Mux(check, conf.asInstanceOf[UInt] >> 1.U, conf)
    	scan.out := Mux(fire & !stop_fire, 0.U,
                    Mux(conf.asInstanceOf[UInt](0) , 1.U, 0.U)) 
	}
	else{
		val conf_temp = for(i <- 0 to affected_bits.length - 1) yield {
			val reg = RegInit((configuration.asInstanceOf[List[chisel3.UInt]])(i))
			reg := Mux(check && count_fires === i.U, reg >> 1.U, reg)
			reg
		}
		val conf = VecInit(conf_temp)
		scan.out := Mux(fire & !stop_fire, 0.U,
                    Mux(conf(count_fires)(0) , 1.U, 0.U)) 

	}

    scan.clk := check

	scan.en := Mux(fire & !stop_fire, 1.U, 0.U)

	when (fire & !stop_fire){
		printf(s"""|[info] Fire!""")
	}
	}
}
