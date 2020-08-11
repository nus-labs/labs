package labs

import chisel3._
import chiffre._

class FaultController(var input_width: Int, var datatarget: String, var number_of_fires: Int, var affected_bits: List[Int], var faulty_width: Int) extends Module with chiffre.ChiffreController {
        lazy val scanId = "main"
        val io = IO(new Bundle{
                val data_in = Input(UInt(input_width.W))
        })

		val r = scala.util.Random

	def rand( n:Int, limit:Int, size:Int) : String = { 
		val arr = Seq("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F")
  		val r = scala.util.Random
		//val idx = r.nextInt(limit)
		val idx = limit
  		var out = "h_"
  		for(i <- 0 to n - 1){
			if( i >= size * idx & i < size * idx + size ){
    			//out = out.concat(arr(r.nextInt(arr.length)))
				out = out.concat("0")
			}
			else{
				out = out.concat(arr(arr.length - 1))
			}
		}
		return out
	}

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
	val resetB = ~reset.asBool
	//withReset (resetB){
	val lfsr_length = 16
	//val number_of_injectors = faulty_width
	//val datatarget = "h_800011c8".U
    //val number_of_fires = 4096.U
	//val bits = 11// bits that will be affected from idx 0

	val condition = "h_80001b70".U
//	val set = r.nextInt(16).U

    //val datalength = 2 * lfsr_length * number_of_injectors
  	val datalength = if (affected_bits.size != 0) faulty_width else faulty_width * 2 * 16
	val configuration = if (affected_bits.size != 0) choose_bit( affected_bits, faulty_width ).U else random_config(faulty_width).U     

	println("RANDOMCONFIG")
	println(configuration)
    // put logics here

//	val number_of_sets = RegInit(0.U(set.getWidth.W))
//	number_of_sets := Mux(condition === io.data_in, number_of_sets + 1.U, number_of_sets)

	//val condition_met = RegInit(0.U(1.W))
	//condition_met := Mux(io.data_in === "h_80001bc0".U, 0.U,
						//Mux(condition === io.data_in, 1.U, condition_met))

	val fire = (scan.clk === false.B) & (io.data_in === datatarget.U)// & condition_met.asBool // & set === number_of_sets

        val count = RegInit(0.U(datalength.U.getWidth.W)) // used for sending configuration information to an injector
        count := Mux(count === datalength.U, count, count + 1.U)

        val conf = RegInit(configuration)
        conf := Mux(scan.clk, conf >> 1.U, conf)

        //val fire = scan.clk === false.B & (io.data_in === datatarget | io.data_in === datatarget2 )

        val count_fires = RegInit(0.U(number_of_fires.U.getWidth.W)) // used for counting number of fires (in this case it is 16)
        count_fires := Mux(count_fires === number_of_fires.U, count_fires, count_fires + fire)

        val stop_fire = Wire(Bool())
        stop_fire := Mux(scan.clk, 0.U,
                        Mux(count_fires === number_of_fires.U, 1.U, 0.U))

        scan.out := Mux(fire & !stop_fire, 0.U,
                        Mux(conf(0) , 1.U, 0.U)) 

        scan.clk := count =/= datalength.U

	scan.en := Mux(fire & !stop_fire, 1.U, 0.U)

	when (fire & !stop_fire){
		printf(s"""|[info] Fire!""")
	}
	//}
}
