package aps
import csw.prefix.models.Prefix
import esw.ocs.dsl.core.reusableScript
import esw.ocs.dsl.core.ScriptScope
import esw.ocs.dsl.params.stringKey
import esw.ocs.dsl.params.intKey
import esw.ocs.dsl.params.kGet
import esw.ocs.dsl.params.first
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.CompletableDeferred
import esw.ocs.dsl.par

val rigidBodyAndSegmentFigureA = reusableScript {

    val scriptScope: ScriptScope = this

    // File-level global for the RBSF M1 operator response, set by
    // rbsfAskOpIfCmdM1 and read by subsequent steps in the same procedure run.
    var rbsfM1OpResp: String = DecisionResponse.NO

    // =========================================================================
    // ORIGINAL HANDLERS — prototype steps carried over from prior work
    // =========================================================================

    onSetupWithRestoreOnError("cmd-m1cs-moves") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "cmd-m1cs-moves-start",
            helpKey   = "help.cmd-m1cs-moves",
            messageId = "msg.cmd-m1cs-moves.start"
        ))
        val response = scriptScope.sendToGlc(command)
        println("cmd-m1cs-moves response: $response")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "cmd-m1cs-moves-complete",
            helpKey   = "help.cmd-m1cs-moves",
            messageId = "msg.cmd-m1cs-moves.complete"
        ))
    }

    onSetupWithRestoreOnError("calc-colorstep") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "calc-colorstep-start",
            helpKey   = "help.calc-colorstep",
            messageId = "msg.calc-colorstep.start"
        ))
        delay(2.seconds)
        println("RigidBodyAndSegmentFigureA: received calc-colorstep")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "calc-colorstep-complete",
            helpKey   = "help.calc-colorstep",
            messageId = "msg.calc-colorstep.complete"
        ))
    }

    onSetupWithRestoreOnError("calc-tt-offsets-to-acts") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "calc-tt-offsets-start",
            helpKey   = "help.calc-tt-offsets-to-acts",
            messageId = "msg.calc-tt-offsets-to-acts.start"
        ))
        delay(2.seconds)
        println("RigidBodyAndSegmentFigureA: received calc-tt-offsets-to-acts")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "calc-tt-offsets-complete",
            helpKey   = "help.calc-tt-offsets-to-acts",
            messageId = "msg.calc-tt-offsets-to-acts.complete"
        ))
    }

    onSetupWithRestoreOnError("calc-decompose-acts") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "calc-decompose-acts-start",
            helpKey   = "help.calc-decompose-acts",
            messageId = "msg.calc-decompose-acts.start"
        ))
        delay(2.seconds)
        println("RigidBodyAndSegmentFigureA: received calc-decompose-acts")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "calc-decompose-acts-complete",
            helpKey   = "help.calc-decompose-acts",
            messageId = "msg.calc-decompose-acts.complete"
        ))
    }

    onSetupWithRestoreOnError("send-sequence-to-sequencerB") { command: csw.params.commands.Setup ->
        println("RigidBodyAndSegmentFigureA: received send-sequence-to-sequencerB")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "send-sequence-to-sequencerB-start",
            helpKey   = "help.send-sequence-to-sequencerB",
            messageId = "msg.send-sequence-to-sequencerB.start"
        ))

        val seqBJsonStr: String = command.kGet(stringKey("sequencerBSequence"))!!.first
        val sequencerBSequence = aps.deserializeSequence(seqBJsonStr)
        val sequencerB = scriptScope.getPeasSequencer(SequencerLabel.A, SequencerLabel.B)

        println("RigidBodyAndSegmentFigureA: submitting sequence to sequencerB")
        val response = sequencerB.submitAndWait(sequencerBSequence, timeout = 120.seconds)
        println("RigidBodyAndSegmentFigureA: sequencerB response: $response")

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "send-sequence-to-sequencerB-complete",
            helpKey   = "help.send-sequence-to-sequencerB",
            messageId = "msg.send-sequence-to-sequencerB.complete"
        ))
    }

    // =========================================================================
    // RBSF PROCEDURE HANDLERS — ICD section 30.2.1.33–40
    // =========================================================================

    // ICD 30.2.1.33 — no parameters
    onSetupWithRestoreOnError("rbsfGetCurrentWhSettings") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfGetCurrentWhSettings-start",
            helpKey   = "help.rbsfGetCurrentWhSettings",
            messageId = "msg.rbsfGetCurrentWhSettings.start"
        ))
        println("RigidBodyAndSegmentFigureA: rbsfGetCurrentWhSettings — getting WH configs and current strains")
        // TODO: implement — send command to WH assembly to retrieve configs and strains
        delay(1.seconds)
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfGetCurrentWhSettings-complete",
            helpKey   = "help.rbsfGetCurrentWhSettings",
            messageId = "msg.rbsfGetCurrentWhSettings.complete"
        ))
    }

    // ICD 30.2.1.34 — exposureCount: Int,
    //                  rbsfB1SerializedSequence: String, rbsfC1SerializedSequence: String
    // Loop runs exposureCount + 1 times (n = 1..exposureCount+1):
    //   n <= exposureCount : submit B1 (take exposure)
    //   n > 1              : submit C1 (process previous exposure)
    //   both apply         : submit B1 and C1 in parallel via par
    onSetupWithRestoreOnError("rbsfTakeExposureWhileProcessingPrevious") { command ->
        val exposureCount: Int    = command.kGet(intKey("exposureCount"))!!.first
        val rbsfB1JsonStr: String = command.kGet(stringKey("rbsfB1SerializedSequence"))!!.first
        val rbsfC1JsonStr: String = command.kGet(stringKey("rbsfC1SerializedSequence"))!!.first

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfTakeExposureWhileProcessingPrevious-start",
            helpKey   = "help.rbsfTakeExposureWhileProcessingPrevious",
            messageId = "msg.rbsfTakeExposureWhileProcessingPrevious.start"
        ))

        val sequencerB = scriptScope.getPeasSequencer(SequencerLabel.A, SequencerLabel.B)
        val sequencerC = scriptScope.getPeasSequencer(SequencerLabel.A, SequencerLabel.C)
        val seqB1 = aps.deserializeSequence(rbsfB1JsonStr)
        val seqC1 = aps.deserializeSequence(rbsfC1JsonStr)

        for (n in 1..exposureCount + 1) {
            // AbortSequence sent to A only discards A's own PENDING steps -- it has no visibility
            // into this loop, which is just Kotlin control flow inside ONE in-flight step from A's
            // Engine's perspective. Without this check, an operator abort mid-loop would let the
            // current exposure finish (correct -- in-flight work isn't interrupted) but then just
            // keep starting new iterations regardless (the bug we saw: "everything just continued").
            // Checking here, between iterations, stops new ones from starting once abort is
            // requested, while still letting whatever's currently in-flight resolve naturally.
            if (operatorAbortRequested.get()) {
                println("RigidBodyAndSegmentFigureA: rbsfTakeExposureWhileProcessingPrevious — operator abort requested, stopping iteration loop early at n=$n")
                break
            }
            println("RigidBodyAndSegmentFigureA: rbsfTakeExposureWhileProcessingPrevious — iteration $n of ${exposureCount + 1}")
            when {
                n == 1 -> {
                    // First iteration: take exposure only, nothing to process yet
                    publishEvent(buildProcedureEvent(Prefix.apply(prefix),
                        type      = ProcedureEventType.ITERATION,
                        dialogKey = "iteration",
                        helpKey   = "help.iteration",
                        messageId = n.toString()
                    ))
                    println("RigidBodyAndSegmentFigureA: rbsfTakeExposureWhileProcessingPrevious — n=1, submitting B only (take exposure)")
                    sequencerB.submitAndWait(seqB1, timeout = 120.seconds)
                }
                n <= exposureCount -> {
                    // Middle iterations: take next exposure while processing previous in parallel
                    publishEvent(buildProcedureEvent(Prefix.apply(prefix),
                        type      = ProcedureEventType.ITERATION,
                        dialogKey = "iteration",
                        helpKey   = "help.iteration",
                        messageId = n.toString()
                    ))
                    println("RigidBodyAndSegmentFigureA: rbsfTakeExposureWhileProcessingPrevious — n=$n, submitting B and C in parallel")
                    par(
                        { sequencerB.submitAndWait(seqB1, timeout = 120.seconds) },
                        { sequencerC.submitAndWait(seqC1, timeout = 120.seconds) }
                    )
                }
                else -> {
                    // Final iteration (n = exposureCount + 1): process last exposure, no new exposure
                    println("RigidBodyAndSegmentFigureA: rbsfTakeExposureWhileProcessingPrevious — n=$n (final), submitting C only (process last exposure)")
                    sequencerC.submitAndWait(seqC1, timeout = 120.seconds)
                }
            }
        }

        // Signal end of exposure loop — triggers Align Completion tab in UI
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.ITERATION,
            dialogKey = "iteration",
            helpKey   = "help.iteration",
            messageId = "0"
        ))

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfTakeExposureWhileProcessingPrevious-complete",
            helpKey   = "help.rbsfTakeExposureWhileProcessingPrevious",
            messageId = "msg.rbsfTakeExposureWhileProcessingPrevious.complete"
        ))
    }

    // ICD 30.2.1.35 — no parameters
    onSetupWithRestoreOnError("rbsfCalcAverages") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCalcAverages-start",
            helpKey   = "help.rbsfCalcAverages",
            messageId = "msg.rbsfCalcAverages.start"
        ))
        println("RigidBodyAndSegmentFigureA: rbsfCalcAverages — calculating averages over exposures")
        // TODO: implement — calc averages of centroid offsets, M2 PTT, M2 PXY, Seg PTT, Seg Zernikes
        delay(1.seconds)
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCalcAverages-complete",
            helpKey   = "help.rbsfCalcAverages",
            messageId = "msg.rbsfCalcAverages.complete"
        ))
    }

    // ICD 30.2.1.36 — no parameters
    onSetupWithRestoreOnError("rbsfRenderAvgCentroidOffsetsDisplay") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.VIZ_DISPLAY,
            dialogKey = "rbsfRenderAvgCentroidOffsetsDisplay",
            helpKey   = "help.rbsfRenderAvgCentroidOffsetsDisplay",
            messageId = "msg.rbsfRenderAvgCentroidOffsetsDisplay"
        ))
        println("RigidBodyAndSegmentFigureA: rbsfRenderAvgCentroidOffsetsDisplay — rendering display")
        // TODO: implement — publish viz display event with avg centroid offsets data
        delay(1.seconds)
    }

    // ICD 30.2.1.37 — no parameters
    onSetupWithRestoreOnError("rbsfRenderM1CmdsDisplay") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.VIZ_DISPLAY,
            dialogKey = "rbsfRenderM1CmdsDisplay",
            helpKey   = "help.rbsfRenderM1CmdsDisplay",
            messageId = "msg.rbsfRenderM1CmdsDisplay"
        ))
        println("RigidBodyAndSegmentFigureA: rbsfRenderM1CmdsDisplay — rendering M1 commands display")
        // TODO: implement — publish viz display event with M1 commands data
        delay(1.seconds)
    }

    // ICD 30.2.1.38 — promptAutoResponse: enum(YES, NO) optional
    //                  blocks until operator responds (unless promptAutoResponse is supplied);
    //                  stores result in rbsfM1OpResp global for use by subsequent steps
    onSetupWithRestoreOnError("rbsfAskOpIfCmdM1") { command ->
        val promptAutoResponse: String? = command.kGet(stringKey("promptAutoResponse"))?.first

        if (promptAutoResponse != null) {
            println("RigidBodyAndSegmentFigureA: rbsfAskOpIfCmdM1 — auto-response: $promptAutoResponse (no UI prompt)")
            rbsfM1OpResp = promptAutoResponse
        } else {
            val promptMessageId = "msg.rbsfAskOpIfCmdM1.prompt"
            val promptEvent = buildProcedureEvent(Prefix.apply(prefix),
                type      = ProcedureEventType.USER_PROMPT,
                dialogKey = OriginatingPromptType.DECISION,
                helpKey   = "help.rbsfAskOpIfCmdM1",
                messageId = promptMessageId
            )
            val promptMessageUuid = messageUuidOf(promptEvent)
                ?: throw IllegalStateException("RigidBodyAndSegmentFigureA: failed to read back messageUuid from the rbsfAskOpIfCmdM1 prompt event we just built")
            publishEvent(promptEvent)

            println("RigidBodyAndSegmentFigureA: rbsfAskOpIfCmdM1 — waiting for userPromptResponseEvent matching $promptMessageUuid")
            val responseReceived = CompletableDeferred<String>()
            val responseEventKey = userPromptResponseEventKey(Prefix.apply(prefix)).toString()
            val subscription = onEvent(responseEventKey) { event ->
                if (event.isInvalid) return@onEvent
                val response = decodeUserPromptResponseEvent(event)
                if (response == null) return@onEvent
                if (response.originatingMessageUuid != promptMessageUuid) {
                    println("RigidBodyAndSegmentFigureA: ignoring stale/non-matching userPromptResponseEvent: $response")
                    return@onEvent
                }
                println("RigidBodyAndSegmentFigureA: received matching userPromptResponseEvent: $response")
                responseReceived.complete(response.decisionResponse)
            }
            rbsfM1OpResp = responseReceived.await()
            subscription.cancel()
        }

        println("RigidBodyAndSegmentFigureA: rbsfAskOpIfCmdM1 — rbsfM1OpResp=$rbsfM1OpResp")
    }

    // ICD 30.2.1.39 — rbsfB2SerializedSequence: String, rbsfC2SerializedSequence: String
    onSetupWithRestoreOnError("rbsfCmdM1CalcWhCmdsWhileAskOpCmdM2") { command ->
        val rbsfB2JsonStr: String = command.kGet(stringKey("rbsfB2SerializedSequence"))!!.first
        val rbsfC2JsonStr: String = command.kGet(stringKey("rbsfC2SerializedSequence"))!!.first

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCmdM1CalcWhCmdsWhileAskOpCmdM2-start",
            helpKey   = "help.rbsfCmdM1CalcWhCmdsWhileAskOpCmdM2",
            messageId = "msg.rbsfCmdM1CalcWhCmdsWhileAskOpCmdM2.start"
        ))
        println("RigidBodyAndSegmentFigureA: rbsfCmdM1CalcWhCmdsWhileAskOpCmdM2 — commanding M1, calc WH cmds, ask op re: M2")

        val sequencerB = scriptScope.getPeasSequencer(SequencerLabel.A, SequencerLabel.B)
        val sequencerC = scriptScope.getPeasSequencer(SequencerLabel.A, SequencerLabel.C)
        val seqB2 = aps.deserializeSequence(rbsfB2JsonStr)
        val seqC2 = aps.deserializeSequence(rbsfC2JsonStr)

        // TODO: In parallel — send M1 cmds, calc WH cmds, submit sub-sequences to B and C
        par(
            { sequencerB.submitAndWait(seqB2, timeout = 120.seconds) },
            { sequencerC.submitAndWait(seqC2, timeout = 120.seconds) }
        )
 
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfCmdM1CalcWhCmdsWhileAskOpCmdM2-complete",
            helpKey   = "help.rbsfCmdM1CalcWhCmdsWhileAskOpCmdM2",
            messageId = "msg.rbsfCmdM1CalcWhCmdsWhileAskOpCmdM2.complete"
        ))
    }

    // ICD 30.2.1.40 — rbsfB3SerializedSequence: String, rbsfC3SerializedSequence: String
    onSetupWithRestoreOnError("rbsfAskAndCmdWhWhileCmdM2") { command ->
        val rbsfB3JsonStr: String = command.kGet(stringKey("rbsfB3SerializedSequence"))!!.first
        val rbsfC3JsonStr: String = command.kGet(stringKey("rbsfC3SerializedSequence"))!!.first

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfAskAndCmdWhWhileCmdM2-start",
            helpKey   = "help.rbsfAskAndCmdWhWhileCmdM2",
            messageId = "msg.rbsfAskAndCmdWhWhileCmdM2.start"
        ))
        println("RigidBodyAndSegmentFigureA: rbsfAskAndCmdWhWhileCmdM2 — render WH display, ask op, cmd WH; in parallel cmd M2")

        val sequencerB = scriptScope.getPeasSequencer(SequencerLabel.A, SequencerLabel.B)
        val sequencerC = scriptScope.getPeasSequencer(SequencerLabel.A, SequencerLabel.C)
        val seqB3 = aps.deserializeSequence(rbsfB3JsonStr)
        val seqC3 = aps.deserializeSequence(rbsfC3JsonStr)

        par(
            { sequencerB.submitAndWait(seqB3, timeout = 120.seconds) },
            { sequencerC.submitAndWait(seqC3, timeout = 120.seconds) }
        )

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "rbsfAskAndCmdWhWhileCmdM2-complete",
            helpKey   = "help.rbsfAskAndCmdWhWhileCmdM2",
            messageId = "msg.rbsfAskAndCmdWhWhileCmdM2.complete"
        ))
    }

}
