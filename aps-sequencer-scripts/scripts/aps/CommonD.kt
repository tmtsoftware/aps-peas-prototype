package aps
import csw.prefix.models.Prefix
import csw.params.javadsl.JKeyType
import esw.ocs.dsl.core.reusableScript
import esw.ocs.dsl.params.floatKey
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

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "takeGoodExposure-start",
            helpKey   = "help.takeGoodExposure",
            messageId = "msg.takeGoodExposure.start"
        ))
        println("CommonD: takeGoodExposure — intTime=$intTime, testAbort=$testAbort")
        // TODO: implement — take PSH exposure with the specified integration time
        delay(1.seconds)

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
