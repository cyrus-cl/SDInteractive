package com.sdinteractive.app.interactions.data

data class CharacterProfile(
    val id: String,
    val name: String,
    val identity: String,
    val tags: List<String>,
    val description: String
)

enum class RelationKind {
    EMOTIONAL_TENSION,
    ALLY,
    SUPPORT,
    ENEMY,
    SIBLING_RIVALRY,
    FAMILY
}

data class CharacterRelation(
    val sourceCharacterId: String,
    val targetCharacterId: String,
    val kind: RelationKind,
    val label: String,
    val unlockEpisode: Int
)

object CharacterData {
    private const val SU_YU = "suyu"
    private const val LV_ZHEN = "lvzhen"

    // The source specifies filtering by unlockEpisode but not exact values.
    // These inferred constants follow each relationship's first relevant story stage.
    private const val UNLOCK_FAMILY_AND_PRINCESS = 1
    private const val UNLOCK_QIN_WU = 2
    private const val UNLOCK_LV_ZHEN = 3
    private const val UNLOCK_SU_MING_WU = 8
    private const val UNLOCK_EMPEROR_ALLIANCE = 18

    private val profiles = listOf(
        CharacterProfile(
            id = SU_YU,
            name = "苏羽",
            identity = "镇北侯府二公子",
            tags = listOf("隐忍", "腹黑", "隐藏高手"),
            description = "表面是京城人人嘲笑的纨绔，实则暗中积蓄力量等待复仇时机。"
        ),
        CharacterProfile(
            "jinning",
            "锦宁公主",
            "大炎皇帝独女",
            listOf("前期傲娇", "眼高手低", "后期成长"),
            "前期嫌弃苏羽却爱慕怪侠一枝梅，受人蛊惑后背刺苏羽，得知真相后追悔莫及。"
        ),
        CharacterProfile(
            "emperor",
            "大炎皇帝",
            "大炎王朝君主",
            listOf("老谋深算", "知人善任", "帝王心术"),
            "看似信任权臣，实则暗藏制衡之心，是苏羽复仇与护国路上的重要盟友。"
        ),
        CharacterProfile(
            "qinwu",
            "秦武",
            "大内第一侍卫",
            listOf("忠诚", "正直", "武功高强"),
            "奉命暗访权贵子弟实力，意外发现苏羽的真实实力，成为其宫中助手。"
        ),
        CharacterProfile(
            LV_ZHEN,
            "吕甄",
            "镇北侯夫人、苏羽继母",
            listOf("阴险", "偏执", "争夺爵位"),
            "为让亲儿子苏明武继承爵位而毒杀苏羽生母，罪行败露后被苏羽处决。"
        ),
        CharacterProfile(
            "sumingwu",
            "苏明武",
            "苏羽同父异母弟弟",
            listOf("嫉妒", "渴望权力", "争夺继承权"),
            "被吕甄利用与苏羽争夺爵位和权力，最终身败名裂。"
        ),
        CharacterProfile(
            "barbarian_prince",
            "蛮夷三皇子",
            "北方蛮夷部落皇子",
            listOf("外患", "比武招亲", "野心"),
            "企图通过比武招亲娶锦宁公主并控制大炎，后被苏羽击败。"
        ),
        CharacterProfile(
            "zhenbeihou",
            "镇北侯",
            "镇北侯苏烈、苏羽父亲",
            listOf("战功赫赫", "刚直", "父亲"),
            "前期对苏羽失望透顶，得知真相后全力支持儿子。"
        ),
        CharacterProfile(
            "liuruyan",
            "柳如烟",
            "苏羽生母",
            listOf("出身名门", "温柔善良", "复仇动机"),
            "被吕甄毒杀，她的死亡是苏羽隐忍二十年并追查真相的全部动力。"
        )
    ).associateBy(CharacterProfile::id)

    val suYu: CharacterProfile = requireNotNull(profiles[SU_YU])

    val relations: List<CharacterRelation> = listOf(
        CharacterRelation(
            SU_YU, "jinning", RelationKind.EMOTIONAL_TENSION, "误解 / 情感拉扯",
            UNLOCK_FAMILY_AND_PRINCESS
        ),
        CharacterRelation(
            SU_YU, "emperor", RelationKind.ALLY, "盟友 / 君臣互信",
            UNLOCK_EMPEROR_ALLIANCE
        ),
        CharacterRelation(SU_YU, "qinwu", RelationKind.SUPPORT, "宫中助力", UNLOCK_QIN_WU),
        CharacterRelation(SU_YU, LV_ZHEN, RelationKind.ENEMY, "仇敌", UNLOCK_LV_ZHEN),
        CharacterRelation(
            SU_YU, "sumingwu", RelationKind.SIBLING_RIVALRY, "兄弟对立",
            UNLOCK_SU_MING_WU
        ),
        CharacterRelation(
            SU_YU, "zhenbeihou", RelationKind.FAMILY, "父子",
            UNLOCK_FAMILY_AND_PRINCESS
        )
    )

    fun profile(characterId: String): CharacterProfile =
        requireNotNull(profiles[characterId]) { "Unknown character: $characterId" }

    fun unlockedRelations(episodeNumber: Int): List<CharacterRelation> =
        relations.filter { it.unlockEpisode <= episodeNumber }

    fun relationsForGraph(
        episodeNumber: Int,
        minimumNodeCount: Int
    ): List<CharacterRelation> {
        val unlocked = unlockedRelations(episodeNumber)
        val nodeIds = unlocked
            .flatMap { listOf(it.sourceCharacterId, it.targetCharacterId) }
            .toMutableSet()
        if (nodeIds.size >= minimumNodeCount) return unlocked

        val backfilled = unlocked.toMutableList()
        relations
            .filterNot { relation ->
                backfilled.any {
                    it.sourceCharacterId == relation.sourceCharacterId &&
                        it.targetCharacterId == relation.targetCharacterId &&
                        it.kind == relation.kind
                }
            }
            .forEach { relation ->
                if (nodeIds.size < minimumNodeCount) {
                    backfilled += relation
                    nodeIds += relation.sourceCharacterId
                    nodeIds += relation.targetCharacterId
                }
            }
        return backfilled
    }

    fun detect(episodeNumber: Int, currentTimeSec: Double): CharacterProfile =
        if (episodeNumber == 3 && currentTimeSec >= 150.0) {
            profile(LV_ZHEN)
        } else {
            suYu
        }
}
