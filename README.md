# Laser Attack Benchmark Suite (LABS) (Alpha)

Laser fault injection is one of the most dangerous means to introduce faults into a target system. Attackers can exploit faults induced by lasers to, for example, retrieve confidential information from an AES cryptographic algorithm or degrade neural network performance.

We propose the Laser Attack Benchmark Suite (LABS) that tries to complete the security evaluation loop against laser fault injection by allowing early testing and automatic redundancy integration. 

The official website of the Laser Attack Benchmark Suite can be found in this website [https://nus-labs.github.io/](https://nus-labs.github.io/).

Detailed information can be found in a ICCAD 2020 paper, [https://dl.acm.org/doi/10.1145/3400302.3415646](Laser Attack Benchmark Suite).

```code99
@inproceedings{10.1145/3400302.3415646,
author = {Amornpaisannon, Burin and Diavastos, Andreas and Peh, Li-Shiuan and Carlson, Trevor E.},
title = {Laser Attack Benchmark Suite},
year = {2020},
isbn = {9781450380263},
publisher = {Association for Computing Machinery},
address = {New York, NY, USA},
url = {https://doi.org/10.1145/3400302.3415646},
doi = {10.1145/3400302.3415646},
ooktitle = {Proceedings of the 39th International Conference on Computer-Aided Design},
articleno = {50},
numpages = {9},
keywords = {hardware security, benchmark suite, laser fault attack, integrated circuits},
location = {Virtual Event, USA},
series = {ICCAD '20}
}
```

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

## Configurations
LABS uses JSONs to read a configuration. A configuration consists of a variety of annotation classes with different fields which are described below. Note that some classes may be different from the figure in the paper as LABS has been updated. The implementation of all the annotations for LABS can be found in /src/main/scala/labs/passes/Annotations.scala.

### ClockAnnotation and ResetAnnotation
As a FIRRTL design generated from Yosys may not be fully compatible with the FIRRTL compiler especially due t clock and reset ports, ClockAnnotation and ResetAnnotation are used to help.

```code5
  {
    "class":"labs.passes.ClockAnnotation",
    "target":"None.None.<clock_name>"
  }
```

ClockAnnotation has one field which requires the user to indicate the clock name of the entire design. LABS converts <clock\_name> to "clock" and makes the signal compatible with FIRRTL internal representation requirements.

```code6
  {
    "class":"labs.passes.ResetAnnotation",
    "target":"None.None.<reset_name>",
    "posedge_reset": <0/1>
  }
```

ResetAnnotation has two fields. The "target" field requires the user to indicate the reset name of the entire design. Similar to ClockAnnotation, in this example, LABS converts <reset\_name> to "reset" and makes the signal compatible with FIRRTL internal representation requirements. The "posedge\_reset" field is used to indicate whether the given design uses positive or negetive edge reset. All of the generated modules will have the same reset logic based on this.

### FaultInjectionAnnotation and ScanChainAnnotation
These two annotations are from a fault injection framework, [https://github.com/IBM/chiffre](Chiffre), that LABS relies on, which are used to integrate fault controllers and fault injectors. 

```code7
  {
    "class":"chiffre.passes.FaultInjectionAnnotation",
    "target":"<circuit_name>.<module_name>.<component_name>",
    "id":"main",
    "injector":"chiffre.inject.<injector_name>"
  }
```

FaultInjectionAnnotation is used to indicate the target component that will be attacked. The "target" field indicates the target component, and the "injector" field indicates the fault injector to be used.

```code8
  {
    "class":"chiffre.passes.ScanChainAnnotation",
    "target":"aes.FaultController.scan",
    "ctrl":"master",
    "dir":"scan",
    "id":"main"
  }
```

### FaultControllerAnnotation
FaultControllerAnnotation has 2 types, FaultControllerUDAnnotation and FaultControllerProbAnnotation, which are read to configure a fault controller. The former is used when using user-defined faults, and the latter is used when using random or probabilistic faults.

```code9
  {
    "class":"labs.passes.FaultControllerUDAnnotation",
    "target":["<circuit_name>.<module_name>.<component_name>", "<circuit_name>.<module_name>.<component_name>"],
    "data_target":["h_<value>", "h_<value>"],
    "affected_bits": [[0], [1], [2], [<indices_to_be_injected>]],
    "durations": [1, 1, 1, <duration_in_cycle>]
  }
```

FaultControllerUDAnnotation consists of 4 fields. The "target" field is used to indicate the signals to be observed by the fault controller. The "data\_target" field indicates the condition of each target signal. The fault controller sends a signal to a fault injector when all of the signals in the "target" field equal to its associated value (in hex) in the "data\_target" field. The "affected\_bits" specifies the indices of faults to be injected to the component indicated in FaultInjectionAnnotation, and the "durations" indicates how long each injection takes in cycles. In this case, the fault injector can fire at most 3 times when the conditions are met. The first fault is injected to the bit index 0, second fault to index 1 and third fault to index 2 with 1 cycle duration.

TODO Explain FaultControllerProbAnnotation

### FaultTolerantAnnotation
FaultTolerantAnnotation consists of 3 types, FaultTolerantDMRAnnotation, FaultTolerantTMRAnnotation and FaultTolerantTemporalAnnotation, to integrate DMR, TMR and temporal redundancy, respectively.

```code10
  {
    "class":"labs.passes.FaultTolerant<DMR/TMR>Annotation",
    "target":"<circuit_name>.<module_name>.<component_name>",
    "feedback_target":[<target_port>],
    "feedback":<0/1/2>
  }
```

To integrate DMR or TMR, 3 fields are needed. The "target" field indicates the target location that will be protected. The <component\_name> can be a component name or "None". If "None" is found, the entire <module\_name> will be protected. The "feedback\_target" field is used to indicate the ports that need a feedback mechanism. The "feedback" field indicates the feedback mechanism to be used. Currently, there are 3 feedback mechanisms, 0 no feedback mechanism, 1 static feedback mechanism and 2 random feedback mechanism.

TODO Explain FaultTolerantTemporalAnnotation
