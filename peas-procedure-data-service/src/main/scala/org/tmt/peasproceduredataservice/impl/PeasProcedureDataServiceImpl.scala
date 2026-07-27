package org.tmt.peasproceduredataservice.impl

import org.tmt.peasproceduredataservice.core.models.{AdminGreetResponse, GreetResponse, UserInfo}
import org.tmt.peasproceduredataservice.service.PeasProcedureDataServiceService

import scala.concurrent.Future

// The two new procedure data methods are implemented in Java via
// JPeasProcedureDataServiceImpl — this Scala impl handles only the
// existing greeting endpoints.
class PeasProcedureDataServiceImpl() extends PeasProcedureDataServiceService {

  def greeting(userInfo: UserInfo): Future[GreetResponse] =
    Future.successful(GreetResponse(userInfo))

  def adminGreeting(userInfo: UserInfo): Future[AdminGreetResponse] =
    Future.successful(AdminGreetResponse(userInfo))
}
