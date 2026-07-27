package aps
import csw.prefix.models.Prefix
import csw.params.core.models.Choice
import esw.ocs.dsl.core.reusableScript
import esw.ocs.dsl.par
import esw.ocs.dsl.params.floatKey
import esw.ocs.dsl.params.choiceKey
import esw.ocs.dsl.params.choicesOf
import esw.ocs.dsl.params.kGet
import esw.ocs.dsl.params.first
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

// Global sequencer script variables — populated by setup() and startPitLoop(), consumed
// later during PIT loop execution computations and exposures (ICD 21.2.1.1, 21.2.1.2).
var currentPitToPshPrRotationOffset: Float = 0.0f
var currentPitToPshPrScaleOffset: Float = 0.0f
var currentPitToPshPrXTranslationOffset: Float = 0.0f
var currentPitToPshPrYTranslationOffset: Float = 0.0f
var currentPshPointingOffsetsCalibrationImageCenterX: Float? = null
var currentPshPointingOffsetsCalibrationImageCenterY: Float? = null
var currentPitPointingOffsetsCalibrationImageCenterX: Float? = null
var currentPitPointingOffsetsCalibrationImageCenterY: Float? = null
var currentAptPointingOffsetsCalibrationCentroidX: Float? = null
var currentAptPointingOffsetsCalibrationCentroidY: Float? = null

var currentIntegrationTime: Float? = null
var currentGainMode: Choice? = null

val pitCommon = reusableScript {

    // =========================================================================
    // ICS.PIT.Sequencer COMMAND HANDLERS
    // ICD 21.2 Commands for ICS.PIT.Sequencer
    // =========================================================================

    // ICD 21.2.1.1 setup — sets up the PIT Loop: filter, pupil mask, and pupil
    // registration offsets from the most recent Pupil Registration Calibration Procedure.
    // pitFilter, pitPupilMask, pitToPshPrRotationOffset, pitToPshPrScaleOffset,
    // pitToPshPrXTranslationOffset, pitToPshPrYTranslationOffset: required
    // pshPointingOffsetsCalibrationImageCenterX/Y, pitPointingOffsetsCalibrationImageCenterX/Y,
    // aptPointingOffsetsCalibrationCentroidX/Y: optional
    // Completion Type: longRunning
    onSetup("setup") { command ->
        val pitFilter: Choice = command.kGet(choiceKey("pitFilter", choicesOf("F890N", "F891N", "F850M", "F750W", "F810N", "F630N", "F865N")))!!.first
        val pitPupilMask: Choice = command.kGet(choiceKey("pitPupilMask", choicesOf("PH-1-1", "Clear")))!!.first
        val pitToPshPrRotationOffset: Float                 = command.kGet(floatKey("pitToPshPrRotationOffset"))!!.first
        val pitToPshPrScaleOffset: Float                    = command.kGet(floatKey("pitToPshPrScaleOffset"))!!.first
        val pitToPshPrXTranslationOffset: Float             = command.kGet(floatKey("pitToPshPrXTranslationOffset"))!!.first
        val pitToPshPrYTranslationOffset: Float             = command.kGet(floatKey("pitToPshPrYTranslationOffset"))!!.first
        val pshPointingOffsetsCalibrationImageCenterX: Float? = command.kGet(floatKey("pshPointingOffsetsCalibrationImageCenterX"))?.first
        val pshPointingOffsetsCalibrationImageCenterY: Float? = command.kGet(floatKey("pshPointingOffsetsCalibrationImageCenterY"))?.first
        val pitPointingOffsetsCalibrationImageCenterX: Float? = command.kGet(floatKey("pitPointingOffsetsCalibrationImageCenterX"))?.first
        val pitPointingOffsetsCalibrationImageCenterY: Float? = command.kGet(floatKey("pitPointingOffsetsCalibrationImageCenterY"))?.first
        val aptPointingOffsetsCalibrationCentroidX: Float?  = command.kGet(floatKey("aptPointingOffsetsCalibrationCentroidX"))?.first
        val aptPointingOffsetsCalibrationCentroidY: Float?  = command.kGet(floatKey("aptPointingOffsetsCalibrationCentroidY"))?.first

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "setup-start",
            helpKey   = "help.setup",
            messageId = "msg.setup.start"
        ))
        println("PitCommon: setup — pitFilter=${pitFilter.name()}, pitPupilMask=${pitPupilMask.name()}, " +
                "pitToPshPrRotationOffset=$pitToPshPrRotationOffset, pitToPshPrScaleOffset=$pitToPshPrScaleOffset, " +
                "pitToPshPrXTranslationOffset=$pitToPshPrXTranslationOffset, pitToPshPrYTranslationOffset=$pitToPshPrYTranslationOffset, " +
                "pshPointingOffsetsCalibrationImageCenterX=$pshPointingOffsetsCalibrationImageCenterX, " +
                "pshPointingOffsetsCalibrationImageCenterY=$pshPointingOffsetsCalibrationImageCenterY, " +
                "pitPointingOffsetsCalibrationImageCenterX=$pitPointingOffsetsCalibrationImageCenterX, " +
                "pitPointingOffsetsCalibrationImageCenterY=$pitPointingOffsetsCalibrationImageCenterY, " +
                "aptPointingOffsetsCalibrationCentroidX=$aptPointingOffsetsCalibrationCentroidX, " +
                "aptPointingOffsetsCalibrationCentroidY=$aptPointingOffsetsCalibrationCentroidY")
        // Selects the filter and pupil mask on the PIT assemblies, in parallel.
        // NOTE: selectPupilMask's parameter is oddly named "filter" rather than "pupilMask"
        // (apparent copy-paste artifact from selectFilter's table). pitPupilMask is declared
        // above using PupilMaskWheel's own physical enum (PH-1-1, Clear), so it passes through
        // directly here with no further mapping needed.
        par(
            {
                sendAssemblyCommand("ICS.PIT.FilterWheel", Setup(prefix, "selectFilter")
                    .add(choiceKey("filter", choicesOf("F890N", "F891N", "F850M", "F750W", "F810N", "F630N", "F865N")).set(pitFilter)))
            },
            {
                sendAssemblyCommand("ICS.PIT.PupilMaskWheel", Setup(prefix, "selectPupilMask")
                    .add(choiceKey("filter", choicesOf("PH-1-1", "Clear")).set(pitPupilMask)))
            }
        )

        // Saves the remaining values (pupil registration offsets + optional calibration
        // centers/centroids) in global sequencer script variables for later use during PIT
        // loop execution computations and exposures.
        currentPitToPshPrRotationOffset = pitToPshPrRotationOffset
        currentPitToPshPrScaleOffset = pitToPshPrScaleOffset
        currentPitToPshPrXTranslationOffset = pitToPshPrXTranslationOffset
        currentPitToPshPrYTranslationOffset = pitToPshPrYTranslationOffset
        currentPshPointingOffsetsCalibrationImageCenterX = pshPointingOffsetsCalibrationImageCenterX
        currentPshPointingOffsetsCalibrationImageCenterY = pshPointingOffsetsCalibrationImageCenterY
        currentPitPointingOffsetsCalibrationImageCenterX = pitPointingOffsetsCalibrationImageCenterX
        currentPitPointingOffsetsCalibrationImageCenterY = pitPointingOffsetsCalibrationImageCenterY
        currentAptPointingOffsetsCalibrationCentroidX = aptPointingOffsetsCalibrationCentroidX
        currentAptPointingOffsetsCalibrationCentroidY = aptPointingOffsetsCalibrationCentroidY
        delay(1.seconds)
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "setup-complete",
            helpKey   = "help.setup",
            messageId = "msg.setup.complete"
        ))
    }

    // ICD 21.2.1.2 startPitLoop — integrationTime: float (second), gainMode: enum (12-BIT, 16-BIT), both required
    // Completion Type: longRunning
    onSetup("startPitLoop") { command ->
        val integrationTime: Float = command.kGet(floatKey("integrationTime"))!!.first
        val gainMode: Choice = command.kGet(choiceKey("gainMode", choicesOf("12-BIT", "16-BIT")))!!.first

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "startPitLoop-start",
            helpKey   = "help.startPitLoop",
            messageId = "msg.startPitLoop.start"
        ))
        println("PitCommon: startPitLoop — integrationTime=$integrationTime, gainMode=${gainMode.name()}")
        // Stores the detector integration time and gain mode in global sequencer script
        // variables. No assemblies are commanded here -- these values are consumed later
        // during PIT loop execution (e.g. when driving APT.Detector exposures).
        currentIntegrationTime = integrationTime
        currentGainMode = gainMode
        delay(1.seconds)
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "startPitLoop-complete",
            helpKey   = "help.startPitLoop",
            messageId = "msg.startPitLoop.complete"
        ))
    }

    // ICD 21.2.1.3 stopPitLoop — no parameters
    // Completion Type: immediate
    onSetup("stopPitLoop") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "stopPitLoop-start",
            helpKey   = "help.stopPitLoop",
            messageId = "msg.stopPitLoop.start"
        ))
        println("PitCommon: stopPitLoop — stopping the PIT Loop")
        // TODO: implement — stops the PIT Loop
        // Completion Type is immediate, so no simulated delay here unlike the longRunning handlers above
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "stopPitLoop-complete",
            helpKey   = "help.stopPitLoop",
            messageId = "msg.stopPitLoop.complete"
        ))
    }

}
