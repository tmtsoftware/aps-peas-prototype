package aps
import csw.prefix.models.Prefix
import csw.params.commands.Sequence
import csw.params.core.models.Choice
import esw.ocs.dsl.core.reusableScript
import esw.ocs.dsl.core.ScriptScope
import esw.ocs.dsl.params.stringKey
import esw.ocs.dsl.params.floatKey
import esw.ocs.dsl.params.intKey
import esw.ocs.dsl.params.choiceKey
import esw.ocs.dsl.params.choicesOf
import esw.ocs.dsl.params.kGet
import esw.ocs.dsl.params.first
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

// GLC sensor settings snapshot key, captured at the start of alignmentProcedureStartup via
// saveSensorSettings() (GlcFacade.kt) and consumed by restoreTelescopeState()
// (RestoreOnErrorWrapper.kt) whenever any onSetupWithRestoreOnError-wrapped handler on this
// sequencer either throws or completes after an operator abort. File-level (package "aps")
// rather than local to the reusableScript block below so it's visible from
// RestoreOnErrorWrapper.kt and PeasSequencerA.kts too -- all compiled together as part of
// the same sbt-kotlin-plugin module. Reset to null once consumed by restoreTelescopeState(),
// so a stale key from a prior sequence run is never reused.
//
// Uses AtomicReference rather than a plain var (even @Volatile) -- written in
// alignmentProcedureStartup's coroutine, read in restoreTelescopeState's, which can
// legitimately run on different underlying threads. @Volatile on this top-level property was
// tried first and did NOT fix the cross-thread visibility problem in practice (verified:
// operatorAbortRequested below had the same issue with @Volatile and switching to
// AtomicBoolean is what actually fixed it) -- rather than keep debugging exactly why
// @Volatile didn't take effect here, AtomicReference is the unambiguous, guaranteed-correct
// alternative.
val sensorSnapshotKeyRef = AtomicReference<Int?>(null)

// Set by PeasSequencerA.kts's onAbortSequence handler when the UI calls abortSequence() on
// this sequencer (operator pressed Abort -- either the top-level Abort button, or Abort from
// a step's WARNING prompt). onAbortSequence fires concurrently with whatever step is
// currently in-flight (abort does not cancel/interrupt a running step -- see
// SequencerBehavior.scala's discardPending, which only removes PENDING steps), so this flag
// is just recorded here; onSetupWithRestoreOnError (RestoreOnErrorWrapper.kt) is what checks
// it AFTER a handler's own body completes, guaranteeing restoration only runs once whatever
// was in-flight (e.g. the full B/D submitAndWait chain from rbsfTakeExposureWhileProcessingPrevious)
// has actually finished.
//
// @Volatile: verified necessary, not precautionary -- without it, the exposure loop in
// rbsfTakeExposureWhileProcessingPrevious (RigidBodyAndSegmentFigureA.kt) kept reading this as
// false and running all remaining iterations, even after onAbortSequence had already set it to
// true on a different thread/coroutine. onAbortSequence and the loop's own check are not
// guaranteed to run on the same thread, so a plain var gave no visibility guarantee at all.
val operatorAbortRequested = AtomicBoolean(false)

val commonA = reusableScript {

    val scriptScope: ScriptScope = this

    // =========================================================================
    // COMMON HANDLERS — shared across multiple procedures on Sequencer A
    // =========================================================================

    // ICD 30.2.1.1 — filter: enum required, pupilMask: enum required, analogGainMode: enum optional.
    // pshRoiStartRow/Col/Width/Height, pshBinning, procedureId, observationId are NOT part of the
    // ICD's declared parameter set for this command -- added here as prototype-only extensions so
    // configureDetector (downstream, via setupPshOpticalArm) has real, editable values instead of
    // hardcoded placeholders. ROI/binning stay optional (matching configureDetector's own
    // optionality); procedureId/observationId are required here since there's no other source for
    // them yet.
    //
    // Uses onSetupWithRestoreOnError (not raw onSetup) -- see RestoreOnErrorWrapper.kt -- so any
    // exception this handler throws (including one propagated up from B/D via the default
    // submit()/submitAndWait() resumeOnError=false behavior), or an operator abort recorded via
    // operatorAbortRequested, triggers restoreTelescopeState().
    onSetupWithRestoreOnError("alignmentProcedureStartup") { command ->
        // Snapshot GLC's current sensor settings before anything else runs, per ICD
        // M1CS-APS SDB §2.2.1.1.
        sensorSnapshotKeyRef.set(scriptScope.saveSensorSettings())

        val filter: Choice          = command.kGet(choiceKey("filter", choicesOf("F890N", "F891N", "F850M", "F750W", "F810N", "F630N", "F865N")))!!.first
        val pupilMask: Choice       = command.kGet(choiceKey("pupilMask", choicesOf("PH-2-0", "SH-0", "SH-2", "SH-5", "Clear")))!!.first
        val analogGainMode: Choice? = command.kGet(choiceKey("analogGainMode", choicesOf("LOW", "HIGH", "HDR")))?.first
        val pshRoiStartRow: Int?    = command.kGet(intKey("pshRoiStartRow"))?.first
        val pshRoiStartCol: Int?    = command.kGet(intKey("pshRoiStartCol"))?.first
        val pshRoiWidth: Int?       = command.kGet(intKey("pshRoiWidth"))?.first
        val pshRoiHeight: Int?      = command.kGet(intKey("pshRoiHeight"))?.first
        val pshBinning: Int?        = command.kGet(intKey("pshBinning"))?.first
        val procedureId: String     = command.kGet(stringKey("procedureId"))!!.first
        val observationId: String   = command.kGet(stringKey("observationId"))!!.first

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "alignmentProcedureStartup-start",
            helpKey   = "help.alignmentProcedureStartup",
            messageId = "msg.alignmentProcedureStartup.start"
        ))
        println("CommonA: alignmentProcedureStartup — filter=${filter.name()}, pupilMask=${pupilMask.name()}, analogGainMode=${analogGainMode?.name()}, " +
                "pshRoiStartRow=$pshRoiStartRow, pshRoiStartCol=$pshRoiStartCol, pshRoiWidth=$pshRoiWidth, pshRoiHeight=$pshRoiHeight, " +
                "pshBinning=$pshBinning, procedureId=$procedureId, observationId=$observationId")

        // Step 1: set the APS Instrument Operating Mode to ON_SKY_MODE (ICS Sequencer).
        // sendToIcsSequencer applies the isSoftwareOnlyMode() facade internally.
        val setOperatingModeCmd = Setup(prefix, "setOperatingMode")
            .add(choiceKey("operatingMode", choicesOf("CALIBRATION_SOURCE_MODE", "STIMULUS_SOURCE_MODE", "ON_SKY_MODE", "STANDBY_MODE", "STARTUP_MODE")).set(Choice("ON_SKY_MODE")))
        val setOperatingModeResponse = scriptScope.sendToIcsSequencer(setOperatingModeCmd)
        println("alignmentProcedureStartup: setOperatingMode response=$setOperatingModeResponse")

        // Step 2: stop the PIT Loop before reconfiguring it (PIT Sequencer, facade-gated)
        val stopPitLoopCmd = Setup(prefix, "stopPitLoop")
        val stopPitLoopResponse = scriptScope.sendToPitSequencer(stopPitLoopCmd)
        println("alignmentProcedureStartup: stopPitLoop response=$stopPitLoopResponse")

        // Step 3: in parallel — set up the PIT Loop (PIT Sequencer, facade-gated) and set up
        // the PSH optical arm (PEAS Sequencer B, always sent for real — not facade-gated).
        // pitFilter reuses this command's filter directly (same enum). pitPupilMask, however,
        // maps down from this command's wider pupilMask enum (PH-2-0, SH-0, SH-2, SH-5, Clear)
        // to PIT.PupilMaskWheel's actual physical positions (PH-1-1, Clear) -- anything that
        // isn't "Clear" becomes "PH-1-1". pshFilter/pshPupilMask reuse filter/pupilMask
        // directly since PSH's enums match this command's own exactly.
        // TODO: pitToPshPr*Offset values should come from the most recent Pupil Registration
        // Calibration Procedure (peas-procedure-data-service) — placeholders (0.0f) until that's wired up.
        val pitPupilMaskValue = if (pupilMask.name() == "Clear") "Clear" else "PH-1-1"
        coroutineScope {
            val setupPitLoopDeferred = async {
                val setupPitLoopCmd = Setup(prefix, "setup")
                    .add(choiceKey("pitFilter", choicesOf("F890N", "F891N", "F850M", "F750W", "F810N", "F630N", "F865N")).set(filter))
                    .add(choiceKey("pitPupilMask", choicesOf("PH-1-1", "Clear")).set(Choice(pitPupilMaskValue)))
                    .add(floatKey("pitToPshPrRotationOffset").set(0.0f))
                    .add(floatKey("pitToPshPrScaleOffset").set(0.0f))
                    .add(floatKey("pitToPshPrXTranslationOffset").set(0.0f))
                    .add(floatKey("pitToPshPrYTranslationOffset").set(0.0f))
                scriptScope.sendToPitSequencer(setupPitLoopCmd)
            }
            val setupPshOpticalArmDeferred = async {
                var setupPshOpticalArmCmd = Setup(prefix, "setupPshOpticalArm")
                    .add(choiceKey("pshFilter", choicesOf("F890N", "F891N", "F850M", "F750W", "F810N", "F630N", "F865N")).set(filter))
                    .add(choiceKey("pshPupilMask", choicesOf("PH-2-0", "SH-0", "SH-2", "SH-5", "Clear")).set(pupilMask))
                    .add(stringKey("procedureId").set(procedureId))
                    .add(stringKey("observationId").set(observationId))
                pshRoiStartRow?.let { setupPshOpticalArmCmd = setupPshOpticalArmCmd.add(intKey("pshRoiStartRow").set(it)) }
                pshRoiStartCol?.let { setupPshOpticalArmCmd = setupPshOpticalArmCmd.add(intKey("pshRoiStartCol").set(it)) }
                pshRoiWidth?.let { setupPshOpticalArmCmd = setupPshOpticalArmCmd.add(intKey("pshRoiWidth").set(it)) }
                pshRoiHeight?.let { setupPshOpticalArmCmd = setupPshOpticalArmCmd.add(intKey("pshRoiHeight").set(it)) }
                pshBinning?.let { setupPshOpticalArmCmd = setupPshOpticalArmCmd.add(intKey("pshBinning").set(it)) }
                val sequencerB = scriptScope.getPeasSequencer(SequencerLabel.A, SequencerLabel.B)
                sequencerB.submitAndWait(Sequence.create(listOf(setupPshOpticalArmCmd)))
            }
            val setupPitLoopResponse = setupPitLoopDeferred.await()
            val setupPshOpticalArmResponse = setupPshOpticalArmDeferred.await()
            println("alignmentProcedureStartup: setup(PIT) response=$setupPitLoopResponse")
            println("alignmentProcedureStartup: setupPshOpticalArm response=$setupPshOpticalArmResponse")
        }

        // Step 4: start the PIT Loop (PIT Sequencer, facade-gated)
        // TODO: integrationTime/gainMode placeholders — should come from procedure configuration.
        val startPitLoopCmd = Setup(prefix, "startPitLoop")
            .add(floatKey("integrationTime").set(1.0f))
            .add(choiceKey("gainMode", choicesOf("12-BIT", "16-BIT")).set(Choice("16-BIT")))
        val startPitLoopResponse = scriptScope.sendToPitSequencer(startPitLoopCmd)
        println("alignmentProcedureStartup: startPitLoop response=$startPitLoopResponse")

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "alignmentProcedureStartup-complete",
            helpKey   = "help.alignmentProcedureStartup",
            messageId = "msg.alignmentProcedureStartup.complete"
        ))
    }

    // ICD 30.2.1.2 — all parameters required except aptGainMode (optional)
    onSetupWithRestoreOnError("alignmentProcedureNewStarStartup") { command ->
        val pitFilter: Choice                             = command.kGet(choiceKey("pitFilter", choicesOf("F890N", "F891N", "F850M", "F750W", "F810N", "F630N", "F865N")))!!.first
        val pitPupilMask: Choice                          = command.kGet(choiceKey("pitPupilMask", choicesOf("PH-2-0", "SH-0", "SH-2", "SH-5", "Clear")))!!.first
        val pitIntTime: Float                             = command.kGet(floatKey("pitIntTime"))!!.first
        val aptFilter: Choice                              = command.kGet(choiceKey("aptFilter", choicesOf("ND1", "ND2", "NB589", "OPEN")))!!.first
        val aptIntTime: Float                             = command.kGet(floatKey("aptIntTime"))!!.first
        val aptGainMode: Choice?                          = command.kGet(choiceKey("aptGainMode", choicesOf("12-BIT", "16-BIT")))?.first
        val pitToPshPrRotationOffset: Float               = command.kGet(floatKey("pitToPshPrRotationOffset"))!!.first
        val pitToPshPrScaleOffset: Float                  = command.kGet(floatKey("pitToPshPrScaleOffset"))!!.first
        val pitToPshPrXTranslationOffset: Float           = command.kGet(floatKey("pitToPshPrXTranslationOffset"))!!.first
        val pitToPshPrYTranslationOffset: Float           = command.kGet(floatKey("pitToPshPrYTranslationOffset"))!!.first
        val pshPointingOffsetsCalibrationImageCenterX: Float = command.kGet(floatKey("pshPointingOffsetsCalibrationImageCenterX"))!!.first
        val pshPointingOffsetsCalibrationImageCenterY: Float = command.kGet(floatKey("pshPointingOffsetsCalibrationImageCenterY"))!!.first
        val pitPointingOffsetsCalibrationImageCenterX: Float = command.kGet(floatKey("pitPointingOffsetsCalibrationImageCenterX"))!!.first
        val pitPointingOffsetsCalibrationImageCenterY: Float = command.kGet(floatKey("pitPointingOffsetsCalibrationImageCenterY"))!!.first
        val aptPointingOffsetsCalibrationCentroidX: Float = command.kGet(floatKey("aptPointingOffsetsCalibrationCentroidX"))!!.first
        val aptPointingOffsetsCalibrationCentroidY: Float = command.kGet(floatKey("aptPointingOffsetsCalibrationCentroidY"))!!.first

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "alignmentProcedureNewStarStartup-start",
            helpKey   = "help.alignmentProcedureNewStarStartup",
            messageId = "msg.alignmentProcedureNewStarStartup.start"
        ))
        println("CommonA: alignmentProcedureNewStarStartup — pitFilter=${pitFilter.name()}, pitPupilMask=${pitPupilMask.name()}, " +
                "pitIntTime=$pitIntTime, aptFilter=${aptFilter.name()}, aptIntTime=$aptIntTime, aptGainMode=${aptGainMode?.name()}")
        // TODO: implement — first step in on-sky procedures requiring acquisition of a new star target
        delay(1.seconds)
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "alignmentProcedureNewStarStartup-complete",
            helpKey   = "help.alignmentProcedureNewStarStartup",
            messageId = "msg.alignmentProcedureNewStarStartup.complete"
        ))
    }

}
