package com.sdinteractive.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonIdentificationTest {
    @Test
    fun `ark request timeout allows slower vision inference`() {
        assertTrue(ARK_REQUEST_TIMEOUT_SECONDS >= 40L)
    }

    @Test
    fun `catalog contains only the nine authoritative characters`() {
        assertEquals(
            setOf(
                "suyu",
                "jinning",
                "emperor",
                "qinwu",
                "lvzhen",
                "sumingwu",
                "barbarian_prince",
                "zhenbeihou",
                "liuruyan"
            ),
            PersonCatalog.characters.keys
        )
        assertFalse("lvyouwei" in PersonCatalog.characters)
    }

    @Test
    fun `vision result returns every confirmed person in current frame`() {
        val response = PersonIdentificationEngine().identify(
            request = request(episodeNumber = 1),
            modelContent = """
                {
                  "characters": [
                    {
                      "id": "suyu",
                      "confidence": 0.91,
                      "screenPosition": "left",
                      "evidence": "左侧黑衣男性，面部与苏羽一致"
                    },
                    {
                      "id": "qinwu",
                      "confidence": 0.88,
                      "screenPosition": "right",
                      "evidence": "右侧侍卫服饰男性，面部与秦武一致"
                    }
                  ]
                }
            """.trimIndent(),
            source = "doubao"
        )

        assertEquals(listOf("suyu", "qinwu"), response.characters.map { it.character.id })
        assertEquals(listOf("left", "right"), response.characters.map { it.screenPosition })
        assertEquals(listOf(0.91, 0.88), response.characters.map { it.confidence })
        assertTrue(response.characters.all { it.evidence.isNotBlank() })
        assertTrue(response.candidateCharacters.isEmpty())
        assertEquals(1, response.frameCount)
        assertFalse(response.usedFallback)
    }

    @Test
    fun `timeline is only a prior and does not reject another catalog character`() {
        val response = PersonIdentificationEngine().identify(
            request = request(episodeNumber = 3, positionSec = 165.0),
            modelContent = """
                {
                  "characters": [
                    {
                      "id": "barbarian_prince",
                      "confidence": 0.93,
                      "screenPosition": "center",
                      "evidence": "中央异族服饰男性，五官清晰"
                    }
                  ]
                }
            """.trimIndent(),
            source = "doubao"
        )

        assertEquals(listOf("barbarian_prince"), response.characters.map { it.character.id })
        assertFalse(response.usedFallback)
    }

    @Test
    fun `post processing removes invalid low confidence and duplicate people`() {
        val response = PersonIdentificationEngine().identify(
            request = request(episodeNumber = 1),
            modelContent = """
                {
                  "characters": [
                    {
                      "id": "suyu",
                      "confidence": 0.61,
                      "screenPosition": "LEFT",
                      "evidence": "较弱证据"
                    },
                    {
                      "id": "suyu",
                      "confidence": 0.94,
                      "screenPosition": "center",
                      "evidence": "更清晰的正脸证据"
                    },
                    {
                      "id": "unknown_extra",
                      "confidence": 0.99,
                      "screenPosition": "right",
                      "evidence": "未知群众演员"
                    },
                    {
                      "id": "qinwu",
                      "confidence": 0.54,
                      "screenPosition": "right",
                      "evidence": "脸部模糊"
                    }
                  ]
                }
            """.trimIndent(),
            source = "doubao"
        )

        assertEquals(1, response.characters.size)
        assertEquals("suyu", response.characters.single().character.id)
        assertEquals(0.94, response.characters.single().confidence)
        assertEquals("center", response.characters.single().screenPosition)
    }

    @Test
    fun `model failure returns unconfirmed timeline candidates without fake recognition`() {
        val response = PersonIdentificationEngine().identify(
            request = request(episodeNumber = 3, positionSec = 165.0),
            modelContent = null,
            source = "doubao"
        )

        assertTrue(response.characters.isEmpty())
        assertTrue(response.candidateCharacters.isNotEmpty())
        assertTrue(response.candidateCharacters.any { it.id == "lvzhen" })
        assertTrue(response.usedFallback)
        assertEquals("timeline_fallback", response.source)
    }

    @Test
    fun `prompt requires single frame visual evidence and ignores on screen text`() {
        val request = request(episodeNumber = 1)
        val prompt = buildPersonPrompt(
            request = request,
            scene = CharacterTimeline.resolve(request.episodeNumber, request.positionSec)
        )

        assertTrue(prompt.contains("唯一一张当前视频原始帧"))
        assertTrue(prompt.contains("忽略字幕"))
        assertTrue(prompt.contains("所有清晰可见"))
        assertTrue(prompt.contains("likelyCharacterIds"))
        PersonCatalog.characters.keys.forEach { id ->
            assertTrue(prompt.contains("id=$id"), "Prompt is missing $id")
        }
    }

    private fun request(
        episodeNumber: Int,
        positionSec: Double = 12.0
    ) = PersonIdentifyRequest(
        episodeNumber = episodeNumber,
        positionSec = positionSec,
        frameImageBase64 = "abc123",
        frameMimeType = "image/jpeg"
    )
}
