package com.sdinteractive.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun `episode APIs expose parsed video duration and malformed files use fallback`() = testApplication {
        val videoDir = createTempDirectory("sd-videos")
        videoDir.resolve("第1集.mp4").writeBytes(
            mp4Box(
                "moov",
                mp4Box(
                    "mvhd",
                    ByteBuffer.allocate(20)
                        .order(ByteOrder.BIG_ENDIAN)
                        .putInt(0)
                        .putInt(0)
                        .putInt(0)
                        .putInt(1_000)
                        .putInt(75_478)
                        .array()
                )
            )
        )
        videoDir.resolve("第2集.mp4").writeBytes("invalid".encodeToByteArray())

        environment {
            config = MapApplicationConfig("sd.videoDir" to videoDir.toString())
        }
        application { module() }

        val episodes = client.get("/api/dramas/drama_001/episodes").bodyAsText()
        val play = client.get("/api/episodes/ep_001/play").bodyAsText()

        assertTrue(episodes.contains("\"episodeId\":\"ep_001\""))
        assertTrue(episodes.contains("\"durationMs\":75478"))
        assertTrue(episodes.contains("\"episodeId\":\"ep_002\""))
        assertTrue(episodes.contains("\"durationMs\":90000"))
        assertTrue(play.contains("\"durationMs\":75478"))
    }

    @Test
    fun `drama episode and play APIs return a remote video url`() = testApplication {
        val videoDir = createTempDirectory("sd-videos")
        videoDir.resolve("第1集.mp4").writeBytes("abcdef".encodeToByteArray())

        environment {
            config = MapApplicationConfig(
                "sd.videoDir" to videoDir.toString(),
                "sd.publicBaseUrl" to "http://test-host"
            )
        }
        application { module() }

        val dramas = client.get("/api/dramas")
        assertEquals(HttpStatusCode.OK, dramas.status)
        assertTrue(dramas.bodyAsText().contains("\"dramaId\":\"drama_001\""))

        val episodes = client.get("/api/dramas/drama_001/episodes")
        assertEquals(HttpStatusCode.OK, episodes.status)
        assertTrue(episodes.bodyAsText().contains("\"episodeId\":\"ep_001\""))

        val play = client.get("/api/episodes/ep_001/play")
        assertEquals(HttpStatusCode.OK, play.status)
        assertTrue(play.bodyAsText().contains("\"videoUrl\":\"http://test-host/static/videos/ep_001.mp4\""))
    }

    @Test
    fun `loopback public url falls back to request host for phone access`() = testApplication {
        val videoDir = createTempDirectory("sd-videos")
        videoDir.resolve("第1集.mp4").writeBytes("abcdef".encodeToByteArray())

        environment {
            config = MapApplicationConfig(
                "sd.videoDir" to videoDir.toString(),
                "sd.publicBaseUrl" to "http://127.0.0.1:8081"
            )
        }
        application { module() }

        val play = client.get("/api/episodes/ep_001/play") {
            header(HttpHeaders.Host, "10.171.239.206:8081")
        }

        assertEquals(HttpStatusCode.OK, play.status)
        assertTrue(
            play.bodyAsText().contains(
                "\"videoUrl\":\"http://10.171.239.206:8081/static/videos/ep_001.mp4\""
            )
        )
    }

    @Test
    fun `static video endpoint supports byte range requests for seeking`() = testApplication {
        val videoDir = createTempDirectory("sd-videos")
        videoDir.resolve("第1集.mp4").writeBytes("abcdef".encodeToByteArray())

        environment {
            config = MapApplicationConfig("sd.videoDir" to videoDir.toString())
        }
        application { module() }

        val response = client.get("/static/videos/ep_001.mp4") {
            header(HttpHeaders.Range, "bytes=1-3")
        }

        assertEquals(HttpStatusCode.PartialContent, response.status)
        assertEquals("bytes 1-3/6", response.headers[HttpHeaders.ContentRange])
        assertEquals("bcd", response.bodyAsText())
    }

    @Test
    fun `reserved telemetry and manifest endpoints are callable`() = testApplication {
        val videoDir = createTempDirectory("sd-videos")
        videoDir.resolve("第1集.mp4").writeBytes("abcdef".encodeToByteArray())

        environment {
            config = MapApplicationConfig("sd.videoDir" to videoDir.toString())
        }
        application { module() }

        val manifest = client.get("/api/episodes/ep_001/interaction-manifest")
        assertEquals(HttpStatusCode.OK, manifest.status)
        assertTrue(manifest.bodyAsText().contains("\"points\":[]"))

        val progress = client.post("/api/playback/progress") {
            contentType(ContentType.Application.Json)
            setBody("""{"episodeId":"ep_001","positionMs":1200,"durationMs":90000,"isFinished":false}""")
        }
        assertEquals(HttpStatusCode.OK, progress.status)
        assertTrue(progress.bodyAsText().contains("\"accepted\":true"))

        val qoe = client.post("/api/qoe/events") {
            contentType(ContentType.Application.Json)
            setBody("""{"events":[{"eventId":"evt_1","episodeId":"ep_001","sessionId":"s_1","eventType":"FIRST_FRAME","value":{"costMs":500},"clientTime":1}]}""")
        }
        assertEquals(HttpStatusCode.OK, qoe.status)
        assertTrue(qoe.bodyAsText().contains("\"accepted\":1"))

        val interaction = client.post("/api/interactions/events") {
            contentType(ContentType.Application.Json)
            setBody("""{"eventId":"evt_2","userId":"guest","episodeId":"ep_001","interactionId":"debug","eventType":"EXPOSE","playPositionMs":1,"clientTime":1}""")
        }
        assertEquals(HttpStatusCode.OK, interaction.status)
        assertTrue(interaction.bodyAsText().contains("\"accepted\":true"))
    }

    @Test
    fun `person insight endpoint returns a fallback interactive prompt without ai config`() = testApplication {
        val videoDir = createTempDirectory("sd-videos")
        videoDir.resolve("第1集.mp4").writeBytes("abcdef".encodeToByteArray())

        environment {
            config = MapApplicationConfig(
                "sd.videoDir" to videoDir.toString(),
                "sd.ai.enabled" to "false"
            )
        }
        application { module() }

        val response = client.post("/api/ai/person-insight") {
            contentType(ContentType.Application.Json)
            setBody("""{"episodeNumber":1,"positionSec":12.0,"characterName":"苏羽","identity":"镇北侯府二公子"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val text = response.bodyAsText()
        assertTrue(text.contains("\"source\":\"fallback\""))
        assertTrue(text.contains("苏羽"))
        assertTrue(text.contains("即时看点"))
    }

    @Test
    fun `interaction tagging endpoint returns deterministic candidates when ai is disabled`() = testApplication {
        val videoDir = createTempDirectory("sd-videos")
        videoDir.resolve("第1集.mp4").writeBytes("abcdef".encodeToByteArray())

        environment {
            config = MapApplicationConfig(
                "sd.videoDir" to videoDir.toString(),
                "sd.ai.enabled" to "false"
            )
        }
        application { module() }

        val response = client.post("/api/ai/interaction-tagging") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                    {
                      "episodeId":"ep_001",
                      "asr":[{
                        "start":82.3,
                        "end":88.6,
                        "speaker":"SPEAKER_01",
                        "text":"这点钱算什么，本公子今日就是要让他们看看。"
                      }]
                    }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val text = response.bodyAsText()
        assertTrue(text.contains("\"generatedBy\":\"heuristic\""))
        assertTrue(text.contains("\"manifestPreview\""))
    }

    @Test
    fun `person identify endpoint returns timeline fallback when ai is disabled`() = testApplication {
        val videoDir = createTempDirectory("sd-videos")
        videoDir.resolve("第3集.mp4").writeBytes("abcdef".encodeToByteArray())

        environment {
            config = MapApplicationConfig(
                "sd.videoDir" to videoDir.toString(),
                "sd.ai.enabled" to "false"
            )
        }
        application { module() }

        val response = client.post("/api/ai/person-identify") {
            contentType(ContentType.Application.Json)
            setBody("""{"episodeNumber":3,"positionSec":165.0}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val text = response.bodyAsText()
        assertTrue(text.contains("\"id\":\"lvzhen\""))
        assertTrue(text.contains("\"usedFallback\":true"))
        assertTrue(text.contains("\"source\":\"timeline_fallback\""))
    }

    @Test
    fun `user profile records like favorite comment and share actions`() = testApplication {
        val videoDir = createTempDirectory("sd-videos")
        videoDir.resolve("第1集.mp4").writeBytes("abcdef".encodeToByteArray())

        environment {
            config = MapApplicationConfig("sd.videoDir" to videoDir.toString())
        }
        application { module() }

        val login = client.post("/api/auth/guest") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"device_account_test"}""")
        }.bodyAsText()
        assertTrue(login.contains("\"userId\":\"guest_unt_test\""))

        val like = client.post("/api/users/guest_unt_test/actions") {
            contentType(ContentType.Application.Json)
            setBody("""{"episodeId":"ep_001","actionType":"LIKE"}""")
        }
        assertEquals(HttpStatusCode.OK, like.status)
        assertTrue(like.bodyAsText().contains("\"liked\":true"))

        val favorite = client.post("/api/users/guest_unt_test/actions") {
            contentType(ContentType.Application.Json)
            setBody("""{"episodeId":"ep_001","actionType":"FAVORITE"}""")
        }
        assertEquals(HttpStatusCode.OK, favorite.status)
        assertTrue(favorite.bodyAsText().contains("\"favorited\":true"))

        val comment = client.post("/api/users/guest_unt_test/actions") {
            contentType(ContentType.Application.Json)
            setBody("""{"episodeId":"ep_001","actionType":"COMMENT","commentText":"好看"}""")
        }
        assertEquals(HttpStatusCode.OK, comment.status)

        val share = client.post("/api/users/guest_unt_test/actions") {
            contentType(ContentType.Application.Json)
            setBody("""{"episodeId":"ep_001","actionType":"SHARE"}""")
        }
        assertEquals(HttpStatusCode.OK, share.status)

        val profile = client.get("/api/users/guest_unt_test/profile")
        assertEquals(HttpStatusCode.OK, profile.status)
        val profileText = profile.bodyAsText()
        assertTrue(profileText.contains("\"likedCount\":1"))
        assertTrue(profileText.contains("\"favoriteCount\":1"))
        assertTrue(profileText.contains("\"commentCount\":1"))
        assertTrue(profileText.contains("\"shareCount\":1"))
    }

    private fun mp4Box(type: String, vararg payloads: ByteArray): ByteArray {
        val payloadSize = payloads.sumOf(ByteArray::size)
        return ByteBuffer.allocate(8 + payloadSize)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(8 + payloadSize)
            .put(type.toByteArray(Charsets.US_ASCII))
            .apply { payloads.forEach(::put) }
            .array()
    }
}
