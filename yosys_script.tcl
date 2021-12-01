yosys read_verilog $::env(VERILOG_FILES) 
yosys hierarchy -auto-top
yosys setattr -set keep 1
yosys proc
#yosys opt
yosys async2sync
#yosys opt
yosys write_firrtl TestDesign.fir
