package com.sdinteractive.app.interactions

import com.sdinteractive.app.interactions.data.InteractionReferenceRegistry
import com.sdinteractive.app.interactions.data.CharacterData
import com.sdinteractive.app.interactions.model.InteractionType
import com.sdinteractive.app.interactions.ui.referenceDesignCoverage
import com.sdinteractive.app.interactions.ui.EmotionSkinShape
import com.sdinteractive.app.interactions.ui.broadcastSkinSpec
import com.sdinteractive.app.interactions.ui.emotionSkinSpec
import com.sdinteractive.app.interactions.ui.highlightSkinSpec
import com.sdinteractive.app.interactions.ui.knowledgeSkinSpec
import com.sdinteractive.app.interactions.ui.personSkinSpec
import com.sdinteractive.app.interactions.ui.quizSkinSpec
import com.sdinteractive.app.interactions.ui.ratingSkinSpec
import com.sdinteractive.app.interactions.ui.relationsForFocus
import com.sdinteractive.app.interactions.ui.valueSkinSpec
import com.sdinteractive.app.interactions.ui.warningSkinSpec
import com.sdinteractive.app.interactions.ui.shouldShowAmbientPersonHost
import com.sdinteractive.app.interactions.ui.ambientPersonTopPaddingDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionSkinTest {
    @Test
    fun `all twenty three reference designs have a component skin`() {
        val expected = listOf(
            "一键识别任务组件",
            "丞相管职介绍",
            "人物关系图谱",
            "全体起立",
            "公子大气",
            "名场面保存组件",
            "吕甄阴险值",
            "哈哈哈哈",
            "封神",
            "干他",
            "心疼苏羽",
            "我再忍",
            "收藏名场面组件",
            "演技打分滑动条",
            "爽",
            "皇帝愤怒表",
            "竞猜卡片",
            "竞猜结果反馈状态",
            "老爹你糊涂啊",
            "苏羽快上",
            "苏羽隐忍值",
            "顶层播报组件",
            "高能预警组件"
        )

        assertEquals(expected, referenceDesignCoverage().map { it.referenceName })
        assertEquals(23, referenceDesignCoverage().map { it.skinKey }.distinct().size)
    }

    @Test
    fun `emotion skins preserve reference visual families`() {
        assertEquals(EmotionSkinShape.MEDALLION, emotionSkinSpec("stand_up").shape)
        assertEquals(EmotionSkinShape.ACTION, emotionSkinSpec("ganta").shape)
        assertEquals(EmotionSkinShape.MEDALLION, emotionSkinSpec("shuang").shape)
        assertEquals(EmotionSkinShape.PILL, emotionSkinSpec("wzren").shape)
        assertEquals(202, emotionSkinSpec("suyu_go").widthDp)
        assertEquals(true, emotionSkinSpec("ganta").shake)
        assertEquals("😄", emotionSkinSpec("haha").leadingMark)
        assertEquals("🔥", emotionSkinSpec("ganta").leadingMark)
        assertEquals("↗", emotionSkinSpec("suyu_go").leadingMark)
    }

    @Test
    fun `emotion skins expose compact button dimensions from reference states`() {
        val haha = emotionSkinSpec("haha")
        val ganta = emotionSkinSpec("ganta")
        val standUp = emotionSkinSpec("stand_up")
        val fengshen = emotionSkinSpec("fengshen")

        assertEquals(132, haha.buttonWidthDp)
        assertEquals(44, haha.buttonHeightDp)
        assertEquals(150, ganta.buttonWidthDp)
        assertEquals(46, ganta.buttonHeightDp)
        assertEquals(76, standUp.buttonWidthDp)
        assertEquals(76, standUp.buttonHeightDp)
        assertEquals(76, fengshen.buttonWidthDp)
        assertEquals(76, fengshen.buttonHeightDp)
        assertEquals(170, haha.socialProofMaxWidthDp)
        assertEquals(28, haha.socialProofHeightDp)
    }

    @Test
    fun `value skins match compact reference meters`() {
        val anger = valueSkinSpec("anger")
        val villain = valueSkinSpec("villain")
        val forbearance = valueSkinSpec("forbearance")

        assertEquals("怒气值", anger.badgeText)
        assertEquals("阴险值", villain.badgeText)
        assertEquals("隐忍值", forbearance.badgeText)
        assertEquals(236, anger.widthDp)
        assertEquals(236, villain.widthDp)
        assertEquals(236, forbearance.widthDp)
        assertEquals(78, anger.heightDp)
        assertEquals(36, anger.iconSizeDp)
        assertEquals(18, anger.meterHeightDp)
        assertEquals(114, anger.comboPromptWidthDp)
        assertEquals(30, anger.comboPromptHeightDp)
        assertEquals(92, anger.burstLabelWidthDp)
        assertEquals(6, anger.shockRingCount)
        assertEquals(16, anger.maxParticleCount)
        assertEquals(12, villain.maxParticleCount)
        assertEquals(10, forbearance.maxParticleCount)
        assertEquals("怒气爆表", anger.maxBurstText)
        assertEquals("阴险拉满", villain.maxBurstText)
        assertEquals("继续隐忍", forbearance.maxBurstText)
    }

    @Test
    fun `warning and quiz skins match compact reference cards`() {
        val warning = warningSkinSpec("danger")
        val quiz = quizSkinSpec()

        assertEquals("triangle", warning.iconKey)
        assertEquals(188, warning.widthDp)
        assertEquals(42, warning.heightDp)
        assertEquals(3, warning.countdownSizeSp)
        assertEquals(276, quiz.widthDp)
        assertEquals(156, quiz.heightDp)
        assertEquals(44, quiz.optionHeightDp)
        assertEquals(9, quiz.resultParticleCount)
    }

    @Test
    fun `highlight skins match collect and saved reference states`() {
        val highlight = highlightSkinSpec()

        assertEquals(168, highlight.collectWidthDp)
        assertEquals(44, highlight.collectHeightDp)
        assertEquals(300, highlight.savedWidthDp)
        assertEquals(76, highlight.savedHeightDp)
        assertEquals(40, highlight.savedIconSizeDp)
        assertEquals(58, highlight.savedActionWidthDp)
        assertEquals(8, highlight.particleCount)
    }

    @Test
    fun `person skins match scan task and relation graph references`() {
        val person = personSkinSpec()

        assertEquals(128, person.detectButtonWidthDp)
        assertEquals(44, person.detectButtonHeightDp)
        assertEquals(192, person.scanCardWidthDp)
        assertEquals(212, person.scanCardHeightDp)
        assertEquals(300, person.resultCardWidthDp)
        assertEquals(520, person.relationModalHeightDp)
        assertEquals(7, person.relationNodeCount)
        assertEquals(11, person.scanParticleCount)
        assertEquals(1000, person.scanDurationMs)
        assertEquals(66, person.anchorTopPaddingDp)
        assertEquals(18, person.anchorEndPaddingDp)
        assertEquals(66, person.primaryTopPaddingDp)
        assertEquals(18, person.primaryEndPaddingDp)
    }

    @Test
    fun `ambient person recognition remains available beside non blocking primary interactions`() {
        assertTrue(
            shouldShowAmbientPersonHost(
                hasResults = false,
                hasSavedToast = false,
                hasBroadcast = false,
                primaryType = InteractionType.KNOWLEDGE
            )
        )
        assertTrue(
            shouldShowAmbientPersonHost(
                hasResults = false,
                hasSavedToast = false,
                hasBroadcast = false,
                primaryType = InteractionType.VALUE_BOOST
            )
        )
        assertFalse(
            shouldShowAmbientPersonHost(
                hasResults = false,
                hasSavedToast = false,
                hasBroadcast = false,
                primaryType = InteractionType.PERSON_DETECT
            )
        )
        assertFalse(
            shouldShowAmbientPersonHost(
                hasResults = false,
                hasSavedToast = false,
                hasBroadcast = false,
                primaryType = InteractionType.RELATION_GRAPH
            )
        )
        assertTrue(
            shouldShowAmbientPersonHost(
                hasResults = false,
                hasSavedToast = false,
                hasBroadcast = true,
                primaryType = null
            )
        )
        assertTrue(
            shouldShowAmbientPersonHost(
                hasResults = true,
                hasSavedToast = true,
                hasBroadcast = true,
                primaryType = InteractionType.KNOWLEDGE
            )
        )
    }

    @Test
    fun `ambient person recognition moves below top broadcast to avoid overlap`() {
        val skin = personSkinSpec()

        assertEquals(
            skin.anchorTopPaddingDp,
            ambientPersonTopPaddingDp(
                hasResults = false,
                hasSavedToast = false,
                hasBroadcast = false,
                skin = skin
            )
        )
        assertTrue(
            ambientPersonTopPaddingDp(
                hasResults = false,
                hasSavedToast = false,
                hasBroadcast = true,
                skin = skin
            ) > skin.anchorTopPaddingDp
        )
        assertTrue(
            ambientPersonTopPaddingDp(
                hasResults = true,
                hasSavedToast = true,
                hasBroadcast = true,
                skin = skin
            ) >
                ambientPersonTopPaddingDp(
                    hasResults = false,
                    hasSavedToast = false,
                    hasBroadcast = true,
                    skin = skin
                )
        )
    }

    @Test
    fun `relation graph preview backfills enough core character nodes`() {
        val relations = CharacterData.relationsForGraph(episodeNumber = 1, minimumNodeCount = 7)
        val nodeIds = relations
            .flatMap { listOf(it.sourceCharacterId, it.targetCharacterId) }
            .distinct()

        assertEquals(7, nodeIds.size)
        assertTrue(nodeIds.contains("suyu"))
        assertTrue(nodeIds.contains("lvzhen"))
    }

    @Test
    fun `focused relation screen only shows direct relations`() {
        val focused = relationsForFocus(
            focusCharacterId = "jinning",
            relations = CharacterData.relations
        )

        assertEquals(1, focused.size)
        assertTrue(
            focused.all {
                it.sourceCharacterId == "jinning" || it.targetCharacterId == "jinning"
            }
        )
    }

    @Test
    fun `knowledge skin matches official explanation reference card`() {
        val knowledge = knowledgeSkinSpec()

        assertEquals(300, knowledge.widthDp)
        assertEquals(190, knowledge.expandedHeightDp)
        assertEquals(48, knowledge.iconSizeDp)
        assertEquals(34, knowledge.tagHeightDp)
        assertEquals(8, knowledge.particleCount)
        assertEquals(3, knowledge.tagCount)
    }

    @Test
    fun `rating skin matches purple glass slider reference`() {
        val rating = ratingSkinSpec()

        assertEquals(300, rating.widthDp)
        assertEquals(212, rating.heightDp)
        assertEquals(48, rating.valueBubbleWidthDp)
        assertEquals(32, rating.valueBubbleHeightDp)
        assertEquals(52, rating.sliderHeightDp)
        assertEquals(46, rating.submitHeightDp)
        assertEquals(10, rating.particleCount)
    }

    @Test
    fun `broadcast skin matches top heat broadcast reference`() {
        val broadcast = broadcastSkinSpec()

        assertEquals(340, broadcast.widthDp)
        assertEquals(54, broadcast.heightDp)
        assertEquals(3, broadcast.avatarCount)
        assertEquals(14, broadcast.avatarOverlapDp)
        assertEquals(11, broadcast.particleCount)
        assertEquals("heat", broadcast.trailingIconKey)
    }

    @Test
    fun `reference preview events are titled by source image names`() {
        val expectedTitles = listOf(
            "一键识别任务组件",
            "丞相管职介绍",
            "人物关系图谱",
            "全体起立",
            "公子大气",
            "名场面保存组件",
            "吕甄阴险值",
            "哈哈哈哈",
            "封神",
            "干他",
            "心疼苏羽",
            "我再忍",
            "收藏名场面组件",
            "演技打分滑动条",
            "爽",
            "皇帝愤怒表",
            "竞猜卡片",
            "竞猜结果反馈状态",
            "老爹你糊涂啊",
            "苏羽快上",
            "苏羽隐忍值",
            "顶层播报组件",
            "高能预警组件"
        )

        assertEquals(expectedTitles, InteractionReferenceRegistry.previewEvents(7).map { it.title })
        assertEquals(listOf(7), InteractionReferenceRegistry.previewEvents(7).map { it.episodeNumber }.distinct())
        assertTrue(InteractionReferenceRegistry.previewEvents(7).all { it.displayDurationSec >= 8.0 })
    }

    @Test
    fun `saved highlight reference previews saved toast separately from collect button`() {
        val previews = InteractionReferenceRegistry.previewEvents(7)
        val saved = previews.single { it.title == "名场面保存组件" }
        val collect = previews.single { it.title == "收藏名场面组件" }

        assertEquals(InteractionType.HIGHLIGHT_COLLECT, saved.type)
        assertTrue(InteractionReferenceRegistry.isSavedToastPreview(saved))
        assertFalse(InteractionReferenceRegistry.isSavedToastPreview(collect))
    }

    @Test
    fun `knowledge reference preview opens as expanded card`() {
        val knowledge = InteractionReferenceRegistry.previewEvents(7)
            .single { it.title == "丞相管职介绍" }

        assertTrue(InteractionReferenceRegistry.isExpandedKnowledgePreview(knowledge))
    }
}
