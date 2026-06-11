package com.sdinteractive.app.interactions.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

internal val InteractionOrange = Color(0xFFFF7A00)
internal val InteractionGold = Color(0xFFFFC447)
internal val InteractionGlass = Color(0xE6212024)
internal val InteractionMuted = Color(0xFFB9B6B2)
internal val InteractionRed = Color(0xFFFF4C35)
internal val InteractionPurple = Color(0xFF9A62FF)

data class EventPalette(
    val accent: Color,
    val secondary: Color,
    val glow: Color
)

data class ReferenceDesignSkin(
    val referenceName: String,
    val componentName: String,
    val skinKey: String
)

fun referenceDesignCoverage(): List<ReferenceDesignSkin> = listOf(
    ReferenceDesignSkin("一键识别任务组件", "PersonInteractionHost", "person_detect_task"),
    ReferenceDesignSkin("丞相管职介绍", "KnowledgeCard", "knowledge_official"),
    ReferenceDesignSkin("人物关系图谱", "RelationGraphModal", "relation_graph_dark"),
    ReferenceDesignSkin("全体起立", "EmotionBurstButton", "stand_up"),
    ReferenceDesignSkin("公子大气", "EmotionBurstButton", "gongzi"),
    ReferenceDesignSkin("名场面保存组件", "HighlightSavedToast", "highlight_saved"),
    ReferenceDesignSkin("吕甄阴险值", "ValueBoostCard", "villain"),
    ReferenceDesignSkin("哈哈哈哈", "EmotionBurstButton", "haha"),
    ReferenceDesignSkin("封神", "EmotionBurstButton", "fengshen"),
    ReferenceDesignSkin("干他", "EmotionBurstButton", "ganta"),
    ReferenceDesignSkin("心疼苏羽", "EmotionBurstButton", "heartache"),
    ReferenceDesignSkin("我再忍", "EmotionBurstButton", "wzren"),
    ReferenceDesignSkin("收藏名场面组件", "HighlightCollectButton", "highlight_collect"),
    ReferenceDesignSkin("演技打分滑动条", "RatingSliderCard", "rating_slider"),
    ReferenceDesignSkin("爽", "EmotionBurstButton", "shuang"),
    ReferenceDesignSkin("皇帝愤怒表", "ValueBoostCard", "anger"),
    ReferenceDesignSkin("竞猜卡片", "QuizCard", "quiz_card"),
    ReferenceDesignSkin("竞猜结果反馈状态", "QuizResultToast", "quiz_result"),
    ReferenceDesignSkin("老爹你糊涂啊", "EmotionBurstButton", "laodie"),
    ReferenceDesignSkin("苏羽快上", "EmotionBurstButton", "suyu_go"),
    ReferenceDesignSkin("苏羽隐忍值", "ValueBoostCard", "forbearance"),
    ReferenceDesignSkin("顶层播报组件", "GlobalBroadcastBar", "broadcast_top"),
    ReferenceDesignSkin("高能预警组件", "HighEnergyWarning", "high_energy_warning")
)

enum class EmotionSkinShape {
    PILL,
    MEDALLION,
    SEAL,
    ACTION
}

data class EmotionSkinSpec(
    val key: String,
    val shape: EmotionSkinShape,
    val widthDp: Int,
    val heightDp: Int,
    val primary: Color,
    val secondary: Color,
    val surface: Color,
    val text: Color,
    val accentWords: List<String>,
    val shake: Boolean = false,
    val leadingMark: String = "",
    val buttonWidthDp: Int = 132,
    val buttonHeightDp: Int = 44,
    val socialProofMaxWidthDp: Int = 170,
    val socialProofHeightDp: Int = 28
)

data class ValueSkinSpec(
    val key: String,
    val badgeText: String,
    val widthDp: Int,
    val heightDp: Int,
    val iconSizeDp: Int,
    val meterHeightDp: Int,
    val comboPromptWidthDp: Int,
    val comboPromptHeightDp: Int,
    val burstLabelWidthDp: Int,
    val shockRingCount: Int,
    val primary: Color,
    val secondary: Color,
    val surface: Color,
    val text: Color,
    val maxParticleCount: Int,
    val maxGlowAlpha: Float,
    val maxBurstText: String
)

data class WarningSkinSpec(
    val key: String,
    val iconKey: String,
    val widthDp: Int,
    val heightDp: Int,
    val countdownSizeSp: Int,
    val primary: Color,
    val secondary: Color,
    val surface: Color
)

data class QuizSkinSpec(
    val widthDp: Int,
    val heightDp: Int,
    val optionHeightDp: Int,
    val resultParticleCount: Int,
    val primary: Color,
    val secondary: Color,
    val surface: Color
)

data class HighlightSkinSpec(
    val collectWidthDp: Int,
    val collectHeightDp: Int,
    val savedWidthDp: Int,
    val savedHeightDp: Int,
    val savedIconSizeDp: Int,
    val savedActionWidthDp: Int,
    val particleCount: Int,
    val primary: Color,
    val secondary: Color,
    val surface: Color
)

data class PersonSkinSpec(
    val detectButtonWidthDp: Int,
    val detectButtonHeightDp: Int,
    val scanCardWidthDp: Int,
    val scanCardHeightDp: Int,
    val resultCardWidthDp: Int,
    val relationModalHeightDp: Int,
    val relationNodeCount: Int,
    val scanParticleCount: Int,
    val scanDurationMs: Int,
    val anchorTopPaddingDp: Int,
    val anchorEndPaddingDp: Int,
    val primaryTopPaddingDp: Int,
    val primaryEndPaddingDp: Int,
    val primary: Color,
    val secondary: Color,
    val surface: Color
)

data class KnowledgeSkinSpec(
    val widthDp: Int,
    val expandedHeightDp: Int,
    val iconSizeDp: Int,
    val tagHeightDp: Int,
    val particleCount: Int,
    val tagCount: Int,
    val primary: Color,
    val secondary: Color,
    val surface: Color,
    val text: Color
)

data class RatingSkinSpec(
    val widthDp: Int,
    val heightDp: Int,
    val valueBubbleWidthDp: Int,
    val valueBubbleHeightDp: Int,
    val sliderHeightDp: Int,
    val submitHeightDp: Int,
    val particleCount: Int,
    val primary: Color,
    val secondary: Color,
    val surface: Color
)

data class BroadcastSkinSpec(
    val widthDp: Int,
    val heightDp: Int,
    val avatarCount: Int,
    val avatarOverlapDp: Int,
    val particleCount: Int,
    val trailingIconKey: String,
    val primary: Color,
    val secondary: Color,
    val surface: Color
)

fun emotionSkinSpec(key: String): EmotionSkinSpec = when (key.lowercase()) {
    "stand_up" -> EmotionSkinSpec(
        key = "stand_up",
        shape = EmotionSkinShape.MEDALLION,
        widthDp = 188,
        heightDp = 126,
        primary = InteractionGold,
        secondary = Color(0xFFFFF1B8),
        surface = Color(0xFF3B2811),
        text = Color.White,
        accentWords = listOf("起立", "全体起立"),
        buttonWidthDp = 76,
        buttonHeightDp = 76
    )
    "gongzi" -> EmotionSkinSpec(
        key = "gongzi",
        shape = EmotionSkinShape.PILL,
        widthDp = 184,
        heightDp = 118,
        primary = Color(0xFFE7B96A),
        secondary = Color(0xFFFFF3C2),
        surface = Color(0xFFF9E5BD),
        text = Color(0xFF5A3411),
        accentWords = listOf("公子", "大气"),
        buttonWidthDp = 128,
        buttonHeightDp = 42
    )
    "haha" -> EmotionSkinSpec(
        key = "haha",
        shape = EmotionSkinShape.PILL,
        widthDp = 196,
        heightDp = 118,
        primary = Color(0xFFFFC65A),
        secondary = Color(0xFFFF8BB2),
        surface = Color(0xFFF7F0EA),
        text = Color(0xFF5C3B20),
        accentWords = listOf("哈哈", "笑死"),
        leadingMark = "😄",
        buttonWidthDp = 132,
        buttonHeightDp = 44
    )
    "fengshen" -> EmotionSkinSpec(
        key = "fengshen",
        shape = EmotionSkinShape.SEAL,
        widthDp = 178,
        heightDp = 124,
        primary = InteractionGold,
        secondary = Color(0xFFFFF4B7),
        surface = Color(0xFF2C2114),
        text = Color.White,
        accentWords = listOf("封神", "名场面"),
        buttonWidthDp = 76,
        buttonHeightDp = 76
    )
    "ganta" -> EmotionSkinSpec(
        key = "ganta",
        shape = EmotionSkinShape.ACTION,
        widthDp = 202,
        heightDp = 120,
        primary = InteractionRed,
        secondary = InteractionOrange,
        surface = Color(0xFF1D0909),
        text = Color.White,
        accentWords = listOf("干他", "上啊"),
        shake = true,
        leadingMark = "🔥",
        buttonWidthDp = 150,
        buttonHeightDp = 46
    )
    "heartache" -> EmotionSkinSpec(
        key = "heartache",
        shape = EmotionSkinShape.PILL,
        widthDp = 202,
        heightDp = 118,
        primary = Color(0xFFFF7DA0),
        secondary = Color(0xFFA8C7FF),
        surface = Color(0xFFF7EEF5),
        text = Color(0xFF6B3144),
        accentWords = listOf("心疼", "抱抱"),
        buttonWidthDp = 142,
        buttonHeightDp = 42
    )
    "wzren",
    "ren" -> EmotionSkinSpec(
        key = "wzren",
        shape = EmotionSkinShape.PILL,
        widthDp = 184,
        heightDp = 112,
        primary = InteractionGold,
        secondary = Color(0xFF8C6A2B),
        surface = Color(0xFF17120C),
        text = Color.White,
        accentWords = listOf("忍", "我再忍"),
        buttonWidthDp = 124,
        buttonHeightDp = 40
    )
    "shuang" -> EmotionSkinSpec(
        key = "shuang",
        shape = EmotionSkinShape.MEDALLION,
        widthDp = 176,
        heightDp = 120,
        primary = InteractionPurple,
        secondary = Color(0xFFFFA8ED),
        surface = Color(0xFFF5EEF9),
        text = Color.White,
        accentWords = listOf("爽"),
        buttonWidthDp = 76,
        buttonHeightDp = 76
    )
    "laodie" -> EmotionSkinSpec(
        key = "laodie",
        shape = EmotionSkinShape.PILL,
        widthDp = 206,
        heightDp = 116,
        primary = Color(0xFFE9A84D),
        secondary = Color(0xFFFFE3A0),
        surface = Color(0xFFF6EFE4),
        text = Color(0xFF5B3A17),
        accentWords = listOf("糊涂", "醒醒"),
        buttonWidthDp = 146,
        buttonHeightDp = 42
    )
    "suyu_go" -> EmotionSkinSpec(
        key = "suyu_go",
        shape = EmotionSkinShape.ACTION,
        widthDp = 202,
        heightDp = 118,
        primary = InteractionRed,
        secondary = InteractionGold,
        surface = Color(0xFF2A0E0B),
        text = Color.White,
        accentWords = listOf("快上", "出手"),
        shake = true,
        leadingMark = "↗",
        buttonWidthDp = 150,
        buttonHeightDp = 46
    )
    else -> EmotionSkinSpec(
        key = key,
        shape = EmotionSkinShape.PILL,
        widthDp = 184,
        heightDp = 116,
        primary = InteractionOrange,
        secondary = InteractionGold,
        surface = Color(0xFF271B12),
        text = Color.White,
        accentWords = listOf(key)
    )
}

internal fun emotionPalette(key: String): EventPalette = when (key.lowercase()) {
    "shuang", "haha" -> EventPalette(InteractionPurple, Color(0xFFFF73D0), Color(0x669A62FF))
    "heartache" -> EventPalette(Color(0xFFFF668A), Color(0xFFFFB2C4), Color(0x66FF668A))
    "ren", "wzren", "forbearance" -> EventPalette(InteractionGold, Color(0xFFFFE3A0), Color(0x66FFC447))
    "ganta", "anger" -> EventPalette(InteractionRed, InteractionOrange, Color(0x66FF4C35))
    else -> EventPalette(InteractionOrange, InteractionGold, Color(0x66FF7A00))
}

internal fun valuePalette(theme: String): EventPalette = when (theme.lowercase()) {
    "anger" -> EventPalette(InteractionRed, InteractionOrange, Color(0x66FF4C35))
    "villain" -> EventPalette(InteractionPurple, Color(0xFF6D3DB8), Color(0x669A62FF))
    "forbearance" -> EventPalette(InteractionGold, Color(0xFF8F702A), Color(0x55FFC447))
    else -> EventPalette(InteractionOrange, InteractionGold, Color(0x55FF7A00))
}

fun valueSkinSpec(theme: String): ValueSkinSpec = when (theme.lowercase()) {
    "anger" -> ValueSkinSpec(
        key = "anger",
        badgeText = "怒气值",
        widthDp = 236,
        heightDp = 78,
        iconSizeDp = 36,
        meterHeightDp = 18,
        comboPromptWidthDp = 114,
        comboPromptHeightDp = 30,
        burstLabelWidthDp = 92,
        shockRingCount = 6,
        primary = InteractionRed,
        secondary = InteractionOrange,
        surface = Color(0xF0181112),
        text = Color.White,
        maxParticleCount = 16,
        maxGlowAlpha = 0.34f,
        maxBurstText = "怒气爆表"
    )
    "villain" -> ValueSkinSpec(
        key = "villain",
        badgeText = "阴险值",
        widthDp = 236,
        heightDp = 78,
        iconSizeDp = 36,
        meterHeightDp = 18,
        comboPromptWidthDp = 114,
        comboPromptHeightDp = 30,
        burstLabelWidthDp = 92,
        shockRingCount = 5,
        primary = InteractionPurple,
        secondary = Color(0xFFB678FF),
        surface = Color(0xF018121F),
        text = Color.White,
        maxParticleCount = 12,
        maxGlowAlpha = 0.28f,
        maxBurstText = "阴险拉满"
    )
    "forbearance" -> ValueSkinSpec(
        key = "forbearance",
        badgeText = "隐忍值",
        widthDp = 236,
        heightDp = 78,
        iconSizeDp = 36,
        meterHeightDp = 18,
        comboPromptWidthDp = 114,
        comboPromptHeightDp = 30,
        burstLabelWidthDp = 92,
        shockRingCount = 4,
        primary = InteractionGold,
        secondary = Color(0xFFFFE1A0),
        surface = Color(0xF017130E),
        text = Color.White,
        maxParticleCount = 10,
        maxGlowAlpha = 0.22f,
        maxBurstText = "继续隐忍"
    )
    else -> ValueSkinSpec(
        key = theme,
        badgeText = theme.take(3),
        widthDp = 236,
        heightDp = 78,
        iconSizeDp = 36,
        meterHeightDp = 18,
        comboPromptWidthDp = 114,
        comboPromptHeightDp = 30,
        burstLabelWidthDp = 92,
        shockRingCount = 4,
        primary = InteractionOrange,
        secondary = InteractionGold,
        surface = Color(0xF0181512),
        text = Color.White,
        maxParticleCount = 8,
        maxGlowAlpha = 0.20f,
        maxBurstText = "已拉满"
    )
}

fun warningSkinSpec(theme: String): WarningSkinSpec = when (theme.lowercase()) {
    "danger" -> WarningSkinSpec(
        key = "danger",
        iconKey = "triangle",
        widthDp = 188,
        heightDp = 42,
        countdownSizeSp = 3,
        primary = InteractionRed,
        secondary = Color(0xFFFFA06A),
        surface = Color(0xEE120D10)
    )
    else -> WarningSkinSpec(
        key = theme,
        iconKey = "triangle",
        widthDp = 188,
        heightDp = 42,
        countdownSizeSp = 3,
        primary = InteractionRed,
        secondary = InteractionGold,
        surface = Color(0xEE120D10)
    )
}

fun quizSkinSpec(): QuizSkinSpec = QuizSkinSpec(
    widthDp = 276,
    heightDp = 156,
    optionHeightDp = 44,
    resultParticleCount = 9,
    primary = InteractionOrange,
    secondary = InteractionGold,
    surface = Color(0xEE202127)
)

fun highlightSkinSpec(): HighlightSkinSpec = HighlightSkinSpec(
    collectWidthDp = 168,
    collectHeightDp = 44,
    savedWidthDp = 300,
    savedHeightDp = 76,
    savedIconSizeDp = 40,
    savedActionWidthDp = 58,
    particleCount = 8,
    primary = InteractionGold,
    secondary = Color(0xFFFFE4A2),
    surface = Color(0xEE17171B)
)

fun personSkinSpec(): PersonSkinSpec = PersonSkinSpec(
    detectButtonWidthDp = 128,
    detectButtonHeightDp = 44,
    scanCardWidthDp = 192,
    scanCardHeightDp = 212,
    resultCardWidthDp = 300,
    relationModalHeightDp = 520,
    relationNodeCount = 7,
    scanParticleCount = 11,
    scanDurationMs = 1000,
    anchorTopPaddingDp = 66,
    anchorEndPaddingDp = 18,
    primaryTopPaddingDp = 66,
    primaryEndPaddingDp = 18,
    primary = Color(0xFFE8EDF7),
    secondary = InteractionGold,
    surface = Color(0xE61E222B)
)

fun knowledgeSkinSpec(): KnowledgeSkinSpec = KnowledgeSkinSpec(
    widthDp = 300,
    expandedHeightDp = 190,
    iconSizeDp = 48,
    tagHeightDp = 34,
    particleCount = 8,
    tagCount = 3,
    primary = Color(0xFF4E8FE8),
    secondary = Color(0xFFB9DCFF),
    surface = Color(0xF5F7FCFF),
    text = Color(0xFF17263B)
)

fun ratingSkinSpec(): RatingSkinSpec = RatingSkinSpec(
    widthDp = 300,
    heightDp = 212,
    valueBubbleWidthDp = 48,
    valueBubbleHeightDp = 32,
    sliderHeightDp = 52,
    submitHeightDp = 46,
    particleCount = 10,
    primary = InteractionPurple,
    secondary = Color(0xFFC7A7FF),
    surface = Color(0xE91C1E27)
)

fun broadcastSkinSpec(): BroadcastSkinSpec = BroadcastSkinSpec(
    widthDp = 340,
    heightDp = 54,
    avatarCount = 3,
    avatarOverlapDp = 14,
    particleCount = 11,
    trailingIconKey = "heat",
    primary = Color(0xFFFF7A61),
    secondary = InteractionGold,
    surface = Color(0xEE1A1B22)
)

@Composable
internal fun GlassSurface(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val shimmer = rememberInfiniteTransition(label = "glassSurfaceMotion")
    val shimmerProgress by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing)),
        label = "glassSurfaceSweep"
    )
    Box(
        modifier = modifier
            .shadow(22.dp, shape, ambientColor = accent.copy(alpha = 0.18f), spotColor = accent.copy(alpha = 0.24f))
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xF02D3038),
                        InteractionGlass,
                        Color(0xF016171B)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
            .border(1.dp, accent.copy(alpha = 0.54f), shape)
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.24f), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(1.dp)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.13f), Color.Transparent, Color.Black.copy(alpha = 0.16f))
                    )
                )
        )
        Canvas(Modifier.matchParentSize()) {
            val sweepX = size.width * (shimmerProgress * 1.35f - 0.2f)
            drawLine(
                color = Color.White.copy(alpha = 0.16f),
                start = Offset(sweepX, -size.height * 0.12f),
                end = Offset(sweepX + size.width * 0.26f, size.height * 1.12f),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
            repeat(6) { index ->
                val phase = (shimmerProgress + index * 0.17f) % 1f
                drawCircle(
                    color = accent.copy(alpha = 0.10f + phase * 0.12f),
                    radius = 1.4f + (index % 3),
                    center = Offset(
                        x = size.width * (0.12f + (index * 0.16f + phase * 0.08f) % 0.76f),
                        y = size.height * (0.18f + (index % 4) * 0.19f)
                    )
                )
            }
        }
        Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), content = content)
    }
}

@Composable
internal fun Modifier.pressFeedback(
    enabled: Boolean = true,
    role: Role = Role.Button,
    selected: Boolean? = null,
    stateDescription: String? = null,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.94f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "interactionPress"
    )
    return minimumInteractiveComponentSize()
        .semantics {
            selected?.let { this.selected = it }
            stateDescription?.let { this.stateDescription = it }
        }
        .graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(
        enabled = enabled,
        role = role,
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        onClick = onClick
    )
}
