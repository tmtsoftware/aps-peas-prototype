import kotlin.Keys._

// aps-peas-prototype root build
//
// Consolidates: aps-sequencer-prototype, aps-computation-assembly-prototype
// (4 submodules), aps-procedure-data-service, and the aps-submitter-prototype
// backend into one sbt build. peas-web-application (React/TS, was the
// aps-submitter-prototype frontend) is NOT part of this build -- it's a
// sibling npm project, built independently (per Scott's decision to keep
// builds fully decoupled).

ThisBuild / version := "0.1.0-SNAPSHOT"

val KotlincOptions = Seq(
  "-opt-in=kotlin.time.ExperimentalTime",
  "-Xallow-any-scripts-in-source-roots",
  "-Xuse-fir-lt=false",
  "-jvm-target",
  "21"
)
val KotlinVersion = "2.1.10"

// -----------------------------------------------------------------------------
// Shared settings for the PeasComputationAssembly family (assembly/deploy/
// client) -- ported from aps-computation-assembly-prototype's Common.scala,
// but applied explicitly only to these four projects rather than as a
// build-wide AutoPlugin, so it doesn't silently change defaults for the
// sequencer/data-service/setup-service projects that never had these settings.
// -----------------------------------------------------------------------------
lazy val computationAssemblyFamilySettings = Seq(
  organization := "com.github.tmtsoftware.peascomputationassembly",
  organizationName := "TMT",
  scalaVersion := "3.6.4",
  scalacOptions ++= Seq("-encoding", "UTF-8", "-feature", "-unchecked", "-deprecation"),
  Compile / doc / javacOptions ++= Seq("-Xdoclint:none"),
  Test / testOptions ++= Seq(
    Tests.Argument("-oDF"),
    Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
  ),
  resolvers += "jitpack" at "https://jitpack.io",
  resolvers += Resolver.mavenLocal,
  fork := true,
  javaOptions += "-Djava.library.path=/opt/apps/lib",
  Test / parallelExecution := false,
  autoCompilerPlugins := true
)

lazy val root = (project in file("."))
  .aggregate(
    apsSequencerScripts,
    apsSequencerScriptsRunner,
    peasComputationAssembly,
    peasComputationDeploy,
    peasComputationClient,
    peasProcedureDataService,
    peasProcedureSetupService,
    peasExposureService
  )
  .settings(
    name := "aps-peas-prototype",
    publish / skip := true
  )

// =============================================================================
// ApsSequencerScripts (Kotlin ESW sequencer scripts + Scala runner)
// Kept as "Aps..." per Scott's explicit call -- everything else uses "Peas".
// =============================================================================

lazy val apsSequencerScripts = project
  .in(file("aps-sequencer-scripts/scripts"))
  .enablePlugins(KotlinPlugin)
  .settings(
    name             := "aps-sequencer-scripts",
    organization     := "org.tmt.aps",
    scalaVersion     := "3.6.4",
    kotlinVersion    := KotlinVersion,
    kotlincJvmTarget := "21",
    kotlincOptions ++= KotlincOptions,
    kotlinLib("stdlib"),
    resolvers += "jitpack" at "https://jitpack.io",
    Compile / unmanagedSourceDirectories  := Seq(baseDirectory.value),
    Compile / unmanagedSources / excludeFilter := "*.conf",
    Compile / unmanagedResourceDirectories := Seq(baseDirectory.value),
    Compile / unmanagedResources / includeFilter := "*.conf",
    Compile / unmanagedResources / excludeFilter :=
      (Compile / unmanagedResources / excludeFilter).value || new SimpleFileFilter(_.getCanonicalPath.contains("/target/")),
    libraryDependencies ++= Seq(
      Libs.`esw-ocs-dsl-kt`,
      Libs.`esw-ocs-app`
    )
  )

lazy val apsSequencerScriptsRunner = project
  .in(file("aps-sequencer-scripts/runner"))
  .dependsOn(apsSequencerScripts)
  .settings(
    name         := "aps-sequencer-scripts-runner",
    organization := "org.tmt.aps",
    scalaVersion := "3.6.4",
    resolvers += "jitpack" at "https://jitpack.io",
    libraryDependencies ++= Seq(
      Libs.`esw-ocs-app`
    ),
    Compile / mainClass := Some("aps.SequencerMain"),
    fork := true
  )

// =============================================================================
// PeasComputationAssembly family
// =============================================================================

lazy val peasComputationAssembly = project
  .in(file("peas-computation-assembly/assembly"))
  .settings(computationAssemblyFamilySettings: _*)
  .settings(
    name := "peas-computation-assembly",
    libraryDependencies ++= Seq(
      Libs.`csw-framework`,
      Libs.`csw-testkit` % Test,
      Libs.`scalatest` % Test,
      Libs.`junit4-interface` % Test,
      Libs.`testng-6-7` % Test,
      Libs.`algorithm-lib` // TODO: confirm this resolves -- see note in project/Libs.scala
    )
  )

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

lazy val peasComputationClient = project
  .in(file("peas-computation-assembly/client"))
  .settings(computationAssemblyFamilySettings: _*)
  .settings(
    name := "peas-computation-client",
    libraryDependencies ++= Seq(
      Libs.`csw-framework`,
      Libs.`csw-testkit` % Test
    )
  )

// =============================================================================
// PeasProcedureDataService
// =============================================================================

lazy val peasProcedureDataService = project
  .in(file("peas-procedure-data-service"))
  .settings(
    name := "peas-procedure-data-service",
    organizationName := "TMT Org",
    scalaVersion := "3.6.4",
    fork := true,
    Test / fork := true,
    resolvers += "jitpack" at "https://jitpack.io",
    libraryDependencies ++= Seq(
      Libs.`esw-http-template-wiring` % "compile->compile;test->test",
      Libs.`embedded-keycloak` % Test,
      Libs.`scalatest` % Test,
      Libs.`pekko-http-testkit` % Test,
      Libs.`mockito` % Test,
      Libs.`junit4-interface` % Test,
      Libs.`testng-6-7` % Test,
      Libs.`pekko-actor-testkit-typed` % Test,
      Libs.`pekko-stream-testkit` % Test,
      Libs.`csw-database`,
      Libs.`jooq`,
      Libs.`postgresql`
    )
  )

// =============================================================================
// PeasProcedureSetupService (was aps-submitter-prototype backend)
// =============================================================================

lazy val peasProcedureSetupService = project
  .in(file("peas-procedure-setup-service"))
  .settings(
    name := "peas-procedure-setup-service",
    scalaVersion := "3.6.4",
    fork := true,
    Test / fork := true,
    resolvers += "jitpack" at "https://jitpack.io",
    libraryDependencies ++= Seq(
      Libs.`esw-http-template-wiring` % "compile->compile;test->test",
      Libs.`pekko-http-spray-json`,
      Libs.`embedded-keycloak` % Test,
      Libs.`scalatest` % Test,
      Libs.`pekko-http-testkit` % Test,
      Libs.`mockito` % Test,
      Libs.`junit4-interface` % Test,
      Libs.`testng-6-7` % Test,
      Libs.`pekko-actor-testkit-typed` % Test,
      Libs.`pekko-stream-testkit` % Test
    )
  )

// =============================================================================
// PeasExposureService (new placeholder -- health-check stub only, from
// the earlier scaffolding pass)
// =============================================================================

lazy val peasExposureService = project
  .in(file("peas-exposure-service"))
  .settings(
    name := "peas-exposure-service",
    scalaVersion := "3.6.4",
    libraryDependencies ++= Seq(
      Libs.`pekko-http`,
      Libs.`pekko-actor-typed`,
      Libs.`pekko-stream`
    )
  )
