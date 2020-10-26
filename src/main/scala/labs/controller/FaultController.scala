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
	
	val lfsr_length = 16
	val condition = "h_80001b70".U

  	val datalength = if (affected_bits.size != 0) faulty_width else faulty_width * 2 * 16
	val configuration = if (affected_bits.size != 0) choose_bit( affected_bits, faulty_width ).U else random_config(faulty_width).U     

    // put logics here

	val fire = (scan.clk === false.B) & (io.data_in === datatarget.U)// & condition_met.asBool // & set === number_of_sets

    val count = RegInit(0.U(datalength.U.getWidth.W)) // used to send configuration information to an injector
    count := Mux(count === datalength.U, count, count + 1.U)

    val conf = RegInit(configuration)
    conf := Mux(scan.clk, conf >> 1.U, conf)

    val count_fires = RegInit(0.U(number_of_fires.U.getWidth.W)) // used to count the number of fires
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
	
}
