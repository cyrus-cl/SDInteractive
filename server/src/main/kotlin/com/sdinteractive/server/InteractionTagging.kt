package com.sdinteractive.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToLong

@Serializable
data class AsrSegment(
    val start: Double,
    val end: Double,
    val speaker: String? = null,
    val text: String
)

@Serializable
data class InteractionTaggingRequest(
    val episodeId: String,
    val asr: List<AsrSegment>,
    val storyContext: String? = null
)

@Serializable
data class AiInteractionCandidate(
    val id: String,
    val type: String,
    val startSec: Double,
    val durationSec: Double,
    val title: String,
    val text: String,
    val reason: String,
    val confidence: Double,
    val position: String,
    val payload: JsonObject,
    val supported: Boolean,
    val approved: Boolean = false
)

@Serializable
data class InteractionManifestPreview(
    val interactionId: String,
    val type: String,
    val startMs: Long,
    val endMs: Long,
    val priority: Int,
    val title: String,
    val payload: JsonObject
)

@Serializable
data class InteractionTaggingResponse(
    val episodeId: String,
    val generatedBy: String,
    val candidates: List<AiInteractionCandidate>,
    val manifestPreview: List<InteractionManifestPreview>,
    val warnings: List<String> = emptyList()
)

@Serializable
private data class ModelCandidateEnvelope(
    val candidates: List<ModelCandidate> = emptyList()
)

@Serializable
private data class ModelCandidate(
    val id: String? = null,
    val type: String = "none",
    val startSec: Double = 0.0,
    val durationSec: Double = 3.0,
    val title: String = "",
    val text: String = "",
    val reason: String = "",
    val confidence: Double = 0.0,
    val position: String = "left_bottom",
    val payload: JsonObject = JsonObject(emptyMap())
)

internal class InteractionTaggingEngine {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun generate(
        request: InteractionTaggingRequest,
        modelContent: String?,
        source: String,
        fallbackWarning: String? = null
    ): InteractionTaggingResponse {
        val parsed = modelContent?.let(::parseCandidates).orEmpty()
        val warnings = mutableListOf<String>()
        fallbackWarning?.let(warnings::add)
        val drafts = if (parsed.isNotEmpty()) {
            parsed
        } else {
            if (!modelContent.isNullOrBlank()) warnings += "模型返回无法解析，已使用本地启发式算法"
            heuristicCandidates(request)
        }
        val candidates = normalize(request.episodeId, drafts)
        return InteractionTaggingResponse(
            episodeId = request.episodeId,
            generatedBy = if (parsed.isNotEmpty()) source else "heuristic",
            candidates = candidates,
            manifestPreview = candidates
                .filter { it.supported && it.type != "none" }
                .map(::toManifestPreview),
            warnings = warnings
        )
    }

    private fun parseCandidates(content: String): List<ModelCandidate> = runCatching {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        require(start >= 0 && end > start)
        json.decodeFromString<ModelCandidateEnvelope>(content.substring(start, end + 1)).candidates
    }.getOrDefault(emptyList())

    private fun normalize(
        episodeId: String,
        drafts: List<ModelCandidate>
    ): List<AiInteractionCandidate> {
        val normalized = drafts.mapIndexed { index, draft ->
            val requestedType = draft.type.lowercase()
            val supported = requestedType in SUPPORTED_TYPES
            val type = requestedType.takeIf { supported } ?: "none"
            val startSec = draft.startSec.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
            val durationSec = draft.durationSec
                .takeIf { it.isFinite() }
                ?.coerceIn(1.0, 12.0)
                ?: 3.0
            val title = draft.title.trim().take(40).ifBlank {
                draft.text.trim().take(40).ifBlank { typeLabel(type) }
            }
            AiInteractionCandidate(
                id = draft.id?.trim()?.takeIf { it.isNotBlank() }
                    ?: "ai_${episodeId}_${(startSec * 1_000).roundToLong()}_$index",
                type = type,
                startSec = startSec,
                durationSec = durationSec,
                title = title,
                text = draft.text.trim().take(80).ifBlank { title },
                reason = draft.reason.trim().take(160).ifBlank { "基于台词语义和互动节奏推荐" },
                confidence = draft.confidence
                    .takeIf { it.isFinite() }
                    ?.coerceIn(0.0, 1.0)
                    ?: 0.0,
                position = draft.position.lowercase().takeIf { it in SUPPORTED_POSITIONS }
                    ?: defaultPosition(type),
                payload = draft.payload,
                supported = supported
            )
        }
        return normalized
            .groupBy { (it.startSec / WINDOW_SECONDS).toInt() }
            .values
            .flatMap { window ->
                window.sortedByDescending(AiInteractionCandidate::confidence).take(MAX_PER_WINDOW)
            }
            .sortedBy(AiInteractionCandidate::startSec)
    }

    private fun heuristicCandidates(request: InteractionTaggingRequest): List<ModelCandidate> =
        request.asr.mapNotNull { segment ->
            val text = segment.text.trim()
            when {
                text.isBlank() -> null
                listOf("会不会", "愿不愿意", "谁会赢", "是否").any(text::contains) ->
                    heuristic(segment, "quiz", "剧情竞猜", text, 0.72)
                listOf("高能", "危险", "小心").any(text::contains) ->
                    heuristic(segment, "warning", "高能预警", "高能预警", 0.74)
                listOf("是什么", "古代", "丞相", "官职", "怡香院").any(text::contains) ->
                    heuristic(segment, "knowledge", "一键了解剧情背景", text, 0.70)
                listOf("哈哈", "笑死", "本公子", "大气", "爽", "忍", "封神", "干他").any(text::contains) ->
                    heuristic(
                        segment,
                        "emotion",
                        emotionLabel(text),
                        emotionLabel(text),
                        0.76
                    )
                else -> null
            }
        }

    private fun heuristic(
        segment: AsrSegment,
        type: String,
        title: String,
        text: String,
        confidence: Double
    ): ModelCandidate = ModelCandidate(
        type = type,
        startSec = segment.start.coerceAtLeast(0.0),
        durationSec = if (type == "quiz") 8.0 else 3.0,
        title = title,
        text = text.take(60),
        reason = "关键词与语义规则识别到${typeLabel(type)}时机",
        confidence = confidence,
        position = defaultPosition(type),
        payload = buildJsonObject {
            when (type) {
                "emotion" -> {
                    put("label", title)
                    put("broadcast", "观众正在发送同款互动")
                }
                "quiz" -> put("question", text.take(50))
                "warning" -> put("theme", "danger")
                "knowledge" -> put("summaryQuery", text.take(60))
            }
        }
    )
}

internal class InteractionTaggingService(
    private val aiClient: AiTextClient?,
    private val engine: InteractionTaggingEngine = InteractionTaggingEngine()
) {
    suspend fun generate(request: InteractionTaggingRequest): InteractionTaggingResponse {
        val content = aiClient?.let { client ->
            runCatching {
                client.complete(
                    systemPrompt = TAGGING_SYSTEM_PROMPT,
                    userPrompt = buildTaggingPrompt(request),
                    temperature = 0.2,
                    maxTokens = 3_000
                )
            }.onFailure {
                System.err.println("interaction_tagging_ai_failed message=${it.message}")
            }.getOrNull()
        }
        return engine.generate(
            request = request,
            modelContent = content,
            source = "doubao",
            fallbackWarning = when {
                aiClient == null -> "未配置可用的豆包模型，已使用本地启发式算法"
                content == null -> "豆包调用失败，已回退到本地启发式算法"
                else -> null
            }
        )
    }
}

private fun toManifestPreview(candidate: AiInteractionCandidate): InteractionManifestPreview {
    val startMs = (candidate.startSec * 1_000).roundToLong()
    return InteractionManifestPreview(
        interactionId = candidate.id,
        type = candidate.type,
        startMs = startMs,
        endMs = startMs + (candidate.durationSec * 1_000).roundToLong(),
        priority = priority(candidate.type),
        title = candidate.title,
        payload = buildJsonObject {
            put("text", candidate.text)
            put("reason", candidate.reason)
            put("confidence", candidate.confidence)
            put("component", candidate.payload)
        }
    )
}

internal fun buildTaggingPrompt(request: InteractionTaggingRequest): String = """
    剧情背景：
    架空大炎王朝内忧外患。镇北侯府二公子苏羽表面纨绔，实为隐藏高手，
    为查明生母柳如烟被害真相长期隐忍。锦宁公主、皇帝、秦武可能成为盟友，
    继母吕甄和苏明武构成侯府阻力，蛮夷三皇子构成外患，镇北侯苏烈后期支持苏羽。

    人工打标风格：
    - 爽点或吐槽点使用 emotion，例如“公子大气”“爽”“忍”。
    - 悬念问题使用 quiz，并给出可展示的问题。
    - 官职、古代场所等背景使用 knowledge。
    - 激烈反转前可使用 warning。
    - 不要过度打标，每 30 秒最多 2 个。

    可用类型：
    emotion, value_boost, quiz, quiz_result, rating, knowledge, warning,
    highlight_collect, global_broadcast, none

    任务：
    根据 ASR 判断适合出现互动的位置。只输出 JSON 对象：
    {"candidates":[{"id":"","type":"","startSec":0,"durationSec":3,"title":"",
    "text":"","reason":"","confidence":0.0,"position":"left_bottom","payload":{}}]}
    未知组件必须使用 none。confidence 范围 0 到 1。

    episodeId: ${request.episodeId}
    补充背景: ${request.storyContext.orEmpty()}
    ASR:
    ${Json.encodeToString(ListSerializer, request.asr)}
""".trimIndent()

private val ListSerializer = kotlinx.serialization.builtins.ListSerializer(AsrSegment.serializer())

private fun emotionLabel(text: String): String = when {
    "哈哈" in text || "笑死" in text -> "哈哈哈哈"
    "本公子" in text || "大气" in text -> "公子大气"
    "封神" in text -> "封神"
    "干他" in text -> "干他"
    "忍" in text -> "忍"
    else -> "爽"
}

private fun typeLabel(type: String): String = when (type) {
    "emotion" -> "情绪互动"
    "value_boost" -> "数值互动"
    "quiz" -> "剧情竞猜"
    "quiz_result" -> "竞猜结果"
    "rating" -> "评分互动"
    "knowledge" -> "知识卡片"
    "warning" -> "高能预警"
    "highlight_collect" -> "名场面收藏"
    "global_broadcast" -> "全站播报"
    else -> "无需互动"
}

private fun defaultPosition(type: String): String = when (type) {
    "warning" -> "center"
    "quiz_result", "global_broadcast" -> "top_center"
    else -> "left_bottom"
}

private fun priority(type: String): Int = when (type) {
    "warning" -> 8
    "quiz_result" -> 7
    "quiz" -> 6
    "highlight_collect" -> 5
    "value_boost" -> 4
    "rating" -> 3
    "knowledge" -> 2
    "emotion" -> 1
    else -> 0
}

private val SUPPORTED_TYPES = setOf(
    "emotion",
    "value_boost",
    "quiz",
    "quiz_result",
    "rating",
    "knowledge",
    "warning",
    "highlight_collect",
    "global_broadcast",
    "none"
)
private val SUPPORTED_POSITIONS = setOf(
    "top",
    "top_center",
    "bottom",
    "bottom_center",
    "left_bottom",
    "right_bottom",
    "center",
    "left",
    "right"
)
private const val WINDOW_SECONDS = 30.0
private const val MAX_PER_WINDOW = 2
private const val TAGGING_SYSTEM_PROMPT =
    "你是短剧互动点位打标算法，只做可程序解析的稀疏互动推荐，不总结剧情。"
