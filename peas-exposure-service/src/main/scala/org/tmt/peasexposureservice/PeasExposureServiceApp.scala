package org.tmt.peasexposureservice

import caseapp.core.RemainingArgs
import csw.location.api.models.Metadata
import csw.network.utils.SocketUtils
import PeasExposureServiceAppCommand.StartOptions
import caseapp.Command
import caseapp.core.help.Help
import caseapp.core.parser.Parser
import caseapp.core.app.{Command, CommandsEntryPoint}
import esw.constants.CommonTimeouts
import esw.http.template.wiring.ServerWiring
import org.tmt.peasexposureservice.impl.PeasExposureServiceWiring

import scala.concurrent.Await
import scala.util.control.NonFatal

// Mirrors PeasProcedureSetupServiceApp / PeasProcedureDataServiceApp exactly --
// same caseapp CommandsEntryPoint + ServerWiring.start(metadata) pattern, so
// location-service HTTP registration (prefix APS.PeasExposureService, from
// application.conf) is handled by the framework, not by hand as the earlier
// placeholder Main.scala's TODO assumed.
object PeasExposureServiceApp extends CommandsEntryPoint {
  private val appName: String = getClass.getSimpleName.dropRight(1)
  private val appVersion: String = "0.1.0"
  override def progName: String = "peasexposureservice"

  private val StartCommand: Runner[StartOptions] = Runner[StartOptions]()
  override def commands: Seq[Command[?]] = List(StartCommand)

  private class Runner[T: {Parser, Help}] extends Command[T] {
    override def run(command: T, args: RemainingArgs): Unit = {
      command match {
        case StartOptions(port) =>
          val wiring = new PeasExposureServiceWiring(Some(port.getOrElse(SocketUtils.getFreePort)))
          start(wiring, Metadata.empty)
      }
    }

    private def start(wiring: ServerWiring, metadata: Metadata): Unit = {
      try {
        wiring.actorRuntime.startLogging(progName, appVersion)
        wiring.logger.debug(s"starting $appName")
        val (binding, _) = Await.result(wiring.start(metadata), CommonTimeouts.Wiring)
        wiring.logger.info(s"$appName online at http://${binding.localAddress.getHostString}:${binding.localAddress.getPort}/")
      }
      catch {
        case NonFatal(ex) =>
          ex.printStackTrace()
          wiring.logger.error(s"$appName crashed")
          exit(1)
      }
    }
  }
}
