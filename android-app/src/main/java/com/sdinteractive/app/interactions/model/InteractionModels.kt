package com.sdinteractive.app.interactions.model

import kotlin.math.abs
import kotlin.math.round

enum class InteractionType {
    EMOTION,
    VALUE_BOOST,
    QUIZ,
    QUIZ_RESULT,
    RATING,
    KNOWLEDGE,
    PERSON_DETECT,
    RELATION_GRAPH,
    WARNING,
    HIGHLIGHT_COLLECT,
    BROADCAST
}

enum class InteractionPosition {
    TOP,
    TOP_CENTER,
    BOTTOM,
    BOTTOM_CENTER,
    LEFT_BOTTOM,
    RIGHT_BOTTOM,
    CENTER,
    LEFT,
    RIGHT
}

sealed interface InteractionTrigger {
    data class Fixed(val timeSec: Double) : InteractionTrigger {
        init {
            require(timeSec.isFinite() && timeSec >= 0.0) {
                "Fixed timeSec must be finite and non-negative"
            }
        }
    }

    data class Range(
        val startSec: Double,
        val endSec: Double
    ) : InteractionTrigger {
        init {
            require(startSec.isFinite() && endSec.isFinite()) {
                "Range bounds must be finite"
            }
            require(startSec >= 0.0 && endSec >= 0.0) {
                "Range bounds must be non-negative"
            }
            require(endSec >= startSec) { "Range endSec must be greater than or equal to startSec" }
        }
    }

    data class EpisodeEnding(val remainingSec: Double) : InteractionTrigger {
        init {
            require(remainingSec.isFinite() && remainingSec > 0.0) {
                "remainingSec must be finite and positive"
            }
        }
    }
}

sealed interface InteractionPayload

data class EmotionPayload(
    val emotionKey: String,
    val label: String,
    val particles: List<String>,
    val broadcast: String,
    val showBroadcastAfterSec: Double? = null
) : InteractionPayload {
    init {
        require(particles.isNotEmpty()) { "Emotion particles must not be empty" }
        require(showBroadcastAfterSec == null || showBroadcastAfterSec.isFinite() && showBroadcastAfterSec >= 0.0) {
            "Broadcast delay must be finite and non-negative"
        }
    }
}

data class ValueBoostPayload(
    val valueKey: String,
    val label: String,
    val initialValue: Int,
    val maxValue: Int,
    val step: Int,
    val clickText: String,
    val maxText: String,
    val theme: String,
    val persistKey: String? = null
) : InteractionPayload {
    init {
        require(initialValue >= 0) { "Initial value must be non-negative" }
        require(maxValue >= 0) { "Max value must be non-negative" }
        require(initialValue <= maxValue) { "Initial value must not exceed max value" }
        require(step > 0) { "Value step must be positive" }
    }
}

data class QuizOption(
    val id: String,
    val text: String,
    val isCorrect: Boolean = false
)

enum class QuizLayout {
    STANDARD,
    VERSUS
}

data class QuizPayload(
    val quizId: String,
    val question: String,
    val options: List<QuizOption>,
    val participants: String,
    val resultTimeSec: Double? = null,
    val resultEpisodeId: Int? = null,
    val afterSubmitAction: String? = null,
    val layout: QuizLayout = QuizLayout.STANDARD
) : InteractionPayload {
    init {
        require(options.size >= 2) { "Quiz requires at least two options" }
        require(options.map { it.id }.toSet().size == options.size) {
            "Quiz option IDs must be unique"
        }
    }
}

data class QuizResultPayload(
    val quizId: String,
    val correctOptionId: String,
    val correctText: String,
    val successText: String,
    val compareText: String,
    val rewardText: String
) : InteractionPayload

data class RatingPayload(
    val ratingId: String,
    val question: String,
    val min: Float,
    val max: Float,
    val step: Float,
    val defaultValue: Float,
    val maxLabel: String,
    val submitText: String,
    val resultText: String
) : InteractionPayload {
    init {
        require(min.isFinite() && max.isFinite() && step.isFinite() && defaultValue.isFinite()) {
            "Rating values must be finite"
        }
        require(min < max) { "Rating min must be less than max" }
        require(step > 0f) { "Rating step must be positive" }
        require(step <= max - min) { "Rating step must not exceed range" }
        require(defaultValue in min..max) { "Rating default must be within bounds" }
        val defaultStep = (defaultValue - min) / step
        require(abs(defaultStep - round(defaultStep)) < 0.0001f) {
            "Rating default must align to a step"
        }
    }
}

data class KnowledgePayload(
    val title: String,
    val summary: String,
    val tags: List<String> = emptyList()
) : InteractionPayload

data class WarningPayload(
    val text: String,
    val countdown: List<Int>,
    val theme: String
) : InteractionPayload {
    init {
        require(countdown.isNotEmpty()) { "Warning countdown must not be empty" }
    }
}

sealed interface ClipEnd {
    data class Fixed(val seconds: Double) : ClipEnd {
        init {
            require(seconds.isFinite() && seconds >= 0.0) {
                "Clip end seconds must be finite and non-negative"
            }
        }
    }

    data object EpisodeEnd : ClipEnd
}

data class HighlightPayload(
    val highlightId: String,
    val title: String,
    val clipStartSec: Double,
    val clipEnd: ClipEnd,
    val coverTimeSec: Double?,
    val buttonText: String,
    val successText: String,
    val detailText: String,
    val actionText: String,
    val triggerWhenRemainingSec: Double? = null
) : InteractionPayload {
    init {
        require(clipStartSec.isFinite() && clipStartSec >= 0.0) {
            "Highlight clip start must be finite and non-negative"
        }
        if (clipEnd is ClipEnd.Fixed) {
            require(clipEnd.seconds >= clipStartSec) {
                "Highlight fixed clip end must not precede clip start"
            }
        }
    }
}

data class BroadcastPayload(
    val message: String,
    val avatarUrls: List<String> = emptyList()
) : InteractionPayload

data class PersonDetectPayload(
    val prompt: String = "识别人物",
    val scanDurationMs: Long = 600
) : InteractionPayload

data class RelationGraphPayload(
    val focusCharacterId: String,
    val title: String = "人物关系图谱"
) : InteractionPayload

data class InteractionEvent(
    val id: String,
    val episodeNumber: Int,
    val trigger: InteractionTrigger,
    val type: InteractionType,
    val title: String,
    val position: InteractionPosition,
    val displayDurationSec: Double = 3.0,
    val payload: InteractionPayload
) {
    init {
        require(id.isNotBlank()) { "Interaction event id must not be blank" }
        require(episodeNumber > 0) { "episodeNumber must be positive" }
        require(displayDurationSec.isFinite() && displayDurationSec > 0.0) {
            "displayDurationSec must be finite and positive"
        }
        require(payloadMatchesType(type, payload)) {
            "Payload ${payload::class.simpleName} does not match interaction type $type"
        }
        if (
            type == InteractionType.HIGHLIGHT_COLLECT &&
            trigger is InteractionTrigger.EpisodeEnding
        ) {
            val highlight = payload as HighlightPayload
            require(highlight.triggerWhenRemainingSec == trigger.remainingSec) {
                "Ending highlight payload remaining seconds must match its trigger"
            }
        }
    }
}

private fun payloadMatchesType(
    type: InteractionType,
    payload: InteractionPayload
): Boolean = when (type) {
    InteractionType.EMOTION -> payload is EmotionPayload
    InteractionType.VALUE_BOOST -> payload is ValueBoostPayload
    InteractionType.QUIZ -> payload is QuizPayload
    InteractionType.QUIZ_RESULT -> payload is QuizResultPayload
    InteractionType.RATING -> payload is RatingPayload
    InteractionType.KNOWLEDGE -> payload is KnowledgePayload
    InteractionType.PERSON_DETECT -> payload is PersonDetectPayload
    InteractionType.RELATION_GRAPH -> payload is RelationGraphPayload
    InteractionType.WARNING -> payload is WarningPayload
    InteractionType.HIGHLIGHT_COLLECT -> payload is HighlightPayload
    InteractionType.BROADCAST -> payload is BroadcastPayload
}

data class TriggerRecord(
    val eventId: String,
    val eventPositionSec: Double,
    val triggeredAtSec: Double
)

data class TriggerSession(
    val records: Map<String, TriggerRecord> = emptyMap()
) {
    val triggeredIds: Set<String>
        get() = records.keys

    fun hasTriggered(eventId: String): Boolean = eventId in records

    fun recordFor(eventId: String): TriggerRecord? = records[eventId]

    fun record(
        eventId: String,
        eventPositionSec: Double,
        triggeredAtSec: Double
    ): TriggerSession = copy(
        records = records + (
            eventId to TriggerRecord(
                eventId = eventId,
                eventPositionSec = eventPositionSec,
                triggeredAtSec = triggeredAtSec
            )
        )
    )

    fun allowReplayAfter(positionSec: Double): TriggerSession = copy(
        records = records.filterValues { record ->
            record.eventPositionSec <= positionSec
        }
    )
}

data class ScheduledInteractions(
    val primary: InteractionEvent? = null,
    val results: List<InteractionEvent> = emptyList(),
    val broadcast: InteractionEvent? = null,
    val session: TriggerSession = TriggerSession(),
    val triggeredIds: Set<String> = emptySet()
) {
    companion object {
        fun empty(session: TriggerSession = TriggerSession()) =
            ScheduledInteractions(session = session)
    }
}
