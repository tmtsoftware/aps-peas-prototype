package org.tmt.peasexposureservice.http

import org.apache.pekko.http.scaladsl.model.{ContentType, HttpEntity, MediaTypes, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import org.tmt.peasexposureservice.service.PeasExposureServiceService

import scala.concurrent.ExecutionContext

class PeasExposureServiceRoute(service: PeasExposureServiceService)(implicit ec: ExecutionContext) {

  private val PngContentType: ContentType.Binary = ContentType(MediaTypes.`image/png`)

  val route: Route =
    get {
      // GET /retrieveLowResImage?apsFilename=... -- matches ICD SS35.2
      // exactly: raw byte[] response (200), not JSON. apsFilename is
      // required -- pekko-http's `parameter` directive rejects the request
      // (-> 400) automatically when it's missing, matching the ICD's
      // documented 400 response.
      path("retrieveLowResImage") {
        parameter("apsFilename") { apsFilename =>
          onSuccess(service.retrieveLowResImage(apsFilename)) {
            case Some(pngBytes) => complete(HttpEntity(PngContentType, pngBytes))
            case None           => complete(StatusCodes.NotFound, "No file was found with the provided URI")
          }
        }
      } ~
      // Kept from the original placeholder scaffold.
      path("health") {
        complete(HttpEntity(ContentType(MediaTypes.`application/json`), """{"status":"UP","service":"PeasExposureService"}"""))
      }
    }
}
