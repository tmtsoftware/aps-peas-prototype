package aps

import csw.params.commands.CommandResponse
import csw.params.commands.Result
import csw.params.commands.Setup
import csw.params.core.models.Choice
import esw.ocs.dsl.highlevel.CswHighLevelDslApi
import esw.ocs.dsl.params.choiceKey
import esw.ocs.dsl.params.choicesOf
import esw.ocs.dsl.params.first
import esw.ocs.dsl.params.intKey
import esw.ocs.dsl.params.kGet

// GLC-specific command facades, built on top of the generic sendToGlc() wrapper in
// CommandWrapper.kt. Kept in a separate file from CommandWrapper.kt so the generic
// per-command submission plumbing stays separate from GLC domain-specific commands.
//
// Per ICD M1CS-APS SDB (TIO.CTR.ICD.21.003.CCR02) §2.2.1.1 / §2.2.1.2.

private val alignmentStateKey = choiceKey(
    "alignmentState",
    choicesOf("INITIAL", "PARTIALLY", "ALIGNED", "SENSOR_CAL", "DIAG")
)
private val sensorSettingsDataIdKey = intKey("sensorSettingsDataId")

// ICD §2.2.1.1 — SaveSensorSettings
// Snapshots GLC's current rigid body position so it can be restored later via
// restoreSensorSettings(). alignmentState is fixed to INITIAL here since Sequencer A
// always calls this at the very start of a sequence, before any alignment has occurred.
// metadata is optional per the ICD and unused in this prototype.
//
// Per the ICD, sensorSettingsDataId is "0 on error" -- but per Scott, GLC will always
// surface a real CSW Error/exception on failure rather than a valid Completed with id=0,
// so this does not special-case 0.
//
// sendToGlc's simulate branch (used whenever obsMode isn't Operational -- i.e. always, for
// this prototype, since GLC hardware/simulator won't exist for years) has no way to know
// what a plausible result looks like for an arbitrary command, so a fake sensorSettingsDataId
// is supplied here as the simulatedResult for sendToGlc to hand back when not actually
// submitting to a real GLC assembly.
suspend fun CswHighLevelDslApi.saveSensorSettings(): Int {
    val command = Setup(prefix, "SaveSensorSettings")
        .add(alignmentStateKey.set(Choice("INITIAL")))
    val simulatedResult = Result.emptyResult().add(sensorSettingsDataIdKey.set((1000..9999).random()))

    return when (val response = sendToGlc(command, simulatedResult)) {
        is CommandResponse.Completed ->
            response.result().kGet(sensorSettingsDataIdKey)?.first
                ?: throw RuntimeException("SaveSensorSettings completed but sensorSettingsDataId missing from result")
        else -> throw RuntimeException("SaveSensorSettings failed: $response")
    }
}

// ICD §2.2.1.2 — RestoreSensorSettings
// Restores GLC sensor settings previously captured via saveSensorSettings().
// alignmentState/metadata come back in the result but aren't currently consumed by any
// caller, so no simulatedResult is needed here -- sendToGlc's default empty Result is fine.
suspend fun CswHighLevelDslApi.restoreSensorSettings(sensorSettingsDataId: Int) {
    val command = Setup(prefix, "RestoreSensorSettings")
        .add(sensorSettingsDataIdKey.set(sensorSettingsDataId))

    val response = sendToGlc(command)
    if (response !is CommandResponse.Completed) {
        throw RuntimeException("RestoreSensorSettings failed: $response")
    }
}
