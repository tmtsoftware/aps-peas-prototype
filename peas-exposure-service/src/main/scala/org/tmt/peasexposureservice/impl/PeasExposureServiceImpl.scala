package org.tmt.peasexposureservice.impl

import csw.event.api.scaladsl.{EventService, EventSubscription}
import csw.logging.api.scaladsl.Logger
import csw.params.core.generics.{Key, KeyType}
import csw.params.events.{Event, EventKey, EventName, SystemEvent}
import csw.prefix.models.{Prefix, Subsystem}
import org.tmt.peasexposureservice.service.PeasExposureServiceService

import java.io.File
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * Implements the Exposure Service against the real ICD definitions
 * (ICD-SDB-APS-APS_CCR03.pdf -- exposureStoreCompleted: SS5.1.6/17.1.4/22.1.4
 * and their summary in SS35.3; apsImageDisplayEvent: SS35.1.2;
 * GET /retrieveLowResImage: SS35.2):
 *
 *  1. Subscribes to exposureStoreCompleted from all three detector
 *     publishers and generates a PNG automatically -- no /generateLoRezImage
 *     endpoint (per Scott: generation is automatic here, not externally
 *     triggered).
 *  2. Publishes apsImageDisplayEvent once the PNG is ready, using the ICD's
 *     real payload shape.
 *  3. Serves GET /retrieveLowResImage?apsFilename=... as raw PNG bytes,
 *     matching the ICD's byte[] response exactly. The web UI is the only
 *     consumer, so the simplest effective design is for it to point an
 *     <img src="...retrieveLowResImage?apsFilename=..."> straight at this
 *     endpoint -- no JSON/base64 wrapper, no extra frontend fetch logic.
 *
 * Cache is keyed by apsFilename (ConcurrentHashMap, same pattern as
 * JPeasProcedureDataServiceImpl's in-memory store), not "latest only" --
 * the ICD's apsFilename query param requires looking up a specific
 * exposure, not just whatever was most recently generated.
 *
 * TODO(Scott): as before, the csw-event-client Scala calls
 * (EventService.defaultSubscriber.subscribeAsync / defaultPublisher.publish)
 * are modeled on the Java IEventService usage already demonstrated in
 * JPeasProcedureDataServiceImpl -- I couldn't resolve the csw-framework jar
 * in this sandbox to confirm the Scala-side signatures compile
 * character-for-character.
 */
class PeasExposureServiceImpl(
    eventService: EventService,
    logger: Logger,
    fitsRootDir: File
)(implicit ec: ExecutionContext)
    extends PeasExposureServiceService {

  import PeasExposureServiceImpl._

  private val pngCache = new ConcurrentHashMap[String, Array[Byte]]()
  private var subscription: Option[EventSubscription] = None

  // ── Lifecycle ────────────────────────────────────────────────────────────

  def start(): Unit = {
    val keys = ExposureStoreCompletedPublishers.map(EventKey(_, ExposureStoreCompletedEventName)).toSet
    subscription = Some(
      eventService.defaultSubscriber.subscribeAsync(keys, handleExposureStoreCompleted)
    )
    logger.info(
      s"PeasExposureService subscribed to exposureStoreCompleted from: ${ExposureStoreCompletedPublishers.mkString(", ")}"
    )
  }

  def stop(): Unit = subscription.foreach(_.unsubscribe())

  // ── 1. Subscribe: exposureStoreCompleted -> FITS -> PNG ────────────────

  private def handleExposureStoreCompleted(event: Event): Future[Unit] = event match {
    case systemEvent: SystemEvent =>
      systemEvent.get(FilenameKey).map(_.head) match {
        case Some(filename) => generateAndCache(filename, publishEvent = true).map(_ => ())
        case None =>
          logger.warn(s"exposureStoreCompleted missing '${FilenameKey.keyName}' parameter")
          Future.unit
      }
    case other =>
      logger.warn(s"Ignoring non-SystemEvent on exposureStoreCompleted: ${other.getClass.getSimpleName}")
      Future.unit
  }

  // Shared by the event handler and the on-demand fallback in
  // retrieveLowResImage. publishEvent is false for the on-demand path --
  // apsImageDisplayEvent means "a new image just arrived", not "someone
  // looked one up", so a cache-miss lookup must not re-announce it.
  private def generateAndCache(apsFilename: String, publishEvent: Boolean): Future[Option[Array[Byte]]] =
    Future {
      val fitsFile = Paths.get(fitsRootDir.getPath, apsFilename).toFile
      FitsToPng.convert(fitsFile) match {
        case Success(pngBytes) =>
          pngCache.put(apsFilename, pngBytes)
          logger.info(s"Generated low-res PNG for $apsFilename (${pngBytes.length} bytes)")
          if (publishEvent) publishImageDisplayEvent(apsFilename)
          Some(pngBytes)
        case Failure(ex) =>
          logger.error(s"FITS -> PNG conversion failed for $apsFilename: ${ex.getMessage}")
          None
      }
    }

  // ── 2. Publish: apsImageDisplayEvent ────────────────────────────────────

  private def publishImageDisplayEvent(imageFilename: String): Unit = {
    val event = SystemEvent(ImageDisplayEventSourcePrefix, ImageDisplayEventName)
      .add(TypeKey.set(ImageDisplayType.IMAGE))
      .add(DialogKeyKey.set(ExposureImageDialogKey))
      .add(HelpKeyKey.set(ExposureImageHelpKey))
      .add(ImageFilenameKey.set(imageFilename))
    // imageOverlayData intentionally omitted -- ICD marks it as carrying
    // overlay data "in format recognizable to the Procedure Data Service
    // API", which only applies to IMAGE_OVERLAY notifications. This
    // service only ever publishes plain IMAGE events.
    eventService.defaultPublisher.publish(event).onComplete {
      case Success(_)  => logger.info(s"Published $ImageDisplayEventName for $imageFilename")
      case Failure(ex) => logger.error(s"Failed to publish $ImageDisplayEventName: ${ex.getMessage}")
    }
  }

  // ── 3. GET /retrieveLowResImage?apsFilename=... ─────────────────────────

  def retrieveLowResImage(apsFilename: String): Future[Option[Array[Byte]]] =
    Option(pngCache.get(apsFilename)) match {
      case Some(cached) => Future.successful(Some(cached))
      case None         => generateAndCache(apsFilename, publishEvent = false)
    }
}

object PeasExposureServiceImpl {

  // Confirmed against ICD-SDB-APS-APS_CCR03.pdf SS35.3 -- all three detector
  // publishers use the identical exposureStoreCompleted schema (single
  // `filename: string` param).
  val ExposureStoreCompletedPublishers: Seq[Prefix] = Seq(
    Prefix(Subsystem.APS, "ICS.APT.Detector"),
    Prefix(Subsystem.APS, "ICS.PIT.Detector"),
    Prefix(Subsystem.APS, "ICS.PSH.Detector")
  )
  val ExposureStoreCompletedEventName: EventName = EventName("exposureStoreCompleted")
  val FilenameKey: Key[String] = KeyType.StringKey.make("filename")

  // Per Scott: follow peas-procedure-setup-service's prefix convention
  // (APS.PeasExposureService, two segments), not
  // peas-procedure-data-service's (CSW.PeasProcedureDataService -- a known
  // prototype mistake to be corrected later). The ICD's own canonical form
  // is actually the three-segment APS.PEAS.ExposureService; matches
  // application.conf's existing http-server.prefix, unchanged.
  val ImageDisplayEventSourcePrefix: Prefix = Prefix(Subsystem.APS, "PeasExposureService")
  val ImageDisplayEventName: EventName      = EventName("apsImageDisplayEvent")

  // Confirmed against ICD SS35.1.2.
  val TypeKey: Key[String]             = KeyType.StringKey.make("type")
  val DialogKeyKey: Key[String]        = KeyType.StringKey.make("dialogKey")
  val HelpKeyKey: Key[String]          = KeyType.StringKey.make("helpKey")
  val ImageFilenameKey: Key[String]    = KeyType.StringKey.make("imageFilename")
  val ImageOverlayDataKey: Key[String] = KeyType.StringKey.make("imageOverlayData")

  // ICD's "type" is an enum: (IMAGE, IMAGE_OVERLAY). Modeled as plain
  // string constants, matching ProcedureEventType's existing convention in
  // ProcedureEvent.kt (INFO_MESSAGE/WARN_MESSAGE/etc.) rather than
  // introducing CSW's ChoiceKey, which has no precedent anywhere else in
  // this codebase.
  object ImageDisplayType {
    val IMAGE         = "IMAGE"
    val IMAGE_OVERLAY = "IMAGE_OVERLAY"
  }

  // TODO(Scott): dialogKey/helpKey are free-form lookup keys -- same
  // convention as apsProcedureEvent's messageId/dialogKey/helpKey (see
  // ProcedureEvent.kt), presumably resolved against the PEAS UI's
  // message/help resource-properties file. The ICD doesn't enumerate
  // allowed values for an image-display event, so these are placeholders --
  // confirm the actual keys the UI's resource file expects.
  val ExposureImageDialogKey = "EXPOSURE_IMAGE"
  val ExposureImageHelpKey   = "EXPOSURE_IMAGE"
}
