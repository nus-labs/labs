# Laser Attack Benchmark Suite (LABS) (Alpha)

The official website of the Laser Attack Benchmark Suite can be found in this website [https://nus-labs.github.io/](https://nus-labs.github.io/).

## Integrate hardware-based redundancy

```code
cd labs
sbt "runMain firrtl.stage.FirrtlMain -i <your_file_name>.fir -X <low, verilog> -- custom-transforms labs.passes.FaultTolerantTransform -faf work/configuration.anno.json"
```

## Integrate fault injection components to a design

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
