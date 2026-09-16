package aps
import csw.prefix.models.Prefix
import csw.prefix.javadsl.JSubsystem
import csw.params.events.SystemEvent
import csw.params.events.EventName
import csw.params.events.EventKey
import csw.params.javadsl.JKeyType
import esw.ocs.dsl.core.reusableScript
import esw.ocs.dsl.params.floatKey
import esw.ocs.dsl.params.stringKey
import esw.ocs.dsl.params.kGet
import esw.ocs.dsl.params.first
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

// testAbort is a real boolean, built with JKeyType.BooleanKey() (Java DSL), matching
// ProcedureEvent.kt's precedent of mixing JKeyType.* with Kotlin-native key helpers
// (stringKey, floatKey, etc.) in the same file. Note: BooleanKey().make() takes only a
// name, no Units argument (unlike StringKey/other JKeyType makes elsewhere in this codebase).
private val testAbortKey = JKeyType.BooleanKey().make("testAbort")

// generateExposureEvent/exposureFilename: UI-driven test hook, same shape and purpose as
// testAbort above -- lets takeGoodExposure simulate the PSH Detector Assembly's
// exposureStoreCompleted publish directly in PEAS Software Only Mode (no real detector
// assembly to command there -- see isSoftwareOnlyMode() branch below), driving
// peas-exposure-service's subscription end-to-end. exposureFilename is a bare filename (e.g.
// "18JUL2034_PSH_BBP_001_1B.FTS"), matching exposureStoreCompleted's real ICD `filename` param
// exactly -- it carries no directory. peas-exposure-service resolves it against its own
// startup-configured root (exposure-service.fits-root-dir in its application.conf); this
// script has no opinion on where FITS files actually live on disk.
private val generateExposureEventKey = JKeyType.BooleanKey().make("generateExposureEvent")
private val exposureStoreCompletedEventName = EventName("exposureStoreCompleted")

// APS.ICS.PSH.Detector -- confirmed against ICD-SDB-APS-APS_CCR03.pdf SS22 (component prefix
// table, SS22.1.4's exposureStoreCompleted, SS22.2.1.3's takeExposure command). Used both to
// publish exposureStoreCompleted ourselves (Software Only Mode simulation) and to subscribe to
// it from the real assembly (Standalone Mode) -- see takeGoodExposure below.
private val pshDetectorPrefix = Prefix.apply("APS.ICS.PSH.Detector")

// APS.PeasExposureService -- must match PeasExposureServiceImpl.scala's
// ImageDisplayEventSourcePrefix exactly, same as the frontend's useExposureImage.ts.
private val imageDisplayEventSourcePrefix = Prefix.apply("APS.PeasExposureService")
private val apsImageDisplayEventName = EventName("apsImageDisplayEvent")

val commonD = reusableScript {

    // =========================================================================
    // COMMON HANDLERS — shared across multiple procedures on Sequencer D
    // Sender: APS.PEAS.AlignmentProcedureSequencerB (via serialized sequence)
    // =========================================================================

    // Takes a PSH exposure with the specified integration time
    // Parameters: intTime: Float, testAbort: Boolean (prototype-only, defaults false)
    //
    // testAbort is a UI-driven test hook, not part of the real ICD command shape -- it lets
    // the operator force this step to treat the exposure as unacceptable-for-analysis, to
    // exercise the abort-cascade / GLC-restore-on-error path (D -> B -> A -> onSetupWithRestoreOnError)
    // without needing a real bad exposure. When testAbort is true, this step publishes a
    // WARNING USER_PROMPT after the (simulated) exposure completes and blocks for an operator
    // response:
    //   RETRY    -> publish the same "not acceptable" prompt again and keep waiting
    //   CONTINUE -> treat the exposure as acceptable and complete the step normally
    //   ABORT    -> throw, which is the exception this whole cascade design is meant to catch
    onSetup("takeGoodExposure") { command ->
        val intTime: Float = command.kGet(floatKey("intTime"))!!.first
        val testAbort: Boolean = command.kGet(testAbortKey)?.first ?: false
        val generateExposureEvent: Boolean = command.kGet(generateExposureEventKey)?.first ?: false
        val exposureFilename: String = command.kGet(stringKey("exposureFilename"))?.first ?: ""

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "takeGoodExposure-start",
            helpKey   = "help.takeGoodExposure",
            messageId = "msg.takeGoodExposure.start"
        ))
        println("CommonD: takeGoodExposure — intTime=$intTime, testAbort=$testAbort, generateExposureEvent=$generateExposureEvent, exposureFilename=$exposureFilename")

        // 1. Take the exposure. APS Standalone Mode commands the real PSH Detector Assembly
        // (ICD SS22.2.1.3's takeExposure); the assembly publishes exposureStoreCompleted
        // itself as part of that longRunning work (ICD SS22.1.4), so we don't publish it
        // ourselves in this branch. PEAS Software Only Mode has no real detector assembly to
        // command, so it simulates the wait and publishes exposureStoreCompleted itself
        // (unchanged from the previous behavior).
        if (!isSoftwareOnlyMode()) {
            // TODO(Scott): ICD SS22.2.1.3 declares integrationTime with units "second" --
            // not sure whether .set(intTime) below needs an explicit .withUnits() call to
            // match; couldn't verify the exact Kotlin DSL signature for setting param units
            // against a real csw-params jar in this sandbox.
            val takeExposureCmd = Setup(prefix, "takeExposure")
                .add(floatKey("integrationTime").set(intTime))
            val pshDetector = Assembly(JSubsystem.APS, "ICS.PSH.Detector", defaultTimeout = 60.seconds)
            println("CommonD: takeGoodExposure — submitting takeExposure to $pshDetectorPrefix")
            val takeExposureResponse = pshDetector.submitAndWait(takeExposureCmd)
            println("CommonD: takeGoodExposure — PSH Detector Assembly takeExposure response: $takeExposureResponse")
        } else {
            delay(5.seconds)
            if (generateExposureEvent) {
                publishEvent(SystemEvent(pshDetectorPrefix, exposureStoreCompletedEventName)
                    .add(stringKey("filename").set(exposureFilename)))
                println("CommonD: takeGoodExposure — published exposureStoreCompleted, filename=$exposureFilename")
            }
        }

        // 2. APS Standalone Mode additionally waits for the real assembly's own
        // exposureStoreCompleted publish before proceeding -- per Scott, this is an explicit
        // subscribe-and-wait on the event itself, not just relying on takeExposure's own
        // submitAndWait response above.
        if (!isSoftwareOnlyMode()) {
            println("CommonD: takeGoodExposure — waiting for exposureStoreCompleted from $pshDetectorPrefix")
            awaitFreshEvent(EventKey(pshDetectorPrefix, exposureStoreCompletedEventName))
            println("CommonD: takeGoodExposure — received exposureStoreCompleted")
        }

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "takeGoodExposure-findAndIdentify-start",
            helpKey   = "help.takeGoodExposure",
            messageId = "msg.takeGoodExposure.findAndIdentify.start"
        ))
        println("CommonD: takeGoodExposure — Find and Identify started")

        // 3. Waits for peas-exposure-service's apsImageDisplayEvent (ICD SS35.1.2), published
        // once the low-res PNG has actually been generated from this exposure -- that's what
        // marks Find and Identify as complete.
        println("CommonD: takeGoodExposure — waiting for apsImageDisplayEvent from $imageDisplayEventSourcePrefix")
        awaitFreshEvent(EventKey(imageDisplayEventSourcePrefix, apsImageDisplayEventName))
        println("CommonD: takeGoodExposure — received apsImageDisplayEvent")

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "takeGoodExposure-findAndIdentify-complete",
            helpKey   = "help.takeGoodExposure",
            messageId = "msg.takeGoodExposure.findAndIdentify.complete"
        ))

        if (testAbort) {
            var awaitingResponse = true
            while (awaitingResponse) {
                val promptMessageId = "msg.takeGoodExposure.notAcceptable"
                val promptEvent = buildProcedureEvent(Prefix.apply(prefix),
                    type      = ProcedureEventType.USER_PROMPT,
                    dialogKey = OriginatingPromptType.WARNING,
                    helpKey   = "help.takeGoodExposure",
                    messageId = promptMessageId
                )
                val promptMessageUuid = messageUuidOf(promptEvent)
                    ?: throw IllegalStateException("CommonD: failed to read back messageUuid from the takeGoodExposure prompt event we just built")
                publishEvent(promptEvent)

                println("CommonD: takeGoodExposure — waiting for userPromptResponseEvent matching $promptMessageUuid")
                val responseReceived = CompletableDeferred<String>()
                val responseEventKey = userPromptResponseEventKey(Prefix.apply(prefix)).toString()
                val subscription = onEvent(responseEventKey) { event ->
                    if (event.isInvalid) return@onEvent
                    val response = decodeUserPromptResponseEvent(event)
                    if (response == null) return@onEvent
                    if (response.originatingMessageUuid != promptMessageUuid) {
                        println("CommonD: ignoring stale/non-matching userPromptResponseEvent: $response")
                        return@onEvent
                    }
                    println("CommonD: received matching userPromptResponseEvent: $response")
                    responseReceived.complete(response.errorResponse)
                }
                val errorResponse = responseReceived.await()
                subscription.cancel()

                when (errorResponse) {
                    ErrorResponse.RETRY -> println("CommonD: takeGoodExposure — operator chose RETRY, re-prompting")
                    ErrorResponse.CONTINUE -> {
                        println("CommonD: takeGoodExposure — operator chose CONTINUE, treating exposure as acceptable")
                        awaitingResponse = false
                    }
                    // Does NOT throw. An operator-initiated abort is an expected condition, not an
                    // error -- the UI separately calls abortSequence() on Sequencer A (per
                    // PeasSequencerA.kts's onAbortSequence), which is the actual signal that drives
                    // telescope-state restoration once the in-flight chain finishes (see
                    // RestoreOnErrorWrapper.kt). This step just completes normally, same as CONTINUE.
                    ErrorResponse.ABORT -> {
                        println("CommonD: takeGoodExposure — operator chose ABORT, completing step normally")
                        awaitingResponse = false
                    }
                    else ->
                        throw IllegalStateException("CommonD: takeGoodExposure — unexpected errorResponse: $errorResponse")
                }
            }
        }

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "takeGoodExposure-complete",
            helpKey   = "help.takeGoodExposure",
            messageId = "msg.takeGoodExposure.complete"
        ))
    }

}
