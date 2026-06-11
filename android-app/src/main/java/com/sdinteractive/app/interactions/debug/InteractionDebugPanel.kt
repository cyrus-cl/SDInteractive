package com.sdinteractive.app.interactions.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdinteractive.app.interactions.data.InteractionReferenceRegistry
import com.sdinteractive.app.interactions.model.InteractionEvent
import com.sdinteractive.app.interactions.model.InteractionTrigger
import com.sdinteractive.app.interactions.model.InteractionType
import com.sdinteractive.app.interactions.ui.InteractionGlass
import com.sdinteractive.app.interactions.ui.InteractionGold
import com.sdinteractive.app.interactions.ui.InteractionMuted
import com.sdinteractive.app.interactions.ui.InteractionOrange
import com.sdinteractive.app.interactions.ui.pressFeedback

private enum class DebugPanelMode {
    STORY,
    PREVIEW
}

@Composable
fun InteractionDebugPanel(
    currentEpisodeNumber: Int,
    availableEpisodeNumbers: List<Int>,
    episodeDurationsMs: Map<Int, Long>,
    currentPositionSec: Double,
    durationSec: Double,
    activeEventId: String?,
    storyEvents: List<InteractionEvent>,
    onNavigateToEvent: (InteractionEvent) -> Unit,
    onSeekToSec: (Double) -> Unit,
    onPreviewEvent: (InteractionEvent) -> Unit,
    onClearRecords: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(DebugPanelMode.STORY) }
    var selectedEpisode by remember(currentEpisodeNumber) { mutableStateOf(currentEpisodeNumber) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var secondsText by remember(currentEpisodeNumber) {
        mutableStateOf(currentPositionSec.toInt().toString())
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("清除互动记录？") },
            text = { Text("竞猜、评分、收藏和数值进度都会被清除，此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClearRecords()
                    }
                ) {
                    Text("确认清除", color = Color(0xFFFF6657), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (!expanded) {
        CollapsedTimelineButton(
            onClick = { expanded = true },
            modifier = modifier
        )
        return
    }

    val episodeNumbers = remember(availableEpisodeNumbers, storyEvents) {
        (availableEpisodeNumbers + storyEvents.map { it.episodeNumber })
            .distinct()
            .sorted()
    }
    val selectedEvents = remember(storyEvents, selectedEpisode, episodeDurationsMs) {
        storyEvents
            .filter { it.episodeNumber == selectedEpisode }
            .sortedBy { event ->
                val durationMs = episodeDurationsMs[event.episodeNumber] ?: 0L
                if (event.trigger is InteractionTrigger.EpisodeEnding && durationMs <= 0L) {
                    Long.MAX_VALUE
                } else {
                    interactionTriggerPositionMs(
                        trigger = event.trigger,
                        episodeDurationMs = durationMs
                    )
                }
            }
    }
    val referenceEvents = remember(currentEpisodeNumber) {
        InteractionReferenceRegistry.previewEvents(currentEpisodeNumber)
    }

    Column(
        modifier = modifier
            .width(334.dp)
            .heightIn(max = 540.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(InteractionGlass)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PanelHeader(
            eventCount = storyEvents.size,
            onClose = { expanded = false }
        )
        PlaybackStatus(
            currentEpisodeNumber = currentEpisodeNumber,
            currentPositionSec = currentPositionSec,
            durationSec = durationSec
        )
        ModeSelector(
            selected = mode,
            onSelected = { mode = it }
        )

        when (mode) {
            DebugPanelMode.STORY -> StoryTimeline(
                selectedEpisode = selectedEpisode,
                currentEpisodeNumber = currentEpisodeNumber,
                episodeNumbers = episodeNumbers,
                selectedEvents = selectedEvents,
                episodeDurationsMs = episodeDurationsMs,
                onEpisodeSelected = { selectedEpisode = it },
                onNavigateToEvent = {
                    expanded = false
                    onNavigateToEvent(it)
                },
                modifier = Modifier.weight(1f, fill = false)
            )

            DebugPanelMode.PREVIEW -> ComponentPreviewList(
                events = referenceEvents,
                activeEventId = activeEventId,
                onPreviewEvent = {
                    expanded = false
                    onPreviewEvent(it)
                },
                modifier = Modifier.weight(1f, fill = false)
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
        DebugTools(
            secondsText = secondsText,
            onSecondsChanged = {
                secondsText = it.filter { char -> char.isDigit() || char == '.' }.take(7)
            },
            onSeek = { secondsText.toDoubleOrNull()?.let(onSeekToSec) },
            onClear = { showClearConfirmation = true }
        )
    }
}

@Composable
private fun CollapsedTimelineButton(
    onClick: () -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF21A1B20))
            .border(
                width = 1.dp,
                color = InteractionGold.copy(alpha = 0.52f),
                shape = RoundedCornerShape(16.dp)
            )
            .pressFeedback(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Timeline,
            contentDescription = "打开互动时间轴",
            tint = InteractionGold,
            modifier = Modifier.size(25.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(6.dp)
                .background(InteractionOrange, CircleShape)
        )
    }
}

@Composable
private fun PanelHeader(
    eventCount: Int,
    onClose: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(InteractionOrange.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Timeline,
                contentDescription = null,
                tint = InteractionGold,
                modifier = Modifier.size(21.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = "互动时间轴",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 17.sp
            )
            Text(
                text = "$eventCount 个剧情事件 · 调试模式",
                color = InteractionMuted,
                fontSize = 11.sp
            )
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .pressFeedback(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭互动时间轴",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PlaybackStatus(
    currentEpisodeNumber: Int,
    currentPositionSec: Double,
    durationSec: Double
) {
    val progress = if (durationSec > 0.0) {
        (currentPositionSec / durationSec).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF17181D), RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "第 $currentEpisodeNumber 集",
                color = InteractionGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${formatSec(currentPositionSec)} / ${formatSec(durationSec)}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.10f), CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .background(InteractionOrange, CircleShape)
            )
        }
    }
}

@Composable
private fun ModeSelector(
    selected: DebugPanelMode,
    onSelected: (DebugPanelMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.20f), RoundedCornerShape(13.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModeButton(
            text = "剧情时间轴",
            icon = Icons.Default.Timeline,
            selected = selected == DebugPanelMode.STORY,
            onClick = { onSelected(DebugPanelMode.STORY) },
            modifier = Modifier.weight(1f)
        )
        ModeButton(
            text = "组件预览",
            icon = Icons.Default.Widgets,
            selected = selected == DebugPanelMode.PREVIEW,
            onClick = { onSelected(DebugPanelMode.PREVIEW) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ModeButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) InteractionOrange.copy(alpha = 0.92f)
                else Color.Transparent
            )
            .pressFeedback(
                selected = selected,
                stateDescription = if (selected) "已选择" else "未选择",
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color.White else InteractionMuted,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = if (selected) Color.White else InteractionMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StoryTimeline(
    selectedEpisode: Int,
    currentEpisodeNumber: Int,
    episodeNumbers: List<Int>,
    selectedEvents: List<InteractionEvent>,
    episodeDurationsMs: Map<Int, Long>,
    onEpisodeSelected: (Int) -> Unit,
    onNavigateToEvent: (InteractionEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            episodeNumbers.forEach { episode ->
                EpisodeChip(
                    episodeNumber = episode,
                    selected = episode == selectedEpisode,
                    playing = episode == currentEpisodeNumber,
                    onClick = { onEpisodeSelected(episode) }
                )
            }
        }

        if (selectedEvents.isEmpty()) {
            EmptyEpisodeState(
                episodeNumber = selectedEpisode,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                selectedEvents.forEach { event ->
                    StoryEventRow(
                        event = event,
                        episodeDurationMs = episodeDurationsMs[event.episodeNumber] ?: 0L,
                        onClick = { onNavigateToEvent(event) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeChip(
    episodeNumber: Int,
    selected: Boolean,
    playing: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(
                when {
                    selected -> InteractionGold
                    else -> Color.White.copy(alpha = 0.07f)
                }
            )
            .border(
                width = 1.dp,
                color = if (playing && !selected) {
                    InteractionOrange.copy(alpha = 0.60f)
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(11.dp)
            )
            .pressFeedback(
                selected = selected,
                stateDescription = when {
                    selected && playing -> "已选择，正在播放"
                    selected -> "已选择"
                    playing -> "正在播放"
                    else -> "未选择"
                },
                onClick = onClick
            )
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (playing) {
            Box(
                Modifier
                    .size(5.dp)
                    .background(if (selected) Color.Black else InteractionOrange, CircleShape)
            )
        }
        Text(
            text = episodeNumber.toString().padStart(2, '0'),
            color = if (selected) Color(0xFF221406) else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun EmptyEpisodeState(
    episodeNumber: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.045f), RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.07f),
                shape = RoundedCornerShape(14.dp)
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Timeline,
            contentDescription = null,
            tint = InteractionMuted.copy(alpha = 0.65f),
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "第 $episodeNumber 集暂无互动事件",
            color = InteractionMuted,
            fontSize = 12.sp
        )
        Text(
            text = "可选择其他剧集继续检查",
            color = InteractionMuted.copy(alpha = 0.68f),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun StoryEventRow(
    event: InteractionEvent,
    episodeDurationMs: Long,
    onClick: () -> Unit
) {
    val replayPositionMs = interactionReplayPositionMs(
        trigger = event.trigger,
        episodeDurationMs = episodeDurationMs
    )
    val replayLabel = when {
        event.trigger is InteractionTrigger.EpisodeEnding && episodeDurationMs <= 0L -> {
            "片尾前 ${(event.trigger.remainingSec + 3.0).toInt()}s"
        }
        else -> formatTimeMs(replayPositionMs)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.065f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(14.dp)
            )
            .pressFeedback(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier.width(56.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = triggerLabel(event.trigger),
                color = InteractionGold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            Text(
                text = "回放 $replayLabel",
                color = InteractionMuted.copy(alpha = 0.72f),
                fontSize = 8.sp,
                maxLines = 1
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = event.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = interactionTypeLabel(event.type),
                color = InteractionMuted,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(InteractionOrange, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Replay,
                contentDescription = "从触发前 3 秒回放",
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun ComponentPreviewList(
    events: List<InteractionEvent>,
    activeEventId: String?,
    onPreviewEvent: (InteractionEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = "参考组件没有剧情时间点，点击后在当前画面即时预览。",
            color = InteractionMuted,
            fontSize = 10.sp,
            lineHeight = 15.sp
        )
        events.forEachIndexed { index, event ->
            val selected = activeEventId?.startsWith(event.id) == true
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) InteractionOrange.copy(alpha = 0.18f)
                        else Color.White.copy(alpha = 0.055f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) {
                            InteractionOrange.copy(alpha = 0.55f)
                        } else {
                            Color.White.copy(alpha = 0.05f)
                        },
                        shape = RoundedCornerShape(14.dp)
                    )
                    .pressFeedback(
                        selected = selected,
                        onClick = { onPreviewEvent(event) }
                    )
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (index + 1).toString().padStart(2, '0'),
                        color = InteractionGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "即时预览 · ${interactionTypeLabel(event.type)}",
                        color = InteractionMuted,
                        fontSize = 9.sp
                    )
                }
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "预览组件",
                    tint = InteractionOrange,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun DebugTools(
    secondsText: String,
    onSecondsChanged: (String) -> Unit,
    onSeek: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = secondsText,
            onValueChange = onSecondsChanged,
            modifier = Modifier
                .weight(1f)
                .height(50.dp),
            singleLine = true,
            label = { Text("手动定位（秒）", fontSize = 10.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = InteractionOrange,
                unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                focusedLabelColor = InteractionGold,
                unfocusedLabelColor = InteractionMuted,
                cursorColor = InteractionOrange
            )
        )
        DebugToolButton(
            icon = Icons.Default.SkipNext,
            contentDescription = "跳转到指定秒数",
            background = InteractionOrange,
            onClick = onSeek
        )
        DebugToolButton(
            icon = Icons.Default.ClearAll,
            contentDescription = "清除互动记录",
            background = Color(0xFF71302D),
            onClick = onClear
        )
    }
}

@Composable
private fun DebugToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    background: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(background)
            .pressFeedback(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun interactionTypeLabel(type: InteractionType): String = when (type) {
    InteractionType.EMOTION -> "情绪互动"
    InteractionType.VALUE_BOOST -> "数值互动"
    InteractionType.QUIZ -> "剧情竞猜"
    InteractionType.QUIZ_RESULT -> "竞猜结果"
    InteractionType.RATING -> "滑动评分"
    InteractionType.KNOWLEDGE -> "知识卡片"
    InteractionType.PERSON_DETECT -> "人物识别"
    InteractionType.RELATION_GRAPH -> "关系图谱"
    InteractionType.WARNING -> "高能预警"
    InteractionType.HIGHLIGHT_COLLECT -> "名场面收藏"
    InteractionType.BROADCAST -> "顶部播报"
}

private fun triggerLabel(trigger: InteractionTrigger): String = when (trigger) {
    is InteractionTrigger.Fixed -> formatSec(trigger.timeSec)
    is InteractionTrigger.Range -> "${formatSec(trigger.startSec)}–${formatSec(trigger.endSec)}"
    is InteractionTrigger.EpisodeEnding -> "片尾 -${trigger.remainingSec.toInt()}s"
}

private fun formatSec(value: Double): String {
    if (value <= 0.0) return "00:00"
    val total = value.toInt()
    val minutes = total / 60
    val seconds = total % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatTimeMs(value: Long): String = formatSec(value.coerceAtLeast(0L) / 1_000.0)

internal fun interactionTriggerPositionMs(
    trigger: InteractionTrigger,
    episodeDurationMs: Long
): Long {
    val durationMs = episodeDurationMs.coerceAtLeast(0L)
    return when (trigger) {
        is InteractionTrigger.Fixed -> (trigger.timeSec * 1_000.0).toLong().coerceAtLeast(0L)
        is InteractionTrigger.Range -> (trigger.startSec * 1_000.0).toLong().coerceAtLeast(0L)
        is InteractionTrigger.EpisodeEnding -> {
            if (durationMs == 0L) {
                0L
            } else {
                (durationMs - (trigger.remainingSec * 1_000.0).toLong())
                    .coerceIn(0L, durationMs)
            }
        }
    }
}

internal fun interactionReplayPositionMs(
    trigger: InteractionTrigger,
    episodeDurationMs: Long,
    leadInMs: Long = 3_000L
): Long = (
    interactionTriggerPositionMs(trigger, episodeDurationMs) -
        leadInMs.coerceAtLeast(0L)
    ).coerceAtLeast(0L)
