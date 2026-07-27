package peas.computationassembly

import csw.location.api.models.Connection.PekkoConnection
import csw.prefix.models.Prefix
import csw.location.api.models.{ComponentId, ComponentType}
import csw.testkit.scaladsl.CSWService.{AlarmServer, EventServer}
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent.Await
import scala.concurrent.duration._

class ComputationAssemblyTest extends ScalaTestFrameworkTestKit(AlarmServer, EventServer) with AnyFunSuiteLike {

  import frameworkTestKit._

  override def beforeAll(): Unit = {
    super.beforeAll()
    // uncomment if you want one Assembly run for all tests
    spawnStandalone(com.typesafe.config.ConfigFactory.load("ComputationAssemblyStandalone.conf"))
  }

  test("Assembly should be locatable using Location Service") {
    val connection    = PekkoConnection(ComponentId(Prefix("APS.PeasComputationAssembly"), ComponentType.Assembly))
    val pekkoLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    pekkoLocation.connection shouldBe connection
  }
}
