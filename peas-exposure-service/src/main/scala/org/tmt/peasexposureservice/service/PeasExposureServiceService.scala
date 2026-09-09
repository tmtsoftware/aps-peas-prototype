package org.tmt.peasexposureservice.service

import scala.concurrent.Future

trait PeasExposureServiceService {

  /**
   * Returns the low-res PNG bytes for the given APS exposure filename,
   * generating it on demand if it hasn't been generated yet -- matches the
   * ICD's "Creates the low resolution image if it does not exist" for
   * GET /retrieveLowResImage. None means the underlying FITS file itself
   * couldn't be found or converted.
   */
  def retrieveLowResImage(apsFilename: String): Future[Option[Array[Byte]]]
}
