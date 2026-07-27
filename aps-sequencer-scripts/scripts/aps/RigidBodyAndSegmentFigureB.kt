package aps
import csw.prefix.models.Prefix
import esw.ocs.dsl.core.reusableScript
import esw.ocs.dsl.params.stringKey
import esw.ocs.dsl.params.floatKey
import esw.ocs.dsl.params.kGet
import esw.ocs.dsl.params.first
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

val rigidBodyAndSegmentFigureB = reusableScript {

    // File-level globals for RBSF operator responses and procedure config,
    // set by earlier handlers and read by subsequent steps in the same procedure run.
    var rbsfM2OpResp: String = "NO"
    var rbsfM2CmdMethod: String = "PTT"

    // =========================================================================
    // ORIGINAL HANDLERS — prototype steps carried over from prior work
    // =========================================================================

    onSetup("ask-user") { command ->

        println("RigidBodyAndSegmentFigureB: ask-user start")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "ask-user-start",
            helpKey   = "help.ask-user",
            messageId = "msg.ask-user.start"
        ))

        println("RigidBodyAndSegmentFigureB: received ask-user")
        // messageId is a stable, human-readable identifier for this prompt.
        // messageUuid (generated automatically inside buildProcedureEvent) is
        // what's actually unique per invocation - it's what lets the wait
        // below distinguish "this round's" response from a previous round's
        // stale leftover (CSW's Event Service delivers the last published
        // event on a key immediately upon subscribe).
        val promptMessageId = "msg.ask-user.prompt"
        val promptEvent = buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.USER_PROMPT,
            dialogKey = OriginatingPromptType.WARNING,
            helpKey   = "help.ask-user",
            messageId = promptMessageId
        )
        val promptMessageUuid = messageUuidOf(promptEvent)
            ?: throw IllegalStateException("RigidBodyAndSegmentFigureB: failed to read back messageUuid from the prompt event we just built")
        publishEvent(promptEvent)

        // Block until the UI publishes a userPromptResponseEvent whose
        // originatingMessageUuid matches the prompt above. For this phase,
        // the step handler proceeds regardless of decisionResponse /
        // errorResponse content once a matching response arrives; a later
        // phase will branch on those values.
        println("RigidBodyAndSegmentFigureB: waiting for userPromptResponseEvent matching $promptMessageUuid")
        val responseReceived = CompletableDeferred<Unit>()
        val responseEventKey = userPromptResponseEventKey(Prefix.apply(prefix)).toString()
        val subscription = onEvent(responseEventKey) { event ->
            if (event.isInvalid) return@onEvent
            val response = decodeUserPromptResponseEvent(event)
            if (response == null) return@onEvent
            if (response.originatingMessageUuid != promptMessageUuid) {
                println("RigidBodyAndSegmentFigureB: ignoring stale/non-matching userPromptResponseEvent: $response")
                return@onEvent
            }
            println("RigidBodyAndSegmentFigureB: received matching userPromptResponseEvent: $response")
            responseReceived.complete(Unit)
        }
        responseReceived.await()
        subscription.cancel()

        println("RigidBodyAndSegmentFigureB: ask-user complete")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "ask-user-complete",
            helpKey   = "help.ask-user",
            messageId = "msg.ask-user.complete"
        ))
    }

    // =========================================================================
    // RBSF PROCEDURE HANDLERS — ICD section 31.2.1.18–20
    // =========================================================================

    // ICD 31.2.1.18 — promptAutoResponse: enum(YES, NO) optional
    //                  blocks until operator responds (unless promptAutoResponse is supplied);
    //                  stores result in rbsfM2OpResp global for use by subsequent steps
    onSetup("rbsfAskOpIfCmdM2") { command ->
        val promptAutoResponse: String? = command.kGet(stringKey("promptAutoResponse"))?.first

        if (promptAutoResponse != null) {
            println("RigidBodyAndSegmentFigureB: rbsfAskOpIfCmdM2 — auto-response: $promptAutoResponse (no UI prompt)")
            rbsfM2OpResp = promptAutoResponse
        } else {
            val promptMessageId = "msg.rbsfAskOpIfCmdM2.prompt"
            val promptEvent = buildProcedureEvent(Prefix.apply(prefix),
                type      = ProcedureEventType.USER_PROMPT,
                dialogKey = OriginatingPromptType.DECISION,
                helpKey   = "help.rbsfAskOpIfCmdM2",
                messageId = promptMessageId
            )
            val promptMessageUuid = messageUuidOf(promptEvent)
                ?: throw IllegalStateException("RigidBodyAndSegmentFigureB: failed to read back messageUuid from the rbsfAskOpIfCmdM2 prompt event we just built")
            publishEvent(promptEvent)

            println("RigidBodyAndSegmentFigureB: rbsfAskOpIfCmdM2 — waiting for userPromptResponseEvent matching $promptMessageUuid")
            val responseReceived = CompletableDeferred<String>()
            val responseEventKey = userPromptResponseEventKey(Prefix.apply(prefix)).toString()
            val subscription = onEvent(responseEventKey) { event ->
                if (event.isInvalid) return@onEvent
                val response = decodeUserPromptResponseEvent(event)
                if (response == null) return@onEvent
                if (response.originatingMessageUuid != promptMessageUuid) {
                    println("RigidBodyAndSegmentFigureB: ignoring stale/non-matching userPromptResponseEvent: $response")
                    return@onEvent
                }
                println("RigidBodyAndSegmentFigureB: received matching userPromptResponseEvent: $response")
                responseReceived.complete(response.decisionResponse)
            }
            rbsfM2OpResp = responseReceived.await()
            subscription.cancel()
        }

        println("RigidBodyAndSegmentFigureB: rbsfAskOpIfCmdM2 — rbsfM2OpResp=$rbsfM2OpResp")
    }

    // ICD 31.2.1.19 — rbsM2OpResp and m2CmdMethod read from file-level globals
    //                  set by rbsfAskOpIfCmdM2 and procedure setup respectively
    onSetup("rbsfCmdM2PttOrPxyIfRespOk") { command ->
        val rbsM2OpResp: String = rbsfM2OpResp
        val m2CmdMethod: String = rbsfM2CmdMethod

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCmdM2PttOrPxyIfRespOk-start",
            helpKey   = "help.rbsfCmdM2PttOrPxyIfRespOk",
            messageId = "msg.rbsfCmdM2PttOrPxyIfRespOk.start"
        ))
        println("RigidBodyAndSegmentFigureB: rbsfCmdM2PttOrPxyIfRespOk — opResp=$rbsM2OpResp, method=$m2CmdMethod")
        if (rbsM2OpResp == "YES") {
            when (m2CmdMethod) {
                "PTT" -> {
                    println("RigidBodyAndSegmentFigureB: rbsfCmdM2PttOrPxyIfRespOk — sending M2 piston/tip/tilt command")
                    // TODO: implement — send offsetM2Position (Piston, Tip, Tilt) to TCS
                    delay(1.seconds)
                }
                "PXY" -> {
                    println("RigidBodyAndSegmentFigureB: rbsfCmdM2PttOrPxyIfRespOk — sending M2 piston/x-decenter/y-decenter command")
                    // TODO: implement — send offsetM2Position (Piston, x-decenter, y-decenter) to TCS
                    delay(1.seconds)
                }
                else -> println("RigidBodyAndSegmentFigureB: rbsfCmdM2PttOrPxyIfRespOk — unknown m2CmdMethod: $m2CmdMethod")
            }
        } else {
            println("RigidBodyAndSegmentFigureB: rbsfCmdM2PttOrPxyIfRespOk — operator declined, skipping M2 command")
        }
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCmdM2PttOrPxyIfRespOk-complete",
            helpKey   = "help.rbsfCmdM2PttOrPxyIfRespOk",
            messageId = "msg.rbsfCmdM2PttOrPxyIfRespOk.complete"
        ))
    }

    // ICD 31.2.1.20 — rbsM2OpResp read from file-level global set by rbsfAskOpIfCmdM2
    onSetup("rbsfTakeSnapIfRespOk") { command ->
        val rbsM2OpResp: String = rbsfM2OpResp

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfTakeSnapIfRespOk-start",
            helpKey   = "help.rbsfTakeSnapIfRespOk",
            messageId = "msg.rbsfTakeSnapIfRespOk.start"
        ))
        println("RigidBodyAndSegmentFigureB: rbsfTakeSnapIfRespOk — opResp=$rbsM2OpResp")
        if (rbsM2OpResp == "YES") {
            println("RigidBodyAndSegmentFigureB: rbsfTakeSnapIfRespOk — sending saveM2Position to TCS")
            // TODO: implement — send saveM2Position command to TCS
            delay(1.seconds)
        } else {
            println("RigidBodyAndSegmentFigureB: rbsfTakeSnapIfRespOk — operator declined, skipping snapshot")
        }
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfTakeSnapIfRespOk-complete",
            helpKey   = "help.rbsfTakeSnapIfRespOk",
            messageId = "msg.rbsfTakeSnapIfRespOk.complete"
        ))
    }

}
