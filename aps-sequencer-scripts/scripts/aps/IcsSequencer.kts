package aps

import esw.ocs.dsl.core.script
import csw.prefix.models.Prefix

script {

    loadScripts(icsCommon)

    onGoOnline {
        println("IcsSequencer: sequencer going ONLINE")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "sequencer-online",
            helpKey   = "help.sequencer.online",
            messageId = "msg.sequencer.online"
        ))
    }

    onGoOffline {
        println("IcsSequencer: sequencer going OFFLINE")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "sequencer-offline",
            helpKey   = "help.sequencer.offline",
            messageId = "msg.sequencer.offline"
        ))
    }

    onAbortSequence {
        println("IcsSequencer: sequence ABORTED")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.WARN_MESSAGE,
            dialogKey = "sequence-aborted",
            helpKey   = "help.sequence.aborted",
            messageId = "msg.sequence.aborted"
        ))
    }

    onStop {
        println("IcsSequencer: sequencer STOPPED")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.WARN_MESSAGE,
            dialogKey = "sequencer-stopped",
            helpKey   = "help.sequencer.stopped",
            messageId = "msg.sequencer.stopped"
        ))
    }

    onShutdown {
        println("IcsSequencer: sequencer SHUTDOWN - cleaning up")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "sequencer-shutdown",
            helpKey   = "help.sequencer.shutdown",
            messageId = "msg.sequencer.shutdown"
        ))
    }

    onGlobalError { error ->
        println("IcsSequencer: unhandled error - ${error.reason}")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.WARN_MESSAGE,
            dialogKey = "sequencer-error",
            helpKey   = "help.sequencer.error",
            messageId = "msg.sequencer.error"
        ))
    }

}
