@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sdinteractive.app.interactions.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdinteractive.app.interactions.model.KnowledgePayload
import com.sdinteractive.app.interactions.model.RatingPayload
import com.sdinteractive.app.interactions.storage.RatingRecord
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun KnowledgeCard(
    payload: KnowledgePayload,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = knowledgeSkinSpec()
    val knowledgeAccent = skin.primary
    val titleColor = skin.text
    Box(
        modifier = modifier
            .width(skin.widthDp.dp)
            .then(if (expanded) Modifier.heightIn(min = skin.expandedHeightDp.dp) else Modifier.height(84.dp))
            .shadow(
                24.dp,
                RoundedCornerShape(18.dp),
                ambientColor = knowledgeAccent.copy(alpha = 0.22f),
                spotColor = knowledgeAccent.copy(alpha = 0.30f)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        skin.surface,
                        Color(0xEEE9F4FF),
                        Color(0xEED7E9FF)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.82f), RoundedCornerShape(18.dp))
            .border(1.dp, knowledgeAccent.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Canvas(Modifier.matchParentSize()) {
            repeat(skin.particleCount) { index ->
                val xOffset = (index * 0.11f) % 0.76f
                drawCircle(
                    color = knowledgeAccent.copy(alpha = 0.10f + (index % 3) * 0.04f),
                    radius = 2f + index % 2,
                    center = Offset(
                        x = size.width * (0.12f + xOffset),
                        y = size.height * (0.14f + (index % 4) * 0.22f)
                    )
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(skin.iconSizeDp.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.radialGradient(
                                listOf(Color.White, skin.secondary.copy(alpha = 0.92f), skin.primary.copy(alpha = 0.28f))
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.82f), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = knowledgeAccent, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (expanded) payload.title else "了解背景",
                        color = titleColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = if (expanded) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!expanded) {
                        Text(
                            text = payload.title,
                            color = Color(0xFF5F7895),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(knowledgeAccent.copy(alpha = 0.14f))
                        .pressFeedback(onClick = { onExpandedChange(!expanded) }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = titleColor
                    )
                }
                AnimatedVisibility(expanded) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.52f))
                            .pressFeedback(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = titleColor)
                    }
                }
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        text = payload.summary,
                        color = Color(0xFF33465C),
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                    if (payload.tags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            payload.tags.forEach { tag ->
                                if (payload.tags.indexOf(tag) >= skin.tagCount) return@forEach
                                Box(
                                    Modifier
                                        .height(skin.tagHeightDp.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.52f))
                                        .border(1.dp, skin.primary.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(tag, color = Color(0xFF2F77C9), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RatingSliderCard(
    payload: RatingPayload,
    restoredRecord: RatingRecord?,
    onInteraction: () -> Unit = {},
    onSubmit: (Float) -> Boolean,
    modifier: Modifier = Modifier
) {
    val skin = ratingSkinSpec()
    var currentValue by remember(payload.ratingId, restoredRecord) {
        mutableFloatStateOf(restoredRecord?.value ?: payload.defaultValue)
    }
    var submitted by remember(payload.ratingId, restoredRecord) {
        mutableStateOf(restoredRecord != null)
    }
    var showResult by remember(payload.ratingId, restoredRecord) {
        mutableStateOf(restoredRecord != null)
    }
    LaunchedEffect(showResult, submitted) {
        if (showResult && submitted) {
            delay(2_000L)
            showResult = false
        }
    }

    val discreteSteps = (((payload.max - payload.min) / payload.step).roundToInt() - 1)
        .coerceAtLeast(0)
    val ratingMotion = rememberInfiniteTransition(label = "ratingSliderMotion")
    val ratingFlow by ratingMotion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_700, easing = LinearEasing)),
        label = "ratingSliderFlow"
    )
    val ratingProgress = ((currentValue - payload.min) / (payload.max - payload.min).coerceAtLeast(0.1f))
        .coerceIn(0f, 1f)

    GlassSurface(
        accent = skin.primary,
        modifier = modifier
            .width(skin.widthDp.dp)
            .height(skin.heightDp.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = skin.secondary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(payload.question, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${formatRating(payload.min)}分 / ${payload.maxLabel}", color = InteractionMuted, fontSize = 11.sp, maxLines = 1)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(skin.sliderHeightDp.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val trackY = size.height * 0.70f
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(size.width * 0.04f, trackY),
                        end = Offset(size.width * 0.96f, trackY),
                        strokeWidth = 12f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = InteractionPurple.copy(alpha = 0.42f),
                        start = Offset(size.width * 0.04f, trackY),
                        end = Offset(size.width * (0.04f + 0.92f * ratingProgress), trackY),
                        strokeWidth = 12f,
                        cap = StrokeCap.Round
                    )
                    repeat(skin.particleCount) { index ->
                        val phase = (ratingFlow + index * 0.13f) % 1f
                        drawCircle(
                            color = skin.secondary.copy(alpha = 0.22f + (1f - phase) * 0.40f),
                            radius = 2f + index % 2,
                            center = Offset(
                                x = size.width * (0.06f + phase * 0.88f),
                                y = trackY + if (index % 2 == 0) -9f else 9f
                            )
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = ((skin.widthDp - 72) * ratingProgress).dp)
                        .width(skin.valueBubbleWidthDp.dp)
                        .height(skin.valueBubbleHeightDp.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF6F51CE), skin.secondary)))
                        .border(1.dp, Color.White.copy(alpha = 0.42f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${formatRating(currentValue)}分", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
                Slider(
                    value = currentValue,
                    onValueChange = {
                        currentValue = it
                        onInteraction()
                    },
                    enabled = !submitted,
                    valueRange = payload.min..payload.max,
                    steps = discreteSteps,
                    colors = SliderDefaults.colors(
                        thumbColor = skin.secondary,
                        activeTrackColor = skin.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.20f),
                        disabledThumbColor = skin.secondary,
                        disabledActiveTrackColor = skin.primary.copy(alpha = 0.55f)
                    )
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatRating(payload.min), color = InteractionMuted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(formatRating(payload.max), color = InteractionMuted, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(skin.submitHeightDp.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (submitted) {
                            Brush.horizontalGradient(listOf(Color(0xFF3E2C5F), Color(0xFF6A46A8)))
                        } else {
                            Brush.horizontalGradient(listOf(InteractionPurple, Color(0xFFC7A7FF)))
                        }
                    )
                    .pressFeedback(
                        enabled = !submitted,
                        stateDescription = if (submitted) "已评分" else "等待评分",
                        onClick = {
                            onInteraction()
                            if (onSubmit(currentValue)) {
                                submitted = true
                                showResult = true
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (submitted) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = if (submitted) "已评分 ${formatRating(currentValue)}" else payload.submitText,
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            AnimatedVisibility(showResult) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = InteractionGold, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(payload.resultText, color = skin.secondary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

private fun formatRating(value: Float): String {
    val rounded = (value * 10f).roundToInt() / 10f
    return if (rounded % 1f == 0f) rounded.toInt().toString() else "%.1f".format(rounded)
}
