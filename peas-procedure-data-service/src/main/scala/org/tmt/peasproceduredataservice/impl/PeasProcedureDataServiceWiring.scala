package org.tmt.peasproceduredataservice.impl

import org.apache.pekko.http.scaladsl.server.Route
import esw.http.template.wiring.ServerWiring
import org.tmt.peasproceduredataservice.db.ProcedureDbService
import org.tmt.peasproceduredataservice.http.{JPeasProcedureDataServiceImplWrapper, PeasProcedureDataServiceRoute}

class PeasProcedureDataServiceWiring(val port: Option[Int]) extends ServerWiring {
  override val actorSystemName: String = "peasproceduredataservice-actor-system"

  import actorRuntime.ec

  // ── DB service — pool opened via CSW DatabaseServiceFactory at startup ─────
  lazy val procedureDbService: ProcedureDbService =
    new ProcedureDbService(jCswServices.loggerFactory)

  // Initialise the DSL before the service starts accepting requests.
  // "peas" must match your [db.peas] entry in CSW config / application.conf.
  procedureDbService
    .init(actorRuntime.typedSystem, jCswServices, "peas")
    .get(5, java.util.concurrent.TimeUnit.SECONDS)

  lazy val jPeasProcedureDataServiceImpl: JPeasProcedureDataServiceImpl =
    new JPeasProcedureDataServiceImpl(jCswServices, procedureDbService)
  lazy val peasproceduredataserviceImpl = new PeasProcedureDataServiceImpl()

  lazy val peasproceduredataserviceImplWrapper =
    new JPeasProcedureDataServiceImplWrapper(jPeasProcedureDataServiceImpl)

  // Just an example of subscribing to the Event Service, which the Exposure Service will do.
  // The example code here is for implementation example only, it is never used by the Procedure Data Service.
  // Start the exposureStoreCompleted subscription when the service wires up.
  // Called after jPeasProcedureDataServiceImpl is constructed so jCswServices
  // is fully initialised before the event subscriber connects.
  jPeasProcedureDataServiceImpl.subscribeToExposureEvents()

  // Shut down cleanly when the actor system terminates
  actorRuntime.typedSystem.whenTerminated.onComplete(_ => procedureDbService.close())

  override lazy val routes: Route =
    new PeasProcedureDataServiceRoute(
      peasproceduredataserviceImpl,
      peasproceduredataserviceImplWrapper,
      securityDirectives
    ).route
}