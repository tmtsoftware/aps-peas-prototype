package aps
import csw.prefix.models.Prefix
import esw.ocs.dsl.core.reusableScript
import esw.ocs.dsl.params.stringKey
import esw.ocs.dsl.params.kGet
import esw.ocs.dsl.params.first
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

val rigidBodyAndSegmentFigureC = reusableScript {

    // File-level global for the RBSF warping harness operator response, set
    // by rbsfAskOpIfCmdWh and read by subsequent steps in the same procedure run.
    var rbsfWhOpResp: String = DecisionResponse.NO

    // =========================================================================
    // RBSF PROCEDURE HANDLERS — ICD section 32.2.1.7–18
    // =========================================================================

    // ICD 32.2.1.7 — no parameters
    onSetup("rbsfCalcCentroidOffsets") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCalcCentroidOffsets-start",
            helpKey   = "help.rbsfCalcCentroidOffsets",
            messageId = "msg.rbsfCalcCentroidOffsets.start"
        ))
        println("RigidBodyAndSegmentFigureC: rbsfCalcCentroidOffsets — calculating centroid offsets from ref beam calibration map")
        // TODO: implement
        delay(1.seconds)
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCalcCentroidOffsets-complete",
            helpKey   = "help.rbsfCalcCentroidOffsets",
            messageId = "msg.rbsfCalcCentroidOffsets.complete"
        ))
    }

    // ICD 32.2.1.8 — no parameters
    onSetup("rbsfCalcM2PistonTipTilt") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCalcM2PistonTipTilt-start",
            helpKey   = "help.rbsfCalcM2PistonTipTilt",
            messageId = "msg.rbsfCalcM2PistonTipTilt.start"
        ))
        println("RigidBodyAndSegmentFigureC: rbsfCalcM2PistonTipTilt — calculating M2 piston, tip and tilt")
        // TODO: implement
        delay(1.seconds)
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCalcM2PistonTipTilt-complete",
            helpKey   = "help.rbsfCalcM2PistonTipTilt",
            messageId = "msg.rbsfCalcM2PistonTipTilt.complete"
        ))
    }

    // ICD 32.2.1.9 — no parameters
    onSetup("rbsfCalcM2PistonXYDecenter") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCalcM2PistonXYDecenter-start",
            helpKey   = "help.rbsfCalcM2PistonXYDecenter",
            messageId = "msg.rbsfCalcM2PistonXYDecenter.start"
        ))
        println("RigidBodyAndSegmentFigureC: rbsfCalcM2PistonXYDecenter — calculating M2 piston and (x,y) decenter")
        // TODO: implement
        delay(1.seconds)
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCalcM2PistonXYDecenter-complete",
            helpKey   = "help.rbsfCalcM2PistonXYDecenter",
            messageId = "msg.rbsfCalcM2PistonXYDecenter.complete"
        ))
    }

    // ICD 32.2.1.10 — no parameters
    onSetup("rbsfCalcSegmentPistonTipTilt") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCalcSegmentPistonTipTilt-start",
            helpKey   = "help.rbsfCalcSegmentPistonTipTilt",
            messageId = "msg.rbsfCalcSegmentPistonTipTilt.start"
        ))
        println("RigidBodyAndSegmentFigureC: rbsfCalcSegmentPistonTipTilt — calculating M1 segment pistons, tips and tilts")
        // TODO: implement
        delay(1.seconds)
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCalcSegmentPistonTipTilt-complete",
            helpKey   = "help.rbsfCalcSegmentPistonTipTilt",
            messageId = "msg.rbsfCalcSegmentPistonTipTilt.complete"
        ))
    }

    // ICD 32.2.1.11 — no parameters
    onSetup("rbsfCalcSegmentZernikes") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCalcSegmentZernikes-start",
            helpKey   = "help.rbsfCalcSegmentZernikes",
            messageId = "msg.rbsfCalcSegmentZernikes.start"
        ))
        println("RigidBodyAndSegmentFigureC: rbsfCalcSegmentZernikes — calculating M1 segment Zernikes")
        // TODO: implement
        delay(1.seconds)
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCalcSegmentZernikes-complete",
            helpKey   = "help.rbsfCalcSegmentZernikes",
            messageId = "msg.rbsfCalcSegmentZernikes.complete"
        ))
    }

    // ICD 32.2.1.12 — no parameters
    // Note: ICD description says "calculates M1 segments warping harness commands"
    // despite the command name suggesting "cmd M1 if resp ok" — using ICD description as authoritative
    onSetup("rbsfCmdM1IfRespOk") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCmdM1IfRespOk-start",
            helpKey   = "help.rbsfCmdM1IfRespOk",
            messageId = "msg.rbsfCmdM1IfRespOk.start"
        ))
        println("RigidBodyAndSegmentFigureC: rbsfCmdM1IfRespOk — calculating M1 segment warping harness commands")
        // TODO: implement
        delay(1.seconds)
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCmdM1IfRespOk-complete",
            helpKey   = "help.rbsfCmdM1IfRespOk",
            messageId = "msg.rbsfCmdM1IfRespOk.complete"
        ))
    }

    // ICD 32.2.1.13 — no parameters
    onSetup("rbsfCalcWhCmds") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCalcWhCmds-start",
            helpKey   = "help.rbsfCalcWhCmds",
            messageId = "msg.rbsfCalcWhCmds.start"
        ))
        println("RigidBodyAndSegmentFigureC: rbsfCalcWhCmds — TBD")
        // TODO: implement — ICD description is TBD
        delay(1.seconds)
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCalcWhCmds-complete",
            helpKey   = "help.rbsfCalcWhCmds",
            messageId = "msg.rbsfCalcWhCmds.complete"
        ))
    }

    // ICD 32.2.1.14 — no parameters
    onSetup("rbsfRenderWhDisplay") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.VIZ_DISPLAY,
            dialogKey = "rbsfRenderWhDisplay",
            helpKey   = "help.rbsfRenderWhDisplay",
            messageId = "msg.rbsfRenderWhDisplay"
        ))
        println("RigidBodyAndSegmentFigureC: rbsfRenderWhDisplay — rendering WH visualization display")
        // TODO: implement — publish viz display event with WH data
        delay(1.seconds)
    }

    // ICD 32.2.1.15 — promptAutoResponse: enum(YES, NO) optional
    //                  blocks until operator responds (unless promptAutoResponse is supplied);
    //                  stores result in rbsfWhOpResp global for use by subsequent steps
    onSetup("rbsfAskOpIfCmdWh") { command ->
        val promptAutoResponse: String? = command.kGet(stringKey("promptAutoResponse"))?.first

        if (promptAutoResponse != null) {
            println("RigidBodyAndSegmentFigureC: rbsfAskOpIfCmdWh — auto-response: $promptAutoResponse (no UI prompt)")
            rbsfWhOpResp = promptAutoResponse
        } else {
            val promptMessageId = "msg.rbsfAskOpIfCmdWh.prompt"
            val promptEvent = buildProcedureEvent(Prefix.apply(prefix),
                type      = ProcedureEventType.USER_PROMPT,
                dialogKey = OriginatingPromptType.DECISION,
                helpKey   = "help.rbsfAskOpIfCmdWh",
                messageId = promptMessageId
            )
            val promptMessageUuid = messageUuidOf(promptEvent)
                ?: throw IllegalStateException("RigidBodyAndSegmentFigureC: failed to read back messageUuid from the rbsfAskOpIfCmdWh prompt event we just built")
            publishEvent(promptEvent)

            println("RigidBodyAndSegmentFigureC: rbsfAskOpIfCmdWh — waiting for userPromptResponseEvent matching $promptMessageUuid")
            val responseReceived = CompletableDeferred<String>()
            val responseEventKey = userPromptResponseEventKey(Prefix.apply(prefix)).toString()
            val subscription = onEvent(responseEventKey) { event ->
                if (event.isInvalid) return@onEvent
                val response = decodeUserPromptResponseEvent(event)
                if (response == null) return@onEvent
                if (response.originatingMessageUuid != promptMessageUuid) {
                    println("RigidBodyAndSegmentFigureC: ignoring stale/non-matching userPromptResponseEvent: $response")
                    return@onEvent
                }
                println("RigidBodyAndSegmentFigureC: received matching userPromptResponseEvent: $response")
                responseReceived.complete(response.decisionResponse)
            }
            rbsfWhOpResp = responseReceived.await()
            subscription.cancel()
        }

        println("RigidBodyAndSegmentFigureC: rbsfAskOpIfCmdWh — rbsfWhOpResp=$rbsfWhOpResp")
    }

    // ICD 32.2.1.16 — no parameters; branches on rbsfWhOpResp global set by rbsfAskOpIfCmdWh
    onSetup("rbsfCmdWhIfRespOk") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCmdWhIfRespOk-start",
            helpKey   = "help.rbsfCmdWhIfRespOk",
            messageId = "msg.rbsfCmdWhIfRespOk.start"
        ))
        if (rbsfWhOpResp == DecisionResponse.YES) {
            println("RigidBodyAndSegmentFigureC: rbsfCmdWhIfRespOk — sending WH correction commands to M1CS")
            // TODO: implement — send warping harness correction cmds to M1CS
            delay(1.seconds)
        } else {
            println("RigidBodyAndSegmentFigureC: rbsfCmdWhIfRespOk — operator declined ($rbsfWhOpResp), skipping WH commands")
        }
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCmdWhIfRespOk-complete",
            helpKey   = "help.rbsfCmdWhIfRespOk",
            messageId = "msg.rbsfCmdWhIfRespOk.complete"
        ))
    }

    // ICD 32.2.1.17 — no parameters
    onSetup("rbsfWaitM1CmdComplete") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfWaitM1CmdComplete-start",
            helpKey   = "help.rbsfWaitM1CmdComplete",
            messageId = "msg.rbsfWaitM1CmdComplete.start"
        ))
        println("RigidBodyAndSegmentFigureC: rbsfWaitM1CmdComplete — waiting for M1CS segment PTT correction cmds to complete")
        // TODO: implement — wait for M1CS offsetActuatorPositions cmd complete
        delay(1.seconds)
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfWaitM1CmdComplete-complete",
            helpKey   = "help.rbsfWaitM1CmdComplete",
            messageId = "msg.rbsfWaitM1CmdComplete.complete"
        ))
    }

    // ICD 32.2.1.18 — no parameters
    onSetup("rbsfTakeSnapIfM1OrWhCmdSent") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfTakeSnapIfM1OrWhCmdSent-start",
            helpKey   = "help.rbsfTakeSnapIfM1OrWhCmdSent",
            messageId = "msg.rbsfTakeSnapIfM1OrWhCmdSent.start"
        ))
        println("RigidBodyAndSegmentFigureC: rbsfTakeSnapIfM1OrWhCmdSent — saving sensor calibration data if M1 or WH cmds were sent")
        // TODO: implement — if M1 PTT or WH cmds were sent, send saveSensorSettings to M1CS
        delay(1.seconds)
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfTakeSnapIfM1OrWhCmdSent-complete",
            helpKey   = "help.rbsfTakeSnapIfM1OrWhCmdSent",
            messageId = "msg.rbsfTakeSnapIfM1OrWhCmdSent.complete"
        ))
    }

}
