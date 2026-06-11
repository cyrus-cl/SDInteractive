package com.sdinteractive.app.interactions.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sdinteractive.app.interactions.data.CharacterData
import com.sdinteractive.app.interactions.data.CharacterProfile
import com.sdinteractive.app.interactions.data.InteractionEvents
import com.sdinteractive.app.interactions.data.InteractionReferenceRegistry
import com.sdinteractive.app.interactions.engine.InteractionCoordinator
import com.sdinteractive.app.interactions.engine.HighlightSavedState
import com.sdinteractive.app.interactions.engine.InteractionUiState
import com.sdinteractive.app.interactions.model.EmotionPayload
import com.sdinteractive.app.interactions.model.HighlightPayload
import com.sdinteractive.app.interactions.model.InteractionEvent
import com.sdinteractive.app.interactions.model.InteractionPosition
import com.sdinteractive.app.interactions.model.InteractionType
import com.sdinteractive.app.interactions.model.KnowledgePayload
import com.sdinteractive.app.interactions.model.QuizPayload
import com.sdinteractive.app.interactions.model.RatingPayload
import com.sdinteractive.app.interactions.model.RelationGraphPayload
import com.sdinteractive.app.interactions.model.ValueBoostPayload
import com.sdinteractive.app.interactions.model.WarningPayload
import com.sdinteractive.app.interactions.storage.HighlightRecord
import com.sdinteractive.app.interactions.storage.InteractionRecords
import com.sdinteractive.app.player.PlayerSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun InteractionLayer(
    episodeNumber: Int,
    snapshot: PlayerSnapshot,
    records: InteractionRecords,
    onNextEpisode: () -> Unit,
    onOpenHighlights: () -> Unit,
    onPersonIdentifyRequest: (suspend () -> PersonIdentificationResult?)? = null,
    recordsRevision: Int = 0,
    debugEvent: InteractionEvent? = null,
    modifier: Modifier = Modifier
) {
    key(episodeNumber, recordsRevision) {
        InteractionLayerContent(
            episodeNumber = episodeNumber,
            snapshot = snapshot,
            records = records,
            onNextEpisode = onNextEpisode,
            onOpenHighlights = onOpenHighlights,
            onPersonIdentifyRequest = onPersonIdentifyRequest,
            debugEvent = debugEvent,
            modifier = modifier
        )
    }
}

@Composable
private fun InteractionLayerContent(
    episodeNumber: Int,
    snapshot: PlayerSnapshot,
    records: InteractionRecords,
    onNextEpisode: () -> Unit,
    onOpenHighlights: () -> Unit,
    onPersonIdentifyRequest: (suspend () -> PersonIdentificationResult?)?,
    debugEvent: InteractionEvent?,
    modifier: Modifier
) {
    val coordinator = remember(episodeNumber, records) { InteractionCoordinator(records) }
    var uiState by remember(coordinator) { mutableStateOf(InteractionUiState()) }
    var recordRevision by remember(coordinator) { mutableIntStateOf(0) }
    var displayedPrimary by remember(coordinator) { mutableStateOf<InteractionEvent?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val events = remember(episodeNumber) { InteractionEvents.forEpisode(episodeNumber) }

    LaunchedEffect(episodeNumber, snapshot, debugEvent, coordinator) {
        uiState = coordinator.update(
            episodeNumber = episodeNumber,
            snapshot = snapshot,
            events = events,
            debugEvent = debugEvent
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        uiState.broadcast?.let { message ->
            GlobalBroadcastBar(
                message = message,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 62.dp)
            )
        }

        if (uiState.results.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(start = 18.dp, end = 92.dp, top = 112.dp)
                    .widthIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.results.take(2).forEach { presentation ->
                    QuizResultToast(
                        result = presentation.result
                    )
                }
            }
        }

        LaunchedEffect(uiState.primary?.id) {
            uiState.primary?.let { displayedPrimary = it }
        }
        displayedPrimary?.let { event ->
            AnimatedVisibility(
                visible = uiState.primary != null,
                enter = fadeIn(tween(120)) + scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(140)
                ),
                exit = fadeOut(tween(360)) + scaleOut(
                    targetScale = 0.96f,
                    animationSpec = tween(360)
                ),
                modifier = primaryPlacement(event)
            ) {
                PrimaryInteraction(
                    event = event,
                    snapshot = snapshot,
                    coordinator = coordinator,
                    onOpenHighlights = onOpenHighlights,
                    recordRevision = recordRevision,
                    onRecordChanged = {
                        recordRevision += 1
                        uiState = coordinator.state
                    },
                    onStateChanged = { uiState = coordinator.state },
                    onNextEpisode = { delayMs ->
                        coroutineScope.launch {
                            delay(delayMs)
                            onNextEpisode()
                        }
                    },
                    onPersonIdentifyRequest = onPersonIdentifyRequest
                )
            }
        }

        uiState.savedToast?.let { saved ->
            HighlightSavedToast(
                state = saved,
                onOpenHighlights = onOpenHighlights,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(start = 18.dp, end = 92.dp, top = 112.dp)
                    .widthIn(max = 316.dp)
            )
        }

        if (
            shouldShowAmbientPersonHost(
                hasResults = uiState.results.isNotEmpty(),
                hasSavedToast = uiState.savedToast != null,
                hasBroadcast = uiState.broadcast != null,
                primaryType = uiState.primary?.type
            )
        ) {
            val personSkin = personSkinSpec()
            val personTopPaddingDp = ambientPersonTopPaddingDp(
                hasResults = uiState.results.isNotEmpty(),
                hasSavedToast = uiState.savedToast != null,
                hasBroadcast = uiState.broadcast != null,
                skin = personSkin
            )
            PersonInteractionHost(
                episodeNumber = episodeNumber,
                positionSec = snapshot.positionSec,
                onIdentifyRequest = onPersonIdentifyRequest,
                modifier = Modifier
                    .fillMaxSize(),
                entryTopPaddingDp = personTopPaddingDp,
                entryEndPaddingDp = personSkin.anchorEndPaddingDp
            )
        }
    }
}

internal fun shouldShowAmbientPersonHost(
    hasResults: Boolean,
    hasSavedToast: Boolean,
    hasBroadcast: Boolean,
    primaryType: InteractionType?
): Boolean {
    return primaryType != InteractionType.PERSON_DETECT &&
        primaryType != InteractionType.RELATION_GRAPH
}

internal fun ambientPersonTopPaddingDp(
    hasResults: Boolean,
    hasSavedToast: Boolean,
    hasBroadcast: Boolean,
    skin: PersonSkinSpec = personSkinSpec()
): Int = when {
    hasBroadcast && (hasResults || hasSavedToast) -> skin.anchorTopPaddingDp + 96
    hasBroadcast -> skin.anchorTopPaddingDp + 72
    else -> skin.anchorTopPaddingDp
}

@Composable
private fun PrimaryInteraction(
    event: InteractionEvent,
    snapshot: PlayerSnapshot,
    coordinator: InteractionCoordinator,
    onOpenHighlights: () -> Unit,
    recordRevision: Int,
    onRecordChanged: () -> Unit,
    onStateChanged: () -> Unit,
    onNextEpisode: (Long) -> Unit,
    onPersonIdentifyRequest: (suspend () -> PersonIdentificationResult?)?,
) {
    when (event.type) {
        InteractionType.EMOTION -> {
            var count by remember(event.id) {
                mutableIntStateOf(coordinator.emotionCount(event.id))
            }
            EmotionBurstButton(
                payload = event.payload as EmotionPayload,
                count = count,
                onClick = {
                    count = coordinator.onEmotionClick(event)
                    onStateChanged()
                },
                modifier = Modifier
            )
        }

        InteractionType.VALUE_BOOST -> {
            val payload = event.payload as ValueBoostPayload
            var value by remember(event.id, recordRevision) {
                mutableIntStateOf(coordinator.valueFor(event))
            }
            var reachedMax by remember(event.id, value) {
                mutableStateOf(value >= payload.maxValue)
            }
            ValueBoostCard(
                payload = payload,
                value = value,
                reachedMax = reachedMax,
                onIncrement = {
                    val update = coordinator.incrementValue(event)
                    value = update.value
                    reachedMax = update.reachedMax
                    onRecordChanged()
                },
                modifier = Modifier
            )
        }

        InteractionType.QUIZ -> {
            val answer = remember(event.id, recordRevision) {
                coordinator.quizAnswer(event)
            }
            QuizCard(
                payload = event.payload as QuizPayload,
                trigger = event.trigger,
                positionSec = snapshot.positionSec,
                durationMs = snapshot.durationMs,
                restoredAnswer = answer,
                onSubmit = { optionId ->
                    val submission = coordinator.submitQuiz(event, optionId)
                    if (submission.saved) {
                        coordinator.dismissPrimary()
                        onRecordChanged()
                        if (submission.openNextEpisode) {
                            onNextEpisode(submission.nextEpisodeDelayMs)
                        }
                    }
                    submission.saved
                },
                modifier = Modifier.widthIn(max = 276.dp)
            )
        }

        InteractionType.HIGHLIGHT_COLLECT -> {
            val payload = event.payload as HighlightPayload
            if (InteractionReferenceRegistry.isSavedToastPreview(event)) {
                HighlightSavedToast(
                    state = remember(event.id) {
                        HighlightSavedState(
                            record = HighlightRecord(
                                episodeNumber = event.episodeNumber,
                                highlightId = payload.highlightId,
                                title = payload.title,
                                clipStartSec = payload.clipStartSec,
                                clipEnd = payload.clipEnd,
                                coverTimeSec = payload.coverTimeSec,
                                createdAtMs = 0L
                            ),
                            successText = payload.successText,
                            detailText = payload.detailText,
                            actionText = payload.actionText
                        )
                    },
                    onOpenHighlights = {
                        coordinator.dismissPrimary()
                        onStateChanged()
                        onOpenHighlights()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                val collected = remember(event.id, recordRevision) {
                    coordinator.isHighlightCollected(event)
                }
                HighlightCollectButton(
                    payload = payload,
                    isCollected = collected,
                    onCollect = {
                        val result = coordinator.collectHighlight(event, snapshot.durationSec)
                        if (result.saved) {
                            coordinator.dismissPrimary()
                            onRecordChanged()
                        }
                    },
                    modifier = Modifier
                )
            }
        }

        InteractionType.RATING -> {
            val restoredRecord = remember(event.id, recordRevision) {
                coordinator.ratingRecord(event)
            }
            RatingSliderCard(
                payload = event.payload as RatingPayload,
                restoredRecord = restoredRecord,
                onInteraction = {
                    coordinator.refreshPrimaryIdle(event)
                    onStateChanged()
                },
                onSubmit = { value ->
                    val submission = coordinator.submitRating(event, value)
                    if (submission.saved) {
                        coordinator.dismissPrimary()
                        onRecordChanged()
                    }
                    submission.saved
                },
                modifier = Modifier.widthIn(max = ratingSkinSpec().widthDp.dp)
            )
        }

        InteractionType.KNOWLEDGE -> {
            var expanded by remember(event.id) {
                mutableStateOf(InteractionReferenceRegistry.isExpandedKnowledgePreview(event))
            }
            LaunchedEffect(expanded, event.id) {
                coordinator.setPrimaryIdleHeld(event, expanded)
            }
            KnowledgeCard(
                payload = event.payload as KnowledgePayload,
                expanded = expanded,
                onExpandedChange = { next ->
                    expanded = next
                    coordinator.setPrimaryIdleHeld(event, next)
                    onStateChanged()
                },
                onDismiss = {
                    coordinator.dismissPrimary()
                    onStateChanged()
                },
                modifier = Modifier.widthIn(max = knowledgeSkinSpec().widthDp.dp)
            )
        }

        InteractionType.WARNING -> {
            HighEnergyWarning(
                payload = event.payload as WarningPayload,
                onFinished = {
                    coordinator.dismissPrimary()
                    onStateChanged()
                },
                modifier = Modifier.widthIn(max = 248.dp)
            )
        }

        InteractionType.PERSON_DETECT -> {
            PersonInteractionHost(
                episodeNumber = event.episodeNumber,
                positionSec = snapshot.positionSec,
                onIdentifyRequest = onPersonIdentifyRequest,
                modifier = Modifier.fillMaxSize(),
                entryTopPaddingDp = personSkinSpec().primaryTopPaddingDp,
                entryEndPaddingDp = personSkinSpec().primaryEndPaddingDp
            )
        }

        InteractionType.RELATION_GRAPH -> {
            val payload = event.payload as RelationGraphPayload
            var open by remember(event.id) { mutableStateOf(true) }
            if (open) {
                RelationGraphModal(
                    focus = CharacterData.profile(payload.focusCharacterId),
                    episodeNumber = event.episodeNumber,
                    onDismiss = {
                        open = false
                        coordinator.dismissPrimary()
                        onStateChanged()
                    }
                )
            }
        }

        InteractionType.QUIZ_RESULT,
        InteractionType.BROADCAST -> Unit
    }
}

private fun BoxScope.primaryPlacement(event: InteractionEvent): Modifier {
    val bottomPadding = when (event.type) {
        InteractionType.EMOTION -> 212.dp
        InteractionType.QUIZ,
        InteractionType.RATING,
        InteractionType.KNOWLEDGE,
        InteractionType.VALUE_BOOST -> 216.dp
        InteractionType.HIGHLIGHT_COLLECT -> 210.dp
        else -> 172.dp
    }
    val compactWidth = Modifier.widthIn(max = 320.dp)
    if (event.type == InteractionType.RELATION_GRAPH) {
        return Modifier
            .fillMaxSize()
            .align(Alignment.Center)
    }
    if (event.type == InteractionType.WARNING) {
        return Modifier
            .align(Alignment.Center)
            .padding(start = 24.dp, end = 92.dp)
            .then(Modifier.widthIn(max = 300.dp))
    }
    if (event.type == InteractionType.PERSON_DETECT) {
        return Modifier
            .fillMaxSize()
            .align(Alignment.Center)
    }
    return when (event.position) {
        InteractionPosition.BOTTOM,
        InteractionPosition.BOTTOM_CENTER -> Modifier
            .align(Alignment.BottomStart)
            .padding(start = 14.dp, end = 74.dp, bottom = bottomPadding)
            .then(compactWidth)

        InteractionPosition.CENTER -> Modifier
            .align(Alignment.Center)
            .padding(horizontal = 24.dp)
            .then(compactWidth)

        InteractionPosition.RIGHT,
        InteractionPosition.RIGHT_BOTTOM -> Modifier
            .align(Alignment.BottomEnd)
            .padding(start = 86.dp, end = 14.dp, bottom = bottomPadding)
            .then(compactWidth)

        InteractionPosition.TOP,
        InteractionPosition.TOP_CENTER -> Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(start = 18.dp, end = 74.dp, top = 106.dp)
            .then(compactWidth)

        InteractionPosition.LEFT,
        InteractionPosition.LEFT_BOTTOM -> Modifier
            .align(Alignment.BottomStart)
            .padding(start = 14.dp, end = 76.dp, bottom = bottomPadding)
            .then(compactWidth)
    }
}
