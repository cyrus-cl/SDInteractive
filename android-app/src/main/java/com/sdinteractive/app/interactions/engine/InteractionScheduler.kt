package com.sdinteractive.app.interactions.engine

import com.sdinteractive.app.interactions.model.InteractionEvent
import com.sdinteractive.app.interactions.model.InteractionTrigger
import com.sdinteractive.app.interactions.model.InteractionType
import com.sdinteractive.app.interactions.model.ScheduledInteractions
import com.sdinteractive.app.interactions.model.TriggerSession
import com.sdinteractive.app.player.PlayerSnapshot
import kotlin.math.abs

class InteractionScheduler(
    private val hitToleranceSec: Double = 0.6
) {
    init {
        require(hitToleranceSec >= 0.0) { "hitToleranceSec must not be negative" }
    }

    fun evaluate(
        events: List<InteractionEvent>,
        previous: PlayerSnapshot,
        current: PlayerSnapshot,
        session: TriggerSession
    ): ScheduledInteractions {
        val effectiveSession = if (previous.positionSec - current.positionSec > REWIND_THRESHOLD_SEC) {
            session.allowReplayAfter(current.positionSec)
        } else {
            session
        }

        if (current.isSeeking || !current.isPlaying) {
            return ScheduledInteractions.empty(effectiveSession)
        }

        val matching = events.filter { event ->
            !effectiveSession.hasTriggered(event.id) &&
                matches(event.trigger, previous, current)
        }
        if (matching.isEmpty()) {
            return ScheduledInteractions.empty(effectiveSession)
        }

        val results = matching
            .asSequence()
            .filter { it.type == InteractionType.QUIZ_RESULT }
            .take(MAX_VISIBLE_RESULTS)
            .toList()
        val primary = matching
            .asSequence()
            .filter { it.type in PRIMARY_TYPES }
            .maxByOrNull { priorityOf(it.type) }
        val broadcast = matching.firstOrNull { it.type == InteractionType.BROADCAST }
        val displayedIds = buildSet {
            primary?.let { add(it.id) }
            results.forEach { add(it.id) }
            broadcast?.let { add(it.id) }
        }
        val consumed = matching.filter { event ->
            event.trigger !is InteractionTrigger.Range || event.id in displayedIds
        }
        val updatedSession = consumed.fold(effectiveSession) { updated, event ->
            updated.record(
                eventId = event.id,
                eventPositionSec = eventPositionSec(event, current),
                triggeredAtSec = current.positionSec
            )
        }

        return ScheduledInteractions(
            primary = primary,
            results = results,
            broadcast = broadcast,
            session = updatedSession,
            triggeredIds = consumed.mapTo(linkedSetOf()) { it.id }
        )
    }

    private fun matches(
        trigger: InteractionTrigger,
        previous: PlayerSnapshot,
        current: PlayerSnapshot
    ): Boolean = when (trigger) {
        is InteractionTrigger.Fixed ->
            abs(current.positionSec - trigger.timeSec) <= hitToleranceSec ||
                (
                    previous.positionSec < trigger.timeSec &&
                        trigger.timeSec <= current.positionSec
                    )

        is InteractionTrigger.Range ->
            current.positionSec in trigger.startSec..trigger.endSec

        is InteractionTrigger.EpisodeEnding ->
            current.durationMs > 0 &&
                current.durationSec - current.positionSec in 0.0..trigger.remainingSec
    }

    private fun eventPositionSec(
        event: InteractionEvent,
        current: PlayerSnapshot
    ): Double = when (val trigger = event.trigger) {
        is InteractionTrigger.Fixed -> trigger.timeSec
        is InteractionTrigger.Range -> trigger.startSec
        is InteractionTrigger.EpisodeEnding -> current.durationSec - trigger.remainingSec
    }

    private fun priorityOf(type: InteractionType): Int = when (type) {
        InteractionType.WARNING -> 8
        InteractionType.QUIZ_RESULT -> 7
        InteractionType.QUIZ -> 6
        InteractionType.HIGHLIGHT_COLLECT -> 5
        InteractionType.VALUE_BOOST -> 4
        InteractionType.RATING -> 3
        InteractionType.KNOWLEDGE -> 2
        InteractionType.EMOTION -> 1
        InteractionType.PERSON_DETECT,
        InteractionType.RELATION_GRAPH,
        InteractionType.BROADCAST -> 0
    }

    private companion object {
        const val REWIND_THRESHOLD_SEC = 3.0
        const val MAX_VISIBLE_RESULTS = 2

        val PRIMARY_TYPES = setOf(
            InteractionType.WARNING,
            InteractionType.QUIZ,
            InteractionType.HIGHLIGHT_COLLECT,
            InteractionType.VALUE_BOOST,
            InteractionType.RATING,
            InteractionType.KNOWLEDGE,
            InteractionType.EMOTION,
            InteractionType.PERSON_DETECT,
            InteractionType.RELATION_GRAPH
        )
    }
}
