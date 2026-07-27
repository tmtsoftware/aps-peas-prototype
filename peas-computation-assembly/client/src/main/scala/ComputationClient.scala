package aps.computationclient

import java.net.InetAddress
import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import org.apache.pekko.util.Timeout
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.params.commands.CommandResponse._
import csw.params.commands.{Setup, CommandName}
import csw.params.core.generics.KeyType
import csw.prefix.models.Prefix
import csw.location.api.models.{ComponentId, HttpLocation, TypedConnection}
import csw.location.api.models.ComponentType.Assembly
import csw.location.api.models.Connection.HttpConnection
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

object ComputationClient extends App {

  // ----------------------------
  // Actor system & logging
  // ----------------------------
  implicit val system: ActorSystem[SpawnProtocol.Command] =
    ActorSystem(SpawnProtocol(), "ComputationClient")
  implicit val ec: ExecutionContextExecutor = system.executionContext
  implicit val timeout: Timeout             = Timeout(5.seconds)

  val host = InetAddress.getLocalHost.getHostName
  LoggingSystemFactory.start("ComputationClientApp", "0.1", host, system)
  val log = GenericLoggerFactory.getLogger
  log.info("Starting ComputationClient")

  // ----------------------------
  // Location Service client
  // ----------------------------
  val locationService                           = HttpLocationServiceFactory.makeLocalClient
  val assemblyId                                = ComponentId(Prefix("APS.PeasComputationAssembly"), Assembly)
  val connection: TypedConnection[HttpLocation] = HttpConnection(assemblyId)

  // Blocking assembly resolution
  val assembly: CommandService = resolveAssemblyBlocking()

  // ----------------------------
  // Pre-canned commands
  // ----------------------------
  // color step: override metadata to force our own input values
  val setup = Setup(Prefix("peas.computationassembly"), CommandName("colorStep"), None)
    .add(KeyType.IntKey.make("stepCount").set(11))
    .add(KeyType.FloatKey.make("stepSizeNm").set(12.6f))

  // override input values: source = Result to be contexted to "centroidOffsets" function
  val ttSetup = Setup(Prefix("peas.computationassembly"), CommandName("ttOffsetsToActs"), None)
    .add(KeyType.StringKey.make("centroidOffsetsX").set("centroidOffsets.centroidOffsetsX"))
    .add(KeyType.StringKey.make("centroidOffsetsY").set("centroidOffsets.centroidOffsetsY"))

  // override input values: source = Result to be contexted to ttOffsetsToActs function
  val decomposeActsSetup = Setup(Prefix("peas.computationassembly"), CommandName("decomposeActs"), None)
    .add(KeyType.StringKey.make("desiredActDeltas").set("ttOffsetsToActs.desiredActDeltas"))

  val commandSequence: Seq[(String, Setup)] = Seq(
    "colorStep"       -> setup,
    "ttOffsetsToActs" -> ttSetup,
    "decomposeActs"   -> decomposeActsSetup
  )

  // ----------------------------
  // Submit commands sequentially
  // ----------------------------
  commandSequence.foreach { case (name, command) =>
    log.info(s"Submitting command: $name")

    try {
      val responseFuture: Future[SubmitResponse] = assembly.submitAndWait(command)
      val response: SubmitResponse               = Await.result(responseFuture, 30.seconds)

      response match {
        case c: Completed   => log.info(s"Completed: $c")
        case i: Invalid     => log.error(s"Invalid: $i")
        case e: Error       => log.error(s"Error: $e")
        case cxl: Cancelled => log.warn(s"Cancelled: $cxl")
        case l: Locked      => log.warn(s"Locked: $l")
      }

    }
    catch {
      case ex: Exception =>
        log.error(s"Failed to submit command $name", Map.empty, ex)
    }
  }

  log.info("All commands submitted sequentially. Terminating client.")
  system.terminate()

  // ----------------------------
  // Helpers
  // ----------------------------
  private def resolveAssemblyBlocking(): CommandService = {
    val locOptFuture = locationService.resolve(connection, 5.seconds)
    val locOpt       = Await.result(locOptFuture, 5.seconds)

    val loc = locOpt.getOrElse {
      log.error("Assembly not found in Location Service")
      system.terminate()
      sys.exit(1)
    }

    log.info(s"Resolved assembly at ${loc.uri}")
    CommandServiceFactory.make(loc)
  }
}
