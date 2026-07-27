# PeasComputationDeploy

This module contains apps and configuration files for host deployment using
HostConfig (https://tmtsoftware.github.io/csw/apps/hostconfig.html) and
ContainerCmd (https://tmtsoftware.github.io/csw/framework/deploying-components.html).

An important part of making this work is ensuring the host config app (`ComputationDeployHostConfigApp`) is built
with all of the necessary dependencies of the components it may run. In the consolidated root `build.sbt`,
this is done via:

```scala
lazy val peasComputationDeploy = project
  .in(file("peas-computation-assembly/deploy"))
  .dependsOn(peasComputationAssembly)
  .enablePlugins(CswBuildInfo)
  .settings(computationAssemblyFamilySettings: _*)
  .settings(
    name := "peas-computation-deploy",
    libraryDependencies ++= Seq(
      Libs.`csw-framework`,
      Libs.`csw-testkit` % Test
    )
  )
```

Note: there is no HCD in this project anymore -- the original `computationprototypehcd`
module was a `.g8`-template remnant that never did anything, and was removed.
`peasComputationDeploy` now depends only on `peasComputationAssembly`.

Note: the CSW Location Service must be running before starting the components.
See https://tmtsoftware.github.io/csw/apps/cswlocationserver.html .
