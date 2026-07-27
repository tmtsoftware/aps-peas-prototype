package org.tmt.peasproceduredataservice.http

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import csw.aas.http.AuthorizationPolicy.RealmRolePolicy
import csw.aas.http.SecurityDirectives
import org.tmt.peasproceduredataservice.core.models.{
  ComputationKeyValuePairList,
  GetProcedureResultDataRequest,
  UserInfo
}
import org.tmt.peasproceduredataservice.service.PeasProcedureDataServiceService

import scala.concurrent.ExecutionContext

class PeasProcedureDataServiceRoute(
    service1: PeasProcedureDataServiceService,
    service2: JPeasProcedureDataServiceImplWrapper,
    securityDirectives: SecurityDirectives
)(implicit ec: ExecutionContext)
    extends HttpCodecs {

  val route: Route =
    post {
      // ── Existing routes ───────────────────────────────────────────────────
      path("greeting") {
        entity(as[UserInfo]) { userInfo =>
          complete(service1.greeting(userInfo))
        }
      } ~
      path("adminGreeting") {
        securityDirectives.sPost(RealmRolePolicy("Esw-user")) { _ =>
          entity(as[UserInfo]) { userInfo =>
            complete(service1.adminGreeting(userInfo))
          }
        }
      } ~
      // ── POST /storeProcedureComputationResults — Java impl ─────────────────
      path("storeProcedureComputationResults") {
        entity(as[ComputationKeyValuePairList]) { request =>
          complete(service2.storeProcedureComputationResults(request))
        }
      } ~
      // ── POST /getProcedureResultData — Java impl ───────────────────────────
      path("getProcedureResultData") {
        entity(as[GetProcedureResultDataRequest]) { request =>
          complete(service2.getProcedureResultData(request))
        }
      }
    } ~
    path("sayBye") {
      complete(service2.sayBye())
    }
}
