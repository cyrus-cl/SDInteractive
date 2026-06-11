package com.sdinteractive.app.interactions

import com.sdinteractive.app.interactions.data.CharacterData
import com.sdinteractive.app.interactions.data.InteractionEvents
import com.sdinteractive.app.interactions.engine.InteractionScheduler
import com.sdinteractive.app.interactions.model.BroadcastPayload
import com.sdinteractive.app.interactions.model.ClipEnd
import com.sdinteractive.app.interactions.model.EmotionPayload
import com.sdinteractive.app.interactions.model.HighlightPayload
import com.sdinteractive.app.interactions.model.InteractionEvent
import com.sdinteractive.app.interactions.model.InteractionPayload
import com.sdinteractive.app.interactions.model.InteractionPosition
import com.sdinteractive.app.interactions.model.InteractionTrigger
import com.sdinteractive.app.interactions.model.InteractionType
import com.sdinteractive.app.interactions.model.KnowledgePayload
import com.sdinteractive.app.interactions.model.PersonDetectPayload
import com.sdinteractive.app.interactions.model.QuizOption
import com.sdinteractive.app.interactions.model.QuizLayout
import com.sdinteractive.app.interactions.model.QuizPayload
import com.sdinteractive.app.interactions.model.QuizResultPayload
import com.sdinteractive.app.interactions.model.RatingPayload
import com.sdinteractive.app.interactions.model.RelationGraphPayload
import com.sdinteractive.app.interactions.model.TriggerSession
import com.sdinteractive.app.interactions.model.ValueBoostPayload
import com.sdinteractive.app.interactions.model.WarningPayload
import com.sdinteractive.app.player.PlayerSnapshot
import com.sdinteractive.app.player.clampSeekPositionMs
import com.sdinteractive.app.player.resolvePlayerDurationMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionSchedulerTest {
    private val scheduler = InteractionScheduler(hitToleranceSec = 0.6)
    private val measuredDurationSecByEpisode = mapOf(
        1 to 308.993,
        2 to 75.478,
        3 to 183.254,
        4 to 183.809,
        5 to 108.609,
        6 to 76.929,
        7 to 348.203,
        8 to 209.302,
        9 to 219.798,
        10 to 99.627,
        11 to 213.249,
        12 to 182.358,
        13 to 159.360,
        14 to 64.640,
        15 to 187.371,
        16 to 187.670,
        17 to 168.726,
        18 to 161.963,
        19 to 233.600,
        20 to 59.969,
        21 to 89.878,
        22 to 60.502,
        23 to 63.958,
        24 to 115.926
    )

    @Test
    fun `emotion payload requires particles`() {
        assertThrows(IllegalArgumentException::class.java) {
            EmotionPayload("shuang", "爽", emptyList(), "热度上升")
        }
    }

    @Test
    fun `value payload validates range and step`() {
        assertThrows(IllegalArgumentException::class.java) {
            ValueBoostPayload("anger", "怒气", 101, 100, 1, "加", "满", "anger")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ValueBoostPayload("anger", "怒气", 0, 100, 0, "加", "满", "anger")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ValueBoostPayload("anger", "怒气", -1, 100, 1, "加", "满", "anger")
        }
    }

    @Test
    fun `quiz payload requires two unique options`() {
        assertThrows(IllegalArgumentException::class.java) {
            QuizPayload("q", "问题", listOf(QuizOption("a", "A")), "1人")
        }
        assertThrows(IllegalArgumentException::class.java) {
            QuizPayload(
                "q",
                "问题",
                listOf(QuizOption("a", "A"), QuizOption("a", "B")),
                "1人"
            )
        }
    }

    @Test
    fun `rating payload validates bounds step and default`() {
        assertThrows(IllegalArgumentException::class.java) {
            RatingPayload("r", "评分", 10f, 1f, 1f, 5f, "满分", "提交", "结果")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RatingPayload("r", "评分", 1f, 10f, 0f, 5f, "满分", "提交", "结果")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RatingPayload("r", "评分", 1f, 10f, 1f, 11f, "满分", "提交", "结果")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RatingPayload("r", "评分", 1f, 10f, 20f, 5f, "满分", "提交", "结果")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RatingPayload("r", "评分", 1f, 10f, 1f, 5.5f, "满分", "提交", "结果")
        }
    }

    @Test
    fun `warning payload requires countdown`() {
        assertThrows(IllegalArgumentException::class.java) {
            WarningPayload("高能预警", emptyList(), "danger")
        }
    }

    @Test
    fun `highlight payload validates clip boundaries`() {
        assertThrows(IllegalArgumentException::class.java) {
            HighlightPayload("h", "高光", -1.0, ClipEnd.Fixed(10.0), null, "收藏", "成功", "详情", "查看")
        }
        assertThrows(IllegalArgumentException::class.java) {
            HighlightPayload("h", "高光", 10.0, ClipEnd.Fixed(9.0), null, "收藏", "成功", "详情", "查看")
        }
    }

    @Test
    fun `player snapshot exposes seconds and clamps unknown duration`() {
        val snapshot = PlayerSnapshot(positionMs = 86_125, durationMs = -1)

        assertEquals(86.125, snapshot.positionSec, 0.0)
        assertEquals(0.0, snapshot.durationSec, 0.0)
    }

    @Test
    fun `player duration uses API metadata until Media3 duration is known`() {
        assertEquals(
            75_478L,
            resolvePlayerDurationMs(
                mediaDurationMs = 0L,
                expectedDurationMs = 75_478L
            )
        )
        assertEquals(
            75_500L,
            resolvePlayerDurationMs(
                mediaDurationMs = 75_500L,
                expectedDurationMs = 75_478L
            )
        )
        assertEquals(
            0L,
            resolvePlayerDurationMs(
                mediaDurationMs = -1L,
                expectedDurationMs = -1L
            )
        )
    }

    @Test
    fun `seek target clamps to best known duration`() {
        assertEquals(0L, clampSeekPositionMs(positionMs = -500L, durationMs = 75_478L))
        assertEquals(40_000L, clampSeekPositionMs(positionMs = 40_000L, durationMs = 75_478L))
        assertEquals(75_478L, clampSeekPositionMs(positionMs = 90_000L, durationMs = 75_478L))
        assertEquals(90_000L, clampSeekPositionMs(positionMs = 90_000L, durationMs = 0L))
    }

    @Test
    fun `fixed event hits inside tolerance and records trigger time`() {
        val event = emotionEvent(id = "ep1_gongzi", timeSec = 86.0)

        val result = scheduler.evaluate(
            events = listOf(event),
            previous = snapshot(positionSec = 85.8),
            current = snapshot(positionSec = 86.1),
            session = TriggerSession()
        )

        assertEquals("ep1_gongzi", result.primary?.id)
        assertEquals(setOf("ep1_gongzi"), result.triggeredIds)
        assertEquals(86.1, result.session.recordFor("ep1_gongzi")?.triggeredAtSec ?: -1.0, 0.0)
    }

    @Test
    fun `fixed event triggers when normal playback crosses timestamp`() {
        val result = scheduler.evaluate(
            events = listOf(emotionEvent(id = "crossed", timeSec = 20.0)),
            previous = snapshot(positionSec = 19.2),
            current = snapshot(positionSec = 20.9),
            session = TriggerSession()
        )

        assertEquals("crossed", result.primary?.id)
        assertEquals(setOf("crossed"), result.triggeredIds)
        assertEquals(20.9, result.session.recordFor("crossed")?.triggeredAtSec ?: -1.0, 0.0)
    }

    @Test
    fun `fixed event outside tolerance does not hit`() {
        val result = scheduler.evaluate(
            events = listOf(emotionEvent(id = "fixed", timeSec = 20.0)),
            previous = snapshot(positionSec = 18.0),
            current = snapshot(positionSec = 19.39),
            session = TriggerSession()
        )

        assertNull(result.primary)
        assertTrue(result.triggeredIds.isEmpty())
    }

    @Test
    fun `range trigger includes both boundaries`() {
        val event = emotionEvent(
            id = "range",
            trigger = InteractionTrigger.Range(startSec = 10.0, endSec = 20.0)
        )

        val atStart = scheduler.evaluate(
            listOf(event),
            snapshot(positionSec = 9.9),
            snapshot(positionSec = 10.0),
            TriggerSession()
        )
        val atEnd = scheduler.evaluate(
            listOf(event),
            snapshot(positionSec = 19.9),
            snapshot(positionSec = 20.0),
            TriggerSession()
        )

        assertEquals("range", atStart.primary?.id)
        assertEquals("range", atEnd.primary?.id)
    }

    @Test
    fun `episode ending requires known duration and matches remaining time`() {
        val event = emotionEvent(
            id = "ending",
            trigger = InteractionTrigger.EpisodeEnding(remainingSec = 8.0)
        )

        val unknownDuration = scheduler.evaluate(
            listOf(event),
            snapshot(positionSec = 80.0, durationSec = 0.0),
            snapshot(positionSec = 92.0, durationSec = 0.0),
            TriggerSession()
        )
        val knownDuration = scheduler.evaluate(
            listOf(event),
            snapshot(positionSec = 91.0, durationSec = 100.0),
            snapshot(positionSec = 92.0, durationSec = 100.0),
            TriggerSession()
        )

        assertNull(unknownDuration.primary)
        assertEquals("ending", knownDuration.primary?.id)
    }

    @Test
    fun `seek does not backfill crossed events`() {
        val result = scheduler.evaluate(
            events = listOf(
                emotionEvent(id = "a", timeSec = 20.0),
                emotionEvent(id = "b", timeSec = 40.0)
            ),
            previous = snapshot(positionSec = 10.0),
            current = snapshot(positionSec = 60.0, isSeeking = true),
            session = TriggerSession()
        )

        assertNull(result.primary)
        assertTrue(result.triggeredIds.isEmpty())
        assertTrue(result.session.records.isEmpty())
    }

    @Test
    fun `rewind over three seconds allows event to trigger on next pass`() {
        val event = emotionEvent(id = "replay", timeSec = 20.0)
        val firstPass = scheduler.evaluate(
            listOf(event),
            snapshot(positionSec = 19.7),
            snapshot(positionSec = 20.1),
            TriggerSession()
        )

        val rewind = scheduler.evaluate(
            listOf(event),
            snapshot(positionSec = 25.0),
            snapshot(positionSec = 10.0),
            firstPass.session
        )
        val secondPass = scheduler.evaluate(
            listOf(event),
            snapshot(positionSec = 19.7),
            snapshot(positionSec = 20.0),
            rewind.session
        )

        assertFalse(rewind.session.hasTriggered("replay"))
        assertEquals("replay", secondPass.primary?.id)
    }

    @Test
    fun `backward seek over three seconds releases later events for replay`() {
        val event = emotionEvent(id = "seek_replay", timeSec = 20.0)
        val firstPass = scheduler.evaluate(
            listOf(event),
            snapshot(positionSec = 19.7),
            snapshot(positionSec = 20.0),
            TriggerSession()
        )

        val backwardSeek = scheduler.evaluate(
            listOf(event),
            snapshot(positionSec = 30.0),
            snapshot(positionSec = 10.0, isSeeking = true),
            firstPass.session
        )
        val secondPass = scheduler.evaluate(
            listOf(event),
            snapshot(positionSec = 19.7),
            snapshot(positionSec = 20.0),
            backwardSeek.session
        )

        assertNull(backwardSeek.primary)
        assertTrue(backwardSeek.triggeredIds.isEmpty())
        assertFalse(backwardSeek.session.hasTriggered("seek_replay"))
        assertEquals("seek_replay", secondPass.primary?.id)
    }

    @Test
    fun `primary uses required priority and consumes lower priority matches`() {
        val events = listOf(
            emotionEvent(id = "emotion", timeSec = 30.0),
            emotionEvent(id = "knowledge", timeSec = 30.0, type = InteractionType.KNOWLEDGE),
            emotionEvent(id = "rating", timeSec = 30.0, type = InteractionType.RATING),
            emotionEvent(id = "value", timeSec = 30.0, type = InteractionType.VALUE_BOOST),
            emotionEvent(id = "highlight", timeSec = 30.0, type = InteractionType.HIGHLIGHT_COLLECT),
            emotionEvent(id = "quiz", timeSec = 30.0, type = InteractionType.QUIZ),
            warningEvent(id = "warning", timeSec = 30.0)
        )

        val result = scheduler.evaluate(
            events,
            snapshot(positionSec = 29.8),
            snapshot(positionSec = 30.0),
            TriggerSession()
        )

        assertEquals("warning", result.primary?.id)
        assertEquals(events.map { it.id }.toSet(), result.triggeredIds)
    }

    @Test
    fun `person detect event can enter primary`() {
        val event = emotionEvent(
            id = "person_detect",
            timeSec = 42.0,
            type = InteractionType.PERSON_DETECT
        )

        val result = scheduler.evaluate(
            listOf(event),
            snapshot(positionSec = 41.8),
            snapshot(positionSec = 42.0),
            TriggerSession()
        )

        assertEquals("person_detect", result.primary?.id)
    }

    @Test
    fun `relation graph event can enter primary`() {
        val event = emotionEvent(
            id = "relation_graph",
            timeSec = 42.0,
            type = InteractionType.RELATION_GRAPH
        )

        val result = scheduler.evaluate(
            listOf(event),
            snapshot(positionSec = 41.8),
            snapshot(positionSec = 42.0),
            TriggerSession()
        )

        assertEquals("relation_graph", result.primary?.id)
    }

    @Test
    fun `suppressed range event remains eligible inside its range`() {
        val rangeEvent = emotionEvent(
            id = "range_emotion",
            trigger = InteractionTrigger.Range(startSec = 30.0, endSec = 40.0)
        )
        val warning = warningEvent(id = "warning", timeSec = 30.0)

        val suppressed = scheduler.evaluate(
            listOf(rangeEvent, warning),
            snapshot(positionSec = 29.8),
            snapshot(positionSec = 30.0),
            TriggerSession()
        )
        val recovered = scheduler.evaluate(
            listOf(rangeEvent, warning),
            snapshot(positionSec = 30.0),
            snapshot(positionSec = 31.0),
            suppressed.session
        )

        assertEquals("warning", suppressed.primary?.id)
        assertFalse(suppressed.session.hasTriggered("range_emotion"))
        assertEquals("range_emotion", recovered.primary?.id)
    }

    @Test
    fun `quiz results use separate queue and keep at most two`() {
        val events = listOf(
            quizResultEvent("result_a", 201.0),
            quizResultEvent("result_b", 201.0),
            quizResultEvent("result_c", 201.0)
        )

        val result = scheduler.evaluate(
            events,
            snapshot(positionSec = 200.7),
            snapshot(positionSec = 201.05),
            TriggerSession()
        )

        assertEquals(listOf("result_a", "result_b"), result.results.map { it.id })
        assertNull(result.primary)
        assertEquals(events.map { it.id }.toSet(), result.triggeredIds)
    }

    @Test
    fun `quiz result outranks quiz while remaining in result queue`() {
        val quiz = emotionEvent(id = "quiz", timeSec = 50.0, type = InteractionType.QUIZ)
        val resultEvent = quizResultEvent(id = "result", timeSec = 50.0)

        val result = scheduler.evaluate(
            listOf(quiz, resultEvent),
            snapshot(positionSec = 49.8),
            snapshot(positionSec = 50.0),
            TriggerSession()
        )

        assertEquals("quiz", result.primary?.id)
        assertEquals(listOf("result"), result.results.map { it.id })
    }

    @Test
    fun `broadcast uses dedicated slot alongside primary`() {
        val broadcast = InteractionEvent(
            id = "broadcast",
            episodeNumber = 1,
            trigger = InteractionTrigger.Fixed(70.0),
            type = InteractionType.BROADCAST,
            title = "全服播报",
            position = InteractionPosition.TOP,
            payload = BroadcastPayload(message = "用户触发名场面")
        )

        val result = scheduler.evaluate(
            listOf(emotionEvent("emotion", 70.0), broadcast),
            snapshot(positionSec = 69.7),
            snapshot(positionSec = 70.0),
            TriggerSession()
        )

        assertEquals("emotion", result.primary?.id)
        assertEquals("broadcast", result.broadcast?.id)
    }

    @Test
    fun `episode nineteen emotion keeps three second display duration`() {
        val event = emotionEvent(
            id = "ep19_haha_224",
            timeSec = 224.0,
            displayDurationSec = 3.0,
            episodeNumber = 19
        )

        val result = scheduler.evaluate(
            listOf(event),
            snapshot(positionSec = 223.7),
            snapshot(positionSec = 224.0),
            TriggerSession()
        )

        assertEquals(3.0, result.primary?.displayDurationSec ?: -1.0, 0.0)
        assertEquals(19, result.primary?.episodeNumber)
    }

    @Test
    fun `paused snapshot neither creates nor repeats an interaction`() {
        val event = emotionEvent(id = "pause", timeSec = 12.0)
        val playingResult = scheduler.evaluate(
            listOf(event),
            snapshot(positionSec = 11.8),
            snapshot(positionSec = 12.0),
            TriggerSession()
        )

        val pausedResult = scheduler.evaluate(
            listOf(event),
            snapshot(positionSec = 12.0),
            snapshot(positionSec = 12.0, isPlaying = false),
            playingResult.session
        )
        val freshPausedResult = scheduler.evaluate(
            listOf(event),
            snapshot(positionSec = 11.8),
            snapshot(positionSec = 12.0, isPlaying = false),
            TriggerSession()
        )

        assertNull(pausedResult.primary)
        assertNull(freshPausedResult.primary)
        assertEquals(playingResult.session, pausedResult.session)
    }

    @Test
    fun `interaction event rejects payload that does not match type`() {
        assertThrows(IllegalArgumentException::class.java) {
            InteractionEvent(
                id = "bad_payload",
                episodeNumber = 1,
                trigger = InteractionTrigger.Fixed(1.0),
                type = InteractionType.WARNING,
                title = "bad",
                position = InteractionPosition.CENTER,
                payload = EmotionPayload("bad", "bad", emptyList(), "bad")
            )
        }
    }

    @Test
    fun `fixed trigger rejects non finite and negative time`() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.1).forEach { time ->
            assertThrows(IllegalArgumentException::class.java) {
                InteractionTrigger.Fixed(time)
            }
        }
    }

    @Test
    fun `range trigger rejects non finite negative and reversed bounds`() {
        val invalidBounds = listOf(
            Double.NaN to 1.0,
            0.0 to Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY to 1.0,
            -0.1 to 1.0,
            2.0 to 1.0
        )

        invalidBounds.forEach { (start, end) ->
            assertThrows(IllegalArgumentException::class.java) {
                InteractionTrigger.Range(start, end)
            }
        }
    }

    @Test
    fun `episode ending requires positive finite remaining time`() {
        listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            -1.0,
            0.0
        ).forEach { remaining ->
            assertThrows(IllegalArgumentException::class.java) {
                InteractionTrigger.EpisodeEnding(remaining)
            }
        }
    }

    @Test
    fun `interaction event requires positive finite display duration`() {
        listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            -1.0,
            0.0
        ).forEach { duration ->
            assertThrows(IllegalArgumentException::class.java) {
                emotionEvent(id = "bad_duration_$duration", displayDurationSec = duration)
            }
        }
    }

    @Test
    fun `interaction catalog contains exactly thirty nine uniquely identified events`() {
        assertEquals(39, InteractionEvents.all.size)
        assertEquals(39, InteractionEvents.all.map { it.id }.distinct().size)
        assertEquals(
            InteractionEvents.all,
            (1..19).flatMap(InteractionEvents::forEpisode)
        )
    }

    @Test
    fun `runtime catalog components never display longer than three seconds`() {
        assertTrue(
            InteractionEvents.all.all { it.displayDurationSec <= 3.0 }
        )
    }

    @Test
    fun `episode one catalog preserves all five authoritative trigger times`() {
        val events = InteractionEvents.forEpisode(1)

        assertEquals(5, events.size)
        assertEquals(InteractionTrigger.Fixed(2.0), events[0].trigger)
        assertEquals(InteractionTrigger.Fixed(86.0), events[1].trigger)
        assertEquals(InteractionTrigger.Fixed(194.0), events[2].trigger)
        assertEquals(InteractionTrigger.Range(265.0, 272.0), events[3].trigger)
        assertEquals(InteractionTrigger.Fixed(303.0), events[4].trigger)
        assertEquals("什么是怡香院？", (events[0].payload as KnowledgePayload).title)
        assertEquals("苏羽会不会出手？", (events[3].payload as QuizPayload).question)
    }

    @Test
    fun `all catalog interactions are reachable within measured episode durations`() {
        InteractionEvents.all.forEach { event ->
            val durationSec = requireNotNull(measuredDurationSecByEpisode[event.episodeNumber])
            when (val trigger = event.trigger) {
                is InteractionTrigger.Fixed -> assertTrue(
                    "${event.id} fixed trigger ${trigger.timeSec}s exceeds episode duration ${durationSec}s",
                    trigger.timeSec < durationSec
                )

                is InteractionTrigger.Range -> {
                    assertTrue(
                        "${event.id} range start ${trigger.startSec}s exceeds episode duration ${durationSec}s",
                        trigger.startSec < durationSec
                    )
                    assertTrue(
                        "${event.id} range end ${trigger.endSec}s exceeds episode duration ${durationSec}s",
                        trigger.endSec <= durationSec
                    )
                }

                is InteractionTrigger.EpisodeEnding -> assertTrue(
                    "${event.id} ending trigger leaves no playable position",
                    trigger.remainingSec <= durationSec
                )
            }

            val highlight = event.payload as? HighlightPayload ?: return@forEach
            assertTrue(
                "${event.id} highlight starts beyond episode duration",
                highlight.clipStartSec < durationSec
            )
            val clipEnd = highlight.clipEnd
            if (clipEnd is ClipEnd.Fixed) {
                assertTrue(
                    "${event.id} highlight ends beyond episode duration",
                    clipEnd.seconds <= durationSec
                )
            }
            highlight.coverTimeSec?.let { coverTimeSec ->
                assertTrue(
                    "${event.id} highlight cover is beyond episode duration",
                    coverTimeSec < durationSec
                )
            }
        }
    }

    @Test
    fun `all catalog interactions enter scheduler results at their playback positions`() {
        InteractionEvents.all.forEach { event ->
            val durationSec = requireNotNull(measuredDurationSecByEpisode[event.episodeNumber])
            val (previousSec, currentSec) = when (val trigger = event.trigger) {
                is InteractionTrigger.Fixed ->
                    (trigger.timeSec - 1.0).coerceAtLeast(0.0) to trigger.timeSec + 0.8

                is InteractionTrigger.Range ->
                    (trigger.startSec - 0.1).coerceAtLeast(0.0) to trigger.startSec

                is InteractionTrigger.EpisodeEnding -> {
                    val triggerPositionSec = durationSec - trigger.remainingSec
                    (triggerPositionSec - 0.1).coerceAtLeast(0.0) to triggerPositionSec
                }
            }

            val result = scheduler.evaluate(
                events = listOf(event),
                previous = snapshot(positionSec = previousSec, durationSec = durationSec),
                current = snapshot(positionSec = currentSec, durationSec = durationSec),
                session = TriggerSession()
            )

            assertTrue(
                "${event.id} did not enter scheduler results at ${currentSec}s",
                event.id in result.triggeredIds
            )
        }
    }

    @Test
    fun `episode ending catalog events use authoritative remaining seconds`() {
        val episodeTwo = InteractionEvents.forEpisode(2).single()
        val episodeNineteen = InteractionEvents.forEpisode(19)
            .single { it.type == InteractionType.HIGHLIGHT_COLLECT }

        assertEquals(InteractionTrigger.EpisodeEnding(8.0), episodeTwo.trigger)
        assertEquals(InteractionTrigger.EpisodeEnding(4.0), episodeNineteen.trigger)
    }

    @Test
    fun `catalog preserves long quiz range and simultaneous result events`() {
        val episodeSevenQuiz = InteractionEvents.forEpisode(7)
            .single { it.type == InteractionType.QUIZ }
        val episodeNineResults = InteractionEvents.forEpisode(9)
            .filter { it.type == InteractionType.QUIZ_RESULT }

        assertEquals(InteractionTrigger.Range(190.0, 293.0), episodeSevenQuiz.trigger)
        assertEquals(2, episodeNineResults.size)
        assertTrue(episodeNineResults.all { it.trigger == InteractionTrigger.Fixed(201.0) })
        assertEquals(
            setOf("ep8_q1_surrender", "ep9_q1_who_win"),
            episodeNineResults.map { (it.payload as QuizResultPayload).quizId }.toSet()
        )
    }

    @Test
    fun `episode nineteen laughter keeps exact three second duration`() {
        val event = InteractionEvents.forEpisode(19)
            .single { it.type == InteractionType.EMOTION }

        assertEquals(InteractionTrigger.Fixed(224.0), event.trigger)
        assertEquals(3.0, event.displayDurationSec, 0.0)
        assertEquals("哈哈哈哈", (event.payload as EmotionPayload).label)
    }

    @Test
    fun `episode nineteen ending highlight keeps exact raw payload shape`() {
        val event = InteractionEvents.forEpisode(19)
            .single { it.type == InteractionType.HIGHLIGHT_COLLECT }
        val payload = event.payload as HighlightPayload

        assertEquals(ClipEnd.EpisodeEnd, payload.clipEnd)
        assertEquals(4.0, payload.triggerWhenRemainingSec ?: -1.0, 0.0)
        assertNull(payload.coverTimeSec)
    }

    @Test
    fun `forbearance values continue across episodes with shared persistence key`() {
        val episodeEleven = InteractionEvents.forEpisode(11)
            .single { it.type == InteractionType.VALUE_BOOST }
        val episodeTwelve = InteractionEvents.forEpisode(12)
            .single { it.type == InteractionType.VALUE_BOOST }
        val first = episodeEleven.payload as ValueBoostPayload
        val second = episodeTwelve.payload as ValueBoostPayload

        assertEquals(listOf(60, 80, 5), listOf(first.initialValue, first.maxValue, first.step))
        assertEquals(listOf(80, 90, 2), listOf(second.initialValue, second.maxValue, second.step))
        assertEquals("suyu_forbearance", first.persistKey)
        assertEquals("suyu_forbearance", second.persistKey)
    }

    @Test
    fun `catalog exposes all ui fields through typed payloads`() {
        val quiz = InteractionEvents.forEpisode(1).single { it.type == InteractionType.QUIZ }
        val value = InteractionEvents.forEpisode(1).single { it.type == InteractionType.VALUE_BOOST }
        val highlight = InteractionEvents.forEpisode(18).single()
        val quizPayload = quiz.payload as QuizPayload
        val valuePayload = value.payload as ValueBoostPayload
        val highlightPayload = highlight.payload as HighlightPayload

        assertEquals("8.7万人", quizPayload.participants)
        assertEquals(303.0, quizPayload.resultTimeSec ?: -1.0, 0.0)
        assertEquals("连点助燃", valuePayload.clickText)
        assertEquals("anger", valuePayload.theme)
        assertEquals("名场面已保存", highlightPayload.successText)
        assertEquals("去我的高光查看", highlightPayload.actionText)
    }

    @Test
    fun `all thirty nine catalog events match authoritative descriptors`() {
        val expected = listOf(
            "ep1_knowledge_yixiangyuan|1|F:2.0|KNOWLEDGE|一键查看古代怡香院背景知识|什么是怡香院？;在古装短剧语境中，怡香院常被用作人物社交、冲突爆发和身份误会的场景。本段用于帮助观众快速理解剧情背景。;古代场所,剧情背景,人物动机",
            "ep1_gongzi_86|1|F:86.0|EMOTION|公子大气|gongzi;公子大气;公子大气,太阔气了,有排面;已有8.2万人为公子撑场;null",
            "ep1_anger_194|1|F:194.0|VALUE_BOOST|皇帝愤怒值|anger;皇帝愤怒值;0;100;20;连点助燃;怒气爆表;anger;null",
            "ep1_quiz_suyu_action|1|R:265.0-272.0|QUIZ|竞猜一下|ep1_q1_suyu_action;苏羽会不会出手？;yes=会,no=不会;8.7万人;303.0;null;null;STANDARD",
            "ep1_quiz_result_suyu_action|1|F:303.0|QUIZ_RESULT|竞猜结果已揭晓|ep1_q1_suyu_action;yes;会;恭喜你答对竞猜;超过78%的观众;获得竞猜币一枚",
            "ep2_quiz_qinwu_test|2|E:8.0|QUIZ|竞猜一下|ep2_q1_qinwu_test;秦武会不会测试苏羽？;yes=会,no=不会;6.3万人;177.0;4;nextEpisode;STANDARD",
            "ep3_laodie_15|3|F:15.0|EMOTION|老爹你糊涂啊|laodie;老爹你糊涂啊;糊涂啊,别误会他,老爹醒醒;全站已有6.6万人发出同款吐槽;null",
            "ep3_rating_lvzhen_75|3|F:75.0|RATING|给吕甄的演技打分|ep3_lvzhen_acting;给吕甄的演技打几分？;1.0;10.0;0.5;8.0;10分满分;确定;全站平均分 8.9",
            "ep3_heartache_94|3|F:94.0|EMOTION|心疼男主|heartache;心疼苏羽;心疼,抱抱苏羽,太能忍了;已有12.3万人心疼苏羽;null",
            "ep3_villain_160|3|F:160.0|VALUE_BOOST|吕甄阴险值|villain;吕甄阴险值;0;100;25;阴险加码;阴险拉满;villain;null",
            "ep3_knowledge_chancellor_170|3|F:170.0|KNOWLEDGE|一键了解古代丞相相关知识|丞相是什么官？;丞相是古代朝廷中的重要辅政官员，通常掌管政务、统筹百官。在剧情中，丞相往往代表权力中枢，也容易成为朝堂矛盾的核心。;朝廷权力,辅佐皇帝,剧情矛盾点",
            "ep4_quiz_result_qinwu_test|4|F:177.0|QUIZ_RESULT|竞猜结果已揭晓|ep2_q1_qinwu_test;yes;会;恭喜你答对竞猜;超过72%的观众;获得竞猜币一枚",
            "ep5_warning_90|5|F:90.0|WARNING|高能预警|高能预警;3,2,1;danger",
            "ep6_shuang_119|6|F:59.0|EMOTION|爽|shuang;爽;爽,太爽了,爽到;有20.48万人爽到了！;null",
            "ep7_quiz_join_fight|7|R:190.0-293.0|QUIZ|竞猜一下|ep7_q1_join_fight;苏羽愿不愿意参加比武招亲？;yes=愿意,no=不愿意;10.4万人;293.0;null;null;STANDARD",
            "ep7_quiz_result_join_fight|7|F:293.0|QUIZ_RESULT|竞猜结果已揭晓|ep7_q1_join_fight;no;不愿意参加;恭喜你答对竞猜;超过69%的观众;获得竞猜币一枚",
            "ep8_haha_18|8|F:18.0|EMOTION|哈哈哈哈|haha;哈哈哈哈;哈哈哈,笑死了,群臣也太损了;已有18.6万人笑出声;null",
            "ep8_quiz_surrender|8|R:40.0-48.0|QUIZ|竞猜一下|ep8_q1_surrender;主角会不会投降？;yes=会投降,no=不会投降;9.8万人;201.0;9;null;STANDARD",
            "ep9_quiz_who_win|9|R:8.0-16.0|QUIZ|谁会赢？|ep9_q1_who_win;这一场谁会赢？;suyu=苏羽,wangxuanzhi=王玄志;12.1万人;201.0;null;null;VERSUS",
            "ep9_ren_64|9|F:64.0|EMOTION|忍|ren;忍;忍住,继续忍,还不是时候;已有7.8万人看出他在忍;null",
            "ep9_wzren_81|9|F:81.0|EMOTION|我再忍|wzren;我再忍;我再忍,继续装,还不是时候;已有8.1万人陪苏羽一起忍;null",
            "ep9_haha_98|9|F:98.0|EMOTION|哈哈哈哈哈|haha;哈哈哈哈;哈哈哈,摔得太假了,绷不住了;已有16.2万人笑出声;null",
            "ep9_continue_fake_117|9|F:117.0|EMOTION|继续装|continue_fake;继续装;继续装,演起来了,别露馅;已有9.3万人看穿苏羽在装;null",
            "ep9_haha_162|9|F:162.0|EMOTION|哈哈哈哈哈|haha;哈哈哈哈;哈哈哈,笑死了,太离谱了;已有18.9万人笑出声;null",
            "ep9_quiz_result_surrender|9|F:201.0|QUIZ_RESULT|竞猜结果已揭晓|ep8_q1_surrender;yes;会投降;恭喜你答对竞猜;超过64%的观众;获得竞猜币一枚",
            "ep9_quiz_result_who_win|9|F:201.0|QUIZ_RESULT|竞猜结果已揭晓|ep9_q1_who_win;wangxuanzhi;王玄志赢;恭喜你答对竞猜;超过58%的观众;获得竞猜币一枚",
            "ep10_ganta_37|10|F:37.0|EMOTION|干他|ganta;干他;干他,上啊,别忍了;全站已有25.6万人想让苏羽出手;null",
            "ep11_suyu_go_91|11|F:91.0|EMOTION|苏羽快上|suyu_go;苏羽快上;快上,别装了,该你出手了;已有14.2万人催苏羽上场;null",
            "ep11_forbearance_128|11|F:128.0|VALUE_BOOST|苏羽隐忍值|forbearance;苏羽隐忍值;60;80;5;继续隐忍;隐忍达到80%;forbearance;suyu_forbearance",
            "ep12_forbearance_72|12|F:72.0|VALUE_BOOST|苏羽隐忍值|forbearance;苏羽隐忍值;80;90;2;继续隐忍;隐忍达到90%;forbearance;suyu_forbearance",
            "ep12_stand_up_171|12|F:171.0|EMOTION|全体起立|stand_up;全体起立;全体起立,起立！,名场面;本站起立人数达到123.4w;null",
            "ep13_shuang_149|13|F:149.0|EMOTION|爽|shuang;爽;爽,太爽了,爽到;有24.9万人爽到了！;null",
            "ep15_fengshen_74|15|F:74.0|EMOTION|封神|fengshen;封神;封神,名场面,太强了;全站已有36.8万人封神认证;null",
            "ep16_stand_up_3|16|F:3.0|EMOTION|全体起立|stand_up;全体起立;全体起立,起立！,名场面;本站起立人数达到123.4w;3.0",
            "ep16_haha_25|16|F:25.0|EMOTION|哈哈哈哈哈|haha;哈哈哈哈;哈哈哈,笑死了,绷不住了;已有19.2万人笑出声;null",
            "ep16_heartache_145|16|F:145.0|EMOTION|心疼苏羽|heartache;心疼苏羽;心疼,抱抱苏羽,太能忍了;已有15.1万人心疼苏羽;null",
            "ep18_highlight_brotherhood|18|F:40.0|HIGHLIGHT_COLLECT|收藏名场面|ep18_highlight_brotherhood;苏羽和皇帝结拜为兄弟;0.0;fixed:44.0;40.0;收藏名场面;名场面已保存;片段：第18集 00:00-00:44;去我的高光查看;null",
            "ep19_highlight_final|19|E:4.0|HIGHLIGHT_COLLECT|收藏名场面|ep19_highlight_final;第十九集结尾名场面;189.0;episodeEnd;null;收藏名场面;名场面已保存;片段：第19集 03:09-本集结尾;去我的高光查看;4.0",
            "ep19_haha_224|19|F:224.0|EMOTION|哈哈哈哈哈|haha;哈哈哈哈;哈哈哈,笑死了,太逗了;已有21.7万人笑出声;null"
        )

        assertEquals(expected, InteractionEvents.all.map(::catalogDescriptor))
    }

    @Test
    fun `all catalog events snapshot position and display duration`() {
        val expected = listOf(
            "ep1_knowledge_yixiangyuan|LEFT_BOTTOM|3.0",
            "ep1_gongzi_86|LEFT_BOTTOM|3.0",
            "ep1_anger_194|LEFT_BOTTOM|3.0",
            "ep1_quiz_suyu_action|BOTTOM_CENTER|3.0",
            "ep1_quiz_result_suyu_action|TOP_CENTER|3.0",
            "ep2_quiz_qinwu_test|BOTTOM_CENTER|3.0",
            "ep3_laodie_15|LEFT_BOTTOM|3.0",
            "ep3_rating_lvzhen_75|BOTTOM_CENTER|3.0",
            "ep3_heartache_94|LEFT_BOTTOM|3.0",
            "ep3_villain_160|LEFT_BOTTOM|3.0",
            "ep3_knowledge_chancellor_170|LEFT_BOTTOM|3.0",
            "ep4_quiz_result_qinwu_test|TOP_CENTER|3.0",
            "ep5_warning_90|CENTER|3.0",
            "ep6_shuang_119|LEFT_BOTTOM|3.0",
            "ep7_quiz_join_fight|BOTTOM_CENTER|3.0",
            "ep7_quiz_result_join_fight|TOP_CENTER|3.0",
            "ep8_haha_18|LEFT_BOTTOM|3.0",
            "ep8_quiz_surrender|BOTTOM_CENTER|3.0",
            "ep9_quiz_who_win|BOTTOM_CENTER|3.0",
            "ep9_ren_64|LEFT_BOTTOM|3.0",
            "ep9_wzren_81|LEFT_BOTTOM|3.0",
            "ep9_haha_98|LEFT_BOTTOM|3.0",
            "ep9_continue_fake_117|LEFT_BOTTOM|3.0",
            "ep9_haha_162|LEFT_BOTTOM|3.0",
            "ep9_quiz_result_surrender|TOP_CENTER|3.0",
            "ep9_quiz_result_who_win|TOP_CENTER|3.0",
            "ep10_ganta_37|LEFT_BOTTOM|3.0",
            "ep11_suyu_go_91|LEFT_BOTTOM|3.0",
            "ep11_forbearance_128|LEFT_BOTTOM|3.0",
            "ep12_forbearance_72|LEFT_BOTTOM|3.0",
            "ep12_stand_up_171|LEFT_BOTTOM|3.0",
            "ep13_shuang_149|LEFT_BOTTOM|3.0",
            "ep15_fengshen_74|LEFT_BOTTOM|3.0",
            "ep16_stand_up_3|LEFT_BOTTOM|3.0",
            "ep16_haha_25|LEFT_BOTTOM|3.0",
            "ep16_heartache_145|LEFT_BOTTOM|3.0",
            "ep18_highlight_brotherhood|LEFT_BOTTOM|3.0",
            "ep19_highlight_final|LEFT_BOTTOM|3.0",
            "ep19_haha_224|LEFT_BOTTOM|3.0"
        )

        assertEquals(
            expected,
            InteractionEvents.all.map { "${it.id}|${it.position}|${it.displayDurationSec}" }
        )
    }

    @Test
    fun `episode ending highlight rejects mismatched payload remaining seconds`() {
        assertThrows(IllegalArgumentException::class.java) {
            InteractionEvent(
                id = "mismatched_ending_highlight",
                episodeNumber = 19,
                trigger = InteractionTrigger.EpisodeEnding(4.0),
                type = InteractionType.HIGHLIGHT_COLLECT,
                title = "收藏名场面",
                position = InteractionPosition.LEFT_BOTTOM,
                payload = HighlightPayload(
                    highlightId = "mismatch",
                    title = "结尾",
                    clipStartSec = 1.0,
                    clipEnd = ClipEnd.EpisodeEnd,
                    coverTimeSec = null,
                    buttonText = "收藏名场面",
                    successText = "名场面已保存",
                    detailText = "片段",
                    actionText = "查看",
                    triggerWhenRemainingSec = 3.0
                )
            )
        }
    }

    @Test
    fun `default detected character is authoritative su yu profile`() {
        val character = CharacterData.detect(episodeNumber = 1, currentTimeSec = 2.0)

        assertEquals("苏羽", character.name)
        assertEquals("镇北侯府二公子", character.identity)
        assertEquals(listOf("隐忍", "腹黑", "隐藏高手"), character.tags)
        assertEquals(
            "表面是京城人人嘲笑的纨绔，实则暗中积蓄力量等待复仇时机。",
            character.description
        )
    }

    @Test
    fun `character catalog contains six exact su yu relations`() {
        assertEquals(6, CharacterData.relations.size)
        assertEquals(
            mapOf(
                "锦宁公主" to "误解 / 情感拉扯",
                "大炎皇帝" to "盟友 / 君臣互信",
                "秦武" to "宫中助力",
                "吕甄" to "仇敌",
                "苏明武" to "兄弟对立",
                "镇北侯" to "父子"
            ),
            CharacterData.relations.associate {
                CharacterData.profile(it.targetCharacterId).name to it.label
            }
        )
    }

    @Test
    fun `character relations match complete authoritative snapshot`() {
        val expected = listOf(
            "suyu|jinning|误解 / 情感拉扯|EMOTIONAL_TENSION|1",
            "suyu|emperor|盟友 / 君臣互信|ALLY|18",
            "suyu|qinwu|宫中助力|SUPPORT|2",
            "suyu|lvzhen|仇敌|ENEMY|3",
            "suyu|sumingwu|兄弟对立|SIBLING_RIVALRY|8",
            "suyu|zhenbeihou|父子|FAMILY|1"
        )

        assertEquals(
            expected,
            CharacterData.relations.map {
                "${it.sourceCharacterId}|${it.targetCharacterId}|${it.label}|${it.kind}|${it.unlockEpisode}"
            }
        )
    }

    @Test
    fun `relations filter by unlock episode`() {
        val firstEpisode = CharacterData.unlockedRelations(episodeNumber = 1)
        val finalEpisode = CharacterData.unlockedRelations(episodeNumber = 19)

        assertTrue(firstEpisode.isNotEmpty())
        assertTrue(firstEpisode.size < finalEpisode.size)
        assertEquals(CharacterData.relations, finalEpisode)
        assertTrue(firstEpisode.all { it.unlockEpisode <= 1 })
    }

    @Test
    fun `episode three later scene can detect lv zhen`() {
        val character = CharacterData.detect(episodeNumber = 3, currentTimeSec = 170.0)

        assertEquals("吕甄", character.name)
        assertNotNull(CharacterData.profile(character.id))
    }

    private fun catalogDescriptor(event: InteractionEvent): String {
        val trigger = when (val value = event.trigger) {
            is InteractionTrigger.Fixed -> "F:${value.timeSec}"
            is InteractionTrigger.Range -> "R:${value.startSec}-${value.endSec}"
            is InteractionTrigger.EpisodeEnding -> "E:${value.remainingSec}"
        }
        val payload = when (val value = event.payload) {
            is EmotionPayload -> listOf(
                value.emotionKey,
                value.label,
                value.particles.joinToString(","),
                value.broadcast,
                value.showBroadcastAfterSec
            ).joinToString(";")

            is ValueBoostPayload -> listOf(
                value.valueKey,
                value.label,
                value.initialValue,
                value.maxValue,
                value.step,
                value.clickText,
                value.maxText,
                value.theme,
                value.persistKey
            ).joinToString(";")

            is QuizPayload -> listOf(
                value.quizId,
                value.question,
                value.options.joinToString(",") { "${it.id}=${it.text}" },
                value.participants,
                value.resultTimeSec,
                value.resultEpisodeId,
                value.afterSubmitAction,
                value.layout
            ).joinToString(";")

            is QuizResultPayload -> listOf(
                value.quizId,
                value.correctOptionId,
                value.correctText,
                value.successText,
                value.compareText,
                value.rewardText
            ).joinToString(";")

            is RatingPayload -> listOf(
                value.ratingId,
                value.question,
                value.min,
                value.max,
                value.step,
                value.defaultValue,
                value.maxLabel,
                value.submitText,
                value.resultText
            ).joinToString(";")

            is KnowledgePayload -> listOf(
                value.title,
                value.summary,
                value.tags.joinToString(",")
            ).joinToString(";")

            is WarningPayload -> listOf(
                value.text,
                value.countdown.joinToString(","),
                value.theme
            ).joinToString(";")

            is HighlightPayload -> listOf(
                value.highlightId,
                value.title,
                value.clipStartSec,
                when (val clipEnd = value.clipEnd) {
                    is ClipEnd.Fixed -> "fixed:${clipEnd.seconds}"
                    ClipEnd.EpisodeEnd -> "episodeEnd"
                },
                value.coverTimeSec,
                value.buttonText,
                value.successText,
                value.detailText,
                value.actionText,
                value.triggerWhenRemainingSec
            ).joinToString(";")

            is BroadcastPayload -> value.message
            is PersonDetectPayload -> "${value.prompt};${value.scanDurationMs}"
            is RelationGraphPayload -> "${value.focusCharacterId};${value.title}"
        }
        return "${event.id}|${event.episodeNumber}|$trigger|${event.type}|${event.title}|$payload"
    }

    private fun snapshot(
        positionSec: Double,
        durationSec: Double = 300.0,
        isPlaying: Boolean = true,
        isSeeking: Boolean = false
    ) = PlayerSnapshot(
        positionMs = (positionSec * 1_000).toLong(),
        durationMs = (durationSec * 1_000).toLong(),
        isPlaying = isPlaying,
        isSeeking = isSeeking
    )

    private fun emotionEvent(
        id: String,
        timeSec: Double = 0.0,
        trigger: InteractionTrigger = InteractionTrigger.Fixed(timeSec),
        type: InteractionType = InteractionType.EMOTION,
        displayDurationSec: Double = 4.0,
        episodeNumber: Int = 1
    ) = InteractionEvent(
        id = id,
        episodeNumber = episodeNumber,
        trigger = trigger,
        type = type,
        title = id,
        position = InteractionPosition.LEFT_BOTTOM,
        displayDurationSec = displayDurationSec,
        payload = payloadFor(type, id)
    )

    private fun payloadFor(type: InteractionType, id: String): InteractionPayload = when (type) {
        InteractionType.EMOTION -> EmotionPayload(
            emotionKey = id,
            label = id,
            particles = listOf(id),
            broadcast = "$id broadcast"
        )

        InteractionType.VALUE_BOOST -> ValueBoostPayload(
            valueKey = id,
            label = id,
            initialValue = 0,
            maxValue = 5,
            step = 1,
            clickText = "点击",
            maxText = "完成",
            theme = "default"
        )
        InteractionType.QUIZ -> QuizPayload(
            quizId = id,
            question = id,
            options = listOf(
                QuizOption("option_a", "选项A"),
                QuizOption("option_b", "选项B")
            ),
            participants = "0人"
        )

        InteractionType.QUIZ_RESULT -> QuizResultPayload(
            quizId = id,
            correctOptionId = "correct",
            correctText = "回答正确",
            successText = "回答正确",
            compareText = "超过0%的观众",
            rewardText = "奖励"
        )

        InteractionType.RATING -> RatingPayload(
            ratingId = id,
            question = id,
            min = 0f,
            max = 10f,
            step = 0.5f,
            defaultValue = 5f,
            maxLabel = "10分",
            submitText = "确定",
            resultText = "已记录"
        )
        InteractionType.KNOWLEDGE -> KnowledgePayload(
            title = id,
            summary = id,
            tags = emptyList()
        )

        InteractionType.PERSON_DETECT -> PersonDetectPayload()
        InteractionType.RELATION_GRAPH -> RelationGraphPayload(focusCharacterId = id)
        InteractionType.WARNING -> WarningPayload(
            text = id,
            countdown = listOf(3, 2, 1),
            theme = "danger"
        )
        InteractionType.HIGHLIGHT_COLLECT -> HighlightPayload(
            highlightId = id,
            title = id,
            clipStartSec = 0.0,
            clipEnd = ClipEnd.Fixed(1.0),
            coverTimeSec = 0.5,
            buttonText = "收藏",
            successText = "已收藏",
            detailText = "片段",
            actionText = "查看"
        )

        InteractionType.BROADCAST -> BroadcastPayload(message = id)
    }

    private fun warningEvent(id: String, timeSec: Double) = InteractionEvent(
        id = id,
        episodeNumber = 1,
        trigger = InteractionTrigger.Fixed(timeSec),
        type = InteractionType.WARNING,
        title = id,
        position = InteractionPosition.CENTER,
        payload = WarningPayload(
            text = id,
            countdown = listOf(3, 2, 1),
            theme = "danger"
        )
    )

    private fun quizResultEvent(id: String, timeSec: Double) = InteractionEvent(
        id = id,
        episodeNumber = 9,
        trigger = InteractionTrigger.Fixed(timeSec),
        type = InteractionType.QUIZ_RESULT,
        title = id,
        position = InteractionPosition.TOP,
        payload = QuizResultPayload(
            quizId = id,
            correctOptionId = "correct",
            correctText = "回答正确",
            successText = "回答正确",
            compareText = "超过0%的观众",
            rewardText = "奖励"
        )
    )
}
