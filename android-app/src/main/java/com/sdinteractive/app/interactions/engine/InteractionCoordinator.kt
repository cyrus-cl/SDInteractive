package com.sdinteractive.app.interactions.engine

import com.sdinteractive.app.interactions.model.BroadcastPayload
import com.sdinteractive.app.interactions.model.ClipEnd
import com.sdinteractive.app.interactions.model.EmotionPayload
import com.sdinteractive.app.interactions.model.HighlightPayload
import com.sdinteractive.app.interactions.model.InteractionEvent
import com.sdinteractive.app.interactions.model.InteractionTrigger
import com.sdinteractive.app.interactions.model.InteractionType
import com.sdinteractive.app.interactions.model.QuizPayload
import com.sdinteractive.app.interactions.model.QuizResultPayload
import com.sdinteractive.app.interactions.model.RatingPayload
import com.sdinteractive.app.interactions.model.TriggerSession
import com.sdinteractive.app.interactions.model.ValueBoostPayload
import com.sdinteractive.app.interactions.storage.HighlightRecord
import com.sdinteractive.app.interactions.storage.InteractionRecords
import com.sdinteractive.app.interactions.storage.QuizAnswerRecord
import com.sdinteractive.app.interactions.storage.RatingRecord
import com.sdinteractive.app.player.PlayerSnapshot
import kotlin.math.ceil
import kotlin.math.roundToInt

data class BroadcastMessage(
    val instanceId: Long,
    val text: String,
    val avatarUrls: List<String> = emptyList(),
    val heatText: String = "热度上升",
    val isClosing: Boolean = false
)

data class HighlightSavedState(
    val record: HighlightRecord,
    val successText: String,
    val detailText: String,
    val actionText: String
)

data class InteractionUiState(
    val primary: InteractionEvent? = null,
    val results: List<QuizResultPresentation> = emptyList(),
    val broadcast: BroadcastMessage? = null,
    val savedToast: HighlightSavedState? = null,
    val debugEvent: InteractionEvent? = null
)

data class ValueUpdate(
    val value: Int,
    val reachedMax: Boolean,
    val saved: Boolean
)

data class QuizSubmission(
    val answer: QuizAnswerRecord?,
    val saved: Boolean,
    val openNextEpisode: Boolean,
    val nextEpisodeDelayMs: Long = 0L
)

data class RatingSubmission(
    val record: RatingRecord?,
    val saved: Boolean
)

enum class QuizOutcome {
    CORRECT,
    INCORRECT,
    NOT_PARTICIPATED
}

data class QuizResultState(
    val outcome: QuizOutcome,
    val answer: QuizAnswerRecord?,
    val payload: QuizResultPayload
)

data class QuizResultPresentation(
    val event: InteractionEvent,
    val result: QuizResultState
) {
    val id: String
        get() = event.id
}

data class InteractionCoordinatorKey(
    val episodeNumber: Int,
    val records: InteractionRecords
)

data class HighlightCollection(
    val record: HighlightRecord,
    val saved: Boolean
)

internal fun interactionPriority(type: InteractionType): Int = when (type) {
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

fun nextEpisodeDelayMs(payload: QuizPayload): Long =
    if (payload.afterSubmitAction == "nextEpisode") 800L else 0L

fun quizDeadlineLabel(
    trigger: InteractionTrigger,
    snapshot: PlayerSnapshot
): String? = when (trigger) {
    is InteractionTrigger.Range -> {
        val remaining = ceil((trigger.endSec - snapshot.positionSec).coerceAtLeast(0.0)).toInt()
        "还剩 $remaining 秒"
    }
    is InteractionTrigger.EpisodeEnding -> {
        if (snapshot.durationMs <= 0L) {
            "本集结尾截止"
        } else {
            val remaining = ceil((snapshot.durationSec - snapshot.positionSec).coerceAtLeast(0.0)).toInt()
            "还剩 $remaining 秒"
        }
    }
    is InteractionTrigger.Fixed -> null
}

class InteractionCoordinator(
    private val records: InteractionRecords,
    private val scheduler: InteractionScheduler = InteractionScheduler(),
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    var state: InteractionUiState = InteractionUiState()
        private set

    var triggerSession: TriggerSession = TriggerSession()
        private set

    private var episodeNumber: Int? = null
    private var previousSnapshot = PlayerSnapshot()
    private var activePrimary: InteractionEvent? = null
    private var primaryDeadlineMs: Long? = null
    private var pauseStartedAtMs: Long? = null
    private val resultDeadlines = linkedMapOf<String, Long>()
    private var broadcastDeadlineMs: Long? = null
    private var savedToastDeadlineMs: Long? = null
    private var pendingBroadcast: PendingBroadcast? = null
    private var broadcastInstanceCounter = 0L
    private var debugRequestId: String? = null
    private var activeDebugEvent: InteractionEvent? = null
    private var debugDeadlineMs: Long? = null
    private val transientValues = linkedMapOf<String, Int>()

    fun update(
        episodeNumber: Int,
        snapshot: PlayerSnapshot,
        events: List<InteractionEvent>,
        debugEvent: InteractionEvent? = null
    ): InteractionUiState {
        val now = nowMs()
        if (this.episodeNumber != episodeNumber) {
            resetForEpisode(episodeNumber, snapshot)
        }

        syncPause(snapshot, now)
        syncDebug(debugEvent, now)
        if (snapshot.isPausedForInteractionClock()) {
            previousSnapshot = snapshot
            publishState(snapshot)
            return state
        }

        expireWallClockState(now)
        revealPendingBroadcast(now)
        expirePlaybackBoundPrimary(snapshot)

        val scheduled = scheduler.evaluate(
            events = events,
            previous = previousSnapshot,
            current = snapshot,
            session = triggerSession
        )
        previousSnapshot = snapshot
        triggerSession = scheduled.session
        handlePrimaryCandidate(scheduled.primary, snapshot, now)
        addResults(scheduled.results, now)
        scheduled.broadcast?.let { showBroadcast(it, now) }

        if (snapshot.isEnded) {
            activePrimary = null
            primaryDeadlineMs = null
        }
        publishState(snapshot)
        return state
    }

    fun onEmotionClick(event: InteractionEvent): Int {
        val payload = event.payload as EmotionPayload
        val count = (transientValues[event.id] ?: 0) + 1
        transientValues[event.id] = count
        refreshPrimaryIdle(event)

        val delayMs = ((payload.showBroadcastAfterSec ?: 0.0) * 1_000.0).toLong()
        val now = nowMs()
        val displayMs = event.displayDurationMs()
        val revealAt = maxOf(
            primaryDeadlineMs ?: (now + displayMs),
            now + delayMs.coerceAtMost(displayMs)
        )
        state = state.copy(broadcast = null)
        broadcastDeadlineMs = null
        pendingBroadcast = PendingBroadcast(
            message = newBroadcast(payload.broadcast),
            revealAtMs = revealAt
        )
        return count
    }

    fun refreshPrimaryIdle(event: InteractionEvent) {
        if (event.trigger is InteractionTrigger.Fixed) {
            primaryDeadlineMs = nowMs() + event.displayDurationMs()
        }
    }

    fun setPrimaryIdleHeld(
        event: InteractionEvent,
        held: Boolean
    ) {
        if (state.primary?.id != event.id) return
        primaryDeadlineMs = if (held) null else nowMs() + event.displayDurationMs()
    }

    fun emotionCount(eventId: String): Int = transientValues[eventId] ?: 0

    fun valueFor(event: InteractionEvent): Int {
        val payload = event.payload as ValueBoostPayload
        val stored = payload.persistKey?.let(records::persistentValue)
        return transientValues.getOrPut(event.id) {
            (stored ?: payload.initialValue).coerceIn(payload.initialValue, payload.maxValue)
        }
    }

    fun incrementValue(event: InteractionEvent): ValueUpdate {
        val payload = event.payload as ValueBoostPayload
        val current = valueFor(event)
        val value = (current + payload.step).coerceAtMost(payload.maxValue)
        transientValues[event.id] = value
        val saved = payload.persistKey?.let { records.savePersistentValue(it, value) } ?: true
        val reachedMax = value >= payload.maxValue
        primaryDeadlineMs = nowMs() +
            if (reachedMax) MAX_FEEDBACK_DURATION_MS else event.displayDurationMs()
        return ValueUpdate(value, reachedMax, saved)
    }

    fun quizAnswer(event: InteractionEvent): QuizAnswerRecord? {
        val payload = event.payload as QuizPayload
        return records.quizAnswer(payload.quizId)
    }

    fun submitQuiz(
        event: InteractionEvent,
        optionId: String
    ): QuizSubmission {
        val payload = event.payload as QuizPayload
        val existing = records.quizAnswer(payload.quizId)
        if (existing != null) {
            return QuizSubmission(existing, saved = false, openNextEpisode = false)
        }
        val option = payload.options.firstOrNull { it.id == optionId }
            ?: return QuizSubmission(null, saved = false, openNextEpisode = false)
        val answer = QuizAnswerRecord(
            quizId = payload.quizId,
            selectedOptionId = option.id,
            selectedText = option.text,
            episodeNumber = event.episodeNumber,
            submitTimeMs = nowMs()
        )
        val saved = records.saveQuizAnswer(answer)
        val delay = nextEpisodeDelayMs(payload)
        return QuizSubmission(
            answer = answer.takeIf { saved },
            saved = saved,
            openNextEpisode = saved && delay > 0L,
            nextEpisodeDelayMs = if (saved) delay else 0L
        )
    }

    fun quizOutcome(event: InteractionEvent): QuizResultState {
        val payload = event.payload as QuizResultPayload
        val answer = records.quizAnswer(payload.quizId)
        val outcome = when {
            answer == null && event.id.startsWith("reference_quiz_result") -> QuizOutcome.CORRECT
            answer == null -> QuizOutcome.NOT_PARTICIPATED
            answer.selectedOptionId == payload.correctOptionId -> QuizOutcome.CORRECT
            else -> QuizOutcome.INCORRECT
        }
        return QuizResultState(outcome, answer, payload)
    }

    fun ratingValue(event: InteractionEvent): Float {
        val payload = event.payload as RatingPayload
        return records.rating(payload.ratingId)?.value ?: payload.defaultValue
    }

    fun ratingRecord(event: InteractionEvent): RatingRecord? {
        val payload = event.payload as RatingPayload
        return records.rating(payload.ratingId)
    }

    fun submitRating(
        event: InteractionEvent,
        value: Float
    ): RatingSubmission {
        val payload = event.payload as RatingPayload
        val existing = records.rating(payload.ratingId)
        if (existing != null) {
            return RatingSubmission(existing, saved = false)
        }
        val normalized = normalizeRating(value, payload)
        val record = RatingRecord(
            ratingId = payload.ratingId,
            value = normalized,
            episodeNumber = event.episodeNumber,
            submitTimeMs = nowMs()
        )
        val saved = records.saveRating(record)
        if (saved) {
            primaryDeadlineMs = nowMs() + RATING_FEEDBACK_DURATION_MS
        }
        return RatingSubmission(record.takeIf { saved }, saved)
    }

    fun isHighlightCollected(event: InteractionEvent): Boolean {
        val payload = event.payload as HighlightPayload
        return records.highlights().any { it.highlightId == payload.highlightId }
    }

    fun collectHighlight(
        event: InteractionEvent,
        durationSec: Double
    ): HighlightCollection {
        val payload = event.payload as HighlightPayload
        val existing = records.highlights().firstOrNull { it.highlightId == payload.highlightId }
        if (existing != null) return HighlightCollection(existing, saved = false)

        val resolvedEnd = when (val clipEnd = payload.clipEnd) {
            is ClipEnd.Fixed -> clipEnd
            ClipEnd.EpisodeEnd -> ClipEnd.Fixed(durationSec.coerceAtLeast(payload.clipStartSec))
        }
        val record = HighlightRecord(
            episodeNumber = event.episodeNumber,
            highlightId = payload.highlightId,
            title = payload.title,
            clipStartSec = payload.clipStartSec,
            clipEnd = resolvedEnd,
            coverTimeSec = payload.coverTimeSec,
            createdAtMs = nowMs()
        )
        val saved = records.saveHighlight(record)
        if (saved) {
            state = state.copy(
                savedToast = HighlightSavedState(
                    record = record,
                    successText = payload.successText,
                    detailText = payload.detailText,
                    actionText = payload.actionText
                )
            )
            savedToastDeadlineMs = nowMs() + SAVED_TOAST_DURATION_MS
        }
        return HighlightCollection(record, saved)
    }

    fun dismissPrimary() {
        val now = nowMs()
        if (activeDebugEvent != null) {
            activeDebugEvent = null
            debugDeadlineMs = null
        } else {
            activePrimary = null
            primaryDeadlineMs = null
        }
        revealPendingBroadcast(now, force = true)
        publishState(previousSnapshot)
    }

    fun dismissSavedToast() {
        state = state.copy(savedToast = null)
        savedToastDeadlineMs = null
    }

    fun dismissBroadcast() {
        state = state.copy(broadcast = null)
        broadcastDeadlineMs = null
    }

    private fun syncPause(snapshot: PlayerSnapshot, now: Long) {
        if (snapshot.isPausedForInteractionClock()) {
            if (pauseStartedAtMs == null) pauseStartedAtMs = now
            return
        }
        val pauseStart = pauseStartedAtMs ?: return
        val pausedFor = (now - pauseStart).coerceAtLeast(0L)
        primaryDeadlineMs = primaryDeadlineMs.shiftBy(pausedFor)
        broadcastDeadlineMs = broadcastDeadlineMs.shiftBy(pausedFor)
        savedToastDeadlineMs = savedToastDeadlineMs.shiftBy(pausedFor)
        debugDeadlineMs = debugDeadlineMs.shiftBy(pausedFor)
        resultDeadlines.replaceAll { _, deadline -> deadline + pausedFor }
        pendingBroadcast = pendingBroadcast?.copy(
            revealAtMs = pendingBroadcast!!.revealAtMs + pausedFor
        )
        pauseStartedAtMs = null
    }

    private fun syncDebug(debugEvent: InteractionEvent?, now: Long) {
        if (debugEvent == null) {
            debugRequestId = null
            activeDebugEvent = null
            debugDeadlineMs = null
            return
        }
        if (debugRequestId != debugEvent.id) {
            debugRequestId = debugEvent.id
            when (debugEvent.type) {
                InteractionType.QUIZ_RESULT -> {
                    activeDebugEvent = null
                    debugDeadlineMs = null
                    addResults(listOf(debugEvent), now)
                }
                InteractionType.BROADCAST -> {
                    activeDebugEvent = null
                    debugDeadlineMs = null
                    showBroadcast(debugEvent, now)
                }
                else -> {
                    activeDebugEvent = debugEvent
                    debugDeadlineMs = now + debugEvent.displayDurationMs()
                }
            }
        }
    }

    private fun expireWallClockState(now: Long) {
        if (primaryDeadlineMs?.let { now >= it } == true) {
            activePrimary = null
            primaryDeadlineMs = null
        }
        if (debugDeadlineMs?.let { now >= it } == true) {
            activeDebugEvent = null
            debugDeadlineMs = null
        }
        val activeResults = state.results.filter { result ->
            resultDeadlines.getOrDefault(result.id, Long.MIN_VALUE) > now
        }
        if (activeResults.size != state.results.size) {
            resultDeadlines.keys.retainAll(activeResults.mapTo(hashSetOf()) { it.id })
            state = state.copy(results = activeResults)
        }
        if (broadcastDeadlineMs?.let { now >= it } == true) {
            state = state.copy(broadcast = null)
            broadcastDeadlineMs = null
        } else if (
            broadcastDeadlineMs?.let { deadline -> deadline - now <= BROADCAST_EXIT_DURATION_MS } == true &&
            state.broadcast?.isClosing == false
        ) {
            state = state.copy(broadcast = state.broadcast?.copy(isClosing = true))
        }
        if (savedToastDeadlineMs?.let { now >= it } == true) {
            state = state.copy(savedToast = null)
            savedToastDeadlineMs = null
        }
    }

    private fun revealPendingBroadcast(now: Long, force: Boolean = false) {
        val pending = pendingBroadcast ?: return
        if (force || now >= pending.revealAtMs) {
            state = state.copy(broadcast = pending.message)
            broadcastDeadlineMs = now + BROADCAST_DURATION_MS
            pendingBroadcast = null
        }
    }

    private fun expirePlaybackBoundPrimary(snapshot: PlayerSnapshot) {
        val event = activePrimary ?: return
        val expired = when (val trigger = event.trigger) {
            is InteractionTrigger.Range -> snapshot.positionSec > trigger.endSec
            is InteractionTrigger.EpisodeEnding -> snapshot.isEnded
            is InteractionTrigger.Fixed -> false
        }
        if (expired) {
            activePrimary = null
            primaryDeadlineMs = null
        }
    }

    private fun handlePrimaryCandidate(
        candidate: InteractionEvent?,
        snapshot: PlayerSnapshot,
        now: Long
    ) {
        candidate ?: return
        val current = activePrimary
        val accepts = current == null ||
            current.id == candidate.id ||
            interactionPriority(candidate.type) > interactionPriority(current.type)
        if (accepts) {
            if (
                current != null &&
                current.id != candidate.id &&
                current.trigger is InteractionTrigger.Range
            ) {
                triggerSession = triggerSession.copy(
                    records = triggerSession.records - current.id
                )
            }
            activePrimary = candidate
            primaryDeadlineMs = deadlineFor(candidate, now)
        } else if (candidate.trigger is InteractionTrigger.Range) {
            triggerSession = triggerSession.copy(
                records = triggerSession.records - candidate.id
            )
        }
        expirePlaybackBoundPrimary(snapshot)
    }

    private fun addResults(results: List<InteractionEvent>, now: Long) {
        val activeResults = state.results.toMutableList()
        results.forEach { event ->
            if (activeResults.none { it.id == event.id } && activeResults.size < MAX_RESULTS) {
                activeResults += QuizResultPresentation(
                    event = event,
                    result = quizOutcome(event)
                )
                resultDeadlines[event.id] = now + event.displayDurationMs()
            }
        }
        state = state.copy(results = activeResults)
    }

    private fun showBroadcast(event: InteractionEvent, now: Long) {
        val payload = event.payload as BroadcastPayload
        state = state.copy(
            broadcast = newBroadcast(payload.message, payload.avatarUrls)
        )
        broadcastDeadlineMs = now + BROADCAST_DURATION_MS
    }

    private fun publishState(snapshot: PlayerSnapshot) {
        state = state.copy(
            primary = if (snapshot.isSeeking) null else activeDebugEvent ?: activePrimary,
            debugEvent = activeDebugEvent
        )
    }

    private fun deadlineFor(event: InteractionEvent, now: Long): Long? =
        now + event.displayDurationMs()

    private fun resetForEpisode(
        episodeNumber: Int,
        snapshot: PlayerSnapshot
    ) {
        this.episodeNumber = episodeNumber
        previousSnapshot = snapshot
        triggerSession = TriggerSession()
        state = InteractionUiState()
        activePrimary = null
        primaryDeadlineMs = null
        pauseStartedAtMs = null
        resultDeadlines.clear()
        broadcastDeadlineMs = null
        savedToastDeadlineMs = null
        pendingBroadcast = null
        debugRequestId = null
        activeDebugEvent = null
        debugDeadlineMs = null
        transientValues.clear()
    }

    private fun newBroadcast(
        text: String,
        avatarUrls: List<String> = emptyList()
    ): BroadcastMessage = BroadcastMessage(
        instanceId = ++broadcastInstanceCounter,
        text = text,
        avatarUrls = avatarUrls
    )

    private fun normalizeRating(value: Float, payload: RatingPayload): Float {
        val clamped = value.coerceIn(payload.min, payload.max)
        val stepsFromMin = ((clamped - payload.min) / payload.step).roundToInt()
        return (payload.min + stepsFromMin * payload.step).coerceIn(payload.min, payload.max)
    }

    private fun PlayerSnapshot.isPausedForInteractionClock(): Boolean =
        !isPlaying && !isEnded && !isSeeking

    private fun Long?.shiftBy(durationMs: Long): Long? = this?.plus(durationMs)

    private fun Double.toMillis(): Long = (this * 1_000.0).toLong().coerceAtLeast(1L)

    private fun InteractionEvent.displayDurationMs(): Long =
        displayDurationSec.toMillis().coerceAtMost(MAX_COMPONENT_DURATION_MS)

    private data class PendingBroadcast(
        val message: BroadcastMessage,
        val revealAtMs: Long
    )

    private companion object {
        const val MAX_RESULTS = 2
        const val BROADCAST_DURATION_MS = 3_000L
        const val BROADCAST_EXIT_DURATION_MS = 240L
        const val MAX_COMPONENT_DURATION_MS = 3_000L
        const val SAVED_TOAST_DURATION_MS = 3_000L
        const val RATING_FEEDBACK_DURATION_MS = 2_000L
        const val MAX_FEEDBACK_DURATION_MS = 900L
    }
}
