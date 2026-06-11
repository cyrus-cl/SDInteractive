package com.sdinteractive.app.interactions.data

import com.sdinteractive.app.interactions.model.ClipEnd
import com.sdinteractive.app.interactions.model.EmotionPayload
import com.sdinteractive.app.interactions.model.HighlightPayload
import com.sdinteractive.app.interactions.model.InteractionEvent
import com.sdinteractive.app.interactions.model.InteractionPayload
import com.sdinteractive.app.interactions.model.InteractionPosition
import com.sdinteractive.app.interactions.model.InteractionTrigger
import com.sdinteractive.app.interactions.model.InteractionType
import com.sdinteractive.app.interactions.model.KnowledgePayload
import com.sdinteractive.app.interactions.model.QuizOption
import com.sdinteractive.app.interactions.model.QuizLayout
import com.sdinteractive.app.interactions.model.QuizPayload
import com.sdinteractive.app.interactions.model.QuizResultPayload
import com.sdinteractive.app.interactions.model.RatingPayload
import com.sdinteractive.app.interactions.model.ValueBoostPayload
import com.sdinteractive.app.interactions.model.WarningPayload

object InteractionEvents {
    val all: List<InteractionEvent> = listOf(
        knowledge(
            id = "ep1_knowledge_yixiangyuan",
            episode = 1,
            time = 2.0,
            eventTitle = "一键查看古代怡香院背景知识",
            title = "什么是怡香院？",
            summary = "在古装短剧语境中，怡香院常被用作人物社交、冲突爆发和身份误会的场景。本段用于帮助观众快速理解剧情背景。",
            tags = listOf("古代场所", "剧情背景", "人物动机")
        ),
        emotion(
            id = "ep1_gongzi_86",
            episode = 1,
            time = 86.0,
            title = "公子大气",
            key = "gongzi",
            label = "公子大气",
            particles = listOf("公子大气", "太阔气了", "有排面"),
            broadcast = "已有8.2万人为公子撑场"
        ),
        value(
            id = "ep1_anger_194",
            episode = 1,
            time = 194.0,
            title = "皇帝愤怒值",
            key = "anger",
            label = "皇帝愤怒值",
            initial = 0,
            max = 100,
            step = 20,
            clickText = "连点助燃",
            maxText = "怒气爆表",
            theme = "anger"
        ),
        quiz(
            id = "ep1_quiz_suyu_action",
            episode = 1,
            trigger = InteractionTrigger.Range(265.0, 272.0),
            title = "竞猜一下",
            quizId = "ep1_q1_suyu_action",
            question = "苏羽会不会出手？",
            options = listOf("yes" to "会", "no" to "不会"),
            participants = "8.7万人",
            resultTime = 303.0
        ),
        quizResult(
            id = "ep1_quiz_result_suyu_action",
            episode = 1,
            time = 303.0,
            quizId = "ep1_q1_suyu_action",
            correctOptionId = "yes",
            correctText = "会",
            successText = "恭喜你答对竞猜",
            compareText = "超过78%的观众",
            rewardText = "获得竞猜币一枚"
        ),
        quiz(
            id = "ep2_quiz_qinwu_test",
            episode = 2,
            trigger = InteractionTrigger.EpisodeEnding(8.0),
            title = "竞猜一下",
            quizId = "ep2_q1_qinwu_test",
            question = "秦武会不会测试苏羽？",
            options = listOf("yes" to "会", "no" to "不会"),
            participants = "6.3万人",
            resultEpisodeId = 4,
            resultTime = 177.0,
            afterSubmitAction = "nextEpisode"
        ),
        emotion(
            id = "ep3_laodie_15",
            episode = 3,
            time = 15.0,
            title = "老爹你糊涂啊",
            key = "laodie",
            label = "老爹你糊涂啊",
            particles = listOf("糊涂啊", "别误会他", "老爹醒醒"),
            broadcast = "全站已有6.6万人发出同款吐槽"
        ),
        rating(
            id = "ep3_rating_lvzhen_75",
            episode = 3,
            time = 75.0,
            title = "给吕甄的演技打分",
            ratingId = "ep3_lvzhen_acting",
            question = "给吕甄的演技打几分？",
            min = 1f,
            max = 10f,
            step = 0.5f,
            defaultValue = 8f,
            maxLabel = "10分满分",
            submitText = "确定",
            resultText = "全站平均分 8.9"
        ),
        emotion(
            id = "ep3_heartache_94",
            episode = 3,
            time = 94.0,
            title = "心疼男主",
            key = "heartache",
            label = "心疼苏羽",
            particles = listOf("心疼", "抱抱苏羽", "太能忍了"),
            broadcast = "已有12.3万人心疼苏羽"
        ),
        value(
            id = "ep3_villain_160",
            episode = 3,
            time = 160.0,
            title = "吕甄阴险值",
            key = "villain",
            label = "吕甄阴险值",
            initial = 0,
            max = 100,
            step = 25,
            clickText = "阴险加码",
            maxText = "阴险拉满",
            theme = "villain"
        ),
        knowledge(
            id = "ep3_knowledge_chancellor_170",
            episode = 3,
            time = 170.0,
            eventTitle = "一键了解古代丞相相关知识",
            title = "丞相是什么官？",
            summary = "丞相是古代朝廷中的重要辅政官员，通常掌管政务、统筹百官。在剧情中，丞相往往代表权力中枢，也容易成为朝堂矛盾的核心。",
            tags = listOf("朝廷权力", "辅佐皇帝", "剧情矛盾点")
        ),
        quizResult(
            id = "ep4_quiz_result_qinwu_test",
            episode = 4,
            time = 177.0,
            quizId = "ep2_q1_qinwu_test",
            correctOptionId = "yes",
            correctText = "会",
            successText = "恭喜你答对竞猜",
            compareText = "超过72%的观众",
            rewardText = "获得竞猜币一枚"
        ),
        warning(
            id = "ep5_warning_90",
            episode = 5,
            time = 90.0,
            text = "高能预警",
            countdown = listOf(3, 2, 1),
            theme = "danger"
        ),
        emotion(
            id = "ep6_shuang_119",
            episode = 6,
            time = 59.0,
            title = "爽",
            key = "shuang",
            label = "爽",
            particles = listOf("爽", "太爽了", "爽到"),
            broadcast = "有20.48万人爽到了！"
        ),
        quiz(
            id = "ep7_quiz_join_fight",
            episode = 7,
            trigger = InteractionTrigger.Range(190.0, 293.0),
            title = "竞猜一下",
            quizId = "ep7_q1_join_fight",
            question = "苏羽愿不愿意参加比武招亲？",
            options = listOf("yes" to "愿意", "no" to "不愿意"),
            participants = "10.4万人",
            resultTime = 293.0
        ),
        quizResult(
            id = "ep7_quiz_result_join_fight",
            episode = 7,
            time = 293.0,
            quizId = "ep7_q1_join_fight",
            correctOptionId = "no",
            correctText = "不愿意参加",
            successText = "恭喜你答对竞猜",
            compareText = "超过69%的观众",
            rewardText = "获得竞猜币一枚"
        ),
        emotion(
            id = "ep8_haha_18",
            episode = 8,
            time = 18.0,
            title = "哈哈哈哈",
            key = "haha",
            label = "哈哈哈哈",
            particles = listOf("哈哈哈", "笑死了", "群臣也太损了"),
            broadcast = "已有18.6万人笑出声"
        ),
        quiz(
            id = "ep8_quiz_surrender",
            episode = 8,
            trigger = InteractionTrigger.Range(40.0, 48.0),
            title = "竞猜一下",
            quizId = "ep8_q1_surrender",
            question = "主角会不会投降？",
            options = listOf("yes" to "会投降", "no" to "不会投降"),
            participants = "9.8万人",
            resultEpisodeId = 9,
            resultTime = 201.0
        ),
        quiz(
            id = "ep9_quiz_who_win",
            episode = 9,
            trigger = InteractionTrigger.Range(8.0, 16.0),
            title = "谁会赢？",
            quizId = "ep9_q1_who_win",
            question = "这一场谁会赢？",
            options = listOf("suyu" to "苏羽", "wangxuanzhi" to "王玄志"),
            participants = "12.1万人",
            resultTime = 201.0,
            layout = QuizLayout.VERSUS
        ),
        emotion(
            id = "ep9_ren_64",
            episode = 9,
            time = 64.0,
            title = "忍",
            key = "ren",
            label = "忍",
            particles = listOf("忍住", "继续忍", "还不是时候"),
            broadcast = "已有7.8万人看出他在忍"
        ),
        emotion(
            id = "ep9_wzren_81",
            episode = 9,
            time = 81.0,
            title = "我再忍",
            key = "wzren",
            label = "我再忍",
            particles = listOf("我再忍", "继续装", "还不是时候"),
            broadcast = "已有8.1万人陪苏羽一起忍"
        ),
        emotion(
            id = "ep9_haha_98",
            episode = 9,
            time = 98.0,
            title = "哈哈哈哈哈",
            key = "haha",
            label = "哈哈哈哈",
            particles = listOf("哈哈哈", "摔得太假了", "绷不住了"),
            broadcast = "已有16.2万人笑出声"
        ),
        emotion(
            id = "ep9_continue_fake_117",
            episode = 9,
            time = 117.0,
            title = "继续装",
            key = "continue_fake",
            label = "继续装",
            particles = listOf("继续装", "演起来了", "别露馅"),
            broadcast = "已有9.3万人看穿苏羽在装"
        ),
        emotion(
            id = "ep9_haha_162",
            episode = 9,
            time = 162.0,
            title = "哈哈哈哈哈",
            key = "haha",
            label = "哈哈哈哈",
            particles = listOf("哈哈哈", "笑死了", "太离谱了"),
            broadcast = "已有18.9万人笑出声"
        ),
        quizResult(
            id = "ep9_quiz_result_surrender",
            episode = 9,
            time = 201.0,
            quizId = "ep8_q1_surrender",
            correctOptionId = "yes",
            correctText = "会投降",
            successText = "恭喜你答对竞猜",
            compareText = "超过64%的观众",
            rewardText = "获得竞猜币一枚"
        ),
        quizResult(
            id = "ep9_quiz_result_who_win",
            episode = 9,
            time = 201.0,
            quizId = "ep9_q1_who_win",
            correctOptionId = "wangxuanzhi",
            correctText = "王玄志赢",
            successText = "恭喜你答对竞猜",
            compareText = "超过58%的观众",
            rewardText = "获得竞猜币一枚"
        ),
        emotion(
            id = "ep10_ganta_37",
            episode = 10,
            time = 37.0,
            title = "干他",
            key = "ganta",
            label = "干他",
            particles = listOf("干他", "上啊", "别忍了"),
            broadcast = "全站已有25.6万人想让苏羽出手"
        ),
        emotion(
            id = "ep11_suyu_go_91",
            episode = 11,
            time = 91.0,
            title = "苏羽快上",
            key = "suyu_go",
            label = "苏羽快上",
            particles = listOf("快上", "别装了", "该你出手了"),
            broadcast = "已有14.2万人催苏羽上场"
        ),
        value(
            id = "ep11_forbearance_128",
            episode = 11,
            time = 128.0,
            title = "苏羽隐忍值",
            key = "forbearance",
            label = "苏羽隐忍值",
            initial = 60,
            max = 80,
            step = 5,
            clickText = "继续隐忍",
            maxText = "隐忍达到80%",
            theme = "forbearance",
            persistKey = "suyu_forbearance"
        ),
        value(
            id = "ep12_forbearance_72",
            episode = 12,
            time = 72.0,
            title = "苏羽隐忍值",
            key = "forbearance",
            label = "苏羽隐忍值",
            initial = 80,
            max = 90,
            step = 2,
            clickText = "继续隐忍",
            maxText = "隐忍达到90%",
            theme = "forbearance",
            persistKey = "suyu_forbearance"
        ),
        emotion(
            id = "ep12_stand_up_171",
            episode = 12,
            time = 171.0,
            title = "全体起立",
            key = "stand_up",
            label = "全体起立",
            particles = listOf("全体起立", "起立！", "名场面"),
            broadcast = "本站起立人数达到123.4w"
        ),
        emotion(
            id = "ep13_shuang_149",
            episode = 13,
            time = 149.0,
            title = "爽",
            key = "shuang",
            label = "爽",
            particles = listOf("爽", "太爽了", "爽到"),
            broadcast = "有24.9万人爽到了！"
        ),
        emotion(
            id = "ep15_fengshen_74",
            episode = 15,
            time = 74.0,
            title = "封神",
            key = "fengshen",
            label = "封神",
            particles = listOf("封神", "名场面", "太强了"),
            broadcast = "全站已有36.8万人封神认证"
        ),
        emotion(
            id = "ep16_stand_up_3",
            episode = 16,
            time = 3.0,
            title = "全体起立",
            key = "stand_up",
            label = "全体起立",
            particles = listOf("全体起立", "起立！", "名场面"),
            broadcast = "本站起立人数达到123.4w",
            displayDuration = 3.0,
            showBroadcastAfterSec = 3.0
        ),
        emotion(
            id = "ep16_haha_25",
            episode = 16,
            time = 25.0,
            title = "哈哈哈哈哈",
            key = "haha",
            label = "哈哈哈哈",
            particles = listOf("哈哈哈", "笑死了", "绷不住了"),
            broadcast = "已有19.2万人笑出声"
        ),
        emotion(
            id = "ep16_heartache_145",
            episode = 16,
            time = 145.0,
            title = "心疼苏羽",
            key = "heartache",
            label = "心疼苏羽",
            particles = listOf("心疼", "抱抱苏羽", "太能忍了"),
            broadcast = "已有15.1万人心疼苏羽"
        ),
        highlight(
            id = "ep18_highlight_brotherhood",
            episode = 18,
            trigger = InteractionTrigger.Fixed(40.0),
            highlightId = "ep18_highlight_brotherhood",
            title = "苏羽和皇帝结拜为兄弟",
            clipStart = 0.0,
            clipEnd = ClipEnd.Fixed(44.0),
            coverTime = 40.0,
            buttonText = "收藏名场面",
            successText = "名场面已保存",
            detailText = "片段：第18集 00:00-00:44",
            actionText = "去我的高光查看"
        ),
        highlight(
            id = "ep19_highlight_final",
            episode = 19,
            trigger = InteractionTrigger.EpisodeEnding(4.0),
            highlightId = "ep19_highlight_final",
            title = "第十九集结尾名场面",
            clipStart = 189.0,
            clipEnd = ClipEnd.EpisodeEnd,
            coverTime = null,
            buttonText = "收藏名场面",
            successText = "名场面已保存",
            detailText = "片段：第19集 03:09-本集结尾",
            actionText = "去我的高光查看"
        ),
        emotion(
            id = "ep19_haha_224",
            episode = 19,
            time = 224.0,
            title = "哈哈哈哈哈",
            key = "haha",
            label = "哈哈哈哈",
            particles = listOf("哈哈哈", "笑死了", "太逗了"),
            broadcast = "已有21.7万人笑出声",
            displayDuration = 3.0
        )
    )

    init {
        require(all.size == 39) { "Interaction catalog must contain exactly 39 events" }
        require(all.map { it.id }.toSet().size == all.size) { "Interaction event IDs must be unique" }
    }

    fun forEpisode(episodeNumber: Int): List<InteractionEvent> =
        all.filter { it.episodeNumber == episodeNumber }

    private fun event(
        id: String,
        episode: Int,
        trigger: InteractionTrigger,
        type: InteractionType,
        title: String,
        position: InteractionPosition,
        payload: InteractionPayload,
        displayDuration: Double = 3.0
    ) = InteractionEvent(
        id = id,
        episodeNumber = episode,
        trigger = trigger,
        type = type,
        title = title,
        position = position,
        displayDurationSec = displayDuration,
        payload = payload
    )

    private fun emotion(
        id: String,
        episode: Int,
        time: Double,
        title: String,
        key: String,
        label: String,
        particles: List<String>,
        broadcast: String,
        displayDuration: Double = 3.0,
        showBroadcastAfterSec: Double? = null
    ) = event(
        id, episode, InteractionTrigger.Fixed(time), InteractionType.EMOTION, title,
        InteractionPosition.LEFT_BOTTOM,
        EmotionPayload(
            emotionKey = key,
            label = label,
            particles = particles,
            broadcast = broadcast,
            showBroadcastAfterSec = showBroadcastAfterSec
        ),
        displayDuration
    )

    private fun value(
        id: String,
        episode: Int,
        time: Double,
        title: String,
        key: String,
        label: String,
        initial: Int,
        max: Int,
        step: Int,
        clickText: String,
        maxText: String,
        theme: String,
        persistKey: String? = null
    ) = event(
        id, episode, InteractionTrigger.Fixed(time), InteractionType.VALUE_BOOST, title,
        InteractionPosition.LEFT_BOTTOM,
        ValueBoostPayload(
            valueKey = key,
            label = label,
            initialValue = initial,
            maxValue = max,
            step = step,
            clickText = clickText,
            maxText = maxText,
            theme = theme,
            persistKey = persistKey
        )
    )

    private fun quiz(
        id: String,
        episode: Int,
        trigger: InteractionTrigger,
        title: String,
        quizId: String,
        question: String,
        options: List<Pair<String, String>>,
        participants: String,
        resultTime: Double,
        resultEpisodeId: Int? = null,
        afterSubmitAction: String? = null,
        layout: QuizLayout = QuizLayout.STANDARD
    ) = event(
        id, episode, trigger, InteractionType.QUIZ, title, InteractionPosition.BOTTOM_CENTER,
        QuizPayload(
            quizId = quizId,
            question = question,
            options = options.map { QuizOption(it.first, it.second) },
            participants = participants,
            resultTimeSec = resultTime,
            resultEpisodeId = resultEpisodeId,
            afterSubmitAction = afterSubmitAction,
            layout = layout
        )
    )

    private fun quizResult(
        id: String,
        episode: Int,
        time: Double,
        quizId: String,
        correctOptionId: String,
        correctText: String,
        successText: String,
        compareText: String,
        rewardText: String
    ) = event(
        id, episode, InteractionTrigger.Fixed(time), InteractionType.QUIZ_RESULT,
        "竞猜结果已揭晓", InteractionPosition.TOP_CENTER,
        QuizResultPayload(
            quizId = quizId,
            correctOptionId = correctOptionId,
            correctText = correctText,
            successText = successText,
            compareText = compareText,
            rewardText = rewardText
        ),
        displayDuration = 3.0
    )

    private fun rating(
        id: String,
        episode: Int,
        time: Double,
        title: String,
        ratingId: String,
        question: String,
        min: Float,
        max: Float,
        step: Float,
        defaultValue: Float,
        maxLabel: String,
        submitText: String,
        resultText: String
    ) = event(
        id, episode, InteractionTrigger.Fixed(time), InteractionType.RATING, title,
        InteractionPosition.BOTTOM_CENTER,
        RatingPayload(
            ratingId = ratingId,
            question = question,
            min = min,
            max = max,
            step = step,
            defaultValue = defaultValue,
            maxLabel = maxLabel,
            submitText = submitText,
            resultText = resultText
        )
    )

    private fun knowledge(
        id: String,
        episode: Int,
        time: Double,
        eventTitle: String,
        title: String,
        summary: String,
        tags: List<String>
    ) = event(
        id, episode, InteractionTrigger.Fixed(time), InteractionType.KNOWLEDGE, eventTitle,
        InteractionPosition.LEFT_BOTTOM,
        KnowledgePayload(
            title = title,
            summary = summary,
            tags = tags
        )
    )

    private fun warning(
        id: String,
        episode: Int,
        time: Double,
        text: String,
        countdown: List<Int>,
        theme: String
    ) = event(
        id, episode, InteractionTrigger.Fixed(time), InteractionType.WARNING, text,
        InteractionPosition.CENTER,
        WarningPayload(text = text, countdown = countdown, theme = theme)
    )

    private fun highlight(
        id: String,
        episode: Int,
        trigger: InteractionTrigger,
        highlightId: String,
        title: String,
        clipStart: Double,
        clipEnd: ClipEnd,
        coverTime: Double?,
        buttonText: String,
        successText: String,
        detailText: String,
        actionText: String
    ) = event(
        id, episode, trigger, InteractionType.HIGHLIGHT_COLLECT, "收藏名场面",
        InteractionPosition.LEFT_BOTTOM,
        HighlightPayload(
            highlightId = highlightId,
            title = title,
            clipStartSec = clipStart,
            clipEnd = clipEnd,
            coverTimeSec = coverTime,
            buttonText = buttonText,
            successText = successText,
            detailText = detailText,
            actionText = actionText,
            triggerWhenRemainingSec = (trigger as? InteractionTrigger.EpisodeEnding)?.remainingSec
        )
    )
}
