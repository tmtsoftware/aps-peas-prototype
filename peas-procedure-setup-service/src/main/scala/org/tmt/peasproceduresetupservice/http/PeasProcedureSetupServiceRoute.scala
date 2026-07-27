package org.tmt.peasproceduresetupservice.http

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import org.tmt.peasproceduresetupservice.core.models.{BuildSequenceRequest, LoadSequenceRequest}
import org.tmt.peasproceduresetupservice.service.PeasProcedureSetupServiceService

import scala.concurrent.ExecutionContext

class PeasProcedureSetupServiceRoute(
    service: PeasProcedureSetupServiceService
)(implicit ec: ExecutionContext)
    extends HttpCodecs {

  val route: Route =
    pathPrefix("sequence") {
      path("templates") {
        get {
          complete(service.listTemplates())
        }
      } ~
      path("template") {
        post {
          entity(as[LoadSequenceRequest]) { request =>
            complete(service.loadTemplate(request.configPath))
          }
        }
      } ~
      path("build") {
        post {
          entity(as[BuildSequenceRequest]) { request =>
            complete(service.buildSequence(request.template, request.substitutions))
          }
        }
      }
    }
}
