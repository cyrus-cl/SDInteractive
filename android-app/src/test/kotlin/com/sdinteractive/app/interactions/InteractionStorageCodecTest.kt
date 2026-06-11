package com.sdinteractive.app.interactions

import com.sdinteractive.app.interactions.model.ClipEnd
import com.sdinteractive.app.interactions.storage.HighlightRecord
import com.sdinteractive.app.interactions.storage.InteractionKeyValueStore
import com.sdinteractive.app.interactions.storage.InteractionStorageCodec
import com.sdinteractive.app.interactions.storage.InteractionStorageKeys
import com.sdinteractive.app.interactions.storage.PersistentValueRecord
import com.sdinteractive.app.interactions.storage.QuizAnswerRecord
import com.sdinteractive.app.interactions.storage.RatingRecord
import com.sdinteractive.app.interactions.storage.SharedPreferencesInteractionStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class InteractionStorageCodecTest {
    @Test
    fun `quiz answer round trips`() {
        val record = QuizAnswerRecord(
            quizId = "quiz_1",
            selectedOptionId = "b",
            selectedText = "苏羽",
            episodeNumber = 1,
            submitTimeMs = 1_717_171_717L
        )

        assertEquals(
            record,
            InteractionStorageCodec.decodeQuizAnswer(
                InteractionStorageCodec.encodeQuizAnswer(record)
            )
        )
    }

    @Test
    fun `rating preserves half point values`() {
        val record = RatingRecord(
            ratingId = "rating_8",
            value = 9.5f,
            episodeNumber = 8,
            submitTimeMs = 2_000L
        )

        assertEquals(
            record,
            InteractionStorageCodec.decodeRating(
                InteractionStorageCodec.encodeRating(record)
            )
        )
    }

    @Test
    fun `persistent value round trips`() {
        val record = PersistentValueRecord(
            key = "endurance",
            value = 73,
            updatedAtMs = 3_000L
        )

        assertEquals(
            record,
            InteractionStorageCodec.decodePersistentValue(
                InteractionStorageCodec.encodePersistentValue(record)
            )
        )
    }

    @Test
    fun `fixed clip end is encoded with explicit type`() {
        val record = highlight(clipEnd = ClipEnd.Fixed(94.5), coverTimeSec = 91.0)
        val json = InteractionStorageCodec.encodeHighlight(record)

        assertEquals(record, InteractionStorageCodec.decodeHighlight(json))
        assertTrue(json.contains("\"type\":\"fixed\""))
    }

    @Test
    fun `episode end is encoded with explicit type`() {
        val record = highlight(clipEnd = ClipEnd.EpisodeEnd, coverTimeSec = null)
        val json = InteractionStorageCodec.encodeHighlight(record)

        assertEquals(record, InteractionStorageCodec.decodeHighlight(json))
        assertTrue(json.contains("\"type\":\"episode_end\""))
    }

    @Test
    fun `highlight list supports empty and populated values`() {
        assertEquals(
            emptyList<HighlightRecord>(),
            InteractionStorageCodec.decodeHighlights(
                InteractionStorageCodec.encodeHighlights(emptyList())
            )
        )

        val records = listOf(
            highlight("h1", ClipEnd.Fixed(20.0), 18.0),
            highlight("h2", ClipEnd.EpisodeEnd, null)
        )
        assertEquals(
            records,
            InteractionStorageCodec.decodeHighlights(
                InteractionStorageCodec.encodeHighlights(records)
            )
        )
    }

    @Test
    fun `damaged or incompatible record json returns null`() {
        assertNull(InteractionStorageCodec.decodeQuizAnswer("{"))
        assertNull(InteractionStorageCodec.decodeRating("""{"schemaVersion":99}"""))
        assertNull(InteractionStorageCodec.decodeHighlight("""{"schemaVersion":1}"""))
        assertNull(InteractionStorageCodec.decodePersistentValue("null"))
    }

    @Test
    fun `damaged or incompatible highlight list returns empty`() {
        assertEquals(emptyList<HighlightRecord>(), InteractionStorageCodec.decodeHighlights("["))
        assertEquals(
            emptyList<HighlightRecord>(),
            InteractionStorageCodec.decodeHighlights("""{"schemaVersion":99,"items":[]}""")
        )
        assertEquals(
            emptyList<HighlightRecord>(),
            InteractionStorageCodec.decodeHighlights(
                """{"schemaVersion":1,"items":[{"highlightId":"broken"}]}"""
            )
        )
    }

    @Test
    fun `highlight list skips damaged items and retains valid items`() {
        val first = highlight("h1", ClipEnd.Fixed(20.0), 18.0)
        val second = highlight("h2", ClipEnd.EpisodeEnd, null)
        val firstJson = InteractionStorageCodec.encodeHighlight(first)
        val secondJson = InteractionStorageCodec.encodeHighlight(second)
        val mixedJson =
            """{"schemaVersion":1,"items":[$firstJson,{"highlightId":"broken"},$secondJson]}"""

        assertEquals(
            listOf(first, second),
            InteractionStorageCodec.decodeHighlights(mixedJson)
        )
    }

    @Test
    fun `merge highlights replaces duplicate in place and appends new records`() {
        val first = highlight("h1", ClipEnd.Fixed(10.0), 8.0, createdAtMs = 1L)
        val retained = highlight("h2", ClipEnd.Fixed(20.0), 18.0, createdAtMs = 2L)
        val replacement = first.copy(title = "更新高光", createdAtMs = 3L)
        val appended = highlight("h3", ClipEnd.EpisodeEnd, null, createdAtMs = 4L)

        assertEquals(
            listOf(replacement, retained, appended),
            InteractionStorageCodec.mergeHighlights(
                existing = listOf(first, retained),
                incoming = listOf(replacement, appended)
            )
        )
    }

    @Test
    fun `merge highlights normalizes existing duplicates using last value at first position`() {
        val firstH1 = highlight("h1", ClipEnd.Fixed(10.0), 8.0, createdAtMs = 1L)
        val h2 = highlight("h2", ClipEnd.Fixed(20.0), 18.0, createdAtMs = 2L)
        val lastH1 = firstH1.copy(title = "last h1", createdAtMs = 3L)
        val replacementH2 = h2.copy(title = "new h2", createdAtMs = 4L)
        val h3 = highlight("h3", ClipEnd.EpisodeEnd, null, createdAtMs = 5L)

        assertEquals(
            listOf(lastH1, replacementH2, h3),
            InteractionStorageCodec.mergeHighlights(
                existing = listOf(firstH1, h2, lastH1),
                incoming = listOf(replacementH2, h3)
            )
        )
    }

    @Test
    fun `clamp value respects inclusive bounds`() {
        assertEquals(0, InteractionStorageCodec.clampValue(-5, 0, 100))
        assertEquals(42, InteractionStorageCodec.clampValue(42, 0, 100))
        assertEquals(100, InteractionStorageCodec.clampValue(130, 0, 100))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `clamp value rejects reversed bounds`() {
        InteractionStorageCodec.clampValue(1, 10, 0)
    }

    @Test
    fun `storage keys use strict prefixes`() {
        assertEquals("quiz_answer_q1", InteractionStorageKeys.quizAnswer("q1"))
        assertEquals("rating_r1", InteractionStorageKeys.rating("r1"))
        assertEquals("user_highlights", InteractionStorageKeys.highlights)
        assertEquals("value_endurance", InteractionStorageKeys.persistentValue("endurance"))
    }

    @Test
    fun `key value storage saves reads and clears every record type`() {
        val store = FakeKeyValueStore()
        val storage = SharedPreferencesInteractionStorage(store)
        val quiz = QuizAnswerRecord("q1", "a", "A", 1, 100L)
        val rating = RatingRecord("r1", 8.5f, 2, 200L)
        val savedHighlight = highlight("h1", ClipEnd.Fixed(20.0), 18.0)

        assertTrue(storage.saveQuizAnswer(quiz))
        assertTrue(storage.saveRating(rating))
        assertTrue(storage.savePersistentValue("endurance", 72))
        assertTrue(storage.saveHighlight(savedHighlight))
        assertEquals(quiz, storage.quizAnswer("q1"))
        assertEquals(rating, storage.rating("r1"))
        assertEquals(72, storage.persistentValue("endurance"))
        assertEquals(listOf(savedHighlight), storage.highlights())

        storage.clearAll()

        assertNull(storage.quizAnswer("q1"))
        assertNull(storage.rating("r1"))
        assertNull(storage.persistentValue("endurance"))
        assertEquals(emptyList<HighlightRecord>(), storage.highlights())
    }

    @Test
    fun `highlight saves share one lock across storage instances`() {
        val store = ControlledKeyValueStore()
        val firstStorage = SharedPreferencesInteractionStorage(store)
        val secondStorage = SharedPreferencesInteractionStorage(store)
        val executor = Executors.newFixedThreadPool(2)

        val first = executor.submit<Boolean> {
            firstStorage.saveHighlight(highlight("h1", ClipEnd.Fixed(10.0), null))
        }
        assertTrue(store.firstHighlightReadEntered.await(5, TimeUnit.SECONDS))
        val second = executor.submit<Boolean> {
            secondStorage.saveHighlight(highlight("h2", ClipEnd.Fixed(20.0), null))
        }

        val secondEnteredBeforeRelease =
            store.secondHighlightReadEntered.await(500, TimeUnit.MILLISECONDS)
        store.releaseFirstHighlightRead.countDown()

        assertTrue(first.get(5, TimeUnit.SECONDS))
        assertTrue(second.get(5, TimeUnit.SECONDS))
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        assertFalse(secondEnteredBeforeRelease)
        assertEquals(setOf("h1", "h2"), firstStorage.highlights().map { it.highlightId }.toSet())
    }

    @Test
    fun `clear all waits for highlight update across storage instances`() {
        val store = ControlledKeyValueStore()
        val firstStorage = SharedPreferencesInteractionStorage(store)
        val secondStorage = SharedPreferencesInteractionStorage(store)
        val executor = Executors.newFixedThreadPool(2)

        val save = executor.submit<Boolean> {
            firstStorage.saveHighlight(highlight("h1", ClipEnd.Fixed(10.0), null))
        }
        assertTrue(store.firstHighlightReadEntered.await(5, TimeUnit.SECONDS))
        val clear = executor.submit<Unit> { secondStorage.clearAll() }

        val clearEnteredBeforeRelease = store.clearEntered.await(500, TimeUnit.MILLISECONDS)
        store.releaseFirstHighlightRead.countDown()

        assertTrue(save.get(5, TimeUnit.SECONDS))
        clear.get(5, TimeUnit.SECONDS)
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        assertFalse(clearEnteredBeforeRelease)
        assertEquals(emptyList<HighlightRecord>(), firstStorage.highlights())
    }

    @Test
    fun `store exceptions return safe results and clear does not throw`() {
        val storage = SharedPreferencesInteractionStorage(ThrowingKeyValueStore())
        val quiz = QuizAnswerRecord("q1", "a", "A", 1, 100L)

        assertFalse(storage.saveQuizAnswer(quiz))
        assertFalse(storage.saveRating(RatingRecord("r1", 8.5f, 1, 100L)))
        assertFalse(storage.saveHighlight(highlight("h1", ClipEnd.EpisodeEnd, null)))
        assertFalse(storage.savePersistentValue("endurance", 10))
        assertNull(storage.quizAnswer("q1"))
        assertNull(storage.rating("r1"))
        assertNull(storage.persistentValue("endurance"))
        assertEquals(emptyList<HighlightRecord>(), storage.highlights())

        storage.clearAll()
    }

    private fun highlight(
        highlightId: String = "highlight_1",
        clipEnd: ClipEnd,
        coverTimeSec: Double?,
        createdAtMs: Long = 5_000L
    ) = HighlightRecord(
        episodeNumber = 18,
        highlightId = highlightId,
        title = "名场面",
        clipStartSec = 86.0,
        clipEnd = clipEnd,
        coverTimeSec = coverTimeSec,
        createdAtMs = createdAtMs
    )

    private class FakeKeyValueStore : InteractionKeyValueStore {
        private val values = ConcurrentHashMap<String, String>()

        override fun getString(key: String): String? = values[key]

        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun clear() {
            values.clear()
        }
    }

    private class ControlledKeyValueStore : InteractionKeyValueStore {
        private val values = ConcurrentHashMap<String, String>()
        private val highlightReadCount = AtomicInteger()
        val firstHighlightReadEntered = CountDownLatch(1)
        val secondHighlightReadEntered = CountDownLatch(1)
        val releaseFirstHighlightRead = CountDownLatch(1)
        val clearEntered = CountDownLatch(1)

        override fun getString(key: String): String? {
            if (key == InteractionStorageKeys.highlights) {
                when (highlightReadCount.incrementAndGet()) {
                    1 -> {
                        firstHighlightReadEntered.countDown()
                        check(releaseFirstHighlightRead.await(5, TimeUnit.SECONDS))
                    }
                    2 -> secondHighlightReadEntered.countDown()
                }
            }
            return values[key]
        }

        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun clear() {
            clearEntered.countDown()
            values.clear()
        }
    }

    private class ThrowingKeyValueStore : InteractionKeyValueStore {
        override fun getString(key: String): String? = error("read failed")

        override fun putString(key: String, value: String): Unit = error("write failed")

        override fun clear(): Unit = error("clear failed")
    }
}
