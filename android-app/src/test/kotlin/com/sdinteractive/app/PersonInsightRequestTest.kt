package com.sdinteractive.app

import com.sdinteractive.app.interactions.data.CharacterProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonInsightRequestTest {
    @Test
    fun `player state maps to person identify request without preselected character`() {
        val request = toPersonIdentifyRequest(
            episodeNumber = 7,
            positionSec = 18.4,
            frameImageBase64 = "abc123",
            frameMimeType = "image/jpeg"
        )

        assertEquals(7, request.episodeNumber)
        assertEquals(18.4, request.positionSec, 0.001)
        assertEquals("abc123", request.frameImageBase64)
        assertEquals("image/jpeg", request.frameMimeType)
    }

    @Test
    fun `person identify response maps every confirmed person`() {
        val response = AiPersonIdentifyResponse(
            characters = listOf(
                AiIdentifiedCharacter(
                    character = character("suyu", "苏羽", "镇北侯府二公子"),
                    confidence = 0.93,
                    screenPosition = "left",
                    evidence = "左侧黑衣男性正脸"
                ),
                AiIdentifiedCharacter(
                    character = character("qinwu", "秦武", "大内第一侍卫"),
                    confidence = 0.86,
                    screenPosition = "right",
                    evidence = "右侧侍卫服饰男性"
                )
            ),
            candidateCharacters = emptyList(),
            sceneRole = "秦武试探苏羽真实实力",
            frameCount = 1,
            usedFallback = false,
            source = "doubao"
        )

        val result = response.toPersonIdentification()

        assertEquals(listOf("苏羽", "秦武"), result.characters.map { it.profile.name })
        assertEquals(listOf("left", "right"), result.characters.map { it.screenPosition })
        assertEquals(0.93, result.characters.first().confidence, 0.001)
        assertEquals("右侧侍卫服饰男性", result.characters.last().evidence)
        assertEquals("秦武试探苏羽真实实力", result.sceneRole)
        assertFalse(result.usedFallback)
    }

    @Test
    fun `fallback response maps candidates without fake identified person`() {
        val response = AiPersonIdentifyResponse(
            characters = emptyList(),
            candidateCharacters = listOf(
                character("suyu", "苏羽", "镇北侯府二公子"),
                character("qinwu", "秦武", "大内第一侍卫")
            ),
            sceneRole = "苏羽隐藏实力",
            frameCount = 1,
            usedFallback = true,
            source = "timeline_fallback"
        )

        val result = response.toPersonIdentification()

        assertTrue(result.characters.isEmpty())
        assertEquals(listOf("苏羽", "秦武"), result.candidateProfiles.map { it.name })
        assertTrue(result.usedFallback)
    }

    @Test
    fun `character profile maps to server person insight request`() {
        val profile = CharacterProfile(
            id = "suyu",
            name = "苏羽",
            identity = "隐忍公子",
            description = "被迫藏锋的主角",
            tags = listOf("隐忍", "反转")
        )

        val request = profile.toPersonInsightRequest(
            episodeNumber = 7,
            positionSec = 18.4,
            sceneHint = "殿前对峙"
        )

        assertEquals(7, request.episodeNumber)
        assertEquals(18.4, request.positionSec, 0.001)
        assertEquals("苏羽", request.characterName)
        assertEquals("隐忍公子", request.identity)
        assertEquals("殿前对峙", request.sceneHint)
    }

    @Test
    fun `person insight request can carry a real captured frame`() {
        val profile = CharacterProfile(
            id = "suyu",
            name = "Su Yu",
            identity = "hidden lead",
            description = "frame test",
            tags = listOf("hidden")
        )

        val request = profile.toPersonInsightRequest(
            episodeNumber = 7,
            positionSec = 18.4,
            sceneHint = "visual scene",
            frameImageBase64 = "abc123",
            frameMimeType = "image/jpeg"
        )

        assertEquals("abc123", request.frameImageBase64)
        assertEquals("image/jpeg", request.frameMimeType)
    }

    @Test
    fun `server person insight response maps to overlay insight`() {
        val response = AiPersonInsightResponse(
            title = "AI 即时看点",
            insight = "苏羽此刻在压最后一口气",
            hook = "点关系图继续追线",
            source = "doubao"
        )

        val insight = response.toPersonAiInsight()

        assertEquals("AI 即时看点", insight.title)
        assertEquals("苏羽此刻在压最后一口气", insight.insight)
        assertEquals("点关系图继续追线", insight.hook)
        assertEquals("doubao", insight.source)
    }

    @Test
    fun `person insight client timeout covers ark server timeout`() {
        assertTrue(PERSON_INSIGHT_TIMEOUT_MS >= 50_000L)
    }

    private fun character(
        id: String,
        name: String,
        identity: String
    ) = AiPersonCharacter(
        id = id,
        name = name,
        identity = identity,
        aliases = emptyList(),
        tags = listOf("角色"),
        storyRole = "剧情人物",
        description = "$name 的人物设定"
    )
}
