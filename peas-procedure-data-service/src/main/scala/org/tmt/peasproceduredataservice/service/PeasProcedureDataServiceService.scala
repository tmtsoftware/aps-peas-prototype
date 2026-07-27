package org.tmt.peasproceduredataservice.service

import org.tmt.peasproceduredataservice.core.models.{AdminGreetResponse, GreetResponse, UserInfo}

import scala.concurrent.Future

// The two new procedure data methods (getProcedureResultData,
// storeProcedureComputationResults) are implemented in Java via
// JPeasProcedureDataServiceImpl and exposed through
// JPeasProcedureDataServiceImplWrapper — they do not belong on this trait.
trait PeasProcedureDataServiceService {
  def greeting(userInfo: UserInfo): Future[GreetResponse]
  def adminGreeting(userInfo: UserInfo): Future[AdminGreetResponse]
}
