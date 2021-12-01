module Detector(
  input  [31:0] io_in1,
  input  [31:0] io_in2,
  output        io_detect
);
  wire [31:0] xored = io_in1 ^ io_in2; // @[Detector.scala 20:25]
  assign io_detect = xored != 32'h0; // @[Detector.scala 21:19]
endmodule
module Preventer(
  input         clock,
  input         reset,
  input  [31:0] io_in,
  output [31:0] io_out,
  input         io_detect
);
  wire  resetB = ~reset; // @[Preventer.scala 21:16]
  reg  _T_1; // @[Preventer.scala 23:31]
  reg [31:0] _RAND_0;
  wire  _T_2 = io_detect | _T_1; // @[Preventer.scala 24:24]
  assign io_out = _T_1 ? 32'h0 : io_in; // @[Preventer.scala 26:38]
`ifdef RANDOMIZE_GARBAGE_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_INVALID_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_REG_INIT
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_MEM_INIT
`define RANDOMIZE
`endif
`ifndef RANDOM
`define RANDOM $random
`endif
`ifdef RANDOMIZE_MEM_INIT
  integer initvar;
`endif
`ifndef SYNTHESIS
initial begin
  `ifdef RANDOMIZE
    `ifdef INIT_RANDOM
      `INIT_RANDOM
    `endif
    `ifndef VERILATOR
      `ifdef RANDOMIZE_DELAY
        #`RANDOMIZE_DELAY begin end
      `else
        #0.002 begin end
      `endif
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _RAND_0 = {1{`RANDOM}};
  _T_1 = _RAND_0[0:0];
  `endif // RANDOMIZE_REG_INIT
  `endif // RANDOMIZE
end // initial
`endif // SYNTHESIS
  always @(posedge clock) begin
    if (resetB) begin
      _T_1 <= 1'h0;
    end else begin
      _T_1 <= _T_2;
    end
  end
endmodule
module OutputReg(
  input         clock,
  input         reset,
  input  [31:0] io_in,
  input         io_ready,
  output [31:0] io_out,
  input  [1:0]  io_ctrl,
  output        io_detect
);
  wire [31:0] detector_io_in1; // @[OutputReg.scala 30:30]
  wire [31:0] detector_io_in2; // @[OutputReg.scala 30:30]
  wire  detector_io_detect; // @[OutputReg.scala 30:30]
  wire  preventer_clock; // @[OutputReg.scala 36:39]
  wire  preventer_reset; // @[OutputReg.scala 36:39]
  wire [31:0] preventer_io_in; // @[OutputReg.scala 36:39]
  wire [31:0] preventer_io_out; // @[OutputReg.scala 36:39]
  wire  preventer_io_detect; // @[OutputReg.scala 36:39]
  wire  controller_clock;
  wire  controller_reset;
  wire [31:0] controller_io_data_in;
  wire  resetB = ~reset; // @[OutputReg.scala 23:22]
  reg [31:0] stored_output_0; // @[OutputReg.scala 26:36]
  reg [31:0] _RAND_0;
  reg [31:0] stored_output_1; // @[OutputReg.scala 26:36]
  reg [31:0] _RAND_1;
  wire  _T_2 = io_ctrl == 2'h1; // @[OutputReg.scala 28:49]
  wire  _T_3 = _T_2 & io_ready; // @[OutputReg.scala 28:63]
  wire  _T_5 = io_ctrl == 2'h2; // @[OutputReg.scala 28:49]
  wire  _T_6 = _T_5 & io_ready; // @[OutputReg.scala 28:63]
  wire  _T_8 = io_ctrl != 2'h2; // @[OutputReg.scala 33:54]
  wire  _T_9 = detector_io_detect & _T_8; // @[OutputReg.scala 33:43]
  Detector detector ( // @[OutputReg.scala 30:30]
    .io_in1(detector_io_in1),
    .io_in2(detector_io_in2),
    .io_detect(detector_io_detect)
  );
  Preventer preventer ( // @[OutputReg.scala 36:39]
    .clock(preventer_clock),
    .reset(preventer_reset),
    .io_in(preventer_io_in),
    .io_out(preventer_io_out),
    .io_detect(preventer_io_detect)
  );
  FaultController controller (
    .clock(controller_clock),
    .reset(controller_reset),
    .io_data_in(controller_io_data_in)
  );
  assign io_out = preventer_io_out; // @[OutputReg.scala 39:24]
  assign io_detect = _T_9 & io_ready; // @[OutputReg.scala 34:19]
  assign detector_io_in1 = stored_output_0; // @[OutputReg.scala 31:25]
  assign detector_io_in2 = stored_output_1; // @[OutputReg.scala 32:25]
  assign preventer_clock = clock;
  assign preventer_reset = ~reset;
  assign preventer_io_in = io_in; // @[OutputReg.scala 37:33]
  assign preventer_io_detect = _T_9 & io_ready; // @[OutputReg.scala 38:37]
  assign controller_clock = clock;
  assign controller_reset = reset;
  assign controller_io_data_in = stored_output_0;
`ifdef RANDOMIZE_GARBAGE_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_INVALID_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_REG_INIT
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_MEM_INIT
`define RANDOMIZE
`endif
`ifndef RANDOM
`define RANDOM $random
`endif
`ifdef RANDOMIZE_MEM_INIT
  integer initvar;
`endif
`ifndef SYNTHESIS
initial begin
  `ifdef RANDOMIZE
    `ifdef INIT_RANDOM
      `INIT_RANDOM
    `endif
    `ifndef VERILATOR
      `ifdef RANDOMIZE_DELAY
        #`RANDOMIZE_DELAY begin end
      `else
        #0.002 begin end
      `endif
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _RAND_0 = {1{`RANDOM}};
  stored_output_0 = _RAND_0[31:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_1 = {1{`RANDOM}};
  stored_output_1 = _RAND_1[31:0];
  `endif // RANDOMIZE_REG_INIT
  `endif // RANDOMIZE
end // initial
`endif // SYNTHESIS
  always @(posedge clock) begin
    if (resetB) begin
      stored_output_0 <= 32'h0;
    end else if (_T_3) begin
      stored_output_0 <= io_in;
    end
    if (resetB) begin
      stored_output_1 <= 32'h0;
    end else if (_T_6) begin
      stored_output_1 <= io_in;
    end
  end
endmodule
module FaultController(
  input         clock,
  input         reset,
  input  [31:0] io_data_in
);
  wire  resetB = ~reset; // @[FaultController.scala 76:22]
  reg  count; // @[FaultController.scala 84:24]
  reg [31:0] _RAND_0;
  wire  _T_3 = count + 1'h1; // @[FaultController.scala 85:55]
  wire  check = ~count; // @[FaultController.scala 87:27]
  wire  _T_5 = ~check; // @[FaultController.scala 88:27]
  wire  _T_6 = io_data_in == 32'ha; // @[FaultController.scala 88:54]
  wire  fire = _T_5 & _T_6; // @[FaultController.scala 88:40]
  reg  count_fires; // @[FaultController.scala 93:30]
  reg [31:0] _RAND_1;
  wire  _T_11 = count_fires + fire; // @[FaultController.scala 94:84]
  wire  stop_fire = check ? 1'h0 : count_fires; // @[FaultController.scala 98:21]
  wire  _T_16 = ~stop_fire; // @[FaultController.scala 101:28]
  wire  _T_17 = fire & _T_16; // @[FaultController.scala 101:26]
`ifdef RANDOMIZE_GARBAGE_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_INVALID_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_REG_INIT
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_MEM_INIT
`define RANDOMIZE
`endif
`ifndef RANDOM
`define RANDOM $random
`endif
`ifdef RANDOMIZE_MEM_INIT
  integer initvar;
`endif
`ifndef SYNTHESIS
initial begin
  `ifdef RANDOMIZE
    `ifdef INIT_RANDOM
      `INIT_RANDOM
    `endif
    `ifndef VERILATOR
      `ifdef RANDOMIZE_DELAY
        #`RANDOMIZE_DELAY begin end
      `else
        #0.002 begin end
      `endif
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _RAND_0 = {1{`RANDOM}};
  count = _RAND_0[0:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_1 = {1{`RANDOM}};
  count_fires = _RAND_1[0:0];
  `endif // RANDOMIZE_REG_INIT
  `endif // RANDOMIZE
end // initial
`endif // SYNTHESIS
  always @(posedge clock) begin
    if (reset) begin
      count <= 1'h0;
    end else if (!(count)) begin
      count <= _T_3;
    end
    if (reset) begin
      count_fires <= 1'h0;
    end else if (!(count_fires)) begin
      count_fires <= _T_11;
    end
    `ifndef SYNTHESIS
    `ifdef PRINTF_COND
      if (`PRINTF_COND) begin
    `endif
        if (_T_17 & resetB) begin
          $fwrite(32'h80000002,"|[info] Fire!"); // @[FaultController.scala 109:23]
        end
    `ifdef PRINTF_COND
      end
    `endif
    `endif // SYNTHESIS
  end
endmodule
