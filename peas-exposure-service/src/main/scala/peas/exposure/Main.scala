package peas.exposure

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
 * PeasExposureService — placeholder service.
 *
 * Day-one scope: expose a health-check endpoint only. Real endpoints
 * (whatever the exposure service is actually meant to expose per the ICD)
 * get added incrementally later.
 *
 * TODO(Scott):
 *  - Register with CSW location service (HttpRegistration) once we know
 *    the intended prefix/component-type for PeasExposureService per ICD.
 *  - Pull host/port from config instead of the hardcoded default below —
 *    check for a conflict against the other services' ports first
 *    (recall the 8085 vs 8084 conflict between data-service and setup-service).
 */
object Main {

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "peas-exposure-service")
    implicit val ec: ExecutionContext = system.executionContext

    val port = sys.env.get("PEAS_EXPOSURE_SERVICE_PORT").map(_.toInt).getOrElse(8086) // TODO confirm free port

    val routes =
      path("health") {
        get {
          complete(
            HttpEntity(
              ContentTypes.`application/json`,
              """{"status":"UP","service":"PeasExposureService"}"""
            )
          )
        }
      }

    Http().newServerAt("0.0.0.0", port).bind(routes).onComplete {
      case Success(binding) =>
        val addr = binding.localAddress
        println(s"PeasExposureService online at http://${addr.getHostString}:${addr.getPort}/health")
      case Failure(ex) =>
        println(s"Failed to bind PeasExposureService: ${ex.getMessage}")
        system.terminate()
    }
  }
}
