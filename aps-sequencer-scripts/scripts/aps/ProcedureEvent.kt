package aps

import csw.params.events.SystemEvent
import csw.params.events.EventName
import csw.prefix.models.Prefix
import csw.params.javadsl.JKeyType
import csw.params.javadsl.JUnits
import esw.ocs.dsl.params.stringKey
import esw.ocs.dsl.params.first
import esw.ocs.dsl.params.kGet
import java.util.UUID

// ---------------------------------------------------------------------------
// apsProcedureEvent — shared definitions
// ---------------------------------------------------------------------------

object ProcedureEventType {
    const val INFO_MESSAGE  = "INFO_MESSAGE"
    const val WARN_MESSAGE  = "WARN_MESSAGE"
    const val USER_PROMPT   = "USER_PROMPT"
    const val VIZ_DISPLAY   = "VIZ_DISPLAY"
    const val ITERATION     = "ITERATION"
}

private val eventName    = EventName("apsProcedureEvent")

private val typeKey        = JKeyType.StringKey().make("type",      JUnits.NoUnits)
private val dialogKeyKey   = JKeyType.StringKey().make("dialogKey", JUnits.NoUnits)
private val helpKeyKey     = JKeyType.StringKey().make("helpKey",   JUnits.NoUnits)
private val messageIdKey   = JKeyType.StringKey().make("messageId", JUnits.NoUnits)
// messageUuid uses the Kotlin-native stringKey factory for consistency with
// UserPromptResponseEvent.kt's keys. Note: reading a param back off a
// SystemEvent (as messageUuidOf does below) works directly via kGet because
// SystemEvent concretely extends ParameterSetType<SystemEvent>. Reading a
// param off the broader Event type (the sealed trait wrapping
// SystemEvent/ObserveEvent) does NOT resolve the same way - that case needs
// event.paramType() first. See decodeUserPromptResponseEvent in
// UserPromptResponseEvent.kt, which receives an Event (from an event
// subscription) rather than a SystemEvent.
private val messageUuidKey = stringKey("messageUuid")

// Optional free-text field -- not part of the messageId lookup convention, used only when
// a message needs to carry runtime-specific text that messages.properties can't express
// (e.g. the actual reason string from a ScriptError, as opposed to a static "Sequencer
// encountered an error" line). Only added to the event when explicitly supplied (see
// buildProcedureEvent below), so every existing call site is unaffected.
private val errorTextKey = stringKey("errorText")

// messageUuid is generated internally on every call so that no existing
// caller needs to change. It gives each published apsProcedureEvent a unique
// identity even when messageId is a stable, reused, human-readable string -
// this is what lets a handler that's waiting for a correlated response (e.g.
// userPromptResponseEvent) distinguish "this round's" prompt from a prior
// round's stale leftover. Callers that need the generated uuid (currently
// just the ask-user USER_PROMPT handler) read it back via messageUuidOf().
fun buildProcedureEvent(
    source: Prefix,
    type: String,
    dialogKey: String,
    helpKey: String,
    messageId: String,
    errorText: String? = null
): SystemEvent {
    var event = SystemEvent(source, eventName)
        .add(typeKey.set(type))
        .add(dialogKeyKey.set(dialogKey))
        .add(helpKeyKey.set(helpKey))
        .add(messageIdKey.set(messageId))
        .add(messageUuidKey.set(UUID.randomUUID().toString()))
    if (errorText != null) {
        event = event.add(errorTextKey.set(errorText))
    }
    return event
}

fun messageUuidOf(event: SystemEvent): String? =
    event.kGet(messageUuidKey)?.first
