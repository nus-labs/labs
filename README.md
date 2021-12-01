# Laser Attack Benchmark Suite (LABS) (Alpha)

Laser fault injection is one of the most dangerous means to introduce faults into a target system. Attackers can exploit faults induced by lasers to, for example, retrieve confidential information from an AES cryptographic algorithm or degrade neural network performance.

We propose the Laser Attack Benchmark Suite (LABS) that tries to complete the security evaluation loop against laser fault injection by allowing early testing and automatic redundancy integration. 

The official website of the Laser Attack Benchmark Suite can be found in this website [https://nus-labs.github.io/](https://nus-labs.github.io/).

## Convert a Verilog design to FIRRTL
LABS is implemented inside the FIRRTL compiler. Thus, Verilog to FIRRTL conversion is needed. Yosys, a framework for RTL synthesis, supports this conversion and is used in this framework. LABS provides a TCL script to automate the conversion inside yosys\_script.tcl. The script can also be modified based on your needs. To input a number of files to Yosys, an asterisk can also be used, for example /<path>/\*, to provide all files inside the path to Yosys.

```code3
cd labs
./labs -a yosys -i <your_design_name>.v -c yosys_script.tcl -d <output_directory>
```

## Convert a Chisel3 design to FIRRTL
Similarly, a Chisel3 design is required to be converted to FIRRTL. As Chisel3 is integrated inside the FIRRTL compiler, this can be done by calling the function in the Chisel3 library. You can add the code below and call _sbt_. For newer Chisel3, this might be different. Take a look at the Chisel3 API documentation to learn more.

```code4
import chisel3._
object Driver extends App {
  chisel3.Driver.toFirrtl(chisel3.Driver.elaborate(() => new <your_top_design_class>(<parameters>)))
}
```

## Integrate hardware-based redundancy
The command below integrates a hardware-based redundancy technique to a FIRRTL file. Note that -d and -x arguments are optional. The default values of -d and -x are the current directory and verilog, respectively. Normally, "-x low" is used when more than one redundancy techniques are used, which informs LABS to generate a low FIRRTL instead of a Verilog file that can be rerun with the same command to add another redundancy technique.

```code
cd labs
./labs -a protect -i <your_design_name>.fir -c <your_configuration_file>.anno.json -d <output_directory> -x <low/verilog>
```

## Integrate fault injection components to a design

### Add a controller and fault injector
The command below integrates a fault controller and injector to a FIRRTL file. 

```code2
cd labs
./labs -a inject -i <your_design_name>.fir -c <your_configuration_file>.anno.json -d <output_directory> -x <low/verilog>
```
