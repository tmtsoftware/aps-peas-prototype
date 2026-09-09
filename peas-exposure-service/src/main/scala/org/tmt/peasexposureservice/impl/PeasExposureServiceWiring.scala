package org.tmt.peasexposureservice.impl

import com.typesafe.config.ConfigFactory
import esw.http.template.wiring.ServerWiring
import org.apache.pekko.http.scaladsl.server.Route
import org.tmt.peasexposureservice.http.PeasExposureServiceRoute

import java.io.File

class PeasExposureServiceWiring(override val port: Option[Int] = None) extends ServerWiring {
  override val actorSystemName: String = "peas-exposure-service"

  import actorRuntime.ec

  // Loaded independently via ConfigFactory rather than through any
  // ServerWiring-internal config accessor (couldn't confirm one exists from
  // this sandbox) -- application.conf is on the classpath either way.
  //
  // TODO(Scott): confirm the actual root directory / resolution scheme for
  // exposure FITS files against the DMS exposure-store ICD. Right now
  // exposureStoreCompleted's `filename` parameter is treated as a bare
  // filename resolved under this configured root, matching how
  // JPeasProcedureDataServiceImpl's example treats it.
  private lazy val fitsRootDir: File =
    new File(ConfigFactory.load().getString("exposure-service.fits-root-dir"))

  private lazy val service = new PeasExposureServiceImpl(
    cswServices.eventService,
    cswServices.loggerFactory.getLogger,
    fitsRootDir
  )

  service.start()
  actorRuntime.typedSystem.whenTerminated.onComplete(_ => service.stop())

  private lazy val exposureRoute = new PeasExposureServiceRoute(service)

  override def routes: Route = exposureRoute.route
}
