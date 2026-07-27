package aps

import csw.params.events.Event
import csw.params.events.SystemEvent
import csw.params.events.EventName
import csw.params.events.EventKey
import csw.prefix.models.Prefix
import esw.ocs.dsl.params.stringKey
import esw.ocs.dsl.params.first
import esw.ocs.dsl.params.kGet

// ---------------------------------------------------------------------------
// userPromptResponseEvent — shared definitions
//
// Published by the UI (aps-submitter-prototype frontend) when an operator
// responds to a USER_PROMPT dialog rendered in the procedure log. Sequencer
// scripts that send a USER_PROMPT and need to pause for a response subscribe
// to this event under the publishing sequencer's own prefix.
// ---------------------------------------------------------------------------

object OriginatingPromptType {
    const val DECISION = "DECISION"
    const val WARNING  = "WARNING"
}

object DecisionResponse {
    const val YES   = "YES"
    const val NO    = "NO"
    const val ABORT = "ABORT"
    const val NA    = "N/A"
}

object ErrorResponse {
    const val RETRY    = "RETRY"
    const val CONTINUE = "CONTINUE"
    const val ABORT    = "ABORT"
    const val NA       = "N/A"
}

val userPromptResponseEventName = EventName("userPromptResponseEvent")

private val originatingPromptTypeKey  = stringKey("originatingPromptType")
private val originatingMessageIdKey   = stringKey("originatingMessageId")
private val originatingMessageUuidKey = stringKey("originatingMessageUuid")
private val decisionResponseKey       = stringKey("decisionResponse")
private val errorResponseKey          = stringKey("errorResponse")

fun userPromptResponseEventKey(source: Prefix): EventKey =
    EventKey(source, userPromptResponseEventName)

// originatingMessageId is the stable, human-readable id echoed back from the
// originating apsProcedureEvent (e.g. "msg.ask-user.prompt") - useful for
// logging/display but NOT unique per invocation. originatingMessageUuid is
// the per-invocation uuid echoed back from that same event's messageUuid
// field, and is the value handlers should match on to correlate a response
// with the specific prompt that triggered it.
fun buildUserPromptResponseEvent(
    source: Prefix,
    originatingPromptType: String,
    originatingMessageId: String,
    originatingMessageUuid: String,
    decisionResponse: String = DecisionResponse.NA,
    errorResponse: String = ErrorResponse.NA
): SystemEvent =
    SystemEvent(source, userPromptResponseEventName)
        .add(originatingPromptTypeKey.set(originatingPromptType))
        .add(originatingMessageIdKey.set(originatingMessageId))
        .add(originatingMessageUuidKey.set(originatingMessageUuid))
        .add(decisionResponseKey.set(decisionResponse))
        .add(errorResponseKey.set(errorResponse))

data class UserPromptResponse(
    val originatingPromptType: String,
    val originatingMessageId: String,
    val originatingMessageUuid: String,
    val decisionResponse: String,
    val errorResponse: String
)

fun decodeUserPromptResponseEvent(event: Event): UserPromptResponse? {
    val params = event.paramType()
    val originatingPromptType  = params.kGet(originatingPromptTypeKey)?.first
    val originatingMessageId   = params.kGet(originatingMessageIdKey)?.first
    val originatingMessageUuid = params.kGet(originatingMessageUuidKey)?.first
    val decisionResponse       = params.kGet(decisionResponseKey)?.first ?: DecisionResponse.NA
    val errorResponse          = params.kGet(errorResponseKey)?.first ?: ErrorResponse.NA

    if (originatingPromptType == null || originatingMessageId == null || originatingMessageUuid == null) return null

    return UserPromptResponse(
        originatingPromptType  = originatingPromptType,
        originatingMessageId   = originatingMessageId,
        originatingMessageUuid = originatingMessageUuid,
        decisionResponse       = decisionResponse,
        errorResponse          = errorResponse
    )
}
