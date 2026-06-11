package com.sdinteractive.app.interactions.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdinteractive.app.interactions.engine.BroadcastMessage
import com.sdinteractive.app.interactions.engine.HighlightSavedState
import com.sdinteractive.app.interactions.model.HighlightPayload
import com.sdinteractive.app.interactions.model.WarningPayload
import kotlinx.coroutines.delay

@Composable
fun GlobalBroadcastBar(
    message: BroadcastMessage,
    modifier: Modifier = Modifier
) {
    val skin = broadcastSkinSpec()
    val broadcastMotion = rememberInfiniteTransition(label = "broadcastMotion")
    val broadcastGlow by broadcastMotion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_700, easing = LinearEasing)),
        label = "broadcastGlow"
    )
    key(message.instanceId) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(message.instanceId, message.isClosing) {
            visible = !message.isClosing
        }
        AnimatedVisibility(
            visible = visible,
            modifier = modifier,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(220)
            ) + fadeIn(tween(180)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(220)
            ) + fadeOut(tween(180))
        ) {
            Box(
                modifier = Modifier
                    .width(skin.widthDp.dp)
                    .height(skin.heightDp.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                skin.surface,
                                Color(0xEE171820),
                                Color(0xEE3A1821),
                                skin.primary.copy(alpha = 0.18f)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.18f + broadcastGlow * 0.08f), RoundedCornerShape(100.dp))
                    .border(1.dp, skin.primary.copy(alpha = 0.30f + broadcastGlow * 0.30f), RoundedCornerShape(100.dp))
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        stateDescription = message.text
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val sweepX = size.width * (broadcastGlow * 1.18f - 0.08f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.16f),
                        start = Offset(sweepX, 0f),
                        end = Offset(sweepX + size.width * 0.18f, size.height),
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )
                    repeat(skin.particleCount) { index ->
                        val phase = (broadcastGlow + index * 0.08f) % 1f
                        drawCircle(
                            color = if (index % 2 == 0) skin.primary else skin.secondary,
                            radius = 1.4f + (index % 3),
                            center = Offset(
                                x = size.width * (0.10f + ((index * 0.09f + phase * 0.20f) % 0.82f)),
                                y = size.height * (0.22f + (index % 4) * 0.17f)
                            ),
                            alpha = 0.10f + (1f - phase) * 0.34f
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val avatarSizeDp = 28
                    Box(
                        Modifier
                            .width((avatarSizeDp + (skin.avatarCount - 1) * (avatarSizeDp - skin.avatarOverlapDp)).dp)
                            .height(avatarSizeDp.dp)
                    ) {
                        repeat(skin.avatarCount) { index ->
                            Box(
                                Modifier
                                    .offset(x = (index * (avatarSizeDp - skin.avatarOverlapDp)).dp)
                                    .size(avatarSizeDp.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                listOf(InteractionOrange, InteractionGold),
                                                listOf(InteractionPurple, Color(0xFFFF73D0)),
                                                listOf(Color(0xFF2E9C8C), Color(0xFF7AE4B8))
                                            )[index]
                                        ),
                                        CircleShape
                                    )
                                    .border(2.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text((index + 1).toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(26.dp)
                            .background(Color.White.copy(alpha = 0.14f))
                    )
                    Spacer(Modifier.width(7.dp))
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = skin.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(message.heatText, color = Color(0xFFFF8B5F), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun HighlightCollectButton(
    payload: HighlightPayload,
    isCollected: Boolean,
    onCollect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = highlightSkinSpec()
    val collectMotion = rememberInfiniteTransition(label = "collectMotion")
    val collectGlow by collectMotion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_450, easing = LinearEasing)),
        label = "collectGlow"
    )
    Row(
        modifier = modifier
            .width(skin.collectWidthDp.dp)
            .height(skin.collectHeightDp.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.horizontalGradient(
                    if (isCollected) {
                        listOf(Color(0xEE2B2117), Color(0xDD6E4E19), skin.surface)
                    } else {
                        listOf(skin.surface, Color(0xE62D2518), Color(0xE6131215))
                    }
                )
            )
            .border(
                1.dp,
                if (isCollected) skin.primary.copy(alpha = 0.55f + collectGlow * 0.25f)
                else skin.primary.copy(alpha = 0.35f + collectGlow * 0.45f),
                RoundedCornerShape(8.dp)
            )
            .pressFeedback(
                enabled = !isCollected,
                stateDescription = if (isCollected) "已收藏" else "未收藏",
                onClick = onCollect
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isCollected) Icons.Default.Check else Icons.Default.Star,
            contentDescription = null,
            tint = skin.primary,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                if (isCollected) "已收藏" else payload.buttonText,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                maxLines = 1
            )
            Text(payload.title, color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
fun HighlightSavedToast(
    state: HighlightSavedState,
    onOpenHighlights: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = highlightSkinSpec()
    Box(
        modifier = modifier
            .width(skin.savedWidthDp.dp)
            .height(skin.savedHeightDp.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(skin.surface, Color(0xEE272019), Color(0xEE1A1718))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .border(1.dp, skin.primary.copy(alpha = 0.38f), RoundedCornerShape(8.dp))
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = state.successText
            }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.matchParentSize()) {
                repeat(skin.particleCount) { index ->
                    drawCircle(
                        color = skin.primary.copy(alpha = 0.12f + (index % 3) * 0.05f),
                        radius = 2f + index % 2,
                        center = Offset(
                            x = size.width * (0.10f + (index * 0.09f) % 0.82f),
                            y = size.height * (0.18f + (index % 4) * 0.20f)
                        )
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(skin.savedIconSizeDp.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.radialGradient(listOf(Color(0xFFFFF2B8), skin.primary, Color(0xFF8A5514))))
                        .border(1.dp, Color.White.copy(alpha = 0.62f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = skin.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(state.successText, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                    Text(state.detailText, color = InteractionMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .width(skin.savedActionWidthDp.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF6A4B21), skin.primary)))
                        .pressFeedback(onClick = onOpenHighlights),
                    contentAlignment = Alignment.Center
                ) {
                    Text("查看", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun HighEnergyWarning(
    payload: WarningPayload,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = warningSkinSpec(payload.theme)
    var countdownIndex by remember(payload.text) { mutableIntStateOf(0) }
    val currentValue = payload.countdown.getOrNull(countdownIndex)
    val warningMotion = rememberInfiniteTransition(label = "warningMotion")
    val warningProgress by warningMotion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "warningPulse"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if ((currentValue ?: 0) % 2 == 0) 0.45f else 1f,
        animationSpec = tween(420),
        label = "warningBorder"
    )

    LaunchedEffect(payload.text) {
        payload.countdown.indices.forEach { index ->
            countdownIndex = index
            delay(1_000L)
        }
        onFinished()
    }

    Box(
        modifier = modifier
            .width(skin.widthDp.dp)
            .height(skin.heightDp.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        skin.surface,
                        Color(0xEE2A1015),
                        skin.surface
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .border(1.dp, skin.primary.copy(alpha = borderAlpha), RoundedCornerShape(8.dp))
            .semantics {
                liveRegion = LiveRegionMode.Assertive
                stateDescription = payload.text
            }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            repeat(4) { index ->
                val phase = (warningProgress + index * 0.18f) % 1f
                val x = size.width * (0.14f + phase * 0.68f)
                drawLine(
                    color = skin.primary.copy(alpha = 0.14f + (1f - phase) * 0.24f),
                    start = Offset(x, size.height * 0.18f),
                    end = Offset(x + 28f, size.height * 0.82f),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
            }
            repeat(5) { index ->
                val phase = (warningProgress + index * 0.11f) % 1f
                drawCircle(
                    color = if (index % 2 == 0) skin.secondary else skin.primary,
                    radius = 1.8f + (index % 3),
                    center = Offset(
                        x = size.width * (0.08f + (index % 4) * 0.22f),
                        y = size.height * (0.18f + phase * 0.64f)
                    ),
                    alpha = 0.12f + (1f - phase) * 0.42f
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = skin.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                payload.text,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(skin.primary.copy(alpha = 0.18f))
                    .border(1.dp, skin.primary.copy(alpha = 0.72f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentValue?.toString() ?: "",
                    color = skin.secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
