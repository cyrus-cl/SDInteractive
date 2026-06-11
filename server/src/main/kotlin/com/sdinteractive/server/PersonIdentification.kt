package com.sdinteractive.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PersonIdentifyRequest(
    val episodeNumber: Int,
    val positionSec: Double,
    val frameImageBase64: String? = null,
    val frameMimeType: String? = null
)

@Serializable
data class PersonCharacter(
    val id: String,
    val name: String,
    val identity: String,
    val aliases: List<String>,
    val tags: List<String>,
    val storyRole: String,
    val description: String
)

@Serializable
data class IdentifiedCharacter(
    val character: PersonCharacter,
    val confidence: Double,
    val screenPosition: String,
    val evidence: String
)

@Serializable
data class PersonIdentifyResponse(
    val characters: List<IdentifiedCharacter>,
    val candidateCharacters: List<PersonCharacter> = emptyList(),
    val sceneRole: String,
    val frameCount: Int = 1,
    val usedFallback: Boolean,
    val source: String
)

internal data class CharacterScene(
    val primaryCharacterId: String,
    val candidateCharacterIds: List<String>,
    val sceneRole: String
)

internal object PersonCatalog {
    val characters: Map<String, PersonCharacter> = listOf(
        PersonCharacter(
            id = "suyu",
            name = "苏羽",
            identity = "镇北侯府二公子 / 怪侠一枝梅 / 护国将军",
            aliases = listOf("怪侠一枝梅"),
            tags = listOf("隐忍腹黑", "智勇双全", "杀伐果断", "不恋爱脑"),
            storyRole = "复仇主线与护国主角",
            description = "表面是京城人人嘲笑的草包纨绔，实则是绝世高手。为报母仇伪装二十年，化身怪侠一枝梅惩奸除恶。"
        ),
        PersonCharacter(
            id = "jinning",
            name = "锦宁公主",
            identity = "大炎皇帝独女",
            aliases = emptyList(),
            tags = listOf("前期傲娇自私", "眼高手低", "后期深明大义"),
            storyRole = "情感冲突与成长主线",
            description = "前期嫌弃苏羽，却爱慕怪侠一枝梅；受乱臣蛊惑背刺苏羽，得知真相后追悔莫及。"
        ),
        PersonCharacter(
            id = "emperor",
            name = "大炎皇帝",
            identity = "大炎王朝君主",
            aliases = emptyList(),
            tags = listOf("老谋深算", "知人善任", "帝王心术"),
            storyRole = "朝堂制衡与护国盟友",
            description = "看似信任权臣，实则暗藏制衡之心，是苏羽复仇与护国路上的重要盟友。"
        ),
        PersonCharacter(
            id = "qinwu",
            name = "秦武",
            identity = "大内第一侍卫",
            aliases = emptyList(),
            tags = listOf("忠诚正直", "武功高强"),
            storyRole = "实力见证者与宫中助手",
            description = "奉命暗访权贵子弟实力，意外发现苏羽的真实实力，成为苏羽在宫中的得力助手。"
        ),
        PersonCharacter(
            id = "lvzhen",
            name = "吕甄",
            identity = "苏羽继母 / 镇北侯夫人",
            aliases = emptyList(),
            tags = listOf("阴险", "偏执", "争夺爵位"),
            storyRole = "侯府复仇线核心反派",
            description = "为让亲儿子苏明武继承镇北侯爵位而毒杀苏羽生母，罪行败露后被苏羽处决。"
        ),
        PersonCharacter(
            id = "sumingwu",
            name = "苏明武",
            identity = "苏羽同父异母弟弟",
            aliases = emptyList(),
            tags = listOf("嫉妒", "渴望权力", "争夺继承权"),
            storyRole = "侯府继承冲突反派",
            description = "嫉妒苏羽，渴望继承爵位和权力，被吕甄利用后最终身败名裂。"
        ),
        PersonCharacter(
            id = "barbarian_prince",
            name = "蛮夷三皇子",
            identity = "北方蛮夷部落皇子",
            aliases = emptyList(),
            tags = listOf("野心", "比武招亲", "外患"),
            storyRole = "联姻与边境外患反派",
            description = "企图通过比武招亲娶锦宁公主并控制大炎，后被苏羽击败。"
        ),
        PersonCharacter(
            id = "zhenbeihou",
            name = "镇北侯苏烈",
            identity = "镇北侯 / 苏羽父亲",
            aliases = listOf("苏烈", "镇北侯"),
            tags = listOf("战功赫赫", "性格刚直", "护子"),
            storyRole = "父子误会与侯府支撑",
            description = "前期对苏羽失望透顶，得知真相后全力支持儿子。"
        ),
        PersonCharacter(
            id = "liuruyan",
            name = "柳如烟",
            identity = "苏羽生母",
            aliases = emptyList(),
            tags = listOf("出身名门", "温柔善良", "复仇动机"),
            storyRole = "苏羽复仇的核心动机",
            description = "被吕甄毒杀，她的死亡是苏羽隐忍二十年并追查真相的全部动力。"
        )
    ).associateBy(PersonCharacter::id)

    fun require(id: String): PersonCharacter =
        requireNotNull(characters[id]) { "Unknown character: $id" }
}

internal object CharacterTimeline {
    fun resolve(episodeNumber: Int, positionSec: Double): CharacterScene {
        val safeEpisode = episodeNumber.coerceAtLeast(1)
        val safeTime = positionSec.coerceAtLeast(0.0)
        return when {
            safeEpisode == 3 && safeTime >= 145.0 ->
                scene("lvzhen", listOf("lvzhen", "suyu", "zhenbeihou", "sumingwu"), "吕甄算计与侯府矛盾升级")
            safeEpisode in 2..4 ->
                scene("suyu", listOf("suyu", "qinwu", "emperor", "lvzhen", "zhenbeihou"), "秦武试探苏羽真实实力")
            safeEpisode in 7..9 ->
                scene(
                    "suyu",
                    listOf("suyu", "jinning", "barbarian_prince", "sumingwu", "emperor"),
                    "比武招亲与苏羽隐藏实力"
                )
            safeEpisode in 10..17 ->
                scene(
                    "suyu",
                    listOf("suyu", "zhenbeihou", "sumingwu", "lvzhen", "liuruyan"),
                    "父子误会与侯府继承冲突"
                )
            safeEpisode >= 18 ->
                scene("suyu", listOf("suyu", "emperor", "qinwu", "jinning"), "朝堂结盟与护国冲突")
            else ->
                scene("suyu", listOf("suyu", "qinwu", "jinning"), "苏羽以纨绔身份隐藏真实实力")
        }
    }

    private fun scene(
        primary: String,
        candidates: List<String>,
        role: String
    ) = CharacterScene(primary, candidates.distinct(), role)
}

@Serializable
private data class ModelDetectedCharacter(
    val id: String,
    val confidence: Double = 0.0,
    val screenPosition: String = "unknown",
    val evidence: String = ""
)

@Serializable
private data class ModelPersonDecision(
    val characters: List<ModelDetectedCharacter> = emptyList()
)

internal class PersonIdentificationEngine {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun identify(
        request: PersonIdentifyRequest,
        modelContent: String?,
        source: String
    ): PersonIdentifyResponse {
        val scene = CharacterTimeline.resolve(request.episodeNumber, request.positionSec)
        val decision = modelContent?.let(::parseDecision)
        val identified = decision?.characters
            .orEmpty()
            .mapIndexedNotNull { index, detected ->
                val confidence = detected.confidence
                    .takeIf(Double::isFinite)
                    ?.coerceIn(0.0, 1.0)
                    ?: return@mapIndexedNotNull null
                val character = PersonCatalog.characters[detected.id]
                    ?: return@mapIndexedNotNull null
                if (confidence < MIN_CONFIDENCE) {
                    return@mapIndexedNotNull null
                }
                IndexedIdentification(
                    index = index,
                    result = IdentifiedCharacter(
                        character = character,
                        confidence = confidence,
                        screenPosition = detected.screenPosition.normalizedScreenPosition(),
                        evidence = detected.evidence.trim().take(MAX_EVIDENCE_LENGTH)
                            .ifBlank { "模型依据当前帧中的人物外观完成匹配" }
                    )
                )
            }
            .groupBy { it.result.character.id }
            .values
            .mapNotNull { matches -> matches.maxByOrNull { it.result.confidence } }
            .sortedBy(IndexedIdentification::index)
            .take(MAX_IDENTIFIED_CHARACTERS)
            .map(IndexedIdentification::result)

        if (identified.isEmpty()) {
            return fallback(scene)
        }

        return PersonIdentifyResponse(
            characters = identified,
            candidateCharacters = emptyList(),
            sceneRole = scene.sceneRole,
            frameCount = 1,
            usedFallback = false,
            source = source
        )
    }

    private fun parseDecision(content: String): ModelPersonDecision? = runCatching {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        require(start >= 0 && end > start)
        json.decodeFromString<ModelPersonDecision>(content.substring(start, end + 1))
    }.getOrNull()

    private fun fallback(scene: CharacterScene): PersonIdentifyResponse =
        PersonIdentifyResponse(
            characters = emptyList(),
            candidateCharacters = scene.candidateCharacterIds.map(PersonCatalog::require),
            sceneRole = scene.sceneRole,
            frameCount = 1,
            usedFallback = true,
            source = "timeline_fallback"
        )

    private data class IndexedIdentification(
        val index: Int,
        val result: IdentifiedCharacter
    )

    private companion object {
        const val MIN_CONFIDENCE = 0.55
        const val MAX_IDENTIFIED_CHARACTERS = 4
        const val MAX_EVIDENCE_LENGTH = 180
    }
}

internal class PersonIdentificationService(
    private val aiClient: AiTextClient?,
    private val engine: PersonIdentificationEngine = PersonIdentificationEngine()
) {
    suspend fun identify(request: PersonIdentifyRequest): PersonIdentifyResponse {
        val scene = CharacterTimeline.resolve(request.episodeNumber, request.positionSec)
        val modelContent = if (!request.frameImageBase64.isNullOrBlank()) {
            aiClient?.let { client ->
                runCatching {
                    client.complete(
                        systemPrompt = PERSON_SYSTEM_PROMPT,
                        userPrompt = buildPersonPrompt(request, scene),
                        imageBase64 = request.frameImageBase64,
                        imageMimeType = request.frameMimeType,
                        temperature = 0.05,
                        maxTokens = 900
                    )
                }.onFailure {
                    System.err.println("person_identify_ai_failed message=${it.message}")
                }.getOrNull()
            }
        } else {
            null
        }
        return engine.identify(request, modelContent, "doubao")
    }
}

internal fun buildPersonPrompt(
    request: PersonIdentifyRequest,
    scene: CharacterScene
): String {
    val catalog = PersonCatalog.characters.values.joinToString("\n") { character ->
        "- id=${character.id}; 姓名=${character.name}; 别名=${character.aliases.joinToString("、")}; " +
            "身份=${character.identity}; 特征=${character.tags.joinToString("、")}; 设定=${character.description}"
    }
    return """
        这是唯一一张当前视频原始帧，只分析这张图，不推测前一帧或后一帧。
        episode: ${request.episodeNumber}
        currentTime: ${request.positionSec}
        sceneHint: ${scene.sceneRole}
        likelyCharacterIds: ${scene.candidateCharacterIds.joinToString(",")}

        完整已知人物目录：
        $catalog

        识别规则：
        1. 返回画面中所有清晰可见、且能与目录匹配的人物，不设置主角，最多返回 4 人。
        2. likelyCharacterIds 只是剧情弱提示。只要有充分视觉证据，可以返回目录中的其他人物。
        3. 忽略字幕、台词、姓名文字、演员表、播放器 UI、角标、Logo 和水印；这些文字不能作为身份依据。
        4. 只能依据脸部、发型、服饰、体态和人物在画面中的位置判断。
        5. evidence 必须描述当前帧视觉证据，不得只写剧情推断。
        6. 不确定、背影、严重遮挡或脸部模糊的人物不要返回；不得创建目录外人物。
        7. screenPosition 只能是 left、center、right、unknown。
        8. 只输出严格 JSON，不要 Markdown、解释或代码块。

        输出格式：
        {"characters":[
          {"id":"人物id","confidence":0.0,"screenPosition":"left|center|right|unknown","evidence":"当前帧视觉依据"}
        ]}
    """.trimIndent()
}

private fun String.normalizedScreenPosition(): String =
    lowercase().takeIf { it in VALID_SCREEN_POSITIONS } ?: "unknown"

private val VALID_SCREEN_POSITIONS = setOf("left", "center", "right", "unknown")

private const val PERSON_SYSTEM_PROMPT =
    "你是短剧当前帧人物视觉识别算法。只依据输入图片中的人物外观返回所有可确认人物，并严格输出 JSON。"
