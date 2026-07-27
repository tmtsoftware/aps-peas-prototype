package aps

import csw.location.api.javadsl.JComponentType
import csw.params.commands.CommandResponse
import csw.params.commands.Result
import csw.params.commands.Sequence
import csw.params.commands.SequenceCommand
import csw.params.commands.Setup
import csw.params.core.formats.JavaJsonSupport
import csw.params.javadsl.JKeyType
import csw.prefix.javadsl.JSubsystem
import esw.ocs.api.models.ObsMode
import esw.ocs.dsl.core.ScriptScope
import esw.ocs.dsl.highlevel.CswHighLevelDslApi
import esw.ocs.dsl.highlevel.RichSequencer
import kotlinx.coroutines.delay
import csw.params.core.models.Id
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds
import java.util.ArrayList
import java.util.UUID

enum class SequencerLabel { A, B, C, D }

// Receiver widened from ScriptScope to CswHighLevelDslApi so this is callable from
// HandlerScope contexts too (e.g. onAbortSequence in PeasSequencerA.kts, which needs it to
// propagate an abort directly to B/C/D). Backward compatible: ScriptScope already extends
// CswHighLevelDslApi transitively, so every existing scriptScope.getPeasSequencer(...) call
// site keeps compiling unchanged.
fun CswHighLevelDslApi.getPeasSequencer(source: SequencerLabel, target: SequencerLabel): RichSequencer {
    val targetMode = obsMode.name().replace("Sequencer${source.name}", "Sequencer${target.name}")
    return Sequencer(JSubsystem.APS, ObsMode(targetMode), 120.seconds)
}

// Software-only mode is identified by the "_SoftwareOnlyMode" obsMode suffix.
// Any other suffix (e.g. "_ApsStandaloneMode") is treated as real hardware submission.
fun ScriptScope.isSoftwareOnlyMode(): Boolean = obsMode.name().endsWith("_SoftwareOnlyMode")

// ICS/PIT sequencers use their own Operational/Simulator obsMode suffix convention
// (e.g. "icsSequencer_IcsOperational", "pitSequencer_PitOperational"), distinct from the
// PEAS A/B/C/D "_SoftwareOnlyMode" convention used by isSoftwareOnlyMode().
// Declared against CswHighLevelDslApi (rather than ScriptScope) since that's the common
// interface both ScriptScope and CommandHandlerScope extend -- this makes it callable from
// both top-level sequencer scripts and reusableScript onSetup handlers like IcsCommon.kt.
fun CswHighLevelDslApi.isOperationalMode(): Boolean = obsMode.name().endsWith("Operational")

// Receiver is CswHighLevelDslApi (not ScriptScope) so this is callable from HandlerScope
// contexts too (e.g. onGlobalError in PeasSequencerA.kts), which need it for GLC
// restore-on-error via GlcFacade.kt. Backward compatible: ScriptScope already extends
// CswHighLevelDslApi transitively.
//
// Gated on isOperationalMode() (not isSoftwareOnlyMode()) -- GLC hardware/simulator won't
// exist for years per Scott, so every obsMode reachable from the UI today (SoftwareOnlyMode,
// ApsStandaloneMode) must simulate; only the (currently unreachable, concept-only)
// ApsOperationalMode would ever attempt the real glc.submitAndWait() branch.
//
// simulatedResult lets a caller (e.g. GlcFacade.saveSensorSettings()) supply a plausible
// fake Result for the simulate branch to hand back, since sendToGlc itself has no way to
// know what result shape an arbitrary GLC command expects. Defaults to Result.emptyResult()
// for callers that don't need anything back (e.g. restoreSensorSettings()).
suspend fun CswHighLevelDslApi.sendToGlc(
    command: Setup,
    simulatedResult: Result = Result.emptyResult()
): CommandResponse.SubmitResponse {
    val glc = Assembly(JSubsystem.M1CS, "GLC", defaultTimeout = 60.seconds)
    return if (isOperationalMode()) {
        glc.submitAndWait(command)
    } else {
        println("sendToGlc: simulating (obsMode=${obsMode.name()}) ...")
        delay(5.seconds)
        println("sendToGlc: simulation complete")
        CommandResponse.Completed(Id(UUID.randomUUID().toString()), simulatedResult)
    }
}

// General-purpose per-assembly command submission for ICS/PIT domain assemblies.
// Guarded by isOperationalMode() rather than isSoftwareOnlyMode(), since ICS/PIT
// sequencers use their own Operational/Simulator obsMode convention (see above) and
// this must remain independent of whatever obsMode the PEAS A/B/C/D sequencers are in.
suspend fun CswHighLevelDslApi.sendAssemblyCommand(componentName: String, command: Setup): CommandResponse.SubmitResponse {
    return if (isOperationalMode()) {
        val assembly = Assembly(JSubsystem.APS, componentName, defaultTimeout = 60.seconds)
        assembly.submitAndWait(command)
    } else {
        println("sendAssemblyCommand: simulating ${command.commandName()} on $componentName (obsMode=${obsMode.name()}) ...")
        delay(500.milliseconds)
        CommandResponse.Completed(Id(UUID.randomUUID().toString()))
    }
}

// ICS/PIT sequencers run under Operational or Simulator obsMode suffixes, and only one is
// running at a time. Rather than guessing which one and probing by submit, we discover the
// currently-registered obsMode directly via the location service (listLocationsBy on
// CswHighLevelDslApi/LocationServiceDsl), then cache the result so repeated calls don't
// re-query the location service every time.
// NOTE: the exact accessor chain for pulling the component name back out of a Location
// (.connection().componentId().prefix().componentName() below) is inferred from usage elsewhere
// in the ESW DSL sources, not independently verified against csw-location-api's actual
// Location/ComponentId/Prefix classes -- first compile will confirm or correct this.
private var icsSequencerObsMode: String? = null
private var pitSequencerObsMode: String? = null

suspend fun CswHighLevelDslApi.resolveIcsSequencer(defaultTimeout: kotlin.time.Duration = 120.seconds): RichSequencer {
    val cached = icsSequencerObsMode
    val obsModeName = if (cached != null) {
        cached
    } else {
        val icsLocation = listLocationsBy(JComponentType.Sequencer)
            .firstOrNull { it.connection().componentId().prefix().componentName().startsWith("icsSequencer_") }
            ?: throw RuntimeException("No ICS sequencer (Operational or Simulator) is currently registered")
        val resolvedName = icsLocation.connection().componentId().prefix().componentName()
        icsSequencerObsMode = resolvedName
        resolvedName
    }
    return Sequencer(JSubsystem.APS, ObsMode(obsModeName), defaultTimeout)
}

suspend fun CswHighLevelDslApi.resolvePitSequencer(defaultTimeout: kotlin.time.Duration = 120.seconds): RichSequencer {
    val cached = pitSequencerObsMode
    val obsModeName = if (cached != null) {
        cached
    } else {
        val pitLocation = listLocationsBy(JComponentType.Sequencer)
            .firstOrNull { it.connection().componentId().prefix().componentName().startsWith("pitSequencer_") }
            ?: throw RuntimeException("No PIT sequencer (Operational or Simulator) is currently registered")
        val resolvedName = pitLocation.connection().componentId().prefix().componentName()
        pitSequencerObsMode = resolvedName
        resolvedName
    }
    return Sequencer(JSubsystem.APS, ObsMode(obsModeName), defaultTimeout)
}

// Fixed sequencers under ICS -- registered under whichever obsMode (Operational/Simulator)
// is currently running, discovered via resolveIcsSequencer()/resolvePitSequencer() above.
suspend fun CswHighLevelDslApi.sendToIcsSequencer(command: Setup): CommandResponse.SubmitResponse {
    return resolveIcsSequencer().submitAndWait(Sequence.create(listOf(command)))
}

suspend fun CswHighLevelDslApi.sendToPitSequencer(command: Setup): CommandResponse.SubmitResponse {
    return resolvePitSequencer().submitAndWait(Sequence.create(listOf(command)))
}

fun deserializeSequence(serializedSequence: String): Sequence {
    val jsArray = play.api.libs.json.Json.parse(serializedSequence) as play.api.libs.json.JsArray
    val jsValueList = scala.jdk.javaapi.CollectionConverters.asJava(jsArray.value())
    val commands = ArrayList<SequenceCommand>()
    for (jsValue in jsValueList) {
        commands.add(JavaJsonSupport.readSequenceCommand(jsValue))
    }
    return Sequence.create(commands)
}

fun getStringParam(command: Setup, keyName: String): String? =
    command.jGetStringMap()[keyName]
