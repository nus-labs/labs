// Please change the values here
`define PROBE testDesign.something // used to check when to collect data
`define PROBE2 testDesign.something // used to collect data
`define TARGET_VALUE 34'h01111
`define CONDITION `PROBE == `TARGET_VALUE
`define RESET_DELAY 777.7
`define CLOCK_PERIOD 1


module TestDriver;

  reg clock = 1'b0;
  reg reset = 1'b1;

  always #(`CLOCK_PERIOD/2.0) clock = ~clock;

  reg [63:0] max_cycles = 0;
  reg [63:0] trace_count = 0;
  int unsigned rand_value;

  integer f;
  reg isprinted;

  initial
  begin
    f = $fopen("./result.txt", "w");
    void'($value$plusargs("max-cycles=%d", max_cycles));
	$dumpfile("waveform.vcd");
	$dumpvars(0, TestDriver);
    rand_value = $urandom;
    rand_value = $random(rand_value);
  end

  reg [255:0] reason = "";
  reg failure = 1'b0;
  wire success;
  integer stderr = 32'h80000002;

  reg[127:0] faulty_result;
  reg[63:0] correct_result;
  reg[7:0] counter;

  TestDesign testDesign(
    .clock(clock),
    .reset(reset),
    .io_success(success)
  ); 

  always @(posedge clock)
  begin

    trace_count = trace_count + 1;
    if (!reset)
    begin
      if (max_cycles > 0 && trace_count > max_cycles)
      begin
        reason = " (timeout)";
        failure = 1'b1;
      end

      if (counter >= 1 && (success || failure)) begin // execution can be replayed!
		$fclose(f);
      end
      
      if (failure)
      begin
		if(counter == 0) begin
			$fwrite(f, "No response from the system\n");
			$fclose(f);       
			isprinted = 1;
		end
        $fatal;
      end

      if (success)
      begin
		if(counter == 0) begin
	  		$fwrite(f, "No response from the system\n");
	  		$fclose(f);  
		end
        $finish;
       end
    end
  end

  reg has_written = 0;

	always @ (posedge clock)  begin
		if(reset) begin
			faulty_result = 0;
		end

		else if((`CONDITION)) begin
			isprinted = 1;
			if(!has_written) begin
				$fwrite(f, "%0h", `PROBE2); // this may be changed depending on the size of PROBE2
				has_written = 1;
			end
		end

		else if(!(`CONDITION)) begin
			has_written = 0;
		end
	end

	always @ (posedge clock) begin
		if(reset) begin
			counter = 0;
		end
	
		else if (`CONDITION)begin
			counter = counter + 1;
		end
	end
	
	initial begin
		reset = 1;
		#(`RESET_DELAY) reset = 0;
		isprinted = 0;
	end
endmodule
