package org.tmt.peasproceduredataservice.http

import org.tmt.peasproceduredataservice.impl.JPeasProcedureDataServiceImpl
import org.tmt.peasproceduredataservice.core.models.{
  ComputationKeyValuePair,
  ComputationKeyValuePairList,
  GetProcedureResultDataRequest,
  GreetResponse
}

import scala.jdk.FutureConverters.*
import scala.jdk.CollectionConverters.*
import scala.concurrent.{ExecutionContext, Future}

class JPeasProcedureDataServiceImplWrapper(jPeasProcedureDataServiceImpl: JPeasProcedureDataServiceImpl)(implicit ec: ExecutionContext) {

  // Existing
  def sayBye(): Future[GreetResponse] =
    jPeasProcedureDataServiceImpl.sayBye().asScala

  // POST /storeProcedureComputationResults
  def storeProcedureComputationResults(request: ComputationKeyValuePairList): Future[Unit] =
    jPeasProcedureDataServiceImpl.storeProcedureComputationResults(request).asScala.map(_ => ())

  // POST /getProcedureResultData
  def getProcedureResultData(request: GetProcedureResultDataRequest): Future[List[ComputationKeyValuePair]] =
    jPeasProcedureDataServiceImpl.getProcedureResultData(request).asScala.map(_.asScala.toList)
}
