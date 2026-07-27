package aps

import esw.ocs.dsl.core.script
import csw.prefix.models.Prefix

script {

    loadScripts(rigidBodyAndSegmentFigureC)

    onGoOnline {
        println("PeasSequencerC: sequencer going ONLINE")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "sequencer-online",
            helpKey   = "help.sequencer.online",
            messageId = "msg.sequencer.online"
        ))
    }

    onGoOffline {
        println("PeasSequencerC: sequencer going OFFLINE")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "sequencer-offline",
            helpKey   = "help.sequencer.offline",
            messageId = "msg.sequencer.offline"
        ))
    }

    onAbortSequence {
        println("PeasSequencerC: sequence ABORTED")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.WARN_MESSAGE,
            dialogKey = "sequence-aborted",
            helpKey   = "help.sequence.aborted",
            messageId = "msg.sequence.aborted"
        ))
    }

    onStop {
        println("PeasSequencerC: sequencer STOPPED")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.WARN_MESSAGE,
            dialogKey = "sequencer-stopped",
            helpKey   = "help.sequencer.stopped",
            messageId = "msg.sequencer.stopped"
        ))
    }

    onShutdown {
        println("PeasSequencerC: sequencer SHUTDOWN - cleaning up")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "sequencer-shutdown",
            helpKey   = "help.sequencer.shutdown",
            messageId = "msg.sequencer.shutdown"
        ))
    }

    onGlobalError { error ->
        println("PeasSequencerC: unhandled error - ${error.reason}")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.WARN_MESSAGE,
            dialogKey = "sequencer-error",
            helpKey   = "help.sequencer.error",
            messageId = "msg.sequencer.error"
        ))
    }

}
