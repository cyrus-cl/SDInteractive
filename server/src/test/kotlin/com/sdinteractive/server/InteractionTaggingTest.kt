package com.sdinteractive.server

import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InteractionTaggingTest {
    private val request = InteractionTaggingRequest(
        episodeId = "ep_001",
        asr = listOf(
            AsrSegment(82.3, 88.6, "SPEAKER_01", "这点钱算什么，本公子今日就是要让他们看看。")
        )
    )

    @Test
    fun `model candidates are normalized to supported interaction JSON`() {
        val response = InteractionTaggingEngine().generate(
            request = request,
            modelContent = """
                ```json
                {
                  "candidates": [{
                    "id": "candidate-1",
                    "type": "emotion",
                    "startSec": 86.0,
                    "durationSec": 3.0,
                    "title": "公子大气",
                    "text": "公子大气",
                    "reason": "豪爽台词形成爽点",
                    "confidence": 1.4,
                    "position": "left_bottom",
                    "payload": {"particles":["公子大气"],"broadcast":"观众正在撑场"}
                  }]
                }
                ```
            """.trimIndent(),
            source = "doubao"
        )

        assertEquals("doubao", response.generatedBy)
        assertEquals(1, response.candidates.size)
        val candidate = response.candidates.single()
        assertEquals("emotion", candidate.type)
        assertEquals(1.0, candidate.confidence)
        assertTrue(candidate.supported)
        assertFalse(candidate.approved)
        assertEquals(86_000L, response.manifestPreview.single().startMs)
    }

    @Test
    fun `unknown model type is downgraded to none`() {
        val response = InteractionTaggingEngine().generate(
            request = request,
            modelContent = """
                {"candidates":[{
                  "type":"hologram",
                  "startSec":86,
                  "durationSec":3,
                  "title":"未知组件",
                  "text":"未知组件",
                  "reason":"模型发明了组件",
                  "confidence":0.8,
                  "position":"center",
                  "payload":{}
                }]}
            """.trimIndent(),
            source = "doubao"
        )

        val candidate = response.candidates.single()
        assertEquals("none", candidate.type)
        assertFalse(candidate.supported)
    }

    @Test
    fun `post processing keeps at most two candidates per thirty second window`() {
        val candidates = (1..4).joinToString(",") { index ->
            """
                {
                  "type":"emotion",
                  "startSec":${80 + index},
                  "durationSec":3,
                  "title":"互动$index",
                  "text":"互动$index",
                  "reason":"测试",
                  "confidence":${0.9 - index * 0.05},
                  "position":"left_bottom",
                  "payload":{}
                }
            """.trimIndent()
        }

        val response = InteractionTaggingEngine().generate(
            request = request,
            modelContent = """{"candidates":[$candidates]}""",
            source = "doubao"
        )

        assertEquals(2, response.candidates.size)
    }

    @Test
    fun `heuristic fallback emits standard JSON for a clear emotional beat`() {
        val response = InteractionTaggingEngine().generate(
            request = request,
            modelContent = null,
            source = "heuristic"
        )

        assertEquals("heuristic", response.generatedBy)
        assertTrue(response.candidates.isNotEmpty())
        assertTrue(
            Json.encodeToString(InteractionTaggingResponse.serializer(), response)
                .contains("\"episodeId\":\"ep_001\"")
        )
    }

    @Test
    fun `service reports a readable warning when ark fails`() = runBlocking {
        val failingClient = object : AiTextClient {
            override suspend fun complete(
                systemPrompt: String,
                userPrompt: String,
                imageBase64: String?,
                imageMimeType: String?,
                temperature: Double,
                maxTokens: Int
            ): String = error("unauthorized")
        }

        val response = InteractionTaggingService(failingClient).generate(request)

        assertEquals("heuristic", response.generatedBy)
        assertTrue(response.warnings.any { it.contains("豆包") && it.contains("回退") })
    }

    @Test
    fun `tagging prompt uses the authoritative character catalog`() {
        val prompt = buildTaggingPrompt(request)

        assertFalse(prompt.contains("吕有为"))
        assertTrue(prompt.contains("吕甄"))
        assertTrue(prompt.contains("蛮夷三皇子"))
        assertTrue(prompt.contains("柳如烟"))
    }
}
