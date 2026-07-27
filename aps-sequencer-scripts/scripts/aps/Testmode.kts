package aps

import esw.ocs.dsl.core.script

script {

    println("APS Testmode script: loaded and initialized")

    onSetup("aps-setup") { _ ->
        println("APS Testmode: received Setup 'aps-setup'")
        println("APS Testmode: aps-setup step completed successfully")
    }

    onObserve("aps-observe") { _ ->
        println("APS Testmode: received Observe 'aps-observe'")
        println("APS Testmode: aps-observe step completed successfully")
    }

    onGoOnline {
        println("APS Testmode: sequencer going ONLINE")
    }

    onGoOffline {
        println("APS Testmode: sequencer going OFFLINE")
    }

    onAbortSequence {
        println("APS Testmode: sequence ABORTED")
    }

    onStop {
        println("APS Testmode: sequencer STOPPED")
    }

    onShutdown {
        println("APS Testmode: sequencer SHUTDOWN - cleaning up")
    }

    onGlobalError { error ->
        println("APS Testmode: unhandled error - ${error.reason}")
    }
}
