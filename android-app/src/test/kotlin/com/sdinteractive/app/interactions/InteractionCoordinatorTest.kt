package com.sdinteractive.app.interactions

import com.sdinteractive.app.interactions.engine.InteractionCoordinator
import com.sdinteractive.app.interactions.engine.InteractionCoordinatorKey
import com.sdinteractive.app.interactions.engine.QuizOutcome
import com.sdinteractive.app.interactions.engine.nextEpisodeDelayMs
import com.sdinteractive.app.interactions.engine.quizDeadlineLabel
import com.sdinteractive.app.interactions.model.ClipEnd
import com.sdinteractive.app.interactions.model.EmotionPayload
import com.sdinteractive.app.interactions.model.HighlightPayload
import com.sdinteractive.app.interactions.model.InteractionEvent
import com.sdinteractive.app.interactions.model.InteractionPosition
import com.sdinteractive.app.interactions.model.InteractionTrigger
import com.sdinteractive.app.interactions.model.InteractionType
import com.sdinteractive.app.interactions.model.QuizOption
import com.sdinteractive.app.interactions.model.QuizPayload
import com.sdinteractive.app.interactions.model.QuizResultPayload
import com.sdinteractive.app.interactions.model.RatingPayload
import com.sdinteractive.app.interactions.model.ValueBoostPayload
import com.sdinteractive.app.interactions.storage.HighlightRecord
import com.sdinteractive.app.interactions.storage.InteractionRecords
import com.sdinteractive.app.interactions.storage.QuizAnswerRecord
import com.sdinteractive.app.interactions.storage.RatingRecord
import com.sdinteractive.app.player.PlayerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionCoordinatorTest {
    private var nowMs = 1_000L
    private val records = FakeInteractionRecords()
    private val coordinator = InteractionCoordinator(
        records = records,
        nowMs = { nowMs }
    )

    @Test
    fun `episode change clears transient state and trigger session`() {
        val emotion = emotionEvent("emotion", episode = 1, timeSec = 10.0)
        coordinator.update(1, snapshot(10.0), listOf(emotion))
        coordinator.onEmotionClick(emotion)

        val state = coordinator.update(2, snapshot(0.0), emptyList())

        assertNull(state.primary)
        assertTrue(state.results.isEmpty())
        assertNull(state.broadcast)
        assertNull(state.savedToast)
        assertTrue(coordinator.triggerSession.triggeredIds.isEmpty())
    }

    @Test
    fun `result queue is limited to two items`() {
        val results = (1..3).map { index ->
            resultEvent("result_$index", quizId = "quiz_$index", timeSec = 20.0)
        }

        val state = coordinator.update(1, snapshot(20.0), results)

        assertEquals(listOf("result_1", "result_2"), state.results.map { it.id })
    }

    @Test
    fun `interaction click refreshes idle deadline`() {
        val emotion = emotionEvent("emotion", episode = 1, timeSec = 10.0)
        coordinator.update(1, snapshot(10.0), listOf(emotion))

        nowMs = 4_500L
        coordinator.onEmotionClick(emotion)
        nowMs = 7_400L
        assertEquals("emotion", coordinator.update(1, snapshot(12.0), listOf(emotion)).primary?.id)

        nowMs = 7_600L
        assertNull(coordinator.update(1, snapshot(12.1), listOf(emotion)).primary)
    }

    @Test
    fun `value increment clamps and persists by persist key`() {
        val event = valueEvent(initial = 80, max = 100, step = 30, persistKey = "suyu_forbearance")

        assertEquals(80, coordinator.valueFor(event))
        val update = coordinator.incrementValue(event)

        assertEquals(100, update.value)
        assertTrue(update.reachedMax)
        assertEquals(100, records.persistentValue("suyu_forbearance"))
        assertEquals(100, coordinator.incrementValue(event).value)
    }

    @Test
    fun `quiz restores answer and result reports correct incorrect and missing`() {
        val quiz = quizEvent("quiz_event", "quiz_1")
        records.saveQuizAnswer(QuizAnswerRecord("quiz_1", "yes", "会", 1, 10L))

        assertEquals("yes", coordinator.quizAnswer(quiz)?.selectedOptionId)
        assertEquals(QuizOutcome.CORRECT, coordinator.quizOutcome(resultEvent("correct", "quiz_1", 20.0)).outcome)

        records.saveQuizAnswer(QuizAnswerRecord("quiz_1", "no", "不会", 1, 11L))
        assertEquals(QuizOutcome.INCORRECT, coordinator.quizOutcome(resultEvent("wrong", "quiz_1", 20.0)).outcome)
        assertEquals(QuizOutcome.NOT_PARTICIPATED, coordinator.quizOutcome(resultEvent("missing", "quiz_2", 20.0)).outcome)
    }

    @Test
    fun `quiz submit persists once and reports next episode action`() {
        val quiz = quizEvent("quiz_event", "quiz_1", afterSubmitAction = "nextEpisode")

        val first = coordinator.submitQuiz(quiz, "yes")
        val second = coordinator.submitQuiz(quiz, "no")

        assertTrue(first.saved)
        assertTrue(first.openNextEpisode)
        assertFalse(second.saved)
        assertEquals("yes", records.quizAnswer("quiz_1")?.selectedOptionId)
    }

    @Test
    fun `rating restores saved value and only submits once`() {
        val rating = ratingEvent("rating_event", "rating_1")
        records.saveRating(RatingRecord("rating_1", 8.5f, 1, 100L))

        assertEquals(8.5f, coordinator.ratingValue(rating), 0.0f)
        assertFalse(coordinator.submitRating(rating, 9.0f).saved)
        assertEquals(8.5f, records.rating("rating_1")?.value ?: -1f, 0.0f)

        val fresh = ratingEvent("fresh_rating", "rating_2")
        val submission = coordinator.submitRating(fresh, 9.25f)

        assertTrue(submission.saved)
        assertEquals(9.5f, records.rating("rating_2")?.value ?: -1f, 0.0f)
    }

    @Test
    fun `rating submit keeps feedback visible for two seconds then dismisses`() {
        val rating = ratingEvent("rating_event", "rating_1")
        coordinator.update(1, snapshot(10.0), listOf(rating))

        nowMs = 4_500L
        assertTrue(coordinator.submitRating(rating, 9.0f).saved)

        nowMs = 6_400L
        assertEquals("rating_event", coordinator.update(1, snapshot(10.5), listOf(rating)).primary?.id)

        nowMs = 6_600L
        assertNull(coordinator.update(1, snapshot(10.6), listOf(rating)).primary)
    }

    @Test
    fun `episode end highlight converts to fixed duration and de duplicates`() {
        val event = highlightEvent(ClipEnd.EpisodeEnd)

        val first = coordinator.collectHighlight(event, durationSec = 240.0)
        val second = coordinator.collectHighlight(event, durationSec = 240.0)

        assertTrue(first.saved)
        assertFalse(second.saved)
        assertEquals(1, records.highlights().size)
        assertEquals(ClipEnd.Fixed(240.0), records.highlights().single().clipEnd)
        assertEquals("highlight_1", coordinator.state.savedToast?.record?.highlightId)
    }

    @Test
    fun `seeking hides active primary and restores it after seeking`() {
        val emotion = emotionEvent("emotion", episode = 1, timeSec = 10.0)
        assertEquals("emotion", coordinator.update(1, snapshot(10.0), listOf(emotion)).primary?.id)

        assertNull(
            coordinator.update(
                1,
                snapshot(10.2, isSeeking = true),
                listOf(emotion)
            ).primary
        )
        assertEquals(
            "emotion",
            coordinator.update(1, snapshot(10.3), listOf(emotion)).primary?.id
        )
    }

    @Test
    fun `debug override replaces primary without consuming scheduled session`() {
        val scheduled = emotionEvent("scheduled", episode = 1, timeSec = 10.0)
        val debug = valueEvent(id = "debug")

        val state = coordinator.update(
            episodeNumber = 1,
            snapshot = snapshot(10.0),
            events = listOf(scheduled),
            debugEvent = debug
        )

        assertEquals("debug", state.primary?.id)
        assertFalse(coordinator.triggerSession.hasTriggered("debug"))
        assertTrue(coordinator.triggerSession.hasTriggered("scheduled"))
    }

    @Test
    fun `pause freezes primary result pending broadcast and saved toast deadlines`() {
        val emotion = emotionEvent("emotion", episode = 1, timeSec = 10.0)
        val result = resultEvent("result", "quiz_1", 10.0)
        coordinator.update(1, snapshot(10.0), listOf(emotion, result))
        coordinator.onEmotionClick(emotion)
        coordinator.collectHighlight(highlightEvent(ClipEnd.Fixed(20.0)), durationSec = 300.0)

        nowMs = 2_000L
        coordinator.update(1, snapshot(10.2, isPlaying = false), listOf(emotion, result))
        nowMs = 20_000L
        val paused = coordinator.update(1, snapshot(10.2, isPlaying = false), listOf(emotion, result))

        assertEquals("emotion", paused.primary?.id)
        assertEquals(listOf("result"), paused.results.map { it.id })
        assertNull(paused.broadcast)
        assertTrue(paused.savedToast != null)

        nowMs = 20_100L
        val resumed = coordinator.update(1, snapshot(10.3), listOf(emotion, result))
        assertEquals("emotion", resumed.primary?.id)
        assertNull(resumed.broadcast)
        assertTrue(resumed.savedToast != null)

        nowMs = 22_099L
        assertNull(coordinator.update(1, snapshot(10.4), listOf(emotion, result)).broadcast)
        nowMs = 22_100L
        assertTrue(coordinator.update(1, snapshot(10.5), listOf(emotion, result)).broadcast != null)
    }

    @Test
    fun `episode ending quiz expires after runtime cap or playback end`() {
        val quiz = quizEvent(
            id = "ending_quiz",
            quizId = "ending",
            trigger = InteractionTrigger.EpisodeEnding(8.0)
        )
        assertEquals(
            "ending_quiz",
            coordinator.update(1, snapshot(92.0, durationSec = 100.0), listOf(quiz)).primary?.id
        )

        nowMs = 3_999L
        assertEquals(
            "ending_quiz",
            coordinator.update(1, snapshot(99.0, durationSec = 100.0), listOf(quiz)).primary?.id
        )
        nowMs = 4_001L
        assertNull(
            coordinator.update(1, snapshot(99.1, durationSec = 100.0), listOf(quiz)).primary
        )
        assertNull(
            coordinator.update(
                1,
                snapshot(100.0, durationSec = 100.0, isEnded = true),
                listOf(quiz)
            ).primary
        )
    }

    @Test
    fun `episode ending quiz deadline label uses playback remaining time`() {
        val trigger = InteractionTrigger.EpisodeEnding(8.0)

        assertEquals("还剩 7 秒", quizDeadlineLabel(trigger, snapshot(93.4, durationSec = 100.0)))
        assertEquals("还剩 0 秒", quizDeadlineLabel(trigger, snapshot(101.0, durationSec = 100.0)))
        assertEquals("本集结尾截止", quizDeadlineLabel(trigger, snapshot(20.0, durationSec = 0.0)))
    }

    @Test
    fun `new primary only replaces active primary when priority is higher`() {
        val active = quizEvent(
            id = "active_quiz",
            quizId = "active",
            trigger = InteractionTrigger.Fixed(10.0),
            durationSec = 1.0
        )
        val suppressedRange = valueEvent(
            id = "range_value",
            trigger = InteractionTrigger.Range(10.5, 20.0)
        )
        coordinator.update(1, snapshot(10.0), listOf(active, suppressedRange))

        nowMs = 1_500L
        assertEquals(
            "active_quiz",
            coordinator.update(1, snapshot(11.0), listOf(active, suppressedRange)).primary?.id
        )
        assertFalse(coordinator.triggerSession.hasTriggered("range_value"))

        nowMs = 2_100L
        assertEquals(
            "range_value",
            coordinator.update(1, snapshot(12.0), listOf(active, suppressedRange)).primary?.id
        )
    }

    @Test
    fun `higher priority event interrupts active lower priority event`() {
        val emotion = emotionEvent("emotion", episode = 1, timeSec = 10.0)
        val quiz = quizEvent(
            id = "quiz",
            quizId = "quiz",
            trigger = InteractionTrigger.Fixed(11.0)
        )
        coordinator.update(1, snapshot(10.0), listOf(emotion, quiz))

        assertEquals("quiz", coordinator.update(1, snapshot(11.0), listOf(emotion, quiz)).primary?.id)
    }

    @Test
    fun `active range resumes after higher priority fixed event ends`() {
        val rangeValue = valueEvent(
            id = "range_value",
            trigger = InteractionTrigger.Range(10.0, 20.0)
        )
        val highQuiz = quizEvent(
            id = "high_quiz",
            quizId = "high",
            trigger = InteractionTrigger.Fixed(12.0),
            durationSec = 1.0
        )

        assertEquals(
            "range_value",
            coordinator.update(1, snapshot(10.0), listOf(rangeValue, highQuiz)).primary?.id
        )
        assertEquals(
            "high_quiz",
            coordinator.update(1, snapshot(12.0), listOf(rangeValue, highQuiz)).primary?.id
        )

        nowMs = 2_100L
        assertEquals(
            "range_value",
            coordinator.update(1, snapshot(13.0), listOf(rangeValue, highQuiz)).primary?.id
        )
    }

    @Test
    fun `clearing debug immediately restores scheduled primary and debug expires independently`() {
        val scheduled = emotionEvent("scheduled", episode = 1, timeSec = 10.0)
        val debug = valueEvent(id = "debug", durationSec = 1.0)
        coordinator.update(1, snapshot(10.0), listOf(scheduled), debugEvent = debug)

        assertEquals(
            "scheduled",
            coordinator.update(1, snapshot(10.1), listOf(scheduled), debugEvent = null).primary?.id
        )

        coordinator.update(1, snapshot(10.2), listOf(scheduled), debugEvent = debug)
        nowMs = 2_100L
        assertEquals(
            "scheduled",
            coordinator.update(1, snapshot(10.3), listOf(scheduled), debugEvent = debug).primary?.id
        )
    }

    @Test
    fun `emotion broadcast waits for primary dismissal and lasts three seconds`() {
        val event = emotionEvent(
            id = "delayed",
            episode = 1,
            timeSec = 10.0
        )
        coordinator.update(1, snapshot(10.0), listOf(event))
        coordinator.onEmotionClick(event)
        assertNull(coordinator.state.broadcast)

        nowMs = 3_999L
        assertNull(coordinator.update(1, snapshot(10.5), listOf(event)).broadcast)
        nowMs = 4_000L
        val shown = coordinator.update(1, snapshot(10.6), listOf(event))
        assertNull(shown.primary)
        assertTrue(shown.broadcast != null)

        nowMs = 6_999L
        assertTrue(coordinator.update(1, snapshot(10.7), listOf(event)).broadcast != null)
        nowMs = 7_000L
        assertNull(coordinator.update(1, snapshot(10.8), listOf(event)).broadcast)
    }

    @Test
    fun `dismissing an emotion primary reveals pending broadcast immediately`() {
        val event = emotionEvent(
            id = "dismissed_emotion",
            episode = 1,
            timeSec = 10.0
        )
        coordinator.update(1, snapshot(10.0), listOf(event))
        coordinator.onEmotionClick(event)

        coordinator.dismissPrimary()

        assertNull(coordinator.state.primary)
        assertEquals("全站热度正在上升", coordinator.state.broadcast?.text)
    }

    @Test
    fun `next episode action keeps submitted state visible for at least 800ms`() {
        val payload = quizEvent("quiz", "quiz", afterSubmitAction = "nextEpisode").payload as QuizPayload

        assertTrue(nextEpisodeDelayMs(payload) >= 800L)
    }

    @Test
    fun `multiple emotion clicks keep one pending broadcast until primary disappears`() {
        val emotion = emotionEvent("emotion", episode = 1, timeSec = 10.0)
        coordinator.update(1, snapshot(10.0), listOf(emotion))

        nowMs = 1_100L
        coordinator.onEmotionClick(emotion)
        assertNull(coordinator.state.broadcast)
        nowMs = 1_300L
        coordinator.onEmotionClick(emotion)
        assertNull(coordinator.state.broadcast)

        nowMs = 4_299L
        assertNull(coordinator.update(1, snapshot(10.5), listOf(emotion)).broadcast)

        nowMs = 4_300L
        val firstId = coordinator.update(1, snapshot(10.6), listOf(emotion)).broadcast!!.instanceId
        nowMs = 4_600L
        val secondId = coordinator.update(1, snapshot(10.7), listOf(emotion)).broadcast!!.instanceId

        assertEquals(firstId, secondId)
    }

    @Test
    fun `quiz result outcome is read once when queued`() {
        records.saveQuizAnswer(QuizAnswerRecord("quiz_1", "yes", "会", 1, 10L))
        val result = resultEvent("result", "quiz_1", 20.0)

        val first = coordinator.update(1, snapshot(20.0), listOf(result))
        repeat(5) {
            coordinator.update(1, snapshot(20.1 + it * 0.1), listOf(result))
        }

        assertEquals(1, records.quizReadCount)
        assertEquals(QuizOutcome.CORRECT, first.results.single().result.outcome)
    }

    @Test
    fun `coordinator key isolates episodes while retaining records identity`() {
        val first = InteractionCoordinatorKey(episodeNumber = 1, records = records)
        val second = InteractionCoordinatorKey(episodeNumber = 2, records = records)

        assertFalse(first == second)
        assertTrue(first.records === second.records)
    }

    private fun snapshot(
        positionSec: Double,
        isSeeking: Boolean = false,
        isPlaying: Boolean = true,
        durationSec: Double = 300.0,
        isEnded: Boolean = false
    ) = PlayerSnapshot(
        positionMs = (positionSec * 1_000).toLong(),
        durationMs = (durationSec * 1_000).toLong(),
        isPlaying = isPlaying,
        isSeeking = isSeeking,
        isEnded = isEnded
    )

    private fun emotionEvent(
        id: String,
        episode: Int,
        timeSec: Double,
        showBroadcastAfterSec: Double? = null
    ) = InteractionEvent(
        id = id,
        episodeNumber = episode,
        trigger = InteractionTrigger.Fixed(timeSec),
        type = InteractionType.EMOTION,
        title = "爽",
        position = InteractionPosition.LEFT_BOTTOM,
        payload = EmotionPayload(
            emotionKey = "爽",
            label = "爽",
            particles = listOf("爽", "太爽了"),
            broadcast = "全站热度正在上升",
            showBroadcastAfterSec = showBroadcastAfterSec
        )
    )

    private fun valueEvent(
        id: String = "value",
        initial: Int = 0,
        max: Int = 100,
        step: Int = 20,
        persistKey: String? = null,
        trigger: InteractionTrigger = InteractionTrigger.Fixed(10.0),
        durationSec: Double = 4.0
    ) = InteractionEvent(
        id = id,
        episodeNumber = 1,
        trigger = trigger,
        type = InteractionType.VALUE_BOOST,
        title = "隐忍值",
        position = InteractionPosition.BOTTOM_CENTER,
        displayDurationSec = durationSec,
        payload = ValueBoostPayload(
            valueKey = "forbearance",
            label = "隐忍值",
            initialValue = initial,
            maxValue = max,
            step = step,
            clickText = "蓄力",
            maxText = "隐忍已满",
            theme = "forbearance",
            persistKey = persistKey
        )
    )

    private fun quizEvent(
        id: String,
        quizId: String,
        afterSubmitAction: String? = null,
        trigger: InteractionTrigger = InteractionTrigger.Range(10.0, 20.0),
        durationSec: Double = 4.0
    ) = InteractionEvent(
        id = id,
        episodeNumber = 1,
        trigger = trigger,
        type = InteractionType.QUIZ,
        title = "竞猜",
        position = InteractionPosition.BOTTOM_CENTER,
        displayDurationSec = durationSec,
        payload = QuizPayload(
            quizId = quizId,
            question = "苏羽会出手吗？",
            options = listOf(
                QuizOption("yes", "会"),
                QuizOption("no", "不会")
            ),
            participants = "8.7万人",
            afterSubmitAction = afterSubmitAction
        )
    )

    private fun ratingEvent(
        id: String,
        ratingId: String
    ) = InteractionEvent(
        id = id,
        episodeNumber = 1,
        trigger = InteractionTrigger.Fixed(10.0),
        type = InteractionType.RATING,
        title = "评分",
        position = InteractionPosition.BOTTOM_CENTER,
        payload = RatingPayload(
            ratingId = ratingId,
            question = "给这一幕打几分？",
            min = 1f,
            max = 10f,
            step = 0.5f,
            defaultValue = 8f,
            maxLabel = "10分满分",
            submitText = "确定",
            resultText = "全站平均分 8.9"
        )
    )

    private fun resultEvent(
        id: String,
        quizId: String,
        timeSec: Double
    ) = InteractionEvent(
        id = id,
        episodeNumber = 1,
        trigger = InteractionTrigger.Fixed(timeSec),
        type = InteractionType.QUIZ_RESULT,
        title = "竞猜结果",
        position = InteractionPosition.TOP_CENTER,
        displayDurationSec = 3.0,
        payload = QuizResultPayload(
            quizId = quizId,
            correctOptionId = "yes",
            correctText = "会",
            successText = "恭喜答对",
            compareText = "超过78%的观众",
            rewardText = "获得竞猜币一枚"
        )
    )

    private fun highlightEvent(clipEnd: ClipEnd) = InteractionEvent(
        id = "highlight_event",
        episodeNumber = 19,
        trigger = InteractionTrigger.Fixed(10.0),
        type = InteractionType.HIGHLIGHT_COLLECT,
        title = "收藏名场面",
        position = InteractionPosition.LEFT_BOTTOM,
        payload = HighlightPayload(
            highlightId = "highlight_1",
            title = "结尾名场面",
            clipStartSec = if (clipEnd is ClipEnd.Fixed) {
                minOf(189.0, clipEnd.seconds)
            } else {
                189.0
            },
            clipEnd = clipEnd,
            coverTimeSec = null,
            buttonText = "收藏名场面",
            successText = "名场面已保存",
            detailText = "片段已保存",
            actionText = "去我的高光查看"
        )
    )

    private class FakeInteractionRecords : InteractionRecords {
        private val quizzes = linkedMapOf<String, QuizAnswerRecord>()
        private val ratings = linkedMapOf<String, RatingRecord>()
        private val highlightRecords = linkedMapOf<String, HighlightRecord>()
        private val values = linkedMapOf<String, Int>()
        var quizReadCount: Int = 0
            private set

        override fun quizAnswer(quizId: String): QuizAnswerRecord? {
            quizReadCount += 1
            return quizzes[quizId]
        }

        override fun saveQuizAnswer(record: QuizAnswerRecord): Boolean {
            quizzes[record.quizId] = record
            return true
        }

        override fun rating(ratingId: String) = ratings[ratingId]

        override fun saveRating(record: RatingRecord): Boolean {
            ratings[record.ratingId] = record
            return true
        }

        override fun highlights(): List<HighlightRecord> = highlightRecords.values.toList()

        override fun saveHighlight(record: HighlightRecord): Boolean {
            highlightRecords[record.highlightId] = record
            return true
        }

        override fun persistentValue(key: String) = values[key]

        override fun savePersistentValue(key: String, value: Int): Boolean {
            values[key] = value
            return true
        }

        override fun clearAll() {
            quizzes.clear()
            ratings.clear()
            highlightRecords.clear()
            values.clear()
        }
    }
}
