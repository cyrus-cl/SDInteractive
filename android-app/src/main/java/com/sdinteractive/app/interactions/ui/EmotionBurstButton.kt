package com.sdinteractive.app.interactions.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdinteractive.app.interactions.model.EmotionPayload
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class EmotionParticle(
    val id: Long,
    val text: String,
    val xDp: Float,
    val yDp: Float,
    val rotation: Float,
    val sizeSp: Float,
    val durationMs: Int
)

@Composable
fun EmotionBurstButton(
    payload: EmotionPayload,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val particles = remember(payload.emotionKey) { mutableStateListOf<EmotionParticle>() }
    var particleId by remember(payload.emotionKey) { mutableStateOf(0L) }
    val palette = emotionPalette(payload.emotionKey)
    val skin = emotionSkinSpec(payload.emotionKey)
    val halo = rememberInfiniteTransition(label = "emotionHalo")
    val haloProgress by halo.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_550, easing = LinearEasing)),
        label = "emotionHaloProgress"
    )
    val compactBadge = skin.shape == EmotionSkinShape.MEDALLION ||
        skin.shape == EmotionSkinShape.SEAL
    val darkSocialPill = skin.surface.red + skin.surface.green + skin.surface.blue < 1.35f
    val buttonWidth = skin.buttonWidthDp.dp
    val buttonHeight = skin.buttonHeightDp.dp
    val buttonShape: Shape = if (compactBadge) CircleShape else RoundedCornerShape(8.dp)

    Box(
        modifier = modifier.size(
            width = (skin.widthDp + 40).dp,
            height = (skin.heightDp + 44).dp
        ),
        contentAlignment = Alignment.BottomStart
    ) {
        SocialProofPill(
            text = payload.broadcast,
            palette = palette,
            dark = darkSocialPill,
            maxWidthDp = skin.socialProofMaxWidthDp,
            heightDp = skin.socialProofHeightDp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 2.dp, y = 4.dp)
        )

        Canvas(
            modifier = Modifier
                .size(width = (skin.widthDp + 8).dp, height = (skin.heightDp - 16).dp)
                .align(Alignment.BottomStart)
                .offset(x = 0.dp, y = 6.dp)
        ) {
            val center = Offset(size.width * 0.45f, size.height * 0.54f)
            drawOval(
                color = skin.primary.copy(alpha = 0.34f * (1f - haloProgress)),
                topLeft = Offset(
                    center.x - size.width * (0.34f + haloProgress * 0.18f),
                    center.y - 26f
                ),
                size = Size(
                    width = size.width * (0.68f + haloProgress * 0.36f),
                    height = 52f + haloProgress * 26f
                ),
                style = Stroke(width = 2.4f)
            )
            drawCircle(
                color = skin.secondary.copy(alpha = 0.18f),
                radius = size.minDimension * (0.38f + haloProgress * 0.16f),
                center = center,
                style = Stroke(width = 3f)
            )
            if (count > 0) {
                repeat(12) { index ->
                    val angle = (index / 12f) * 6.28318f + haloProgress * 0.8f
                    val inner = 38f + (index % 3) * 8f
                    val outer = inner + 18f + haloProgress * 18f
                    drawLine(
                        color = skin.secondary.copy(alpha = 0.12f + 0.28f * (1f - haloProgress)),
                        start = Offset(
                            x = center.x + cos(angle) * inner,
                            y = center.y + sin(angle) * inner * 0.58f
                        ),
                        end = Offset(
                            x = center.x + cos(angle) * outer,
                            y = center.y + sin(angle) * outer * 0.58f
                        ),
                        strokeWidth = if (skin.shape == EmotionSkinShape.ACTION) 3.5f else 2.2f
                    )
                }
                repeat(16) { index ->
                    val angle = (index / 16f) * 6.28318f + haloProgress * 1.2f
                    val radius = 30f + (index % 4) * 7f + haloProgress * 18f
                    drawCircle(
                        color = if (index % 2 == 0) palette.secondary else InteractionGold,
                        radius = 1.8f + index % 3,
                        center = Offset(
                            x = center.x + cos(angle) * radius,
                            y = center.y + sin(angle) * radius * 0.56f
                        ),
                        alpha = 0.24f + 0.38f * (1f - haloProgress)
                    )
                }
            }
        }

        particles.forEach { particle ->
            key(particle.id) {
                EmotionParticleView(
                    particle = particle,
                    color = palette.secondary,
                    onFinished = { particles.removeAll { it.id == particle.id } }
                )
            }
        }

        if (count > 0) {
            skin.accentWords.take(3).forEachIndexed { index, word ->
                val phase = (haloProgress + index * 0.23f) % 1f
                Text(
                    text = word,
                    color = if (index % 2 == 0) skin.secondary else palette.accent,
                    fontSize = (16 + index * 2).sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(
                            x = (skin.widthDp * (0.56f + index * 0.10f)).dp,
                            y = (-(skin.heightDp * (0.42f + index * 0.13f))).dp
                        )
                        .rotate(-12f + index * 13f)
                        .graphicsLayer {
                            alpha = (0.20f + 0.50f * (1f - phase)).coerceIn(0.16f, 0.72f)
                            scaleX = 0.88f + phase * 0.26f
                            scaleY = scaleX
                            translationY = -phase * 16f
                        }
                )
            }
        }

        Box(
            modifier = Modifier
                .width(buttonWidth)
                .height(buttonHeight)
                .shadow(18.dp, buttonShape, ambientColor = palette.glow, spotColor = palette.glow)
                .clip(buttonShape)
                .background(buttonBrush(skin = skin, palette = palette))
                .border(2.dp, Color.White.copy(alpha = 0.74f), buttonShape)
                .border(1.dp, skin.secondary.copy(alpha = 0.88f), buttonShape)
                .graphicsLayer {
                    translationX = if (skin.shake && count > 0) {
                        sin(haloProgress * 12.56636f) * 2f
                    } else {
                        0f
                    }
                }
                .pressFeedback(stateDescription = "已互动 $count 次") {
                    val random = Random(System.nanoTime())
                    val additions = List(random.nextInt(5, 9)) {
                        EmotionParticle(
                            id = ++particleId,
                            text = payload.particles.random(random),
                            xDp = random.nextInt(18, (skin.widthDp + 12).coerceAtLeast(64)).toFloat(),
                            yDp = random.nextInt(8, (skin.heightDp - 10).coerceAtLeast(64)).toFloat(),
                            rotation = random.nextInt(-22, 23).toFloat(),
                            sizeSp = random.nextInt(14, 26).toFloat(),
                            durationMs = random.nextInt(1_050, 1_801)
                        )
                    }
                    val overflow = (particles.size + additions.size - 40).coerceAtLeast(0)
                    repeat(overflow.coerceAtMost(particles.size)) { particles.removeAt(0) }
                    particles.addAll(additions)
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                when (skin.shape) {
                    EmotionSkinShape.MEDALLION -> {
                        drawCircle(
                            color = skin.secondary.copy(alpha = 0.22f),
                            radius = size.minDimension * 0.42f,
                            center = Offset(size.width * 0.5f, size.height * 0.5f),
                            style = Stroke(width = 5f)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.18f),
                            radius = size.minDimension * 0.28f,
                            center = Offset(size.width * 0.5f, size.height * 0.5f),
                            style = Stroke(width = 2.5f)
                        )
                    }

                    EmotionSkinShape.SEAL -> {
                        drawRoundRect(
                            color = skin.secondary.copy(alpha = 0.34f),
                            topLeft = Offset(size.width * 0.14f, size.height * 0.16f),
                            size = Size(size.width * 0.72f, size.height * 0.68f),
                            cornerRadius = CornerRadius(18f, 18f),
                            style = Stroke(width = 3f)
                        )
                    }

                    EmotionSkinShape.ACTION -> {
                        repeat(4) { index ->
                            val y = size.height * (0.24f + index * 0.16f)
                            drawLine(
                                color = skin.secondary.copy(alpha = 0.22f),
                                start = Offset(size.width * 0.08f, y),
                                end = Offset(size.width * 0.92f, y - 18f),
                                strokeWidth = 4f
                            )
                        }
                    }

                    EmotionSkinShape.PILL -> {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.18f),
                            topLeft = Offset(size.width * 0.08f, size.height * 0.18f),
                            size = Size(size.width * 0.84f, 5f),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                        drawRoundRect(
                            color = skin.secondary.copy(alpha = 0.18f),
                            topLeft = Offset(size.width * 0.18f, size.height * 0.72f),
                            size = Size(size.width * 0.64f, 4f),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = if (skin.leadingMark.isBlank()) 4.dp else 8.dp)
            ) {
                if (skin.leadingMark.isNotBlank()) {
                    Text(
                        text = skin.leadingMark,
                        color = skin.secondary,
                        fontSize = if (skin.shape == EmotionSkinShape.ACTION) 17.sp else 15.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = payload.label,
                    color = skin.text,
                    fontSize = when {
                        payload.label.length > 4 -> 16.sp
                        skin.shape == EmotionSkinShape.MEDALLION -> 20.sp
                        skin.shape == EmotionSkinShape.SEAL -> 20.sp
                        skin.shape == EmotionSkinShape.ACTION -> 18.sp
                        else -> 17.sp
                    },
                    fontWeight = FontWeight.Black,
                    maxLines = 2
                )
            }
        }

        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = buttonWidth - 34.dp, y = (-42).dp)
                    .size(46.dp)
                    .shadow(14.dp, CircleShape, ambientColor = InteractionGold.copy(alpha = 0.5f))
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFFFFF2B8), InteractionGold, Color(0xFF8A4B12))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "×$count",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

private fun buttonBrush(
    skin: EmotionSkinSpec,
    palette: EventPalette
): Brush = when {
    skin.shape == EmotionSkinShape.ACTION -> Brush.horizontalGradient(
        listOf(Color(0xFF210809), skin.surface, skin.primary)
    )
    skin.shape == EmotionSkinShape.SEAL -> Brush.radialGradient(
        listOf(skin.secondary, skin.primary, skin.surface)
    )
    skin.shape == EmotionSkinShape.MEDALLION -> Brush.radialGradient(
        listOf(skin.secondary, skin.primary, skin.surface)
    )
    skin.surface.red > 0.85f && skin.surface.green > 0.85f -> Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.96f), skin.surface, skin.primary.copy(alpha = 0.68f))
    )
    else -> Brush.horizontalGradient(
        listOf(skin.surface, palette.accent.copy(alpha = 0.92f), skin.primary)
    )
}

@Composable
private fun SocialProofPill(
    text: String,
    palette: EventPalette,
    dark: Boolean,
    maxWidthDp: Int,
    heightDp: Int,
    modifier: Modifier = Modifier
) {
    val shimmer = rememberInfiniteTransition(label = "socialProofMotion")
    val shimmerProgress by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing)),
        label = "socialProofSweep"
    )
    Row(
        modifier = modifier
            .widthIn(max = maxWidthDp.dp)
            .height(heightDp.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (dark) Color(0xD9201D24) else Color(0xDDF4EFE7))
            .border(1.dp, Color.White.copy(alpha = if (dark) 0.22f else 0.72f), RoundedCornerShape(8.dp))
            .border(1.dp, palette.accent.copy(alpha = 0.26f + shimmerProgress * 0.18f), RoundedCornerShape(8.dp))
            .padding(start = 7.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(42.dp).height(22.dp)) {
            listOf(InteractionOrange, palette.accent, Color(0xFF47311E)).forEachIndexed { index, color ->
                Box(
                    Modifier
                        .offset(x = (index * 12).dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(color, palette.secondary)))
                        .border(1.dp, Color.White, CircleShape)
                )
            }
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            color = if (dark) Color.White.copy(alpha = 0.88f) else Color(0xFF4C3927),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EmotionParticleView(
    particle: EmotionParticle,
    color: Color,
    onFinished: () -> Unit
) {
    val progress = remember(particle.id) { Animatable(0f) }
    LaunchedEffect(particle.id) {
        launch {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(particle.durationMs)
            )
        }
        delay(particle.durationMs.toLong())
        onFinished()
    }
    Text(
        text = particle.text,
        color = color,
        fontSize = particle.sizeSp.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .offset(
                x = particle.xDp.dp,
                y = (particle.yDp - 96f * progress.value).dp
            )
            .rotate(particle.rotation)
            .graphicsLayer {
                alpha = (1f - progress.value).coerceIn(0f, 1f)
                scaleX = 0.82f + progress.value * 0.28f
                scaleY = scaleX
            }
    )
}
