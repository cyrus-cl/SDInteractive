package com.sdinteractive.app.interactions.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdinteractive.app.interactions.model.ValueBoostPayload
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ValueBoostCard(
    payload: ValueBoostPayload,
    value: Int,
    reachedMax: Boolean,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = valuePalette(payload.theme)
    val skin = valueSkinSpec(payload.theme)
    val shape = RoundedCornerShape(8.dp)
    val pulse = remember(payload.valueKey) { Animatable(1f) }
    val progress by animateFloatAsState(
        targetValue = (value.toFloat() / payload.maxValue.coerceAtLeast(1)).coerceIn(0f, 1f),
        animationSpec = tween(180),
        label = "valueProgress"
    )
    val energy = rememberInfiniteTransition(label = "valueEnergy")
    val energyProgress by energy.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_250, easing = LinearEasing)),
        label = "valueEnergyFlow"
    )
    val comboCount = ((value - payload.initialValue) / payload.step)
        .coerceAtLeast(if (value > payload.initialValue) 1 else 0)
    val showCombo = comboCount > 0 && !reachedMax

    LaunchedEffect(value) {
        pulse.snapTo(1f)
        pulse.animateTo(if (reachedMax) 1.055f else 1.03f, tween(90))
        pulse.animateTo(1f, spring(dampingRatio = 0.52f, stiffness = 620f))
    }

    Box(
        modifier = modifier
            .width(skin.widthDp.dp)
            .height((skin.heightDp + 38).dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .width(skin.widthDp.dp)
                .height(skin.heightDp.dp)
                .graphicsLayer {
                    scaleX = pulse.value
                    scaleY = pulse.value
                }
                .shadow(
                    elevation = if (reachedMax) 28.dp else 18.dp,
                    shape = shape,
                    ambientColor = palette.glow.copy(alpha = if (reachedMax) 0.70f else 0.38f),
                    spotColor = palette.glow.copy(alpha = if (reachedMax) 0.82f else 0.48f)
                )
                .clip(shape)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            skin.surface,
                            Color(0xF017171D),
                            skin.primary.copy(alpha = if (reachedMax) 0.50f else 0.28f)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
                .border(1.dp, skin.primary.copy(alpha = 0.34f + progress * 0.48f), shape)
                .pressFeedback(
                    enabled = !reachedMax,
                    stateDescription = if (reachedMax) payload.maxText else "$value%",
                    onClick = onIncrement
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            ValueMeterBackdrop(
                skin = skin,
                palette = palette,
                progress = progress,
                energyProgress = energyProgress,
                reachedMax = reachedMax,
                modifier = Modifier.matchParentSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ValueSigil(
                        text = valueSigil(payload.theme),
                        skin = skin,
                        energyProgress = energyProgress,
                        modifier = Modifier.size(skin.iconSizeDp.dp)
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        ValueTitle(payload = payload, skin = skin)
                        ValueMeterBar(
                            skin = skin,
                            progress = progress,
                            energyProgress = energyProgress,
                            reachedMax = reachedMax
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$value%",
                        color = skin.text,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showCombo,
            enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.88f, animationSpec = tween(120)),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.92f, animationSpec = tween(120)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-8).dp)
        ) {
            ComboPrompt(
                text = "${payload.clickText} x$comboCount",
                skin = skin,
                modifier = Modifier
                    .width(skin.comboPromptWidthDp.dp)
                    .height(skin.comboPromptHeightDp.dp)
            )
        }

        AnimatedVisibility(
            visible = reachedMax,
            enter = fadeIn(tween(110)) + scaleIn(initialScale = 0.72f, animationSpec = tween(160)),
            exit = fadeOut(tween(120)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-16).dp)
        ) {
            BurstLabel(
                text = skin.maxBurstText,
                skin = skin,
                modifier = Modifier
                    .width(skin.burstLabelWidthDp.dp)
                    .height(32.dp)
            )
        }
    }
}

@Composable
private fun ValueMeterBackdrop(
    skin: ValueSkinSpec,
    palette: EventPalette,
    progress: Float,
    energyProgress: Float,
    reachedMax: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val glowX = size.width * (0.18f + progress * 0.68f)
        drawCircle(
            color = palette.glow.copy(alpha = 0.10f + progress * skin.maxGlowAlpha),
            radius = size.height * (0.72f + progress * 0.30f),
            center = Offset(glowX, size.height * 0.54f)
        )
        repeat(skin.shockRingCount) { index ->
            val phase = (energyProgress + index * 0.17f) % 1f
            drawRoundRect(
                color = skin.primary.copy(alpha = (0.16f + progress * 0.18f) * (1f - phase)),
                topLeft = Offset(4f + phase * 16f, 4f + phase * 9f),
                size = Size(
                    width = size.width - 8f - phase * 32f,
                    height = size.height - 8f - phase * 18f
                ),
                cornerRadius = CornerRadius(18f, 18f),
                style = Stroke(width = 2f)
            )
        }
        val particleCount = if (reachedMax) skin.maxParticleCount else 8
        repeat(particleCount) { index ->
            val phase = (energyProgress + index * 0.073f) % 1f
            val angle = index * 0.78f + energyProgress * 6.28318f
            val hotZone = size.width * (0.20f + progress * 0.70f)
            val orbit = if (reachedMax) size.height * (0.36f + (index % 5) * 0.055f) else size.height * 0.28f
            drawCircle(
                color = if (index % 2 == 0) palette.secondary else palette.accent,
                radius = 1.5f + (index % 3),
                center = Offset(
                    x = hotZone + cos(angle) * orbit * 1.36f,
                    y = size.height * 0.52f + sin(angle) * orbit
                ),
                alpha = if (reachedMax) 0.38f + (1f - phase) * 0.44f else 0.16f + progress * 0.32f
            )
        }
        if (reachedMax) {
            repeat(12) { index ->
                val angle = (index / 12f) * 6.28318f + energyProgress
                val center = Offset(size.width * 0.70f, size.height * 0.52f)
                drawLine(
                    color = skin.secondary.copy(alpha = 0.24f + 0.34f * (1f - energyProgress)),
                    start = Offset(center.x + cos(angle) * 26f, center.y + sin(angle) * 16f),
                    end = Offset(center.x + cos(angle) * 58f, center.y + sin(angle) * 36f),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun ValueSigil(
    text: String,
    skin: ValueSkinSpec,
    energyProgress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(12.dp, CircleShape, ambientColor = skin.primary.copy(alpha = 0.52f), spotColor = skin.primary)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        skin.secondary.copy(alpha = 0.95f),
                        skin.primary.copy(alpha = 0.82f),
                        Color(0xFF191015)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.42f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            repeat(3) { index ->
                val phase = (energyProgress + index * 0.24f) % 1f
                drawCircle(
                    color = Color.White.copy(alpha = 0.12f * (1f - phase)),
                    radius = size.minDimension * (0.30f + phase * 0.25f),
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = 1.6f)
                )
            }
        }
        Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ValueTitle(
    payload: ValueBoostPayload,
    skin: ValueSkinSpec
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("◇", color = skin.secondary.copy(alpha = 0.86f), fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text(
            text = payload.label,
            color = skin.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text("◇", color = skin.secondary.copy(alpha = 0.86f), fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ValueMeterBar(
    skin: ValueSkinSpec,
    progress: Float,
    energyProgress: Float,
    reachedMax: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(skin.meterHeightDp.dp)
            .clip(RoundedCornerShape((skin.meterHeightDp / 2).dp))
            .background(Color.Black.copy(alpha = 0.48f))
            .border(
                1.dp,
                Color.White.copy(alpha = if (reachedMax) 0.40f else 0.24f),
                RoundedCornerShape((skin.meterHeightDp / 2).dp)
            )
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape((skin.meterHeightDp / 2).dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            skin.primary.copy(alpha = 0.82f),
                            skin.secondary,
                            Color.White.copy(alpha = if (reachedMax) 0.88f else 0.42f)
                        )
                    )
                )
        ) {
            Canvas(Modifier.matchParentSize()) {
                val sweepX = size.width * (energyProgress * 1.35f - 0.18f)
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.42f), Color.Transparent),
                        startX = sweepX - size.width * 0.18f,
                        endX = sweepX + size.width * 0.18f
                    ),
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = CornerRadius(12f, 12f)
                )
            }
        }
    }
}

@Composable
private fun ComboPrompt(
    text: String,
    skin: ValueSkinSpec,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(8.dp), ambientColor = skin.primary.copy(alpha = 0.42f), spotColor = skin.primary)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xE01C1214), skin.primary.copy(alpha = 0.72f), Color(0xE01C1214))))
            .border(1.dp, skin.secondary.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BurstLabel(
    text: String,
    skin: ValueSkinSpec,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(18.dp, RoundedCornerShape(8.dp), ambientColor = skin.primary.copy(alpha = 0.65f), spotColor = skin.primary)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xF01B0908), skin.primary.copy(alpha = 0.92f), Color(0xF01B0908))
                )
            )
            .border(1.dp, skin.secondary.copy(alpha = 0.82f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

private fun valueSigil(theme: String): String = when (theme.lowercase()) {
    "anger" -> "怒"
    "villain" -> "阴"
    "forbearance" -> "忍"
    else -> "值"
}
