# aps-sequencer-prototype
This project contains the scripts and instructions for starting a sequencer and submitting sequences to the sequencer.

## Running Test 1
The test runs a single sequence step on a sequencer:
1) Sequencer started and registered with Location Service
2) Script loaded and initialized
3) Sequence submitted from esw-shell
4) onSetup handler fired and logged both messages
5) Step completed successfully

### Starting up and registering the sequencer
Step 1) CSW with location service must be running locally

Step 2) cd to project base and run sbt with no arguments.  This starts the sbt shell.

Step 3) From the sbt shell, run the command to load the sequence component with the script and register with location service"
```
sbt "runner/run sequencer -s APS -n primary -m APS_testmode"
```
This loads scripts/aps/Testmode.kts via the classpath config in scripts/aps/aps.conf and starts and registers the sequencer.

### Submit sequences using aps-sequence-submitter

```
cd [aps-sequence-submitter project home]
sbt run
```

## Next Steps
Submitting sequences from a user interface


