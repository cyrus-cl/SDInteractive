package com.sdinteractive.app

import com.sdinteractive.app.player.PERSON_FRAME_JPEG_QUALITY
import com.sdinteractive.app.player.PERSON_FRAME_MAX_WIDTH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteVideoPolicyTest {
    @Test
    fun `server base url is normalized for retrofit`() {
        assertEquals("http://10.0.2.2:8081/", "http://10.0.2.2:8081".normalizedBaseUrl())
    }

    @Test
    fun `server base url must be http`() {
        assertThrows(IllegalArgumentException::class.java) {
            "file:///android_asset/demo.mp4".normalizedBaseUrl()
        }
    }

    @Test
    fun `next episode is selected by sorted order`() {
        val episodes = listOf(
            EpisodeDto("ep3", "drama", "第3集", 3, 30_000L),
            EpisodeDto("ep1", "drama", "第1集", 1, 30_000L),
            EpisodeDto("ep2", "drama", "第2集", 2, 30_000L)
        )

        assertEquals("ep2", nextEpisodeAfter(episodes, episodes[1])?.episodeId)
        assertEquals("ep3", nextEpisodeAfter(episodes, episodes[2])?.episodeId)
        assertEquals(null, nextEpisodeAfter(episodes, episodes[0]))
    }

    @Test
    fun `person recognition keeps more detail in the single current frame`() {
        assertEquals(768, PERSON_FRAME_MAX_WIDTH)
        assertEquals(82, PERSON_FRAME_JPEG_QUALITY)
    }
}
