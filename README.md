# Laser Attack Benchmark Suite (LABS) (Alpha)
## Integrate hardware-based redundancy
cd labs
sbt "runMain firrtl.stage.FirrtlMain -i <your_file_name>.fir -X <low, verilog> -- custom-transforms labs.passes.FaultTolerantTransform -faf work/configuration.anno.json"
## Integrate fault injection components to a design
### Add a controller
cd labs
sbt "runMain firrtl.stage.FirrtlMain -i <you_file_name>.fir -X middle --custom-transforms labs.passes.FaultControllerTransform -faf work/configuration.anno.json"
### Add a fault injector
cd labs
sbt "runMain firrtl.stage.FirrtlMain -i <your_file_name>.fir -X verilog --custom-transforms chiffre.passes.FaultInstrumentationTransform -faf work/configuration.anno.json"
