package com.sdinteractive.app.interactions.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdinteractive.app.interactions.engine.QuizOutcome
import com.sdinteractive.app.interactions.engine.QuizResultState
import com.sdinteractive.app.interactions.engine.quizDeadlineLabel
import com.sdinteractive.app.interactions.model.InteractionTrigger
import com.sdinteractive.app.interactions.model.QuizLayout
import com.sdinteractive.app.interactions.model.QuizPayload
import com.sdinteractive.app.interactions.storage.QuizAnswerRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun QuizCard(
    payload: QuizPayload,
    trigger: InteractionTrigger,
    positionSec: Double,
    durationMs: Long,
    restoredAnswer: QuizAnswerRecord?,
    onSubmit: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    val skin = quizSkinSpec()
    var selectedId by remember(payload.quizId, restoredAnswer) {
        mutableStateOf(restoredAnswer?.selectedOptionId)
    }
    val scope = rememberCoroutineScope()
    val submitted = restoredAnswer != null
    val deadlineLabel = quizDeadlineLabel(
        trigger = trigger,
        snapshot = com.sdinteractive.app.player.PlayerSnapshot(
            positionMs = (positionSec * 1_000.0).toLong(),
            durationMs = durationMs,
            isPlaying = true
        )
    )
    val motion = rememberInfiniteTransition(label = "quizCardMotion")
    val flowProgress by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_900, easing = LinearEasing)),
        label = "quizCardFlow"
    )

    GlassSurface(
        accent = skin.primary,
        modifier = modifier
            .width(skin.widthDp.dp)
            .height(skin.heightDp.dp)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.matchParentSize()) {
                val y = size.height * (0.08f + flowProgress * 0.08f)
                drawLine(
                    color = skin.secondary.copy(alpha = 0.18f),
                    start = Offset(size.width * 0.06f, y),
                    end = Offset(size.width * 0.94f, y + size.height * 0.12f),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
                repeat(6) { index ->
                    val phase = (flowProgress + index * 0.14f) % 1f
                    drawCircle(
                        color = if (index % 2 == 0) skin.secondary else skin.primary,
                        radius = 2f + index % 2,
                        center = Offset(
                            x = size.width * (0.12f + phase * 0.78f),
                            y = size.height * (0.22f + (index % 3) * 0.22f)
                        ),
                        alpha = 0.18f + (1f - phase) * 0.32f
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("竞猜时刻", color = skin.secondary, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    text = deadlineLabel ?: payload.participants,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
            Text(
                payload.question,
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Black
            )
            val selectOption: (String) -> Unit = { optionId ->
                if (!submitted && selectedId == null) {
                    selectedId = optionId
                    scope.launch {
                        delay(150L)
                        if (!onSubmit(optionId)) {
                            selectedId = null
                        }
                    }
                }
            }
            if (payload.layout == QuizLayout.VERSUS && payload.options.size == 2) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuizOptionButton(
                        text = payload.options[0].text,
                        selected = selectedId == payload.options[0].id,
                        enabled = !submitted && selectedId == null,
                        modifier = Modifier.weight(1f),
                        heightDp = skin.optionHeightDp,
                        onClick = { selectOption(payload.options[0].id) }
                    )
                    Text(
                        text = "VS",
                        color = skin.secondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                    QuizOptionButton(
                        text = payload.options[1].text,
                        selected = selectedId == payload.options[1].id,
                        enabled = !submitted && selectedId == null,
                        modifier = Modifier.weight(1f),
                        heightDp = skin.optionHeightDp,
                        onClick = { selectOption(payload.options[1].id) }
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    payload.options.take(2).forEach { option ->
                        QuizOptionButton(
                            text = option.text,
                        selected = selectedId == option.id,
                        enabled = !submitted && selectedId == null,
                        modifier = Modifier.weight(1f),
                        heightDp = skin.optionHeightDp,
                        onClick = { selectOption(option.id) }
                    )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedId != null) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = skin.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = if (selectedId != null) "已参与竞猜" else "${payload.participants} 已参与",
                    color = if (selectedId != null) skin.secondary else InteractionMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        }
    }
}

@Composable
private fun QuizOptionButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    heightDp: Int,
    onClick: () -> Unit
) {
    val borderAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.25f,
        label = "quizOptionBorder"
    )
    val optionScale by animateFloatAsState(
        targetValue = if (selected) 1.035f else 1f,
        animationSpec = tween(140),
        label = "quizOptionScale"
    )
    Row(
        modifier = modifier
            .height(heightDp.dp)
            .graphicsLayer {
                scaleX = optionScale
                scaleY = optionScale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0x44FF7A00) else Color(0x24FFFFFF))
            .border(
                width = 1.dp,
                color = InteractionOrange.copy(alpha = borderAlpha),
                shape = RoundedCornerShape(8.dp)
            )
            .pressFeedback(
                enabled = enabled,
                role = Role.RadioButton,
                selected = selected,
                stateDescription = if (selected) "已选择" else "未选择",
                onClick = onClick
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        AnimatedVisibility(selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "已选择",
                tint = InteractionGold,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun QuizResultToast(
    result: QuizResultState,
    modifier: Modifier = Modifier
) {
    val skin = quizSkinSpec()
    val accent = when (result.outcome) {
        QuizOutcome.CORRECT -> InteractionGold
        QuizOutcome.INCORRECT -> Color(0xFFFF8A65)
        QuizOutcome.NOT_PARTICIPATED -> Color(0xFFB8B4C0)
    }
    val headline = when (result.outcome) {
        QuizOutcome.CORRECT -> result.payload.successText
        QuizOutcome.INCORRECT -> "竞猜未命中"
        QuizOutcome.NOT_PARTICIPATED -> "本轮未参与"
    }
    GlassSurface(
        accent = accent,
        modifier = modifier
            .width(skin.widthDp.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = headline
            }
    ) {
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.matchParentSize()) {
                repeat(8) { index ->
                    val x = size.width * (0.06f + index * 0.12f)
                    drawCircle(
                        color = accent.copy(alpha = 0.10f + (index % 3) * 0.04f),
                        radius = 2f + (index % 3),
                        center = Offset(x, size.height * (0.18f + (index % 4) * 0.18f))
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(accent.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = accent)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(headline, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(
                    "正确答案：${result.payload.correctText}",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 13.sp
                )
                Text(
                    result.payload.compareText,
                    color = InteractionMuted,
                    fontSize = 12.sp
                )
            }
            if (result.outcome == QuizOutcome.CORRECT) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CoinBurst(skin.resultParticleCount)
                    Text(
                        result.payload.rewardText,
                        color = InteractionGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun CoinBurst(particleCount: Int) {
    val transition = rememberInfiniteTransition(label = "quizCoins")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_200, easing = LinearEasing)
        ),
        label = "quizCoinRise"
    )
    Canvas(Modifier.size(width = 70.dp, height = 48.dp)) {
        repeat(particleCount) { index ->
            val phase = (progress + index * 0.11f) % 1f
            val x = size.width * (0.12f + (index % 4) * 0.24f)
            val y = size.height * (1f - phase)
            drawCircle(
                color = if (index % 2 == 0) InteractionGold else Color(0xFFFFE6A1),
                radius = 3.5f + index % 3,
                center = androidx.compose.ui.geometry.Offset(x, y),
                alpha = (1f - phase).coerceIn(0.15f, 1f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.65f * (1f - phase)),
                radius = 1.2f,
                center = androidx.compose.ui.geometry.Offset(x - 1f, y - 1f)
            )
        }
    }
}
