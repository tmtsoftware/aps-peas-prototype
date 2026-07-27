# PeasComputationAssembly

## Overview
This project implements an Assembly that wraps the APS Algorithm Library using
TMT Common Software ([CSW](https://github.com/tmtsoftware/csw)) APIs.

Commands are handled by worker actors that implement the WorkerCommand interface.
This prototype implements three commands: ExecuteColorStep, ExecuteTtOffsetsToActs and ExecuteDecomposeActs.
Each is implemented as a worker that handles the named command.

Fortran computations are called from the AlgorithmLibrary class, referenced within each WorkerCommand implementation class.

A deeper discussion of the prototyping effort, including integration with the prototype procedure data service, is described
in [APS-PEAS PROTOTYPING AND RISK REDUCTION REPORT, section 4](https://docushare.tmt.org/docushare/dsweb/ServicesLib/Document-95089/History)

### WorkerCommands
#### Command Metadata
Each WorkerCommand implementation contains metadata about the parameters passed to the Fortran function:
* name - used to load and store values from Configuration or Result singletons
* class
* array shape or scalar indicator
* source - configuration, setup, constant, previous computation result (from service) or passed in command
* direction - input or output parameter

The WorkerCommand implementation classes each prepare the arguments to the AlgorithmLibrary class function call that worker is responsible for, calls the function and stores all output variable to the Result singleton.

Constants are loaded during initialization.  Setup and configuration parameters are loaded using commands: loadSetup, loadConfig.

#### ExecuteColorStep
A colorstep function command that gets inputs from configuration and outputs to procedure data service.
#### ExecuteTtOffsetsToActs
Executes ttOffsetsToActs, using non-command parameter inputs and outputs
#### ExecuteDecomposeActs
Executes decomposeActs using non-command parameter inputs including procedure data service to get inputs that were outputs of ttOffsetsToActs.

## Submodules (now part of the aps-peas-prototype root build)

* `peas-computation-assembly/assembly` (sbt project ID: `peasComputationAssembly`) -- the computation assembly that wraps the Algorithm Library
* `peas-computation-assembly/deploy` (sbt project ID: `peasComputationDeploy`) -- for starting assembly
* `peas-computation-assembly/client` (sbt project ID: `peasComputationClient`) -- used to send commands to the assembly for testing purposes

Note: there used to be an HCD submodule (`aps-computationprototypehcd`), but it
was a `.g8`-template remnant that never did anything -- removed in this pass.

## Upgrading CSW Version

`project/build.properties` (at the aps-peas-prototype root) contains the `csw.version` property.
Updating it makes sure that CSW services as well as library dependency for HCD and Assembly modules are using the same CSW version.

## Build Instructions

The build is based on sbt and depends on libraries generated from the
[csw](https://github.com/tmtsoftware/csw) project.

See [here](https://www.scala-sbt.org/1.0/docs/Setup.html) for instructions on installing sbt.

## CSW Prerequisites for running Components

The CSW services need to be running before starting the components.
This is done by starting the `csw-services`.
If you are not building csw from the sources, you can run `csw-services` as follows:

- Install `coursier` using steps described [here](https://tmtsoftware.github.io/csw/apps/csinstallation.html) and add TMT channel.
- Run `cs install csw-services`. This will create an executable file named `csw-services` in the default installation directory.
- Run `csw-services start` command to start all the CSW services i.e. Location, Config, Event, Alarm and Database Service
- Run `csw-services --help` to get more information.

Note: while running the csw-services use the csw version from `project/build.properties`

## Algorithm Library Jar dependency

Declared in the root `project/Libs.scala` as `Libs.algorithm-lib` (`org.tmt.aps.peas` % `algorithm-lib` % `1.0.0`),
pulled in by `peasComputationAssembly` in the root `build.sbt`.

**Unresolved from this migration:** the original build.sbt only declared a `jitpack` resolver,
which doesn't appear to host this artifact. There may be a private/internal resolver configured
in your global sbt settings (e.g. `~/.sbt/1.0/repositories`) that isn't visible from the repo
itself -- worth confirming this still resolves before relying on a clean build.

The shared settings previously defined in `project/Common.scala` (organization, scalacOptions,
`mavenLocal` resolver, `/opt/apps/lib` on `java.library.path`, etc.) are now inlined as
`computationAssemblyFamilySettings` in the root `build.sbt`, applied explicitly to the four
submodules in this family only.

## Running the Assembly

Run the container cmd script with arguments. For example:

* Run the Assembly in a standalone mode with a local config file (the standalone config format is different than the container format):

```
sbt "peasComputationDeploy/runMain peas.computationdeploy.ComputationDeployContainerCmdApp --local ./peas-computation-assembly/deploy/src/main/resources/JComputationAssemblyStandalone.conf"
```

## Running the Test Client

Run the app using sbt:

```
sbt "peasComputationClient/run"
```
