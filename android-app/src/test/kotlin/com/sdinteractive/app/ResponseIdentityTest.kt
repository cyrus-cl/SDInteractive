package com.sdinteractive.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseIdentityTest {
    @Test
    fun `response applies only when episode and user still match request`() {
        assertTrue(
            shouldApplyResponse(
                currentEpisodeId = "episode-2",
                currentUserId = "user-1",
                requestEpisodeId = "episode-2",
                requestUserId = "user-1"
            )
        )
        assertFalse(
            shouldApplyResponse(
                currentEpisodeId = "episode-3",
                currentUserId = "user-1",
                requestEpisodeId = "episode-2",
                requestUserId = "user-1"
            )
        )
        assertFalse(
            shouldApplyResponse(
                currentEpisodeId = "episode-2",
                currentUserId = "user-2",
                requestEpisodeId = "episode-2",
                requestUserId = "user-1"
            )
        )
        assertFalse(
            shouldApplyResponse(
                currentEpisodeId = null,
                currentUserId = null,
                requestEpisodeId = "episode-2",
                requestUserId = "user-1"
            )
        )
    }

    @Test
    fun `adjacent episode preload includes current neighbors only`() {
        val episodes = (1..5).map {
            EpisodeDto(
                episodeId = "episode-$it",
                dramaId = "drama-1",
                title = "第${it}集",
                sortOrder = it,
                durationMs = 60_000L
            )
        }

        assertEquals(
            listOf("episode-2", "episode-3", "episode-4"),
            adjacentEpisodeIds(episodes, currentIndex = 2)
        )
        assertEquals(
            listOf("episode-1", "episode-2"),
            adjacentEpisodeIds(episodes, currentIndex = 0)
        )
    }

    @Test
    fun `cached play info is immediately reusable during page switch`() {
        val cached = PlayInfoResponse(
            episodeId = "episode-2",
            videoUrl = "https://example.com/2.mp4",
            durationMs = 60_000L,
            format = "mp4"
        )

        assertEquals(
            cached,
            cachedPlayInfo("episode-2", mapOf("episode-2" to cached))
        )
        assertEquals(null, cachedPlayInfo("episode-3", mapOf("episode-2" to cached)))
    }
}
