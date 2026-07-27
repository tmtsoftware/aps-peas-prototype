package aps

import esw.ocs.dsl.core.script
import csw.prefix.models.Prefix

script {

    loadScripts(commonA, rigidBodyAndSegmentFigureA)

    onGoOnline {
        println("PeasSequencerA: sequencer going ONLINE")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "sequencer-online",
            helpKey   = "help.sequencer.online",
            messageId = "msg.sequencer.online"
        ))
    }

    onGoOffline {
        println("PeasSequencerA: sequencer going OFFLINE")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "sequencer-offline",
            helpKey   = "help.sequencer.offline",
            messageId = "msg.sequencer.offline"
        ))
    }

    // Fires the instant the UI calls abortSequence() on this sequencer -- concurrently with
    // whatever step is currently in-flight, NOT after it (abort doesn't cancel/interrupt a
    // running step). Just records the fact via operatorAbortRequested (CommonA.kt);
    // onSetupWithRestoreOnError (RestoreOnErrorWrapper.kt) is what actually acts on it, once
    // the in-flight handler's own body has finished.
    onAbortSequence {
        println("PeasSequencerA: sequence ABORTED")
        operatorAbortRequested.set(true)

        // Propagate the abort down the hierarchy. B, C, and D are all directly addressable
        // from A via getPeasSequencer's obsMode-name substitution -- independent of the
        // normal command-flow path, where D is otherwise only ever reached indirectly
        // through B -- so each gets abortSequence() called on it directly here, discarding
        // its own pending steps and firing its own onAbortSequence handler (if any), rather
        // than relying on multi-hop cascading through B's/C's own handlers.
        listOf(SequencerLabel.B, SequencerLabel.C, SequencerLabel.D).forEach { target ->
            try {
                getPeasSequencer(SequencerLabel.A, target).abortSequence()
            } catch (e: Exception) {
                println("PeasSequencerA: failed to propagate abort to Sequencer ${target.name}: ${e.message}")
            }
        }

        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.WARN_MESSAGE,
            dialogKey = "sequence-aborted",
            helpKey   = "help.sequence.aborted",
            messageId = "msg.sequence.aborted"
        ))
    }

    onStop {
        println("PeasSequencerA: sequencer STOPPED")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.WARN_MESSAGE,
            dialogKey = "sequencer-stopped",
            helpKey   = "help.sequencer.stopped",
            messageId = "msg.sequencer.stopped"
        ))
    }

    onShutdown {
        println("PeasSequencerA: sequencer SHUTDOWN - cleaning up")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "sequencer-shutdown",
            helpKey   = "help.sequencer.shutdown",
            messageId = "msg.sequencer.shutdown"
        ))
    }

    // NOTE: this is now a last-resort safety net only, not the primary error-handling path.
    // Verified empirically that onGlobalError does NOT fire for a normal step-handler
    // failure (it never printed even though a step correctly failed and the sequence
    // correctly completed with Error) -- see RestoreOnErrorWrapper.kt for the actual
    // mechanism (onSetupWithRestoreOnError's per-handler .onError{} attachment, used
    // throughout CommonA.kt and RigidBodyAndSegmentFigureA.kt) and for
    // restoreTelescopeState(), which does the real work formerly attempted here.
    // Kept in case something genuinely uncaught (e.g. a detached/background coroutine a
    // handler launches itself) ever does hit this.
    onGlobalError { error ->
        println("PeasSequencerA: onGlobalError fired (unexpected path) - ${error.reason}")
    }

}
