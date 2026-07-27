package aps

import csw.params.commands.Setup
import csw.prefix.models.Prefix
import esw.ocs.dsl.core.CommandHandlerKt
import esw.ocs.dsl.core.CommandHandlerScope
import esw.ocs.dsl.core.ScriptScope
import esw.ocs.dsl.highlevel.CswHighLevelDslApi
import esw.ocs.dsl.highlevel.models.OtherError
import esw.ocs.dsl.highlevel.models.ScriptError

// Wraps onSetup so that restoreTelescopeState() (below) runs in either of two cases:
//   1. The handler throws -- via CommandHandlerKt's per-handler .onError{} builder, invoked
//      directly inside go()'s own catch block, in the same coroutine, with no Future/Job
//      boundary in between. (Verified empirically that onGlobalError does NOT fire for a
//      normal step-handler failure -- it never printed even though a step correctly failed
//      and the sequence correctly completed with Error.)
//   2. The operator aborted (operatorAbortRequested, set by PeasSequencerA.kts's
//      onAbortSequence) -- checked AFTER block(command) returns normally, which is what
//      guarantees restoration only runs once whatever was in-flight when the abort was sent
//      (e.g. rbsfTakeExposureWhileProcessingPrevious's full B/D submitAndWait chain) has
//      actually finished. onAbortSequence itself fires concurrently with the in-flight step
//      (abort doesn't cancel/interrupt a running step -- see SequencerBehavior.scala's
//      discardPending, which only removes PENDING steps), so it can only set a flag; this is
//      what actually acts on it at the right time.
fun ScriptScope.onSetupWithRestoreOnError(
    name: String,
    block: suspend CommandHandlerScope.(Setup) -> Unit
): CommandHandlerKt<Setup> =
    onSetup(name) { command ->
        block(command)
        if (operatorAbortRequested.getAndSet(false)) {
            restoreTelescopeState(OtherError("User Abort Requested"))
        }
    }.onError { scriptError -> restoreTelescopeState(scriptError) }

// Telescope subsystem state restoration -- currently just GLC sensor settings (via
// GlcFacade.kt, restoring what saveSensorSettings() captured at the start of
// alignmentProcedureStartup in CommonA.kt), framed generally rather than GLC-specific
// since additional subsystems may need restoration here as this cascade design extends to
// cover more of the telescope.
suspend fun CswHighLevelDslApi.restoreTelescopeState(scriptError: ScriptError) {
    println("Sequencer A: restoring telescope state - ${scriptError.reason}")
    publishEvent(buildProcedureEvent(Prefix.apply(prefix),
        type      = ProcedureEventType.WARN_MESSAGE,
        dialogKey = "sequencer-error",
        helpKey   = "help.sequencer.error",
        messageId = "msg.sequencer.error",
        errorText = scriptError.reason
    ))

    publishEvent(buildProcedureEvent(Prefix.apply(prefix),
        type      = ProcedureEventType.INFO_MESSAGE,
        dialogKey = "telescope-state-restoration-start",
        helpKey   = "help.telescope-state-restoration",
        messageId = "msg.telescope-state-restoration.start"
    ))

    val snapshotKey = sensorSnapshotKeyRef.get()
    var restorationFailed = false

    if (snapshotKey == null) {
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.WARN_MESSAGE,
            dialogKey = "ACK",
            helpKey   = "help.telescope-state-restoration",
            messageId = "msg.telescope-state-restoration.no-snapshot"
        ))
        restorationFailed = true
    } else {
        try {
            restoreSensorSettings(snapshotKey)
        } catch (cleanupEx: Exception) {
            publishEvent(buildProcedureEvent(Prefix.apply(prefix),
                type      = ProcedureEventType.WARN_MESSAGE,
                dialogKey = "ACK",
                helpKey   = "help.telescope-state-restoration",
                messageId = "msg.telescope-state-restoration.failed"
            ))
            restorationFailed = true
        }
    }

    if (!restorationFailed) {
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "telescope-state-restoration-complete",
            helpKey   = "help.telescope-state-restoration",
            messageId = "msg.telescope-state-restoration.complete"
        ))
    }

    sensorSnapshotKeyRef.set(null)
}
