package com.sdinteractive.app.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun PlayerControlsOverlay(
    snapshot: PlayerSnapshot,
    controller: PlayerController?,
    playbackFailed: Boolean,
    controls: PlaybackControlsState,
    controlsRevision: Int,
    onControlsChanged: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }
    val shownPosition = if (dragging) dragPositionMs.toLong() else snapshot.positionMs
    val duration = snapshot.durationMs.coerceAtLeast(1L)
    val progress = (shownPosition.toFloat() / duration).coerceIn(0f, 1f)
    val controlsVisible = controls.visible || playbackFailed || controls.scrubbing

    LaunchedEffect(controlsRevision, controls.pendingAutoHide) {
        if (controls.pendingAutoHide) {
            delay(180L)
            controls.consumeAutoHide()
            onControlsChanged()
        }
    }

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = (controls.visible || playbackFailed) && !controls.scrubbing,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val buttonAlpha = when {
                playbackFailed -> 0.62f
                snapshot.isPlaying -> 0.34f
                else -> 0.56f
            }
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .background(Color.Black.copy(alpha = buttonAlpha), CircleShape)
                    .pointerInput(controller, playbackFailed, snapshot.isPlaying) {
                        detectTapGestures {
                            if (playbackFailed) {
                                onRetry()
                                return@detectTapGestures
                            }
                            when (controls.onCenterButtonTap(snapshot.isPlaying)) {
                                PlaybackCommand.Play,
                                PlaybackCommand.Pause -> controller?.togglePlayPause()
                                PlaybackCommand.None,
                                PlaybackCommand.TogglePlayback -> Unit
                            }
                            onControlsChanged()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        playbackFailed -> Icons.Default.Refresh
                        snapshot.isPlaying -> Icons.Default.Pause
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = if (playbackFailed) "重试" else if (snapshot.isPlaying) "暂停" else "播放",
                    tint = Color.White.copy(alpha = if (snapshot.isPlaying) 0.86f else 0.96f),
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(82.dp)
                .padding(start = 12.dp, end = 12.dp, bottom = 48.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .pointerInput(controller, duration, widthPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val scrub = ProgressScrubState(duration, widthPx)
                            var moved = false
                            dragging = true
                            scrub.start(down.position.x)
                            dragPositionMs = scrub.previewPositionMs.toFloat()
                            controls.onScrubStart()
                            controller?.beginScrub()
                            onControlsChanged()
                            drag(down.id) { change ->
                                val delta = change.positionChange().x
                                if (delta != 0f) moved = true
                                change.consume()
                                scrub.dragBy(delta)
                                dragPositionMs = scrub.previewPositionMs.toFloat()
                            }
                            val target = if (moved) scrub.stop() else scrub.positionForTap(down.position.x)
                            controller?.seekTo(target)
                            controller?.endScrub()
                            controls.onScrubStop()
                            dragging = false
                            onControlsChanged()
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val centerY = size.height / 2f
                    drawLine(
                        color = Color.White.copy(alpha = 0.30f),
                        start = Offset(0f, centerY),
                        end = Offset(size.width, centerY),
                        strokeWidth = 2.5f
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.88f),
                        start = Offset(0f, centerY),
                        end = Offset(size.width * progress, centerY),
                        strokeWidth = 3.5f
                    )
                    drawCircle(
                        color = Color.White,
                        radius = if (dragging) 7f else 4.5f,
                        center = Offset(size.width * progress, centerY)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = dragging,
            enter = fadeIn(tween(80)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 92.dp)
        ) {
            Text(
                text = "${formatPlaybackTime(shownPosition)} / ${formatPlaybackTime(duration)}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}
