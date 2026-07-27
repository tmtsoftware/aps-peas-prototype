package aps
import csw.prefix.models.Prefix
import csw.params.core.models.Choice
import esw.ocs.dsl.core.reusableScript
import esw.ocs.dsl.core.CommandHandlerScope
import esw.ocs.dsl.par
import esw.ocs.dsl.params.floatKey
import esw.ocs.dsl.params.choiceKey
import esw.ocs.dsl.params.choicesOf
import esw.ocs.dsl.params.kGet
import esw.ocs.dsl.params.first
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

// Moves all ICD-defined "moveToDefaultPosition"-capable ICS/PIT assemblies to their default
// position, in parallel. Same 15-assembly set as homeMechanisms (ICD sections 6.2, 9.2-13.2,
// 18.2-20.2, 23.2-25.2, 27.2-29.2). Called from setOperatingMode when targeting STANDBY_MODE.
suspend fun CommandHandlerScope.moveAllMechanismsToDefaultPosition() {
    par(
        { sendAssemblyCommand("ICS.APT.FilterWheel", Setup(prefix, "moveToDefaultPosition")) },
        { sendAssemblyCommand("ICS.FOC.CalibrationSourceStage", Setup(prefix, "moveToDefaultPosition")) },
        { sendAssemblyCommand("ICS.FOC.CollimatorUnit", Setup(prefix, "moveToDefaultPosition")) },
        { sendAssemblyCommand("ICS.FOC.KMirror", Setup(prefix, "moveToDefaultPosition")) },
        { sendAssemblyCommand("ICS.FOC.SteeringBeamSplitterStage", Setup(prefix, "moveToDefaultPosition")) },
        { sendAssemblyCommand("ICS.FOC.TiltPlate", Setup(prefix, "moveToDefaultPosition")) },
        { sendAssemblyCommand("ICS.PSH.FilterWheel", Setup(prefix, "moveToDefaultPosition")) },
        { sendAssemblyCommand("ICS.PSH.FocusStage", Setup(prefix, "moveToDefaultPosition")) },
        { sendAssemblyCommand("ICS.PSH.PupilMaskWheel", Setup(prefix, "moveToDefaultPosition")) },
        { sendAssemblyCommand("ICS.STIM.FiberSourceStage", Setup(prefix, "moveToDefaultPosition")) },
        { sendAssemblyCommand("ICS.STIM.InsertionStage", Setup(prefix, "moveToDefaultPosition")) },
        { sendAssemblyCommand("ICS.STIM.PupilMaskStage", Setup(prefix, "moveToDefaultPosition")) },
        { sendAssemblyCommand("ICS.PIT.FilterWheel", Setup(prefix, "moveToDefaultPosition")) },
        { sendAssemblyCommand("ICS.PIT.FocusStage", Setup(prefix, "moveToDefaultPosition")) },
        { sendAssemblyCommand("ICS.PIT.PupilMaskWheel", Setup(prefix, "moveToDefaultPosition")) }
    )
}

// ICD 16.3 mechanism/mode state table — the four commands here are identical across
// STANDBY_MODE and ON_SKY_MODE (stimulus source off, sky light source selected, calibration
// source stage parked at field-stop with zero intensity). Only ABE.Shutter/ABE.Enclosure
// differ between the two modes, so those are handled separately below.
// NOTE: .set(...)/.add(...) usage here is inferred from standard CSW Java API convention,
// not yet confirmed against this project's actual Setup/Key classes -- first compile will tell.
private suspend fun CommandHandlerScope.setCommonOpticalPathState() {
    par(
        {
            sendAssemblyCommand("ICS.STIM.FiberSourceStage", Setup(prefix, "setSourceIntensity")
                .add(choiceKey("sourcePower", choicesOf("ON", "OFF")).set(Choice("OFF")))
                .add(floatKey("sourceIntensity").set(0.0f)))
        },
        {
            sendAssemblyCommand("ICS.STIM.InsertionStage", Setup(prefix, "selectSource")
                .add(choiceKey("lightSource", choicesOf("SKY", "STIMULUS")).set(Choice("SKY"))))
        },
        {
            sendAssemblyCommand("ICS.FOC.CalibrationSourceStage", Setup(prefix, "setOptic")
                .add(choiceKey("optic", choicesOf("CALIBRATION_SOURCE", "ZERNIKE1", "ZERNIKE2", "FIELD_STOP", "OPEN")).set(Choice("FIELD_STOP"))))
        },
        {
            sendAssemblyCommand("ICS.FOC.CalibrationSourceStage", Setup(prefix, "setSourceIntensity")
                .add(floatKey("sourceIntensity").set(0.0f)))
        }
    )
}

// ICD 16.3: three preparatory commands issued at the very start of STANDBY_MODE, before the
// mechanism default-position sweep. K-Mirror is taken out of TRACKING/SLEWING mode before its
// own moveToDefaultPosition runs (per ICD 11.2.1.6, the setMode parameter is oddly named
// "offset" despite being the operating-mode enum). PIT loop and detector exposure loop are
// stopped before mechanisms are parked. Run in parallel; all three complete before this
// function returns, satisfying the "before default position" ordering requirement for KMirror.
private suspend fun CommandHandlerScope.prepareForStandby() {
    par(
        {
            sendAssemblyCommand("ICS.FOC.KMirror", Setup(prefix, "setMode")
                .add(choiceKey("offset", choicesOf("SLEWING", "TRACKING", "MANUAL")).set(Choice("MANUAL"))))
        },
        { sendToPitSequencer(Setup(prefix, "stopPitLoop")) },
        { sendAssemblyCommand("ICS.APT.Detector", Setup(prefix, "stopExposureLoop")) }
    )
}

// ICD 16.3: STANDBY_MODE — shutter CLOSE, purge air ON, plus the shared optical path state.
suspend fun CommandHandlerScope.setStandbyModeMechanismStates() {
    setCommonOpticalPathState()
    par(
        {
            sendAssemblyCommand("ICS.ABE.Shutter", Setup(prefix, "commandShutter")
                .add(choiceKey("command", choicesOf("OPEN", "CLOSE")).set(Choice("CLOSE"))))
        },
        {
            sendAssemblyCommand("ICS.ABE.Enclosure", Setup(prefix, "commandPurgeAir")
                .add(choiceKey("action", choicesOf("ON", "OFF")).set(Choice("ON"))))
        }
    )
}

// ICD 16.3: ON_SKY_MODE — shutter OPEN, purge air OFF, plus the shared optical path state.
suspend fun CommandHandlerScope.setOnSkyModeMechanismStates() {
    setCommonOpticalPathState()
    par(
        {
            sendAssemblyCommand("ICS.ABE.Shutter", Setup(prefix, "commandShutter")
                .add(choiceKey("command", choicesOf("OPEN", "CLOSE")).set(Choice("OPEN"))))
        },
        {
            sendAssemblyCommand("ICS.ABE.Enclosure", Setup(prefix, "commandPurgeAir")
                .add(choiceKey("action", choicesOf("ON", "OFF")).set(Choice("OFF"))))
        }
    )
}

// ICD 16.3: shutter closed + purge air off — shared between CALIBRATION_SOURCE_MODE and
// STIMULUS_SOURCE_MODE (both keep the enclosure sealed and dry while an internal source is in use).
private suspend fun CommandHandlerScope.setShutterClosedPurgeOffState() {
    par(
        {
            sendAssemblyCommand("ICS.ABE.Shutter", Setup(prefix, "commandShutter")
                .add(choiceKey("command", choicesOf("OPEN", "CLOSE")).set(Choice("CLOSE"))))
        },
        {
            sendAssemblyCommand("ICS.ABE.Enclosure", Setup(prefix, "commandPurgeAir")
                .add(choiceKey("action", choicesOf("ON", "OFF")).set(Choice("OFF"))))
        }
    )
}

// ICD 16.3: CALIBRATION_SOURCE_MODE — internal calibration source active, stimulus fiber
// source off, sky path selected on the insertion stage, calibration optic selected, shutter
// closed, purge air off.
// NOTE: ICD table specifies calibration source intensity as ">0%" without a specific value;
// using 100.0 (full intensity) as a placeholder -- adjust if a different default is wanted.
suspend fun CommandHandlerScope.setCalibrationSourceModeMechanismStates() {
    par(
        {
            sendAssemblyCommand("ICS.STIM.FiberSourceStage", Setup(prefix, "setSourceIntensity")
                .add(choiceKey("sourcePower", choicesOf("ON", "OFF")).set(Choice("OFF")))
                .add(floatKey("sourceIntensity").set(0.0f)))
        },
        {
            sendAssemblyCommand("ICS.STIM.InsertionStage", Setup(prefix, "selectSource")
                .add(choiceKey("lightSource", choicesOf("SKY", "STIMULUS")).set(Choice("SKY"))))
        },
        {
            sendAssemblyCommand("ICS.FOC.CalibrationSourceStage", Setup(prefix, "setOptic")
                .add(choiceKey("optic", choicesOf("CALIBRATION_SOURCE", "ZERNIKE1", "ZERNIKE2", "FIELD_STOP", "OPEN")).set(Choice("CALIBRATION_SOURCE"))))
        },
        {
            sendAssemblyCommand("ICS.FOC.CalibrationSourceStage", Setup(prefix, "setSourceIntensity")
                .add(floatKey("sourceIntensity").set(100.0f)))
        }
    )
    setShutterClosedPurgeOffState()
}

// ICD 16.3: STIMULUS_SOURCE_MODE — stimulus fiber source active, calibration source off
// (field-stop optic, zero intensity), stimulus path selected on the insertion stage, shutter
// closed, purge air off.
// NOTE: ICD table specifies stimulus source intensity as ">0%" without a specific value;
// using 100.0 (full intensity) as a placeholder -- adjust if a different default is wanted.
suspend fun CommandHandlerScope.setStimulusSourceModeMechanismStates() {
    par(
        {
            sendAssemblyCommand("ICS.STIM.FiberSourceStage", Setup(prefix, "setSourceIntensity")
                .add(choiceKey("sourcePower", choicesOf("ON", "OFF")).set(Choice("ON")))
                .add(floatKey("sourceIntensity").set(100.0f)))
        },
        {
            sendAssemblyCommand("ICS.STIM.InsertionStage", Setup(prefix, "selectSource")
                .add(choiceKey("lightSource", choicesOf("SKY", "STIMULUS")).set(Choice("STIMULUS"))))
        },
        {
            sendAssemblyCommand("ICS.FOC.CalibrationSourceStage", Setup(prefix, "setOptic")
                .add(choiceKey("optic", choicesOf("CALIBRATION_SOURCE", "ZERNIKE1", "ZERNIKE2", "FIELD_STOP", "OPEN")).set(Choice("FIELD_STOP"))))
        },
        {
            sendAssemblyCommand("ICS.FOC.CalibrationSourceStage", Setup(prefix, "setSourceIntensity")
                .add(floatKey("sourceIntensity").set(0.0f)))
        }
    )
    setShutterClosedPurgeOffState()
}

val icsCommon = reusableScript {

    // =========================================================================
    // ICS.ICS.Sequencer COMMAND HANDLERS
    // ICD 16.2 Commands for ICS.ICS.Sequencer
    // =========================================================================

    // ICD 16.2.1.1 setOperatingMode — operatingMode: enum required
    // (CALIBRATION_SOURCE_MODE, STIMULUS_SOURCE_MODE, ON_SKY_MODE, STANDBY_MODE, STARTUP_MODE)
    // Completion Type: longRunning
    onSetup("setOperatingMode") { command ->
        val operatingMode: Choice = command.kGet(choiceKey("operatingMode", choicesOf("CALIBRATION_SOURCE_MODE", "STIMULUS_SOURCE_MODE", "ON_SKY_MODE", "STANDBY_MODE", "STARTUP_MODE")))!!.first

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "setOperatingMode-start",
            helpKey   = "help.setOperatingMode",
            messageId = "msg.setOperatingMode.start"
        ))
        println("IcsCommon: setOperatingMode — operatingMode=${operatingMode.name()}")
        // Per ICD 16.3. STARTUP_MODE is left as a TODO for now.
        when (operatingMode.name()) {
            "STANDBY_MODE" -> {
                prepareForStandby()
                moveAllMechanismsToDefaultPosition()
                setStandbyModeMechanismStates()
            }
            "ON_SKY_MODE" -> {
                setOnSkyModeMechanismStates()
            }
            "CALIBRATION_SOURCE_MODE" -> {
                setCalibrationSourceModeMechanismStates()
            }
            "STIMULUS_SOURCE_MODE" -> {
                setStimulusSourceModeMechanismStates()
            }
            "STARTUP_MODE" -> {
                // TODO: not implemented in this prototype.
            }
        }
        delay(1.seconds)

        println("IcsCommon: setOperatingMode — operatingMode=${operatingMode.name()} complete")

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "setOperatingMode-complete",
            helpKey   = "help.setOperatingMode",
            messageId = "msg.setOperatingMode.complete"
        ))
    }

    // ICD 16.2.1.2 setupFocForAcquisition — pitToPshPrOffset: float (degree), required
    // Completion Type: longRunning
    onSetup("setupFocForAcquisition") { command ->
        val pitToPshPrOffset: Float = command.kGet(floatKey("pitToPshPrOffset"))!!.first

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "setupFocForAcquisition-start",
            helpKey   = "help.setupFocForAcquisition",
            messageId = "msg.setupFocForAcquisition.start"
        ))
        println("IcsCommon: setupFocForAcquisition — pitToPshPrOffset=$pitToPshPrOffset")
        // TODO: implement — sets up the K-Mirror for target acquisition using the
        // pupil registration PIT to PSH rotational offset in the K-Mirror tracking demand calculation.
        // Per ICD 16.3, will eventually send FOC.KMirror.updatePitToPshOffset with this value.
        delay(1.seconds)
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "setupFocForAcquisition-complete",
            helpKey   = "help.setupFocForAcquisition",
            messageId = "msg.setupFocForAcquisition.complete"
        ))
    }

    // ICD 16.2.1.3 homeMechanisms — no parameters
    // Completion Type: longRunning
    // Homes all ICD-defined "home"-capable ICS/PIT assemblies in parallel (ICD sections
    // 6.2, 9.2-13.2, 18.2-20.2, 23.2-25.2, 27.2-29.2). Excludes assemblies without a plain
    // "home" command: ABE.Enclosure, ABE.Shutter, all Detector assemblies, HCD.GalilMotion
    // (has homeAxis, not home), ICS.Sequencer itself, PIT.Sequencer, and SAM.
    onSetup("homeMechanisms") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "homeMechanisms-start",
            helpKey   = "help.homeMechanisms",
            messageId = "msg.homeMechanisms.start"
        ))
        println("IcsCommon: homeMechanisms — homing all APS Instrument mechanisms")

        val responses = par(
            { sendAssemblyCommand("ICS.APT.FilterWheel", Setup(prefix, "home")) },
            { sendAssemblyCommand("ICS.FOC.CalibrationSourceStage", Setup(prefix, "home")) },
            { sendAssemblyCommand("ICS.FOC.CollimatorUnit", Setup(prefix, "home")) },
            { sendAssemblyCommand("ICS.FOC.KMirror", Setup(prefix, "home")) },
            { sendAssemblyCommand("ICS.FOC.SteeringBeamSplitterStage", Setup(prefix, "home")) },
            { sendAssemblyCommand("ICS.FOC.TiltPlate", Setup(prefix, "home")) },
            { sendAssemblyCommand("ICS.PSH.FilterWheel", Setup(prefix, "home")) },
            { sendAssemblyCommand("ICS.PSH.FocusStage", Setup(prefix, "home")) },
            { sendAssemblyCommand("ICS.PSH.PupilMaskWheel", Setup(prefix, "home")) },
            { sendAssemblyCommand("ICS.STIM.FiberSourceStage", Setup(prefix, "home")) },
            { sendAssemblyCommand("ICS.STIM.InsertionStage", Setup(prefix, "home")) },
            { sendAssemblyCommand("ICS.STIM.PupilMaskStage", Setup(prefix, "home")) },
            { sendAssemblyCommand("ICS.PIT.FilterWheel", Setup(prefix, "home")) },
            { sendAssemblyCommand("ICS.PIT.FocusStage", Setup(prefix, "home")) },
            { sendAssemblyCommand("ICS.PIT.PupilMaskWheel", Setup(prefix, "home")) }
        )

        println("IcsCommon: homeMechanisms — homing all APS Instrument mechanisms complete")

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "homeMechanisms-complete",
            helpKey   = "help.homeMechanisms",
            messageId = "msg.homeMechanisms.complete"
        ))
    }

}
