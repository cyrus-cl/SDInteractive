@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sdinteractive.app.interactions.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdinteractive.app.interactions.data.CharacterData
import com.sdinteractive.app.interactions.data.CharacterProfile
import com.sdinteractive.app.interactions.data.CharacterRelation
import com.sdinteractive.app.interactions.data.RelationKind
import kotlinx.coroutines.delay

data class PersonAiInsight(
    val title: String,
    val insight: String,
    val hook: String,
    val source: String
)

data class IdentifiedPerson(
    val profile: CharacterProfile,
    val confidence: Double,
    val screenPosition: String,
    val evidence: String
)

data class PersonIdentificationResult(
    val characters: List<IdentifiedPerson>,
    val candidateProfiles: List<CharacterProfile>,
    val sceneRole: String,
    val usedFallback: Boolean,
    val source: String
)

@Composable
fun PersonInteractionHost(
    episodeNumber: Int,
    positionSec: Double,
    onIdentifyRequest: (suspend () -> PersonIdentificationResult?)? = null,
    modifier: Modifier = Modifier,
    entryTopPaddingDp: Int = personSkinSpec().anchorTopPaddingDp,
    entryEndPaddingDp: Int = personSkinSpec().anchorEndPaddingDp
) {
    var scanning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<PersonIdentificationResult?>(null) }
    var selectedPerson by remember { mutableStateOf<IdentifiedPerson?>(null) }
    var graphFocus by remember { mutableStateOf<CharacterProfile?>(null) }

    Box(modifier = modifier) {
        PersonDetectEntry(
            scanning = scanning,
            onClick = {
                if (!scanning) {
                    selectedPerson = null
                    graphFocus = null
                    scanning = true
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = entryTopPaddingDp.dp, end = entryEndPaddingDp.dp)
        )

        when {
            scanning -> PersonScanModal()
            graphFocus != null -> RelationGraphModal(
                focus = graphFocus ?: CharacterData.suYu,
                episodeNumber = episodeNumber,
                onDismiss = { graphFocus = null }
            )
            result != null && selectedPerson != null -> PersonDetailModal(
                person = requireNotNull(selectedPerson),
                result = requireNotNull(result),
                onBack = { selectedPerson = null },
                onClose = {
                    selectedPerson = null
                    result = null
                },
                onOpenGraph = { graphFocus = requireNotNull(selectedPerson).profile }
            )
            result != null -> PersonResultsModal(
                result = requireNotNull(result),
                onSelect = { selectedPerson = it },
                onRetry = {
                    result = null
                    scanning = true
                },
                onClose = { result = null }
            )
        }
    }

    LaunchedEffect(scanning) {
        if (!scanning) return@LaunchedEffect
        delay(personSkinSpec().scanDurationMs.toLong())
        result = onIdentifyRequest
            ?.let { request -> runCatching { request() }.getOrNull() }
            ?: localPersonFallback(episodeNumber, positionSec)
        scanning = false
    }
}

@Composable
fun PersonDetectEntry(
    scanning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = personSkinSpec()
    val transition = rememberInfiniteTransition(label = "personScanEntry")
    val scanProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "personScanEntryProgress"
    )
    Box(
        modifier = modifier
            .width(skin.detectButtonWidthDp.dp)
            .height(skin.detectButtonHeightDp.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xE61D2129), Color(0xE650535C), Color(0xE61D2129))
                )
            )
            .border(
                1.dp,
                if (scanning) skin.primary.copy(alpha = 0.86f) else Color.White.copy(alpha = 0.36f),
                RoundedCornerShape(24.dp)
            )
            .pressFeedback(
                enabled = !scanning,
                stateDescription = if (scanning) "识别中" else "识别人物",
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val sweepX = size.width * scanProgress
            drawLine(
                color = skin.primary.copy(alpha = if (scanning) 0.54f else 0.18f),
                start = Offset(sweepX - 20f, 0f),
                end = Offset(sweepX + 20f, size.height),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
            repeat(3) { index ->
                val phase = (scanProgress + index * 0.22f) % 1f
                drawCircle(
                    color = skin.primary.copy(alpha = 0.18f * (1f - phase)),
                    radius = size.height * (0.45f + phase * 1.4f),
                    center = Offset(size.height * 0.52f, size.height * 0.5f),
                    style = Stroke(width = 2f)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.PersonSearch,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                if (scanning) "识别中" else "识别人物",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun PersonScanModal() {
    PersonModalBackdrop(onDismiss = {}) {
        val skin = personSkinSpec()
        val transition = rememberInfiniteTransition(label = "personScanModal")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
            label = "personScanModalProgress"
        )
        Box(
            modifier = Modifier
                .width(224.dp)
                .height(244.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xF0464B56), Color(0xF0252A33), Color(0xF01B2028))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(18.dp))
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height * 0.48f)
                repeat(4) { index ->
                    val phase = (progress + index * 0.18f) % 1f
                    drawCircle(
                        color = skin.primary.copy(alpha = 0.20f * (1f - phase)),
                        radius = size.minDimension * (0.18f + phase * 0.25f),
                        center = center,
                        style = Stroke(width = 2f)
                    )
                }
                drawArc(
                    color = Color.White.copy(alpha = 0.58f),
                    startAngle = progress * 360f,
                    sweepAngle = 76f,
                    useCenter = false,
                    topLeft = Offset(
                        center.x - size.minDimension * 0.24f,
                        center.y - size.minDimension * 0.24f
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        size.minDimension * 0.48f,
                        size.minDimension * 0.48f
                    ),
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("正在识别人物", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Text("仅分析当前画面", color = InteractionGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PersonSearch,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.82f),
                        modifier = Modifier.size(36.dp)
                    )
                }
                Text("豆包视觉分析中，请稍候", color = Color.White.copy(alpha = 0.74f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PersonResultsModal(
    result: PersonIdentificationResult,
    onSelect: (IdentifiedPerson) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    PersonModalBackdrop(onDismiss = onClose) {
        GlassSurface(
            accent = InteractionGold,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PersonModalHeader(
                    title = if (result.characters.isEmpty()) "未能确认人物" else "当前画面人物",
                    subtitle = if (result.characters.isEmpty()) {
                        "没有人物达到视觉确认阈值"
                    } else {
                        "识别到 ${result.characters.size} 人 · 仅当前帧"
                    },
                    onClose = onClose
                )
                Text(
                    result.sceneRole,
                    color = InteractionMuted,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 410.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    if (result.characters.isNotEmpty()) {
                        items(result.characters, key = { it.profile.id }) { person ->
                            IdentifiedPersonRow(person = person, onClick = { onSelect(person) })
                        }
                    } else {
                        item {
                            Text(
                                "剧情候选 · 未确认",
                                color = InteractionGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        items(result.candidateProfiles, key = { it.id }) { profile ->
                            CandidatePersonRow(profile)
                        }
                    }
                }
                if (result.characters.isEmpty()) {
                    PersonActionButton(text = "重新识别当前画面", onClick = onRetry)
                }
            }
        }
    }
}

@Composable
private fun PersonDetailModal(
    person: IdentifiedPerson,
    result: PersonIdentificationResult,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onOpenGraph: () -> Unit
) {
    PersonModalBackdrop(onDismiss = onClose) {
        GlassSurface(
            accent = InteractionGold,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 650.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .pressFeedback(stateDescription = "返回人物列表", onClick = onBack)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("返回列表", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("人物详情", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Text("豆包视觉 · 当前帧", color = InteractionGold, fontSize = 10.sp)
                    }
                    CloseButton(stateDescription = "关闭人物详情", onClick = onClose)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PersonPortrait(person.profile)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        InfoLine("姓名", person.profile.name)
                        InfoLine("身份", person.profile.identity)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            person.profile.tags.take(3).forEach { tag ->
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(9.dp))
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(tag, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Text(
                    person.profile.description,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                IdentificationDetails(person, result)
                PersonActionButton(text = "查看关系图", onClick = onOpenGraph)
            }
        }
    }
}

@Composable
private fun PersonModalBackdrop(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB8000000))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 16.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .clickable(onClick = {}),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
private fun PersonModalHeader(
    title: String,
    subtitle: String,
    onClose: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = InteractionGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        CloseButton(stateDescription = "关闭人物识别结果", onClick = onClose)
    }
}

@Composable
private fun CloseButton(
    stateDescription: String,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .pressFeedback(stateDescription = stateDescription, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
    }
}

@Composable
private fun IdentifiedPersonRow(
    person: IdentifiedPerson,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .pressFeedback(stateDescription = "查看${person.profile.name}详情", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersonAvatar(person.profile, 48)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(person.profile.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${(person.confidence * 100).toInt()}%",
                    color = InteractionGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Text(
                person.profile.identity,
                color = Color.White.copy(alpha = 0.76f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${screenPositionLabel(person.screenPosition)} · ${person.evidence}",
                color = InteractionMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text("详情", color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CandidatePersonRow(profile: CharacterProfile) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersonAvatar(profile, 42)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.name, color = Color.White.copy(alpha = 0.86f), fontSize = 14.sp, fontWeight = FontWeight.Black)
            Text(
                profile.identity,
                color = InteractionMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text("未确认", color = InteractionGold.copy(alpha = 0.76f), fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun PersonAvatar(
    profile: CharacterProfile,
    sizeDp: Int
) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(InteractionGold, Color(0xFF2B1B12))))
            .border(1.dp, Color.White.copy(alpha = 0.58f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            profile.name.take(1),
            color = Color.White,
            fontSize = (sizeDp * 0.38f).sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun PersonPortrait(profile: CharacterProfile) {
    Box(
        Modifier
            .width(94.dp)
            .height(124.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.radialGradient(
                    listOf(InteractionGold.copy(alpha = 0.74f), Color(0xFF2C241D), Color(0xFF101219))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(profile.name.take(1), color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun IdentificationDetails(
    person: IdentifiedPerson,
    result: PersonIdentificationResult
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, InteractionGold.copy(alpha = 0.44f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            "置信度 ${(person.confidence * 100).toInt()}% · ${screenPositionLabel(person.screenPosition)}",
            color = InteractionGold,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            result.sceneRole,
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            person.evidence,
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PersonActionButton(
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(InteractionGold.copy(alpha = 0.30f))
            .border(1.dp, InteractionGold.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
            .pressFeedback(stateDescription = text, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.AccountTree,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun InfoLine(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$label：", color = InteractionMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                value,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
    }
}

private fun screenPositionLabel(position: String): String = when (position) {
    "left" -> "画面左侧"
    "center" -> "画面中央"
    "right" -> "画面右侧"
    else -> "画面位置未知"
}

private fun localPersonFallback(
    episodeNumber: Int,
    positionSec: Double
): PersonIdentificationResult {
    val profile = CharacterData.detect(episodeNumber, positionSec)
    return PersonIdentificationResult(
        characters = emptyList(),
        candidateProfiles = listOf(profile),
        sceneRole = "客户端剧情候选",
        usedFallback = true,
        source = "android_fallback"
    )
}

@Composable
fun RelationGraphModal(
    focus: CharacterProfile,
    episodeNumber: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val relations = relationsForFocus(
        focusCharacterId = focus.id,
        relations = CharacterData.relationsForGraph(
            episodeNumber = episodeNumber,
            minimumNodeCount = personSkinSpec().relationNodeCount
        )
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xC2000000))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 16.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            accent = InteractionGold,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .heightIn(max = 620.dp)
                .clickable(onClick = {})
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccountTree,
                        contentDescription = null,
                        tint = InteractionGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("人物关系图谱", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                        Text("聚焦 ${focus.name}", color = InteractionGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    CloseButton(stateDescription = "关闭人物关系图", onClick = onDismiss)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, InteractionGold.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PersonAvatar(focus, 54)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(focus.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text(focus.identity, color = InteractionMuted, fontSize = 11.sp)
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 390.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(relations) { relation ->
                        RelationRow(focus = focus, relation = relation)
                    }
                }
                RelationLegend()
            }
        }
    }
}

@Composable
private fun RelationRow(
    focus: CharacterProfile,
    relation: CharacterRelation
) {
    val targetId = when (focus.id) {
        relation.sourceCharacterId -> relation.targetCharacterId
        relation.targetCharacterId -> relation.sourceCharacterId
        else -> relation.targetCharacterId
    }
    val target = CharacterData.profile(targetId)
    val accent = relationColor(relation.kind)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).background(accent, CircleShape))
        Spacer(Modifier.width(10.dp))
        PersonAvatar(target, 38)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(target.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Text(relation.label, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Text("第${relation.unlockEpisode}集", color = InteractionMuted, fontSize = 9.sp)
    }
}

@Composable
private fun RelationLegend() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(
            RelationKind.ALLY to "盟友",
            RelationKind.FAMILY to "家族",
            RelationKind.EMOTIONAL_TENSION to "情感",
            RelationKind.ENEMY to "对立"
        ).forEach { (kind, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(relationColor(kind), CircleShape))
                Spacer(Modifier.width(5.dp))
                Text(label, color = InteractionMuted, fontSize = 11.sp)
            }
        }
    }
}

private fun relationColor(kind: RelationKind): Color = when (kind) {
    RelationKind.ALLY,
    RelationKind.SUPPORT -> Color(0xFF4AD7A3)
    RelationKind.FAMILY -> InteractionGold
    RelationKind.EMOTIONAL_TENSION -> Color(0xFFFF79B0)
    RelationKind.ENEMY,
    RelationKind.SIBLING_RIVALRY -> Color(0xFFFF6652)
}

internal fun relationsForFocus(
    focusCharacterId: String,
    relations: List<CharacterRelation>
): List<CharacterRelation> {
    val directRelations = relations.filter {
        it.sourceCharacterId == focusCharacterId || it.targetCharacterId == focusCharacterId
    }
    return directRelations.ifEmpty { relations }
}
