resolvers += "jitpack" at "https://jitpack.io"

// From aps-sequencer-prototype
addSbtPlugin("io.spray"            % "sbt-revolver"      % "0.10.0")
addSbtPlugin("org.jetbrains.scala" % "sbt-kotlin-plugin" % "3.1.4")

// From aps-computation-assembly-prototype
addSbtPlugin("org.scalameta" % "sbt-scalafmt"  % "2.5.4")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.3.1")
addSbtPlugin("com.eed3si9n"  % "sbt-buildinfo" % "0.13.1") // needed for CswBuildInfo (peas-computation-deploy)

classpathTypes += "maven-plugin"

// From aps-procedure-data-service / aps-submitter-prototype backend --
// jOOQ codegen must be on the sbt classpath, not just the project classpath.
libraryDependencies += "org.jooq"       % "jooq-codegen" % "3.19.6"
libraryDependencies += "org.jooq"       % "jooq-meta"    % "3.19.6"
libraryDependencies += "org.jooq"       % "jooq"         % "3.19.6"
libraryDependencies += "org.postgresql" % "postgresql"   % "42.7.3"

// NOTE: the original aps-submitter-prototype and aps-procedure-data-service
// repos each had a docs subproject using sbt-docs 0.7.1 + Paradox for
// GitHub Pages publishing. Not carried over here -- unrelated to the
// consolidation goal and easy to re-add later if wanted
// (org "com.github.tmtsoftware" name "sbt-docs" version "0.7.1").
