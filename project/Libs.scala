import sbt._
import java.io.FileReader
import java.util.Properties
import scala.util.Using

// Real versions pulled from the four original repos' project/Libs.scala /
// project/Dependencies.scala files — nothing here is a placeholder.
object Libs {

  private def readProp(key: String): String =
    Using.resource(new FileReader("project/build.properties")) { reader =>
      val prop = new Properties()
      prop.load(reader)
      prop.getProperty(key)
    }

  val CswVersion = readProp("csw.version") // 6.0.0, from aps-computation-assembly-prototype
  val EswVersion = readProp("esw.version") // 1.0.2, from aps-sequencer-prototype

  // --- ESW (sequencer scripts) ---------------------------------------------
  val `esw-ocs-dsl-kt` = "com.github.tmtsoftware.esw" %  "esw-ocs-dsl-kt" % EswVersion
  val `esw-ocs-app`    = "com.github.tmtsoftware.esw" %% "esw-ocs-app"    % EswVersion

  // --- ESW http template (procedure-data-service, procedure-setup-service) -
  val `esw-http-template-wiring` = "com.github.tmtsoftware.esw" %% "esw-http-template-wiring" % "v1.0.0"

  // --- CSW (computation assembly family) -----------------------------------
  val `csw-framework` = "com.github.tmtsoftware.csw" %% "csw-framework" % CswVersion
  val `csw-testkit`   = "com.github.tmtsoftware.csw" %% "csw-testkit"   % CswVersion
  val `csw-database`  = "com.github.tmtsoftware.csw" %% "csw-database"  % "6.0.0"

  // --- jOOQ / Postgres (procedure-data-service) ----------------------------
  val `jooq`         = "org.jooq"       % "jooq"         % "3.19.6"
  val `jooq-meta`    = "org.jooq"       % "jooq-meta"    % "3.19.6"
  val `jooq-codegen` = "org.jooq"       % "jooq-codegen" % "3.19.6"
  val `postgresql`   = "org.postgresql" % "postgresql"   % "42.7.3"

  // --- Pekko (procedure-data-service, procedure-setup-service, exposure-service) ---
  val `pekko-http`                 = "org.apache.pekko" %% "pekko-http"                 % "1.1.0"
  val `pekko-http-spray-json`      = "org.apache.pekko" %% "pekko-http-spray-json"      % "1.1.0"
  val `pekko-actor-typed`          = "org.apache.pekko" %% "pekko-actor-typed"          % "1.1.3"
  val `pekko-stream`               = "org.apache.pekko" %% "pekko-stream"               % "1.1.3"
  val `pekko-http-testkit`         = "org.apache.pekko" %% "pekko-http-testkit"         % "1.1.0"
  val `pekko-actor-testkit-typed`  = "org.apache.pekko" %% "pekko-actor-testkit-typed"  % "1.1.3"
  val `pekko-stream-testkit`       = "org.apache.pekko" %% "pekko-stream-testkit"       % "1.1.3"

  // --- Auth / testing (shared across procedure-data-service, procedure-setup-service, computation assembly) ---
  val `embedded-keycloak`  = "com.github.tmtsoftware.embedded-keycloak" %% "embedded-keycloak" % "0.7.4"
  val `scalatest`          = "org.scalatest"       %% "scalatest"       % "3.2.19"
  val `mockito`            = "org.scalatestplus"   %% "mockito-3-4"     % "3.2.10.0"
  val `junit4-interface`   = "com.github.sbt"       % "junit-interface" % "0.13.3"
  val `testng-6-7`         = "org.scalatestplus"   %% "testng-6-7"      % "3.2.10.0"

  // --- Computation assembly's own algorithm library ------------------------
  // TODO(Scott): confirm where this resolves from — not on Maven Central or
  // jitpack per the resolvers in the original build.sbt, so there may be an
  // internal/private resolver configured elsewhere (~/.sbt/repositories or
  // similar) that I can't see from here.
  val `algorithm-lib` = "org.tmt.aps.peas" % "algorithm-lib" % "1.0.0"
    
  // --- FITS reading (peas-exposure-service) --------------------------------
  // TODO(Scott): this sandbox has no Maven Central access, so I couldn't
  // resolve/pin this against the actual registry. "gov.nasa.gsfc.heasarc" /
  // "nom-tam-fits" is the standard/de-facto Java FITS library (BSD-3, used
  // throughout the astronomy Java ecosystem) -- please confirm the latest
  // stable version before relying on this coordinate.
  val `nom-tam-fits` = "gov.nasa.gsfc.heasarc" % "nom-tam-fits" % "1.20.1"
}
