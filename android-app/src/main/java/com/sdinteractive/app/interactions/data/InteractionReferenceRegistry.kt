package com.sdinteractive.app.interactions.data

import com.sdinteractive.app.interactions.model.BroadcastPayload
import com.sdinteractive.app.interactions.model.ClipEnd
import com.sdinteractive.app.interactions.model.EmotionPayload
import com.sdinteractive.app.interactions.model.HighlightPayload
import com.sdinteractive.app.interactions.model.InteractionEvent
import com.sdinteractive.app.interactions.model.InteractionPosition
import com.sdinteractive.app.interactions.model.InteractionTrigger
import com.sdinteractive.app.interactions.model.InteractionType
import com.sdinteractive.app.interactions.model.KnowledgePayload
import com.sdinteractive.app.interactions.model.PersonDetectPayload
import com.sdinteractive.app.interactions.model.QuizOption
import com.sdinteractive.app.interactions.model.QuizPayload
import com.sdinteractive.app.interactions.model.QuizResultPayload
import com.sdinteractive.app.interactions.model.RatingPayload
import com.sdinteractive.app.interactions.model.RelationGraphPayload
import com.sdinteractive.app.interactions.model.ValueBoostPayload
import com.sdinteractive.app.interactions.model.WarningPayload

data class InteractionReferenceSpec(
    val referenceImageName: String,
    val debugEvent: InteractionEvent
)

object InteractionReferenceRegistry {
    private const val HIGHLIGHT_SAVED_REFERENCE_NAME = "名场面保存组件"

    val specs: List<InteractionReferenceSpec> = listOf(
        "一键识别任务组件.png" to person("一键识别人物"),
        "丞相管职介绍.png" to knowledge("一键了解古代丞相相关知识", "丞相是什么官？"),
        "人物关系图谱.png" to relation("人物关系图谱"),
        "全体起立.png" to emotion("全体起立", "stand_up", "本站起立人数达到123.4w"),
        "公子大气.png" to emotion("公子大气", "gongzi", "已有8.2万人为公子撑场"),
        "名场面保存组件.png" to highlight("名场面已保存"),
        "吕甄阴险值.png" to value("吕甄阴险值", "villain", "阴险拉满"),
        "哈哈哈哈.png" to emotion("哈哈哈哈", "haha", "已有18.6万人笑出声"),
        "封神.png" to emotion("封神", "fengshen", "全站已有36.8万人封神认证"),
        "干他.png" to emotion("干他", "ganta", "全站已有25.6万人想让苏羽出手"),
        "心疼苏羽.png" to emotion("心疼苏羽", "heartache", "已有15.1万人心疼苏羽"),
        "我再忍.png" to emotion("我再忍", "wzren", "已有8.1万人陪苏羽一起忍"),
        "收藏名场面组件.png" to highlight("收藏名场面"),
        "演技打分滑动条.png" to rating("给吕甄的演技打几分？"),
        "爽.png" to emotion("爽", "shuang", "有20.48万人爽到了！"),
        "皇帝愤怒表.png" to value("皇帝愤怒值", "anger", "怒气爆表"),
        "竞猜卡片.png" to quiz("苏羽会不会出手？"),
        "竞猜结果反馈状态.png" to quizResult("恭喜你答对竞猜"),
        "老爹你糊涂啊.png" to emotion("老爹你糊涂啊", "laodie", "全站已有6.6万人发出同款吐槽"),
        "苏羽快上.png" to emotion("苏羽快上", "suyu_go", "已有14.2万人催苏羽上场"),
        "苏羽隐忍值.png" to value("苏羽隐忍值", "forbearance", "隐忍达到90%"),
        "顶层播报组件.png" to broadcast("本站起立人数达到123.4w"),
        "高能预警组件.png" to warning("高能预警")
    ).map { (image, event) ->
        InteractionReferenceSpec(referenceImageName = image, debugEvent = event)
    }

    fun previewEvents(episodeNumber: Int): List<InteractionEvent> =
        specs.map { spec ->
            spec.debugEvent.copy(
                episodeNumber = episodeNumber,
                title = spec.referenceImageName.removeSuffix(".png"),
                displayDurationSec = spec.debugEvent.displayDurationSec.coerceAtLeast(8.0)
            )
        }

    fun isSavedToastPreview(event: InteractionEvent): Boolean =
        event.type == InteractionType.HIGHLIGHT_COLLECT &&
            event.title == HIGHLIGHT_SAVED_REFERENCE_NAME

    fun isExpandedKnowledgePreview(event: InteractionEvent): Boolean =
        event.type == InteractionType.KNOWLEDGE &&
            event.id.startsWith("reference_knowledge_")

    private fun base(
        id: String,
        type: InteractionType,
        title: String,
        position: InteractionPosition,
        payload: com.sdinteractive.app.interactions.model.InteractionPayload,
        duration: Double = 4.0
    ) = InteractionEvent(
        id = "reference_$id",
        episodeNumber = 1,
        trigger = InteractionTrigger.Fixed(0.0),
        type = type,
        title = title,
        position = position,
        displayDurationSec = duration,
        payload = payload
    )

    private fun emotion(label: String, key: String, broadcast: String) = base(
        id = key,
        type = InteractionType.EMOTION,
        title = label,
        position = InteractionPosition.LEFT_BOTTOM,
        payload = EmotionPayload(
            emotionKey = key,
            label = label,
            particles = referenceEmotionParticles(key, label),
            broadcast = broadcast
        )
    )

    private fun referenceEmotionParticles(key: String, label: String): List<String> =
        when (key) {
            "stand_up" -> listOf("全体起立", "起立！", "金光", "礼花")
            "gongzi" -> listOf("公子大气", "金光", "有排面", "撑场")
            "haha" -> listOf("😄", "哈哈哈", "笑死了", "绷不住")
            "fengshen" -> listOf("封神", "金光", "名场面", "太强了")
            "ganta" -> listOf("🔥", "干他", "上啊", "别忍了")
            "heartache" -> listOf("💗", "心疼", "抱抱苏羽", "太能忍")
            "wzren" -> listOf("忍", "我再忍", "继续忍", "蓄力")
            "shuang" -> listOf("爽", "紫光", "太爽了", "+10")
            "laodie" -> listOf("糊涂啊", "醒醒", "吐槽", "老爹")
            "suyu_go" -> listOf("↗", "苏羽快上", "出手", "该你了")
            else -> listOf(label, "名场面", "太有感觉了")
        }

    private fun value(label: String, theme: String, maxText: String) = base(
        id = "value_$theme",
        type = InteractionType.VALUE_BOOST,
        title = label,
        position = InteractionPosition.LEFT_BOTTOM,
        payload = ValueBoostPayload(
            valueKey = theme,
            label = label,
            initialValue = if (theme == "forbearance") 80 else 0,
            maxValue = if (theme == "forbearance") 90 else 100,
            step = if (theme == "anger") 20 else 10,
            clickText = if (theme == "anger") "连点助燃" else "点击加码",
            maxText = maxText,
            theme = theme
        )
    )

    private fun quiz(question: String) = base(
        id = "quiz",
        type = InteractionType.QUIZ,
        title = "竞猜一下",
        position = InteractionPosition.LEFT_BOTTOM,
        payload = QuizPayload(
            quizId = "reference_quiz",
            question = question,
            options = listOf(QuizOption("yes", "会"), QuizOption("no", "不会")),
            participants = "8.7万人"
        )
    )

    private fun quizResult(title: String) = base(
        id = "quiz_result",
        type = InteractionType.QUIZ_RESULT,
        title = "竞猜结果反馈",
        position = InteractionPosition.TOP_CENTER,
        payload = QuizResultPayload(
            quizId = "reference_quiz",
            correctOptionId = "yes",
            correctText = "会",
            successText = title,
            compareText = "超过78%的观众",
            rewardText = "获得竞猜币一枚"
        ),
        duration = 3.0
    )

    private fun rating(question: String) = base(
        id = "rating",
        type = InteractionType.RATING,
        title = "演技打分",
        position = InteractionPosition.LEFT_BOTTOM,
        payload = RatingPayload(
            ratingId = "reference_rating",
            question = question,
            min = 1f,
            max = 10f,
            step = 0.5f,
            defaultValue = 8f,
            maxLabel = "10分满分",
            submitText = "确定",
            resultText = "全站平均分 8.9"
        )
    )

    private fun knowledge(title: String, summaryTitle: String) = base(
        id = "knowledge_${summaryTitle.hashCode().toUInt()}",
        type = InteractionType.KNOWLEDGE,
        title = title,
        position = InteractionPosition.LEFT_BOTTOM,
        payload = KnowledgePayload(
            title = summaryTitle,
            summary = "用于在剧情关键处快速补足古代官职、场所和人物关系背景，避免观众跳出短剧语境。",
            tags = listOf("背景知识", "剧情理解", "即时互动")
        )
    )

    private fun highlight(title: String) = base(
        id = "highlight_${title.hashCode().toUInt()}",
        type = InteractionType.HIGHLIGHT_COLLECT,
        title = "收藏名场面",
        position = InteractionPosition.LEFT_BOTTOM,
        payload = HighlightPayload(
            highlightId = "reference_highlight_${title.hashCode().toUInt()}",
            title = title,
            clipStartSec = 0.0,
            clipEnd = ClipEnd.Fixed(44.0),
            coverTimeSec = 40.0,
            buttonText = "收藏名场面",
            successText = "名场面已保存",
            detailText = "片段已保存，可在我的高光查看",
            actionText = "去我的高光查看"
        )
    )

    private fun broadcast(message: String) = base(
        id = "broadcast",
        type = InteractionType.BROADCAST,
        title = "顶层播报组件",
        position = InteractionPosition.TOP_CENTER,
        payload = BroadcastPayload(message)
    )

    private fun warning(text: String) = base(
        id = "warning",
        type = InteractionType.WARNING,
        title = text,
        position = InteractionPosition.CENTER,
        payload = WarningPayload(text = text, countdown = listOf(3, 2, 1), theme = "danger")
    )

    private fun person(title: String) = base(
        id = "person_detect",
        type = InteractionType.PERSON_DETECT,
        title = title,
        position = InteractionPosition.RIGHT,
        payload = PersonDetectPayload()
    )

    private fun relation(title: String) = base(
        id = "relation_graph",
        type = InteractionType.RELATION_GRAPH,
        title = title,
        position = InteractionPosition.CENTER,
        payload = RelationGraphPayload(focusCharacterId = "suyu")
    )
}
