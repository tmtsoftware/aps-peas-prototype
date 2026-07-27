package aps
import csw.prefix.models.Prefix

import esw.ocs.dsl.core.reusableScript
import esw.ocs.dsl.core.ScriptScope
import csw.prefix.javadsl.JSubsystem
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

val rigidBodyAndSegmentFigure = reusableScript {

    val scriptScope: ScriptScope = this

    val glc = Assembly(JSubsystem.M1CS, "GLC", defaultTimeout = 60.seconds)

    onSetup("cmd-m1cs-moves") { command ->
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

    onSetup("calc-colorstep") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "calc-colorstep-start",
            helpKey   = "help.calc-colorstep",
            messageId = "msg.calc-colorstep.start"
        ))
        delay(2.seconds)
        println("RigidBodyAndSegmentFigure: received calc-colorstep " + obsMode)
        if (obsMode.name() == "APS_software_only_mode") {
            println("got it")
        }
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "calc-colorstep-complete",
            helpKey   = "help.calc-colorstep",
            messageId = "msg.calc-colorstep.complete"
        ))
    }

    onSetup("calc-tt-offsets-to-acts") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "calc-tt-offsets-start",
            helpKey   = "help.calc-tt-offsets-to-acts",
            messageId = "msg.calc-tt-offsets-to-acts.start"
        ))
        delay(2.seconds)
        println("RigidBodyAndSegmentFigure: received calc-tt-offsets-to-acts")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "calc-tt-offsets-complete",
            helpKey   = "help.calc-tt-offsets-to-acts",
            messageId = "msg.calc-tt-offsets-to-acts.complete"
        ))
    }

    onSetup("calc-decompose-acts") { command ->
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "calc-decompose-acts-start",
            helpKey   = "help.calc-decompose-acts",
            messageId = "msg.calc-decompose-acts.start"
        ))
        delay(2.seconds)
        println("RigidBodyAndSegmentFigure: received calc-decompose-acts")
        publishEvent(buildProcedureEvent(Prefix.apply(prefix),
            type      = ProcedureEventType.INFO_MESSAGE,
            dialogKey = "calc-decompose-acts-complete",
            helpKey   = "help.calc-decompose-acts",
            messageId = "msg.calc-decompose-acts.complete"
        ))
    }

}
