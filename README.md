# Laser Attack Benchmark Suite (LABS) (Alpha)

Laser fault injection is one of the most dangerous means to introduce faults into a target system. Attackers can exploit faults induced by lasers to, for example, retrieve confidential information from an AES cryptographic algorithm or degrade neural network performance.

We propose the Laser Attack Benchmark Suite (LABS) that tries to complete the security evaluation loop against laser fault injection by allowing early testing and automatic redundancy integration. 

The official website of the Laser Attack Benchmark Suite can be found in this website [https://nus-labs.github.io/](https://nus-labs.github.io/).

## Getting Started
LABS requires sbt installed.
```
git clone https://github.com/nus-labs/labs
cd labs
git submodule update --init
```

## Configuration

### ClockAnnotation & ResetAnnotation
```code4
{
    "class":"labs.passes.ClockAnnotation",
    "target":"None.None.clk"
}
{
    "class":"labs.passes.ResetAnnotation",
    "target":"None.None.reset_n"
}
```
As Chisel3 and FIRRTL uses implicit clock and reset signals, the ClockAnnotation and ResetAnnotation are used to convert the clock and reset signals of the target FIRRTL design implemented in Verilog to the signals compatible with FIRRTL.

### FaultInjectionAnnotation
```code5
{
    "class":"chiffre.passes.FaultInjectionAnnotation",
    "target":"aes.aes_encipher_block.block_w0_reg",
    "id":"main",
    "injector":"chiffre.inject.ConditionInjectorCaller"
}
```
FaultInjectionAnnotation is used to insert a fault injector to a target component. The target component can be a register or wire.

### ScanChainAnnotation
```code6
{
    "class":"chiffre.passes.ScanChainAnnotation",
    "target":"aes.FaultController.scan",
    "ctrl":"master",
    "dir":"scan",
    "id":"main"
}
```
ScanChainAnnotation is used to indicate the location of the fault controller module. Normally, it is at <your_top_module_name>.FaultController.scan.

### FaultControllerAnnotation
```code7
{
    "class":"labs.passes.FaultControllerUDAnnotation",
    "target":"aes.aes_encipher_block.round_ctr_reg",
    "data_target":"h_a",
    "number_of_fires": 1,
    "affected_bits": [1]
}
```
FaultControllerAnnotation is used to configure the fault controller. The target and data\_target indicate the target signal to be observed and the value of the target signal to start injecting a fault respectively. The affected\_bits is used to indicate the bit locations of the signal to be affected by the fault from LSB. Note that this affected signal is the one indicated in the FaultInjectionAnnotation. FaultControllerUDAnnotation is for injecting user-defined faults. (TODO describe other annotations in this category)

### FaultTolerantAnnotation
```code8
{
    "class":"labs.passes.FaultTolerantDMRAnnotation",
    "target":"aes.aes_encipher_block.None"
}

{
    "class":"labs.passes.FaultTolerantTMRAnnotation",
    "target":"aes.aes_encipher_block.None"
}

{
    "class":"labs.passes.FaultTolerantTemporalAnnotation",
    "target":"aes.aes_encipher_block.None"
    "ready_signal":"ready"
    "start_signal":"next"
}
```
FaultTolerantAnnotation is used to integrate a fault-tolerant technique to the target design. Different annotations FaultTolerant<DMR/TMR/Temporal>Annotations are used to integrate double modular, triple modular and temporal redundancy respectively. The FaultTolerantTemporalAnnotation requires the user to indicate a ready signal when a computation is done and a start signal to start a computation.

## Integrate fault injection components to a design
To inject faults to a design, a fault injector and fault controller are required. The commands below are used to insert each of the components. 

### Add a controller

```code2
cd labs
sbt "runMain firrtl.stage.FirrtlMain -i <you_file_name>.fir -X middle --custom-transforms labs.passes.FaultControllerTransform -faf work/configuration.anno.json"
```

### Add a fault injector

```code3
cd labs
sbt "runMain firrtl.stage.FirrtlMain -i <your_file_name>.fir -X verilog --custom-transforms chiffre.passes.FaultInstrumentationTransform -faf work/configuration.anno.json"
```

## Integrate hardware-based redundancy

```code
cd labs
sbt "runMain firrtl.stage.FirrtlMain -i <your_file_name>.fir -X <low, verilog> --custom-transforms labs.passes.FaultTolerantTransform -faf work/configuration.anno.json"
```

