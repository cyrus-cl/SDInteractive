package com.sdinteractive.server

import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.config.propertyOrNull
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.header
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.RandomAccessFile
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.Locale
import java.util.UUID
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.math.max
import kotlin.math.min

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8081
    embeddedServer(Netty, host = "0.0.0.0", port = port, module = Application::module).start(wait = true)
}

fun Application.module() {
    val videoDir = videoDirectory()
    val catalog = VideoCatalog(videoDir)
    val accountStore = AccountStore()
    val aiEnabled = environment.config.propertyOrNull("sd.ai.enabled")
        ?.getString()
        ?.toBooleanStrictOrNull()
        ?: true
    val arkClient = ArkConfig.load()
        ?.takeIf { aiEnabled }
        ?.let(::ArkClient)
    val personInsightService = PersonInsightService(arkClient)
    val interactionTaggingService = InteractionTaggingService(arkClient)
    val personIdentificationService = PersonIdentificationService(arkClient)

    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = false
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        )
    }
    install(CallLogging)
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.Range)
        exposeHeader(HttpHeaders.ContentRange)
        exposeHeader(HttpHeaders.AcceptRanges)
        exposeHeader(HttpHeaders.ContentLength)
    }

    routing {
        get("/") {
            call.respond(ok(ServiceInfo(videoDir = videoDir.absolutePathString(), episodes = catalog.episodes.size)))
        }

        post("/api/auth/guest") {
            val request = runCatching { call.receiveNullable<GuestLoginRequest>() }.getOrNull()
            val deviceId = request?.deviceId?.takeIf { it.isNotBlank() } ?: "device_${UUID.randomUUID()}"
            val suffix = deviceId.takeLast(8).replace(Regex("[^A-Za-z0-9_]"), "_")
            val userId = "guest_$suffix"
            accountStore.ensureUser(userId, deviceId)

            call.respond(
                ok(
                    GuestLoginResponse(
                        userId = userId,
                        token = "demo-token-$suffix",
                        deviceId = deviceId
                    )
                )
            )
        }

        get("/api/dramas") {
            val baseUrl = call.publicBaseUrl()
            call.respond(
                ok(
                    DramaListResponse(
                        items = listOf(
                            DramaDto(
                                dramaId = VideoCatalog.DRAMA_ID,
                                title = "天下第一纨绔",
                                coverUrl = "$baseUrl/static/covers/drama_001.jpg",
                                description = "服务端下发剧集与远程 videoUrl，Android 使用 Media3 播放。",
                                episodeCount = catalog.episodes.size
                            )
                        )
                    )
                )
            )
        }

        get("/api/dramas/{dramaId}/episodes") {
            val dramaId = call.parameters["dramaId"]
            if (dramaId != VideoCatalog.DRAMA_ID) {
                call.respond(HttpStatusCode.NotFound, error("drama_not_found"))
                return@get
            }

            call.respond(ok(EpisodeListResponse(items = catalog.episodes.map { it.toDto() })))
        }

        get("/api/episodes/{episodeId}/play") {
            val episodeId = call.parameters["episodeId"].orEmpty()
            val episode = catalog.findEpisode(episodeId)
            if (episode == null) {
                call.respond(HttpStatusCode.NotFound, error("episode_not_found"))
                return@get
            }

            call.respond(
                ok(
                    PlayInfoResponse(
                        episodeId = episode.episodeId,
                        videoUrl = "${call.publicBaseUrl()}/static/videos/${episode.episodeId}.mp4",
                        durationMs = episode.durationMs,
                        format = "MP4"
                    )
                )
            )
        }

        get("/api/episodes/{episodeId}/interaction-manifest") {
            val episodeId = call.parameters["episodeId"].orEmpty()
            if (catalog.findEpisode(episodeId) == null) {
                call.respond(HttpStatusCode.NotFound, error("episode_not_found"))
                return@get
            }

            call.respond(ok(InteractionManifestResponse(episodeId = episodeId)))
        }

        post("/api/playback/progress") {
            val request = runCatching { call.receiveNullable<PlaybackProgressRequest>() }.getOrNull()
            if (request?.episodeId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, error("episode_required"))
                return@post
            }

            call.respond(ok(AcceptedResponse(accepted = true)))
        }

        post("/api/qoe/events") {
            val request = runCatching { call.receiveNullable<QoEEventsRequest>() }.getOrNull()
            call.respond(ok(BatchAcceptedResponse(accepted = request?.events?.size ?: 0)))
        }

        post("/api/interactions/events") {
            val request = runCatching { call.receiveNullable<InteractionEventRequest>() }.getOrNull()
            if (request?.eventId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, error("event_required"))
                return@post
            }

            call.respond(ok(AcceptedResponse(accepted = true)))
        }

        get("/api/users/{userId}/profile") {
            val userId = call.parameters["userId"].orEmpty()
            if (userId.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, error("user_required"))
                return@get
            }

            call.respond(ok(accountStore.profile(userId)))
        }

        post("/api/users/{userId}/actions") {
            val userId = call.parameters["userId"].orEmpty()
            val request = runCatching { call.receiveNullable<UserActionRequest>() }.getOrNull()
            if (userId.isBlank() || request?.episodeId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, error("action_required"))
                return@post
            }

            val response = accountStore.record(userId, request!!)
            call.respond(ok(response))
        }

        post("/api/ai/branch-content") {
            call.respond(
                ok(
                    AiTaskResponse(
                        taskId = "task_${UUID.randomUUID()}",
                        status = "PENDING"
                    )
                )
            )
        }

        post("/api/ai/person-insight") {
            val request = runCatching { call.receiveNullable<AiPersonInsightRequest>() }.getOrNull()
            if (request == null || request.episodeNumber <= 0 || request.positionSec < 0.0) {
                call.respond(HttpStatusCode.BadRequest, error("person_insight_required"))
                return@post
            }

            call.respond(ok(personInsightService.resolve(request)))
        }

        post("/api/ai/interaction-tagging") {
            val request = runCatching {
                call.receiveNullable<InteractionTaggingRequest>()
            }.getOrNull()
            val valid = request != null &&
                request.episodeId.isNotBlank() &&
                request.asr.isNotEmpty() &&
                request.asr.all { segment ->
                    segment.start.isFinite() &&
                        segment.end.isFinite() &&
                        segment.start >= 0.0 &&
                        segment.end >= segment.start &&
                        segment.text.isNotBlank()
                }
            if (!valid) {
                call.respond(HttpStatusCode.BadRequest, error("interaction_tagging_input_invalid"))
                return@post
            }

            call.respond(ok(interactionTaggingService.generate(request!!)))
        }

        post("/api/ai/person-identify") {
            val request = runCatching {
                call.receiveNullable<PersonIdentifyRequest>()
            }.getOrNull()
            if (
                request == null ||
                request.episodeNumber <= 0 ||
                !request.positionSec.isFinite() ||
                request.positionSec < 0.0
            ) {
                call.respond(HttpStatusCode.BadRequest, error("person_identify_input_invalid"))
                return@post
            }

            call.respond(ok(personIdentificationService.identify(request)))
        }

        get("/api/ai/branch-content/{taskId}") {
            val taskId = call.parameters["taskId"].orEmpty()
            call.respond(
                ok(
                    AiTaskResultResponse(
                        taskId = taskId,
                        status = "PENDING",
                        contentType = null,
                        content = null,
                        resumePositionMs = null
                    )
                )
            )
        }

        get("/static/videos/{fileName}") {
            val fileName = call.parameters["fileName"].orEmpty()
            val episodeId = fileName.removeSuffix(".mp4")
            val episode = catalog.findEpisode(episodeId)
            if (episode == null) {
                call.respond(HttpStatusCode.NotFound, error("video_not_found"))
                return@get
            }

            call.respondVideoFile(episode.file)
        }
    }
}

private class AccountStore {
    private val users = linkedMapOf<String, UserRecord>()

    @Synchronized
    fun ensureUser(userId: String, deviceId: String): UserRecord {
        return users.getOrPut(userId) {
            UserRecord(
                userId = userId,
                deviceId = deviceId,
                nickname = "游客${userId.takeLast(4)}"
            )
        }
    }

    @Synchronized
    fun profile(userId: String): UserProfileResponse {
        return users.getOrPut(userId) {
            UserRecord(userId = userId, deviceId = "unknown", nickname = "游客${userId.takeLast(4)}")
        }.toProfile()
    }

    @Synchronized
    fun record(userId: String, request: UserActionRequest): UserActionResponse {
        val record = users.getOrPut(userId) {
            UserRecord(userId = userId, deviceId = "unknown", nickname = "游客${userId.takeLast(4)}")
        }

        val normalizedAction = request.actionType.uppercase(Locale.ROOT)
        when (normalizedAction) {
            "LIKE" -> {
                if (!record.likedEpisodes.add(request.episodeId)) {
                    record.likedEpisodes.remove(request.episodeId)
                }
            }

            "FAVORITE", "COLLECT" -> {
                if (!record.favoriteEpisodes.add(request.episodeId)) {
                    record.favoriteEpisodes.remove(request.episodeId)
                }
            }

            "COMMENT" -> {
                val text = request.commentText?.trim().orEmpty()
                if (text.isNotBlank()) {
                    record.comments += CommentRecord(
                        episodeId = request.episodeId,
                        text = text,
                        createdAt = System.currentTimeMillis()
                    )
                }
            }

            "SHARE" -> {
                record.shareCount += 1
            }
        }

        return UserActionResponse(
            userId = userId,
            episodeId = request.episodeId,
            liked = record.likedEpisodes.contains(request.episodeId),
            favorited = record.favoriteEpisodes.contains(request.episodeId),
            profile = record.toProfile()
        )
    }
}

private data class UserRecord(
    val userId: String,
    val deviceId: String,
    val nickname: String,
    val likedEpisodes: MutableSet<String> = linkedSetOf(),
    val favoriteEpisodes: MutableSet<String> = linkedSetOf(),
    val comments: MutableList<CommentRecord> = mutableListOf(),
    var shareCount: Int = 0
) {
    fun toProfile(): UserProfileResponse = UserProfileResponse(
        userId = userId,
        nickname = nickname,
        likedCount = likedEpisodes.size,
        favoriteCount = favoriteEpisodes.size,
        commentCount = comments.size,
        shareCount = shareCount,
        coinBalance = 4660,
        recentComments = comments.takeLast(5).reversed().map {
            CommentDto(episodeId = it.episodeId, text = it.text, createdAt = it.createdAt)
        }
    )
}

private data class CommentRecord(
    val episodeId: String,
    val text: String,
    val createdAt: Long
)

private fun Application.videoDirectory(): Path {
    return resolveVideoDirectory(
        configured = environment.config.propertyOrNull("sd.videoDir")?.getString(),
        envVideoDir = System.getenv("VIDEO_DIR"),
        workingDirectory = Paths.get("").toAbsolutePath()
    )
}

internal fun resolveVideoDirectory(
    configured: String?,
    envVideoDir: String?,
    workingDirectory: Path
): Path {
    val explicit = configured?.takeIf { it.isNotBlank() }
        ?: envVideoDir?.takeIf { it.isNotBlank() }
    val baseDirectory = workingDirectory.toAbsolutePath().normalize()
    if (explicit != null) {
        return baseDirectory.resolve(explicit).toAbsolutePath().normalize()
    }

    val candidates = listOfNotNull(
        baseDirectory.resolve("video"),
        baseDirectory.parent?.resolve("video")
    )
    return candidates.firstOrNull { it.exists() && Files.isDirectory(it) }
        ?: baseDirectory.resolve("video").normalize()
}

private fun io.ktor.server.application.ApplicationCall.publicBaseUrl(): String {
    val configured = application.environment.config.propertyOrNull("sd.publicBaseUrl")?.getString()
        ?: System.getenv("PUBLIC_BASE_URL")
    if (!configured.isNullOrBlank()) {
        val normalized = configured.trimEnd('/')
        val configuredHost = runCatching { URI(normalized).host }.getOrNull()
        val requestHost = request.header(HttpHeaders.Host)
        if (!configuredHost.isLoopbackHost() || requestHost.isNullOrBlank()) {
            return normalized
        }
    }

    val host = request.header(HttpHeaders.Host) ?: "localhost:8081"
    val scheme = request.header("X-Forwarded-Proto") ?: "http"
    return "$scheme://$host"
}

private fun String?.isLoopbackHost(): Boolean =
    this.equals("localhost", ignoreCase = true) ||
        this == "127.0.0.1" ||
        this == "::1"

private suspend fun io.ktor.server.application.ApplicationCall.respondVideoFile(file: Path) {
    if (!file.exists() || !Files.isRegularFile(file)) {
        respond(HttpStatusCode.NotFound, error("video_file_missing"))
        return
    }

    val fileLength = Files.size(file)
    if (fileLength <= 0L) {
        respond(HttpStatusCode.NotFound, error("video_file_empty"))
        return
    }

    val byteRange = parseRange(request.header(HttpHeaders.Range), fileLength)

    if (byteRange == ByteRange.Invalid) {
        response.header(HttpHeaders.ContentRange, "bytes */$fileLength")
        respond(HttpStatusCode.RequestedRangeNotSatisfiable, error("invalid_range"))
        return
    }

    val range = byteRange as? ByteRange.Valid
    val start = range?.start ?: 0L
    val endInclusive = range?.endInclusive ?: (fileLength - 1)
    val contentLength = endInclusive - start + 1
    val status = if (range == null) HttpStatusCode.OK else HttpStatusCode.PartialContent

    respond(
        VideoFileContent(
            file = file,
            statusCode = status,
            start = start,
            endInclusive = endInclusive,
            fileLength = fileLength,
            contentBytes = contentLength,
            isRange = range != null
        )
    )
}

private class VideoFileContent(
    private val file: Path,
    private val statusCode: HttpStatusCode,
    private val start: Long,
    private val endInclusive: Long,
    private val fileLength: Long,
    private val contentBytes: Long,
    private val isRange: Boolean
) : OutgoingContent.WriteChannelContent() {
    override val status: HttpStatusCode = statusCode
    override val contentType: ContentType = ContentType.Video.MP4
    override val contentLength: Long = contentBytes
    override val headers: Headers = Headers.build {
        append(HttpHeaders.AcceptRanges, "bytes")
        if (isRange) {
            append(HttpHeaders.ContentRange, "bytes $start-$endInclusive/$fileLength")
        }
    }

    override suspend fun writeTo(channel: ByteWriteChannel) {
        withContext(Dispatchers.IO) {
            RandomAccessFile(file.toFile(), "r").use { randomAccessFile ->
                randomAccessFile.seek(start)
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var remaining = contentBytes
                while (remaining > 0L) {
                    val read = randomAccessFile.read(buffer, 0, min(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) break
                    channel.writeFully(buffer, 0, read)
                    remaining -= read
                }
            }
        }
    }
}

private fun parseRange(rangeHeader: String?, fileLength: Long): ByteRange {
    if (rangeHeader.isNullOrBlank()) return ByteRange.None
    if (!rangeHeader.startsWith("bytes=") || rangeHeader.contains(",")) return ByteRange.Invalid

    val rangeValue = rangeHeader.removePrefix("bytes=")
    val parts = rangeValue.split("-", limit = 2)
    if (parts.size != 2) return ByteRange.Invalid

    val startPart = parts[0]
    val endPart = parts[1]
    return when {
        startPart.isBlank() -> {
            val suffixLength = endPart.toLongOrNull() ?: return ByteRange.Invalid
            if (suffixLength <= 0) return ByteRange.Invalid
            val start = max(0L, fileLength - suffixLength)
            ByteRange.Valid(start, fileLength - 1)
        }

        else -> {
            val start = startPart.toLongOrNull() ?: return ByteRange.Invalid
            val requestedEnd = endPart.toLongOrNull()
            if (start < 0 || start >= fileLength) return ByteRange.Invalid
            val end = min(requestedEnd ?: (fileLength - 1), fileLength - 1)
            if (end < start) return ByteRange.Invalid
            ByteRange.Valid(start, end)
        }
    }
}

private sealed interface ByteRange {
    data object None : ByteRange
    data object Invalid : ByteRange
    data class Valid(val start: Long, val endInclusive: Long) : ByteRange
}

private class VideoCatalog(private val videoDir: Path) {
    val episodes: List<EpisodeRecord> = loadEpisodes()

    fun findEpisode(episodeId: String): EpisodeRecord? = episodes.firstOrNull { it.episodeId == episodeId }

    private fun loadEpisodes(): List<EpisodeRecord> {
        if (!videoDir.exists()) return emptyList()

        return Files.list(videoDir).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().lowercase(Locale.ROOT).endsWith(".mp4") }
                .map { file ->
                    val number = extractEpisodeNumber(file.fileName.toString())
                    EpisodeRecord(
                        episodeId = "ep_%03d".format(number),
                        dramaId = DRAMA_ID,
                        title = "第${number}集",
                        sortOrder = number,
                        durationMs = Mp4DurationReader.readDurationMs(file) ?: DEFAULT_DURATION_MS,
                        file = file.toAbsolutePath().normalize()
                    )
                }
                .sorted(Comparator.comparingInt(EpisodeRecord::sortOrder))
                .toList()
        }
    }

    private fun extractEpisodeNumber(fileName: String): Int {
        val fromName = EPISODE_NUMBER_REGEX.find(fileName)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (fromName != null) return fromName

        val digits = fileName.filter { it.isDigit() }.toIntOrNull()
        return digits ?: 1
    }

    companion object {
        const val DRAMA_ID = "drama_001"
        private const val DEFAULT_DURATION_MS = 90_000L
        private val EPISODE_NUMBER_REGEX = Regex("第(\\d+)集")
    }
}

private data class EpisodeRecord(
    val episodeId: String,
    val dramaId: String,
    val title: String,
    val sortOrder: Int,
    val durationMs: Long,
    val file: Path
) {
    fun toDto(): EpisodeDto = EpisodeDto(
        episodeId = episodeId,
        dramaId = dramaId,
        title = title,
        sortOrder = sortOrder,
        durationMs = durationMs
    )
}

private fun <T> ok(data: T): ApiResponse<T> = ApiResponse(code = 0, message = "ok", data = data)

private fun error(reason: String): ApiResponse<ErrorResponse> =
    ApiResponse(code = 1, message = reason, data = ErrorResponse(reason))

@Serializable
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T
)

@Serializable
data class ErrorResponse(val reason: String)

@Serializable
data class ServiceInfo(val videoDir: String, val episodes: Int)

@Serializable
data class GuestLoginRequest(val deviceId: String? = null)

@Serializable
data class GuestLoginResponse(
    val userId: String,
    val token: String,
    val deviceId: String
)

@Serializable
data class DramaListResponse(val items: List<DramaDto>)

@Serializable
data class DramaDto(
    val dramaId: String,
    val title: String,
    val coverUrl: String,
    val description: String,
    val episodeCount: Int
)

@Serializable
data class EpisodeListResponse(val items: List<EpisodeDto>)

@Serializable
data class EpisodeDto(
    val episodeId: String,
    val dramaId: String,
    val title: String,
    val sortOrder: Int,
    val durationMs: Long
)

@Serializable
data class PlayInfoResponse(
    val episodeId: String,
    val videoUrl: String,
    val durationMs: Long,
    val format: String
)

@Serializable
data class InteractionManifestResponse(
    val episodeId: String,
    val version: Int = 1,
    val generatedBy: String = "manual",
    val points: List<InteractionPointDto> = emptyList()
)

@Serializable
data class InteractionPointDto(
    val interactionId: String,
    val type: String,
    val startMs: Long,
    val endMs: Long,
    val priority: Int,
    val title: String,
    val payload: JsonElement? = null
)

@Serializable
data class PlaybackProgressRequest(
    val episodeId: String,
    val positionMs: Long,
    val durationMs: Long,
    val isFinished: Boolean
)

@Serializable
data class QoEEventsRequest(val events: List<QoEEventDto> = emptyList())

@Serializable
data class QoEEventDto(
    val eventId: String,
    val episodeId: String,
    val sessionId: String,
    val eventType: String,
    val value: JsonElement? = null,
    val clientTime: Long
)

@Serializable
data class InteractionEventRequest(
    val eventId: String,
    val userId: String? = null,
    val episodeId: String,
    val interactionId: String,
    val eventType: String,
    val optionId: String? = null,
    val playPositionMs: Long,
    val clientTime: Long,
    val extra: JsonElement? = null
)

@Serializable
data class AcceptedResponse(val accepted: Boolean)

@Serializable
data class BatchAcceptedResponse(val accepted: Int)

@Serializable
data class AiTaskResponse(
    val taskId: String,
    val status: String
)

@Serializable
data class AiTaskResultResponse(
    val taskId: String,
    val status: String,
    val contentType: String?,
    val content: String?,
    val resumePositionMs: Long?
)

@Serializable
data class AiPersonInsightRequest(
    val episodeNumber: Int,
    val positionSec: Double,
    val characterName: String,
    val identity: String,
    val sceneHint: String? = null,
    val frameImageBase64: String? = null,
    val frameMimeType: String? = null
)

@Serializable
data class AiPersonInsightResponse(
    val title: String,
    val insight: String,
    val hook: String,
    val source: String
)

@Serializable
data class UserActionRequest(
    val episodeId: String,
    val actionType: String,
    val commentText: String? = null
)

@Serializable
data class UserActionResponse(
    val userId: String,
    val episodeId: String,
    val liked: Boolean,
    val favorited: Boolean,
    val profile: UserProfileResponse
)

@Serializable
data class UserProfileResponse(
    val userId: String,
    val nickname: String,
    val likedCount: Int,
    val favoriteCount: Int,
    val commentCount: Int,
    val shareCount: Int,
    val coinBalance: Int,
    val recentComments: List<CommentDto>
)

@Serializable
data class CommentDto(
    val episodeId: String,
    val text: String,
    val createdAt: Long
)

private class PersonInsightService(
    private val aiClient: AiTextClient?
) {
    suspend fun resolve(request: AiPersonInsightRequest): AiPersonInsightResponse {
        val fallback = fallbackInsight(request)
        val client = aiClient ?: return fallback
        return runCatching {
            callArk(client, request)
        }.getOrElse { error ->
            System.err.println(
                "person_insight_ai_failed mode=${if (request.hasFrameImage()) "vision" else "text"} " +
                    "message=${error.message}"
            )
            fallback
        }
    }

    private suspend fun callArk(
        client: AiTextClient,
        request: AiPersonInsightRequest
    ): AiPersonInsightResponse {
        val prompt = """
            你是短剧互动系统，只输出适合浮层展示的一句人物即时看点。
            人物：${request.characterName}
            身份：${request.identity}
            集数：第${request.episodeNumber}集
            进度：${request.positionSec.toInt()}秒
            场景补充：${request.sceneHint.orEmpty()}
            要求：中文，24字以内，带一点悬念，不要解释。
        """.trimIndent()
        val content = client.complete(
            systemPrompt = "你为短剧播放器生成即时互动看点，只返回一句中文短句。",
            userPrompt = prompt,
            imageBase64 = request.frameImageBase64,
            imageMimeType = request.frameMimeType,
            temperature = 0.75,
            maxTokens = 80
        )
        return AiPersonInsightResponse(
            title = "AI 即时看点",
            insight = content.substring(0, min(content.length, 48)),
            hook = "点击关系图谱继续追线",
            source = "doubao"
        )
    }

    private fun fallbackInsight(request: AiPersonInsightRequest): AiPersonInsightResponse =
        AiPersonInsightResponse(
            title = "AI 即时看点",
            insight = "${request.characterName}正在藏锋，下一秒可能反转。",
            hook = "点击关系图谱继续追线",
            source = "fallback"
        )
}

private fun AiPersonInsightRequest.hasFrameImage(): Boolean =
    !frameImageBase64.isNullOrBlank()
