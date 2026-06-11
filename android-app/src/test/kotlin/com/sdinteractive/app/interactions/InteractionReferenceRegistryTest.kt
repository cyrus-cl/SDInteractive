package com.sdinteractive.app.interactions

import com.sdinteractive.app.interactions.data.InteractionReferenceRegistry
import com.sdinteractive.app.interactions.model.EmotionPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionReferenceRegistryTest {
    @Test
    fun `all 23 reference images are represented by dynamic component specs`() {
        val expected = setOf(
            "一键识别任务组件.png",
            "丞相管职介绍.png",
            "人物关系图谱.png",
            "全体起立.png",
            "公子大气.png",
            "名场面保存组件.png",
            "吕甄阴险值.png",
            "哈哈哈哈.png",
            "封神.png",
            "干他.png",
            "心疼苏羽.png",
            "我再忍.png",
            "收藏名场面组件.png",
            "演技打分滑动条.png",
            "爽.png",
            "皇帝愤怒表.png",
            "竞猜卡片.png",
            "竞猜结果反馈状态.png",
            "老爹你糊涂啊.png",
            "苏羽快上.png",
            "苏羽隐忍值.png",
            "顶层播报组件.png",
            "高能预警组件.png"
        )

        val actual = InteractionReferenceRegistry.specs.map { it.referenceImageName }.toSet()

        assertEquals(expected, actual)
        assertEquals(23, InteractionReferenceRegistry.specs.size)
        assertTrue(InteractionReferenceRegistry.specs.all { it.debugEvent.id.startsWith("reference_") })
    }

    @Test
    fun `reference emotion previews carry skin-specific particle words`() {
        val previews = InteractionReferenceRegistry.previewEvents(7)
        fun particles(title: String): List<String> =
            (previews.single { it.title == title }.payload as EmotionPayload).particles

        assertTrue("😄" in particles("哈哈哈哈"))
        assertTrue("🔥" in particles("干他"))
        assertTrue("💗" in particles("心疼苏羽"))
        assertTrue("金光" in particles("封神"))
    }
}
