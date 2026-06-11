package com.sdinteractive.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonInsightVisionTest {
    @Test
    fun `ark user content includes a captured frame when present`() {
        val request = AiPersonInsightRequest(
            episodeNumber = 1,
            positionSec = 12.0,
            characterName = "Su Yu",
            identity = "hidden lead",
            sceneHint = "frame test",
            frameImageBase64 = "abc123",
            frameMimeType = "image/jpeg"
        )

        val content = arkUserMessageContentJson(
            prompt = "look at this scene",
            request = request
        )

        assertTrue(content.contains("\"type\":\"image_url\""))
        assertTrue(content.contains("data:image/jpeg;base64,abc123"))
    }

    @Test
    fun `ark user content remains text only without a captured frame`() {
        val request = AiPersonInsightRequest(
            episodeNumber = 1,
            positionSec = 12.0,
            characterName = "Su Yu",
            identity = "hidden lead"
        )

        val content = arkUserMessageContentJson(
            prompt = "text only",
            request = request
        )

        assertFalse(content.contains("image_url"))
        assertTrue(content.contains("\"text only\""))
    }
}
