package com.sdinteractive.app

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.sdinteractive.app.interactions.data.CharacterProfile
import com.sdinteractive.app.interactions.data.InteractionEvents
import com.sdinteractive.app.interactions.debug.InteractionDebugPanel
import com.sdinteractive.app.interactions.debug.interactionReplayPositionMs
import com.sdinteractive.app.interactions.model.ClipEnd
import com.sdinteractive.app.interactions.model.InteractionEvent
import com.sdinteractive.app.interactions.model.InteractionTrigger
import com.sdinteractive.app.interactions.storage.HighlightRecord
import com.sdinteractive.app.interactions.storage.SharedPreferencesInteractionStorage
import com.sdinteractive.app.interactions.ui.IdentifiedPerson
import com.sdinteractive.app.interactions.ui.InteractionLayer
import com.sdinteractive.app.interactions.ui.PersonAiInsight
import com.sdinteractive.app.interactions.ui.PersonIdentificationResult
import com.sdinteractive.app.player.PlaybackControlsState
import com.sdinteractive.app.player.PlayerController
import com.sdinteractive.app.player.PlayerControlsOverlay
import com.sdinteractive.app.player.PlayerSnapshot
import com.sdinteractive.app.player.RemoteVideoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val DEFAULT_SERVER_URL = BuildConfig.DEFAULT_SERVER_URL
internal const val PERSON_INSIGHT_TIMEOUT_MS = 55_000L
private val Orange = Color(0xFFFF7A00)
private val Amber = Color(0xFFFFC247)
private val Dark = Color(0xFF070707)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            ?: "android-device"

        setContent {
            MaterialTheme {
                AppRoot(deviceId = deviceId)
            }
        }
    }
}

@Composable
private fun AppRoot(deviceId: String, viewModel: MainViewModel = viewModel()) {
    val state = viewModel.state

    LaunchedEffect(deviceId) {
        viewModel.bootstrap(DEFAULT_SERVER_URL, deviceId)
    }

    Surface(color = Dark, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when (state.tab) {
                MainTab.Video -> VideoHome(
                    state = state,
                    onAction = viewModel::recordAction,
                    onCommentSend = viewModel::sendComment,
                    onEpisodeSelect = viewModel::openEpisode,
                    onPreloadEpisodes = viewModel::preloadEpisodes,
                    onOpenHighlights = { viewModel.selectTab(MainTab.Mine) }
                )

                MainTab.Mine -> MineHome(
                    state = state,
                    onServerUrlChange = viewModel::updateServerUrl,
                    onReload = { viewModel.bootstrap(state.serverUrl, deviceId) }
                )
            }

            BottomTabs(
                selected = state.tab,
                onSelect = viewModel::selectTab,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

private data class PendingInteractionJump(
    val episodeId: String,
    val trigger: InteractionTrigger,
    val requestId: Long
)

@Composable
private fun VideoHome(
    state: UiState,
    onAction: (String) -> Unit,
    onCommentSend: (String) -> Unit,
    onEpisodeSelect: (EpisodeDto) -> Unit,
    onPreloadEpisodes: (List<EpisodeDto>) -> Unit,
    onOpenHighlights: () -> Unit
) {
    val context = LocalContext.current
    val interactionRecords = remember(context.applicationContext) {
        SharedPreferencesInteractionStorage.from(context.applicationContext)
    }
    val playInfo = state.player
    var showComments by remember { mutableStateOf(false) }
    var playerSnapshot by remember(playInfo?.episodeId) { mutableStateOf(PlayerSnapshot()) }
    var playerController by remember(playInfo?.episodeId) { mutableStateOf<PlayerController?>(null) }
    var debugEvent by remember(playInfo?.episodeId) { mutableStateOf<InteractionEvent?>(null) }
    var pendingInteractionJump by remember { mutableStateOf<PendingInteractionJump?>(null) }
    var recordsRevision by remember { mutableIntStateOf(0) }
    val playbackControls = remember(playInfo?.episodeId) { PlaybackControlsState() }
    var controlsRevision by remember(playInfo?.episodeId) { mutableIntStateOf(0) }
    var playbackFailed by remember(playInfo?.episodeId) { mutableStateOf(false) }
    fun mutateControls(block: PlaybackControlsState.() -> Unit) {
        playbackControls.block()
        controlsRevision += 1
    }
    val orderedEpisodes = remember(state.episodes) {
        state.episodes.values.flatten().sortedBy { it.sortOrder }
    }
    val currentIndex = orderedEpisodes
        .indexOfFirst { it.episodeId == state.currentEpisode?.episodeId }
        .coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = currentIndex) {
        orderedEpisodes.size.coerceAtLeast(1)
    }
    val showPageChrome = playbackControls.showPageChrome && !pagerState.isScrollInProgress

    LaunchedEffect(
        pendingInteractionJump?.requestId,
        state.currentEpisode?.episodeId,
        playerController,
        playerSnapshot.durationMs
    ) {
        val pending = pendingInteractionJump ?: return@LaunchedEffect
        if (
            state.currentEpisode?.episodeId == pending.episodeId &&
            playerController != null
        ) {
            if (
                pending.trigger is InteractionTrigger.EpisodeEnding &&
                playerSnapshot.durationMs <= 0L
            ) {
                return@LaunchedEffect
            }
            playerController?.seekTo(
                interactionReplayPositionMs(
                    trigger = pending.trigger,
                    episodeDurationMs = playerSnapshot.durationMs
                )
            )
            pendingInteractionJump = null
        }
    }

    LaunchedEffect(currentIndex, orderedEpisodes) {
        val ids = adjacentEpisodeIds(orderedEpisodes, currentIndex).toSet()
        onPreloadEpisodes(orderedEpisodes.filter { it.episodeId in ids })
    }

    LaunchedEffect(pagerState, orderedEpisodes) {
        snapshotFlow { pagerState.targetPage }
            .distinctUntilChanged()
            .collect { page ->
                val ids = adjacentEpisodeIds(orderedEpisodes, page).toSet()
                onPreloadEpisodes(orderedEpisodes.filter { it.episodeId in ids })
            }
    }

    LaunchedEffect(currentIndex, orderedEpisodes.size) {
        if (
            orderedEpisodes.isNotEmpty() &&
            !pagerState.isScrollInProgress &&
            pagerState.currentPage != currentIndex
        ) {
            pagerState.scrollToPage(currentIndex)
        }
    }

    LaunchedEffect(pagerState, orderedEpisodes, state.currentEpisode?.episodeId) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val target = orderedEpisodes.getOrNull(page) ?: return@collect
                if (target.episodeId != state.currentEpisode?.episodeId) {
                    onEpisodeSelect(target)
                }
            }
    }

    LaunchedEffect(playerSnapshot.isEnded, state.currentEpisode?.episodeId, orderedEpisodes) {
        if (playerSnapshot.isEnded) {
            val nextIndex = (currentIndex + 1).takeIf { it < orderedEpisodes.size }
            if (nextIndex != null) pagerState.animateScrollToPage(nextIndex)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (orderedEpisodes.isNotEmpty()) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !showComments && !playbackControls.scrubbing,
                beyondViewportPageCount = 1
            ) { page ->
                val episode = orderedEpisodes.getOrNull(page)
                val pagePlayInfo = episode?.episodeId?.let { state.playInfos[it] }
                if (episode != null && pagePlayInfo != null) {
                    val active = episode.episodeId == state.currentEpisode?.episodeId
                    RemoteVideoPlayer(
                        episodeId = pagePlayInfo.episodeId,
                        videoUrl = pagePlayInfo.videoUrl,
                        expectedDurationMs = pagePlayInfo.durationMs,
                        active = active,
                        modifier = Modifier.fillMaxSize(),
                        onSnapshot = { if (active) playerSnapshot = it },
                        onControllerReady = { if (active) playerController = it },
                        onSingleTap = {
                            if (active && !playbackControls.scrubbing) {
                                mutateControls { onSingleTap() }
                            }
                        },
                        onPlaybackErrorChange = {
                            if (active) {
                                playbackFailed = it
                            }
                        }
                    )
                } else {
                    EpisodeSwipePreview(
                        episode = episode,
                        isLoading = state.isLoading,
                        error = state.error.takeIf {
                            episode?.episodeId == state.currentEpisode?.episodeId
                        },
                        onRetry = { episode?.let(onEpisodeSelect) }
                    )
                }
            }
        } else {
            LoadingOrEmpty(state = state, onFirstEpisode = {
                state.episodes.values.firstOrNull()?.firstOrNull()?.let(onEpisodeSelect)
            })
        }

        AnimatedVisibility(
            visible = showPageChrome,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(180))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0x66000000),
                            0.28f to Color.Transparent,
                            0.62f to Color.Transparent,
                            1f to Color(0xCC000000)
                        )
                    )
            )
        }

        AnimatedVisibility(
            visible = showPageChrome,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(180))
        ) {
            Box(Modifier.fillMaxSize()) {
                TopChrome(modifier = Modifier.align(Alignment.TopCenter))
                RightActionRail(
                    liked = state.liked,
                    favorited = state.favorited,
                    profile = state.profile,
                    onLike = { onAction("LIKE") },
                    onFavorite = { onAction("FAVORITE") },
                    onComment = { showComments = true },
                    onShare = { onAction("SHARE") },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 76.dp)
                )
                VideoCaption(
                    state = state,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, end = 86.dp, bottom = 60.dp)
                )
            }
        }

        state.currentEpisode?.let { episode ->
            AnimatedVisibility(
                visible = showPageChrome,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(360))
            ) {
                InteractionLayer(
                    episodeNumber = episode.sortOrder,
                    snapshot = playerSnapshot,
                    records = interactionRecords,
                    onNextEpisode = {
                        nextEpisodeAfter(orderedEpisodes, episode)?.let(onEpisodeSelect)
                    },
                    onOpenHighlights = onOpenHighlights,
                    onPersonIdentifyRequest = {
                        runCatching {
                            withTimeout(PERSON_INSIGHT_TIMEOUT_MS) {
                                val frameImageBase64 = playerController?.captureFrameJpegBase64()
                                ApiFactory.create(state.serverUrl)
                                    .personIdentify(
                                        toPersonIdentifyRequest(
                                            episodeNumber = episode.sortOrder,
                                            positionSec = playerSnapshot.positionSec,
                                            frameImageBase64 = frameImageBase64,
                                            frameMimeType = frameImageBase64?.let { "image/jpeg" }
                                        )
                                    )
                                    .data
                                    .toPersonIdentification()
                            }
                        }.getOrNull()
                    },
                    recordsRevision = recordsRevision,
                    debugEvent = debugEvent
                )
            }

            if (BuildConfig.DEBUG) {
                InteractionDebugPanel(
                    currentEpisodeNumber = episode.sortOrder,
                    availableEpisodeNumbers = orderedEpisodes.map { it.sortOrder },
                    episodeDurationsMs = mapOf(episode.sortOrder to playerSnapshot.durationMs),
                    currentPositionSec = playerSnapshot.positionSec,
                    durationSec = playerSnapshot.durationSec,
                    activeEventId = debugEvent?.id,
                    storyEvents = InteractionEvents.all,
                    onNavigateToEvent = { event ->
                        val targetEpisode = orderedEpisodes
                            .firstOrNull { it.sortOrder == event.episodeNumber }
                            ?: return@InteractionDebugPanel
                        debugEvent = null
                        pendingInteractionJump = PendingInteractionJump(
                            episodeId = targetEpisode.episodeId,
                            trigger = event.trigger,
                            requestId = System.nanoTime()
                        )
                        if (targetEpisode.episodeId != state.currentEpisode?.episodeId) {
                            onEpisodeSelect(targetEpisode)
                        }
                    },
                    onSeekToSec = { seconds ->
                        playerController?.seekTo((seconds * 1_000.0).toLong())
                    },
                    onPreviewEvent = { event ->
                        debugEvent = event.copy(id = "${event.id}_debug_${System.nanoTime()}")
                    },
                    onClearRecords = {
                        interactionRecords.clearAll()
                        recordsRevision += 1
                        debugEvent = null
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = 12.dp, top = 92.dp)
                )
            }
        }

        PlayerControlsOverlay(
            snapshot = playerSnapshot,
            controller = playerController,
            playbackFailed = playbackFailed,
            controls = playbackControls,
            controlsRevision = controlsRevision,
            onControlsChanged = { controlsRevision += 1 },
            onRetry = {
                playbackFailed = false
                playerController?.retry()
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showComments) {
            CommentPanel(
                profile = state.profile,
                onDismiss = { showComments = false },
                onSend = {
                    onCommentSend(it)
                    showComments = false
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun LoadingOrEmpty(state: UiState, onFirstEpisode: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (state.isLoading) "正在连接短剧服务" else "暂无可播放剧集",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onFirstEpisode,
            colors = ButtonDefaults.buttonColors(containerColor = Orange)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("播放第一集")
        }
        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Color(0xFFFFB4AB), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun EpisodeSwipePreview(
    episode: EpisodeDto?,
    isLoading: Boolean,
    error: String? = null,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF050505),
                        Color(0xFF17100B),
                        Color(0xFF050505)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Orange, Amber))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
            }
            Text(
                text = episode?.title ?: if (isLoading) "正在加载" else "暂无剧集",
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "天下第一纨绔",
                color = Amber,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            if (error != null) {
                Text(
                    text = error,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("重新加载")
                }
            }
        }
    }
}

internal fun nextEpisodeAfter(
    episodes: List<EpisodeDto>,
    currentEpisode: EpisodeDto?
): EpisodeDto? {
    if (currentEpisode == null) return null
    val ordered = episodes.sortedBy { it.sortOrder }
    val currentIndex = ordered.indexOfFirst { it.episodeId == currentEpisode.episodeId }
    if (currentIndex < 0) return null
    return ordered.getOrNull(currentIndex + 1)
}

internal fun adjacentEpisodeIds(
    episodes: List<EpisodeDto>,
    currentIndex: Int
): List<String> = listOf(currentIndex - 1, currentIndex, currentIndex + 1)
    .mapNotNull(episodes::getOrNull)
    .map(EpisodeDto::episodeId)

internal fun cachedPlayInfo(
    episodeId: String,
    playInfos: Map<String, PlayInfoResponse>
): PlayInfoResponse? = playInfos[episodeId]

@Composable
private fun TopChrome(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopChromeIcon(icon = Icons.Default.Menu, contentDescription = "菜单")
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(Color(0x3A000000))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(100.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("短剧", color = Color.White.copy(alpha = 0.64f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(Brush.horizontalGradient(listOf(Orange, Amber)))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("推荐", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.weight(1f))
        TopChromeIcon(icon = Icons.Default.Search, contentDescription = "搜索")
    }
}

@Composable
private fun TopChromeIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.28f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun RightActionRail(
    liked: Boolean,
    favorited: Boolean,
    profile: UserProfileResponse?,
    onLike: () -> Unit,
    onFavorite: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActionButton(
            icon = Icons.Default.Star,
            text = if (favorited) "已收藏" else countText(profile?.favoriteCount ?: 0, "收藏"),
            active = favorited,
            onClick = onFavorite
        )
        ActionButton(
            icon = Icons.Default.ChatBubble,
            text = countText(profile?.commentCount ?: 0, "评论"),
            active = false,
            onClick = onComment
        )
        ActionButton(
            icon = Icons.Default.Favorite,
            text = if (liked) "已点赞" else countText(profile?.likedCount ?: 0, "点赞"),
            active = liked,
            onClick = onLike
        )
        ActionButton(
            icon = Icons.Default.Share,
            text = countText(profile?.shareCount ?: 0, "转发"),
            active = false,
            onClick = onShare
        )
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val motion = rememberInfiniteTransition(label = "railActionMotion")
    val glow by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_450, easing = LinearEasing)),
        label = "railActionGlow"
    )
    val targetScale by animateFloatAsState(
        targetValue = if (active) 1.06f else 1f,
        animationSpec = tween(160),
        label = "railActionScale"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .graphicsLayer {
                    scaleX = targetScale + if (active) glow * 0.025f else 0f
                    scaleY = scaleX
                }
                .shadow(
                    elevation = if (active) 12.dp else 4.dp,
                    shape = CircleShape,
                    ambientColor = if (active) Orange.copy(alpha = 0.32f + glow * 0.18f) else Color.Transparent,
                    spotColor = if (active) Amber.copy(alpha = 0.36f + glow * 0.18f) else Color.Transparent
                )
                .clip(CircleShape)
                .background(
                    if (active) {
                        Brush.radialGradient(listOf(Amber, Orange, Color(0xFF7E2E00)))
                    } else {
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.06f)))
                    }
                )
                .border(1.dp, Color.White.copy(alpha = if (active) 0.42f + glow * 0.18f else 0.18f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = text, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun VideoCaption(state: UiState, modifier: Modifier = Modifier) {
    val episode = state.currentEpisode
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = "${episode?.title ?: "第1集"}  天下第一纨绔",
            color = Color.White,
            fontSize = 23.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("9.5分", Icons.Default.Star)
            Pill("演员 · 苏羽和秦武", null)
        }
        Text(
            text = "热评：苏羽好帅  展开",
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Pill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xB51C1D22))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun darkOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    disabledTextColor = Color.White.copy(alpha = 0.55f),
    cursorColor = Amber,
    focusedBorderColor = Orange,
    unfocusedBorderColor = Color.White.copy(alpha = 0.34f),
    focusedLabelColor = Amber,
    unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
    focusedContainerColor = Color(0xFF17181C),
    unfocusedContainerColor = Color(0xFF17181C)
)

@Composable
private fun CommentPanel(
    profile: UserProfileResponse?,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xF022232A), Color(0xF014151A))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .navigationBarsPadding()
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("评论", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
            }
        }
        profile?.recentComments?.take(3)?.forEach {
            Text("· ${it.text}", color = Color.White.copy(alpha = 0.82f), fontSize = 15.sp, maxLines = 1)
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = darkOutlinedTextFieldColors(),
                label = { Text("说点什么", color = Color.White.copy(alpha = 0.65f)) }
            )
            Spacer(Modifier.width(10.dp))
            IconButton(
                onClick = { if (text.isNotBlank()) onSend(text.trim()) },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Amber, Orange, Color(0xFF7E2E00))))
                    .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", tint = Color.White)
            }
        }
    }
}

@Composable
private fun MineHome(
    state: UiState,
    onServerUrlChange: (String) -> Unit,
    onReload: () -> Unit
) {
    val context = LocalContext.current
    val interactionRecords = remember(context.applicationContext) {
        SharedPreferencesInteractionStorage.from(context.applicationContext)
    }
    val highlights = interactionRecords.highlights()
    val profile = state.profile
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF07080B),
                        Color(0xFF141017),
                        Color(0xFF08080A)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    18.dp,
                    RoundedCornerShape(20.dp),
                    ambientColor = Orange.copy(alpha = 0.14f),
                    spotColor = Amber.copy(alpha = 0.18f)
                )
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xE61B1C22),
                            Color(0xE62A2018),
                            Color(0xE6121318)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                .border(1.dp, Amber.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(62.dp)
                    .shadow(14.dp, CircleShape, ambientColor = Orange.copy(alpha = 0.24f), spotColor = Amber.copy(alpha = 0.28f))
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Amber, Orange, Color(0xFF7E2E00))))
                    .border(1.dp, Color.White.copy(alpha = 0.42f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(profile?.nickname ?: "游客账号", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text(profile?.userId ?: state.guest?.userId.orEmpty(), color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp)
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(Brush.horizontalGradient(listOf(Orange, Amber)))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(100.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("${profile?.coinBalance ?: 0}金币", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("点赞", profile?.likedCount ?: 0, Modifier.weight(1f))
            StatTile("收藏", profile?.favoriteCount ?: 0, Modifier.weight(1f))
            StatTile("评论", profile?.commentCount ?: 0, Modifier.weight(1f))
            StatTile("转发", profile?.shareCount ?: 0, Modifier.weight(1f))
        }

        SectionPanel(title = "服务调试") {
            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = onServerUrlChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = darkOutlinedTextFieldColors(),
                label = { Text("服务地址") }
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onReload,
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("重新连接账号和短剧")
            }
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFFFFB4AB), fontSize = 13.sp)
            }
        }

        SectionPanel(title = "我的高光") {
            if (highlights.isEmpty()) {
                Text("还没有收藏名场面", color = Color.White.copy(alpha = 0.7f))
            } else {
                highlights.sortedByDescending { it.createdAtMs }.forEach { record ->
                    HighlightRow(record)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        SectionPanel(title = "最近评论") {
            if (profile?.recentComments.isNullOrEmpty()) {
                Text("还没有评论。去视频页点评论，记录会同步到账号。", color = Color.White.copy(alpha = 0.7f))
            } else {
                profile!!.recentComments.forEach {
                    Text("《${it.episodeId}》${it.text}", color = Color.White, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(70.dp))
    }
}

@Composable
private fun HighlightRow(record: HighlightRecord) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xE61B1C22), Color(0xE62A2117), Color(0xE614151A))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .border(1.dp, Amber.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(record.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
        Text(
            "第${record.episodeNumber}集 ${record.clipRangeText()}",
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 12.sp
        )
        Text(
            "保存于 ${formatCreatedAt(record.createdAtMs)}",
            color = Color.White.copy(alpha = 0.48f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun StatTile(title: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF24262D), Color(0xFF17181D))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .border(1.dp, Orange.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value.toString(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(title, color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp)
    }
}

@Composable
private fun SectionPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xF0212228), Color(0xF016171C))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun BottomTabs(selected: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
        containerColor = Color(0xF018191F),
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0.dp)
    ) {
        NavigationBarItem(
            selected = selected == MainTab.Video,
            onClick = { onSelect(MainTab.Video) },
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("视频") },
            colors = navColors()
        )
        NavigationBarItem(
            selected = selected == MainTab.Mine,
            onClick = { onSelect(MainTab.Mine) },
            icon = { Icon(Icons.Default.Person, contentDescription = null) },
            label = { Text("我的") },
            colors = navColors()
        )
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Orange,
    selectedTextColor = Color.White,
    indicatorColor = Color(0x22FF7A00),
    unselectedIconColor = Color.White.copy(alpha = 0.52f),
    unselectedTextColor = Color.White.copy(alpha = 0.52f)
)

class MainViewModel : ViewModel() {
    var state by mutableStateOf(UiState())
        private set
    private var contentLoadJob: Job? = null
    private val actionJobs = mutableMapOf<String, Job>()
    private var commentJob: Job? = null
    private val preloadJobs = mutableMapOf<String, Job>()

    fun selectTab(tab: MainTab) {
        state = state.copy(tab = tab)
    }

    fun updateServerUrl(serverUrl: String) {
        state = state.copy(serverUrl = serverUrl)
    }

    fun bootstrap(serverUrl: String, deviceId: String) {
        contentLoadJob?.cancel()
        cancelInteractionJobs()
        preloadJobs.values.forEach(Job::cancel)
        preloadJobs.clear()
        state = state.copy(serverUrl = serverUrl, isLoading = true, error = null)
        contentLoadJob = viewModelScope.launch {
            runCatching {
                val api = ApiFactory.create(serverUrl)
                val guest = api.guest(GuestLoginRequest(deviceId)).data
                val dramas = api.dramas().data.items
                val firstDrama = dramas.firstOrNull()
                val episodes = if (firstDrama != null) api.episodes(firstDrama.dramaId).data.items else emptyList()
                val firstEpisode = episodes.firstOrNull()

                state = state.copy(
                    guest = guest,
                    dramas = dramas,
                    episodes = if (firstDrama != null) mapOf(firstDrama.dramaId to episodes) else emptyMap(),
                    currentEpisode = firstEpisode,
                    player = null,
                    playInfos = emptyMap(),
                    liked = false,
                    favorited = false,
                    isLoading = firstEpisode != null,
                    error = if (firstEpisode == null) "服务端暂无可播放剧集" else null,
                    tab = MainTab.Video
                )

                val playInfo = firstEpisode?.let { episode ->
                    withTimeout(8_000L) {
                        api.play(episode.episodeId).data
                    }
                }
                if (playInfo != null) {
                    require(playInfo.videoUrl.startsWith("http://") || playInfo.videoUrl.startsWith("https://")) {
                        "服务端返回的 videoUrl 不是远程 HTTP URL"
                    }
                }
                val profile = runCatching {
                    withTimeout(5_000L) {
                        api.profile(guest.userId).data
                    }
                }.getOrNull()
                state = state.copy(
                    guest = guest,
                    profile = profile,
                    dramas = dramas,
                    episodes = if (firstDrama != null) mapOf(firstDrama.dramaId to episodes) else emptyMap(),
                    currentEpisode = firstEpisode,
                    player = playInfo,
                    playInfos = playInfo?.let { mapOf(it.episodeId to it) }.orEmpty(),
                    liked = false,
                    favorited = false,
                    isLoading = false,
                    error = null,
                    tab = MainTab.Video
                )
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@onFailure
                state = state.copy(isLoading = false, error = throwable.readableMessage())
            }
        }
    }

    fun openEpisode(episode: EpisodeDto) {
        val guest = state.guest ?: return
        val cached = cachedPlayInfo(episode.episodeId, state.playInfos)
        contentLoadJob?.cancel()
        cancelInteractionJobs()
        state = state.copy(
            currentEpisode = episode,
            player = cached,
            liked = false,
            favorited = false,
            isLoading = cached == null,
            error = null
        )
        if (cached != null) return
        contentLoadJob = viewModelScope.launch {
            runCatching {
                val api = ApiFactory.create(state.serverUrl)
                val playInfo = withTimeout(8_000L) {
                    api.play(episode.episodeId).data
                }
                require(playInfo.videoUrl.startsWith("http://") || playInfo.videoUrl.startsWith("https://")) {
                    "服务端返回的 videoUrl 不是远程 HTTP URL"
                }
                val profile = runCatching {
                    withTimeout(5_000L) {
                        api.profile(guest.userId).data
                    }
                }.getOrNull() ?: state.profile
                state = state.copy(
                    currentEpisode = episode,
                    player = playInfo,
                    playInfos = state.playInfos + (episode.episodeId to playInfo),
                    profile = profile,
                    liked = false,
                    favorited = false,
                    isLoading = false,
                    error = null
                )
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@onFailure
                state = state.copy(isLoading = false, error = throwable.readableMessage())
            }
        }
    }

    fun preloadEpisodes(episodes: List<EpisodeDto>) {
        episodes.forEach { episode ->
            if (
                episode.episodeId in state.playInfos ||
                preloadJobs[episode.episodeId]?.isActive == true
            ) {
                return@forEach
            }
            preloadJobs[episode.episodeId] = viewModelScope.launch {
                val requestedServerUrl = state.serverUrl
                runCatching {
                    val playInfo = withTimeout(8_000L) {
                        ApiFactory.create(requestedServerUrl).play(episode.episodeId).data
                    }
                    require(
                        playInfo.videoUrl.startsWith("http://") ||
                            playInfo.videoUrl.startsWith("https://")
                    )
                    if (state.serverUrl == requestedServerUrl) {
                        state = state.copy(
                            playInfos = state.playInfos + (episode.episodeId to playInfo)
                        )
                    }
                }
                preloadJobs.remove(episode.episodeId)
            }
        }
    }

    fun recordAction(actionType: String) {
        val guest = state.guest ?: return
        val episode = state.currentEpisode ?: return
        val requestUserId = guest.userId
        val requestEpisodeId = episode.episodeId
        actionJobs[actionType]?.cancel()
        actionJobs[actionType] = viewModelScope.launch {
            runCatching {
                val response = ApiFactory.create(state.serverUrl)
                    .userAction(requestUserId, UserActionRequest(requestEpisodeId, actionType))
                    .data
                if (
                    !shouldApplyResponse(
                        currentEpisodeId = state.currentEpisode?.episodeId,
                        currentUserId = state.guest?.userId,
                        requestEpisodeId = requestEpisodeId,
                        requestUserId = requestUserId
                    )
                ) {
                    return@runCatching
                }
                state = state.copy(
                    liked = response.liked,
                    favorited = response.favorited,
                    profile = response.profile
                )
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@onFailure
                state = state.copy(error = throwable.readableMessage())
            }
        }
    }

    fun sendComment(text: String) {
        val guest = state.guest ?: return
        val episode = state.currentEpisode ?: return
        val requestUserId = guest.userId
        val requestEpisodeId = episode.episodeId
        commentJob?.cancel()
        commentJob = viewModelScope.launch {
            runCatching {
                val response = ApiFactory.create(state.serverUrl)
                    .userAction(requestUserId, UserActionRequest(requestEpisodeId, "COMMENT", text))
                    .data
                if (
                    !shouldApplyResponse(
                        currentEpisodeId = state.currentEpisode?.episodeId,
                        currentUserId = state.guest?.userId,
                        requestEpisodeId = requestEpisodeId,
                        requestUserId = requestUserId
                    )
                ) {
                    return@runCatching
                }
                state = state.copy(profile = response.profile)
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@onFailure
                state = state.copy(error = throwable.readableMessage())
            }
        }
    }

    private fun cancelInteractionJobs() {
        actionJobs.values.forEach(Job::cancel)
        actionJobs.clear()
        commentJob?.cancel()
        commentJob = null
    }
}

private object ApiFactory {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun create(serverUrl: String): DramaApi {
        return Retrofit.Builder()
            .baseUrl(serverUrl.normalizedBaseUrl())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DramaApi::class.java)
    }
}

private interface DramaApi {
    @POST("api/auth/guest")
    suspend fun guest(@Body request: GuestLoginRequest): ApiResponse<GuestLoginResponse>

    @GET("api/dramas")
    suspend fun dramas(): ApiResponse<DramaListResponse>

    @GET("api/dramas/{dramaId}/episodes")
    suspend fun episodes(@Path("dramaId") dramaId: String): ApiResponse<EpisodeListResponse>

    @GET("api/episodes/{episodeId}/play")
    suspend fun play(@Path("episodeId") episodeId: String): ApiResponse<PlayInfoResponse>

    @GET("api/users/{userId}/profile")
    suspend fun profile(@Path("userId") userId: String): ApiResponse<UserProfileResponse>

    @POST("api/users/{userId}/actions")
    suspend fun userAction(
        @Path("userId") userId: String,
        @Body request: UserActionRequest
    ): ApiResponse<UserActionResponse>

    @POST("api/ai/person-insight")
    suspend fun personInsight(@Body request: AiPersonInsightRequest): ApiResponse<AiPersonInsightResponse>

    @POST("api/ai/person-identify")
    suspend fun personIdentify(@Body request: AiPersonIdentifyRequest): ApiResponse<AiPersonIdentifyResponse>
}

enum class MainTab { Video, Mine }

data class UiState(
    val serverUrl: String = DEFAULT_SERVER_URL,
    val guest: GuestLoginResponse? = null,
    val profile: UserProfileResponse? = null,
    val dramas: List<DramaDto> = emptyList(),
    val episodes: Map<String, List<EpisodeDto>> = emptyMap(),
    val currentEpisode: EpisodeDto? = null,
    val player: PlayInfoResponse? = null,
    val playInfos: Map<String, PlayInfoResponse> = emptyMap(),
    val liked: Boolean = false,
    val favorited: Boolean = false,
    val tab: MainTab = MainTab.Video,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class ApiResponse<T>(val code: Int, val message: String, val data: T)

data class GuestLoginRequest(val deviceId: String)

data class GuestLoginResponse(val userId: String, val token: String, val deviceId: String)

data class DramaListResponse(val items: List<DramaDto>)

data class DramaDto(
    val dramaId: String,
    val title: String,
    val coverUrl: String,
    val description: String,
    val episodeCount: Int
)

data class EpisodeListResponse(val items: List<EpisodeDto>)

data class EpisodeDto(
    val episodeId: String,
    val dramaId: String,
    val title: String,
    val sortOrder: Int,
    val durationMs: Long
)

data class PlayInfoResponse(
    val episodeId: String,
    val videoUrl: String,
    val durationMs: Long,
    val format: String
)

data class AiPersonInsightRequest(
    val episodeNumber: Int,
    val positionSec: Double,
    val characterName: String,
    val identity: String,
    val sceneHint: String? = null,
    val frameImageBase64: String? = null,
    val frameMimeType: String? = null
)

data class AiPersonInsightResponse(
    val title: String,
    val insight: String,
    val hook: String,
    val source: String
)

data class AiPersonIdentifyRequest(
    val episodeNumber: Int,
    val positionSec: Double,
    val frameImageBase64: String? = null,
    val frameMimeType: String? = null
)

data class AiPersonCharacter(
    val id: String,
    val name: String,
    val identity: String,
    val aliases: List<String>,
    val tags: List<String>,
    val storyRole: String,
    val description: String
)

data class AiIdentifiedCharacter(
    val character: AiPersonCharacter,
    val confidence: Double,
    val screenPosition: String,
    val evidence: String
)

data class AiPersonIdentifyResponse(
    val characters: List<AiIdentifiedCharacter>,
    val candidateCharacters: List<AiPersonCharacter> = emptyList(),
    val sceneRole: String,
    val frameCount: Int = 1,
    val usedFallback: Boolean,
    val source: String
)

data class UserActionRequest(
    val episodeId: String,
    val actionType: String,
    val commentText: String? = null
)

data class UserActionResponse(
    val userId: String,
    val episodeId: String,
    val liked: Boolean,
    val favorited: Boolean,
    val profile: UserProfileResponse
)

data class UserProfileResponse(
    val userId: String,
    val nickname: String,
    val likedCount: Int,
    val favoriteCount: Int,
    val commentCount: Int,
    val shareCount: Int,
    val coinBalance: Int,
    val recentComments: List<CommentDto>
)

data class CommentDto(
    val episodeId: String,
    val text: String,
    val createdAt: Long
)

internal fun String.normalizedBaseUrl(): String {
    val trimmed = trim()
    require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        "服务地址必须以 http:// 或 https:// 开头"
    }
    return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
}

internal fun shouldApplyResponse(
    currentEpisodeId: String?,
    currentUserId: String?,
    requestEpisodeId: String,
    requestUserId: String
): Boolean =
    currentEpisodeId == requestEpisodeId &&
        currentUserId == requestUserId

internal fun CharacterProfile.toPersonInsightRequest(
    episodeNumber: Int,
    positionSec: Double,
    sceneHint: String? = null,
    frameImageBase64: String? = null,
    frameMimeType: String? = null
): AiPersonInsightRequest =
    AiPersonInsightRequest(
        episodeNumber = episodeNumber,
        positionSec = positionSec,
        characterName = name,
        identity = identity,
        sceneHint = sceneHint,
        frameImageBase64 = frameImageBase64,
        frameMimeType = frameMimeType
    )

internal fun AiPersonInsightResponse.toPersonAiInsight(): PersonAiInsight =
    PersonAiInsight(
        title = title,
        insight = insight,
        hook = hook,
        source = source
    )

internal fun toPersonIdentifyRequest(
    episodeNumber: Int,
    positionSec: Double,
    frameImageBase64: String? = null,
    frameMimeType: String? = null
): AiPersonIdentifyRequest = AiPersonIdentifyRequest(
    episodeNumber = episodeNumber,
    positionSec = positionSec,
    frameImageBase64 = frameImageBase64,
    frameMimeType = frameMimeType
)

internal fun AiPersonIdentifyResponse.toPersonIdentification(): PersonIdentificationResult =
    PersonIdentificationResult(
        characters = characters.map { identified ->
            IdentifiedPerson(
                profile = identified.character.toCharacterProfile(),
                confidence = identified.confidence.coerceIn(0.0, 1.0),
                screenPosition = identified.screenPosition,
                evidence = identified.evidence
            )
        },
        candidateProfiles = candidateCharacters.map(AiPersonCharacter::toCharacterProfile),
        sceneRole = sceneRole,
        usedFallback = usedFallback,
        source = source
    )

private fun AiPersonCharacter.toCharacterProfile(): CharacterProfile =
    CharacterProfile(
        id = id,
        name = name,
        identity = identity,
        tags = tags,
        description = description
    )

private fun Throwable.readableMessage(): String = message ?: javaClass.simpleName

private fun countText(value: Int, fallback: String): String = if (value <= 0) fallback else when {
    value >= 10_000 -> "%.1f万".format(value / 10_000.0)
    else -> value.toString()
}

private fun HighlightRecord.clipRangeText(): String {
    val endText = when (val end = clipEnd) {
        is ClipEnd.Fixed -> formatDurationSec(end.seconds)
        ClipEnd.EpisodeEnd -> "本集结尾"
    }
    return "${formatDurationSec(clipStartSec)}-$endText"
}

private fun formatDurationSec(value: Double): String {
    val total = value.toInt().coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatCreatedAt(createdAtMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(createdAtMs))
