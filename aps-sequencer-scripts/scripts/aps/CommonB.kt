package aps
import csw.prefix.models.Prefix
import csw.params.core.models.Choice
import esw.ocs.dsl.core.reusableScript
import esw.ocs.dsl.core.ScriptScope
import esw.ocs.dsl.par
import esw.ocs.dsl.params.stringKey
import esw.ocs.dsl.params.floatKey
import esw.ocs.dsl.params.intKey
import esw.ocs.dsl.params.choiceKey
import esw.ocs.dsl.params.choicesOf
import esw.ocs.dsl.params.kGet
import esw.ocs.dsl.params.first
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

val commonB = reusableScript {

    val scriptScope: ScriptScope = this

    // =========================================================================
    // COMMON HANDLERS — shared across multiple procedures on Sequencer B
    // =========================================================================

    // Common handler — takes a PSH exposure and submits a sequence to Sequencer D
    // to find and identify centroids in that exposure.
    // Parameters: integrationTime: Float (seconds), d1SerializedSequence: String
    onSetup("takeGoodExposureAndFindCentroids") { command ->
        val integrationTime: Float    = command.kGet(floatKey("integrationTime"))!!.first
        val d1JsonStr: String         = command.kGet(stringKey("d1SerializedSequence"))!!.first

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "takeGoodExposureAndFindCentroids-start",
            helpKey   = "help.takeGoodExposureAndFindCentroids",
            messageId = "msg.takeGoodExposureAndFindCentroids.start"
        ))
        println("CommonB: takeGoodExposureAndFindCentroids — integrationTime=$integrationTime s")

        val sequencerD = scriptScope.getPeasSequencer(SequencerLabel.B, SequencerLabel.D)
        val seqD1 = aps.deserializeSequence(d1JsonStr)

        // TODO: implement — take PSH exposure with integrationTime, then submit D sequence
        delay(1.seconds)
        // to find and identify centroids in that exposure
        val responseD = sequencerD.submitAndWait(seqD1, timeout = 120.seconds)
        println("CommonB: takeGoodExposureAndFindCentroids — sequencerD response: $responseD")

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "takeGoodExposureAndFindCentroids-complete",
            helpKey   = "help.takeGoodExposureAndFindCentroids",
            messageId = "msg.takeGoodExposureAndFindCentroids.complete"
        ))
    }

    // ICD 31.2.1.7 — no parameters
    onSetup("calcImageAndPrOffsets") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "calcImageAndPrOffsets-start",
            helpKey   = "help.calcImageAndPrOffsets",
            messageId = "msg.calcImageAndPrOffsets.start"
        ))
        println("CommonB: calcImageAndPrOffsets — calculating pupil registration offsets and image offset from reference center")
        // TODO: implement
        delay(1.seconds)
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "calcImageAndPrOffsets-complete",
            helpKey   = "help.calcImageAndPrOffsets",
            messageId = "msg.calcImageAndPrOffsets.complete"
        ))
    }

    // ICD 31.2.1.8 — no parameters
    onSetup("correctPitTracking") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "correctPitTracking-start",
            helpKey   = "help.correctPitTracking",
            messageId = "msg.correctPitTracking.start"
        ))
        println("CommonB: correctPitTracking — publishing pshPupilAndImageErrors event to PIT Loop")
        // TODO: implement — publish pshPupilAndImageErrors event containing PR and image offsets
        delay(1.seconds)
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "correctPitTracking-complete",
            helpKey   = "help.correctPitTracking",
            messageId = "msg.correctPitTracking.complete"
        ))
    }

    // ICD 31.2.1.9 — sets up the PSH optical arm for taking PSH images, performed during
    // on-sky procedure startup. pshFilter, pshPupilMask, procedureId, observationId: required
    // pshRoiStartRow/Col/Width/Height, pshBinning: optional
    // Completion Type: longRunning
    onSetup("setupPshOpticalArm") { command ->
        val pshFilter: Choice        = command.kGet(choiceKey("pshFilter", choicesOf("F890N", "F891N", "F850M", "F750W", "F810N", "F630N", "F865N")))!!.first
        val pshPupilMask: Choice     = command.kGet(choiceKey("pshPupilMask", choicesOf("PH-2-0", "SH-0", "SH-2", "SH-5", "Clear")))!!.first
        val pshRoiStartRow: Int?    = command.kGet(intKey("pshRoiStartRow"))?.first
        val pshRoiStartCol: Int?    = command.kGet(intKey("pshRoiStartCol"))?.first
        val pshRoiWidth: Int?       = command.kGet(intKey("pshRoiWidth"))?.first
        val pshRoiHeight: Int?      = command.kGet(intKey("pshRoiHeight"))?.first
        val pshBinning: Int?        = command.kGet(intKey("pshBinning"))?.first
        val procedureId: String     = command.kGet(stringKey("procedureId"))!!.first
        val observationId: String   = command.kGet(stringKey("observationId"))!!.first

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "setupPshOpticalArm-start",
            helpKey   = "help.setupPshOpticalArm",
            messageId = "msg.setupPshOpticalArm.start"
        ))
        println("CommonB: setupPshOpticalArm — pshFilter=${pshFilter.name()}, pshPupilMask=${pshPupilMask.name()}, " +
                "pshRoiStartRow=$pshRoiStartRow, pshRoiStartCol=$pshRoiStartCol, pshRoiWidth=$pshRoiWidth, " +
                "pshRoiHeight=$pshRoiHeight, pshBinning=$pshBinning, procedureId=$procedureId, observationId=$observationId")
        // Selects the filter and pupil mask on the PSH assemblies, and optionally configures
        // the PSH detector's ROI/binning/procedure metadata, in parallel. Unlike PIT, PSH's
        // selectFilter/selectPupilMask enums match pshFilter/pshPupilMask exactly, so both
        // pass through directly with no mapping needed.
        // NOTE: configureDetector's own ICD parameter table (22.2.1.2) names one field
        // "rotStartCol" rather than "roiStartCol" -- apparent ICD typo, but using the literal
        // name since that's what the receiving assembly expects. setupPshOpticalArm only
        // supplies a single pshBinning value where configureDetector wants separate hBin/vBin,
        // so pshBinning is applied to both (symmetric binning assumed).
        var configureDetectorCmd = Setup(prefix, "configureDetector")
            .add(stringKey("procedureId").set(procedureId))
            .add(stringKey("observationId").set(observationId))
        pshRoiStartRow?.let { configureDetectorCmd = configureDetectorCmd.add(intKey("roiStartRow").set(it)) }
        pshRoiStartCol?.let { configureDetectorCmd = configureDetectorCmd.add(intKey("rotStartCol").set(it)) }
        pshRoiWidth?.let { configureDetectorCmd = configureDetectorCmd.add(intKey("roiWidth").set(it)) }
        pshRoiHeight?.let { configureDetectorCmd = configureDetectorCmd.add(intKey("roiHeight").set(it)) }
        pshBinning?.let {
            configureDetectorCmd = configureDetectorCmd.add(intKey("hBin").set(it))
            configureDetectorCmd = configureDetectorCmd.add(intKey("vBin").set(it))
        }

        par(
            {
                sendAssemblyCommand("ICS.PSH.FilterWheel", Setup(prefix, "selectFilter")
                    .add(choiceKey("filter", choicesOf("F890N", "F891N", "F850M", "F750W", "F810N", "F630N", "F865N")).set(pshFilter)))
            },
            {
                sendAssemblyCommand("ICS.PSH.PupilMaskWheel", Setup(prefix, "selectPupilMask")
                    .add(choiceKey("pupilMask", choicesOf("PH-2-0", "SH-0", "SH-2", "SH-5", "Clear")).set(pshPupilMask)))
            },
            { sendAssemblyCommand("ICS.PSH.Detector", configureDetectorCmd) }
        )
        delay(1.seconds)
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "setupPshOpticalArm-complete",
            helpKey   = "help.setupPshOpticalArm",
            messageId = "msg.setupPshOpticalArm.complete"
        ))
    }

}
