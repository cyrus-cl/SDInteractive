@file:androidx.annotation.OptIn(
    markerClass = [androidx.media3.common.util.UnstableApi::class]
)

package com.sdinteractive.app.player

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume

internal const val PERSON_FRAME_MAX_WIDTH = 768
internal const val PERSON_FRAME_JPEG_QUALITY = 82

interface PlayerController {
    fun seekTo(positionMs: Long)
    fun beginScrub()
    fun endScrub()
    fun togglePlayPause()
    fun retry()
    suspend fun captureFrameJpegBase64(): String?
}

@Composable
fun RemoteVideoPlayer(
    episodeId: String,
    videoUrl: String,
    expectedDurationMs: Long,
    active: Boolean,
    modifier: Modifier = Modifier,
    onSnapshot: (PlayerSnapshot) -> Unit,
    onControllerReady: (PlayerController?) -> Unit,
    onSingleTap: () -> Unit,
    onPlaybackErrorChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnSnapshot = rememberUpdatedState(onSnapshot)
    val currentOnControllerReady = rememberUpdatedState(onControllerReady)
    val currentOnSingleTap = rememberUpdatedState(onSingleTap)
    val currentOnPlaybackErrorChange = rememberUpdatedState(onPlaybackErrorChange)
    val startPositionMs = remember(episodeId) {
        PlaybackStore.getPosition(context, episodeId)
    }
    val cacheDataSourceFactory = remember {
        VideoCache.dataSourceFactory(context.applicationContext)
    }
    val seekingTracker = remember(episodeId, videoUrl) {
        SeekingStateTracker()
    }
    val lifecyclePolicy = remember(episodeId, videoUrl) {
        PlaybackLifecyclePolicy()
    }
    val timeBarHolder = remember(episodeId, videoUrl) {
        TimeBarHolder()
    }
    val playerViewHolder = remember(episodeId, videoUrl) {
        PlayerViewHolder()
    }
    val scrubListener = remember(seekingTracker) {
        object : TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) {
                seekingTracker.beginScrub()
            }

            override fun onScrubMove(timeBar: TimeBar, position: Long) = Unit

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                seekingTracker.endScrub(SystemClock.elapsedRealtime())
            }
        }
    }

    val player = remember(episodeId, videoUrl) {
        ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15_000, 60_000, 1_000, 2_000)
                    .build()
            )
            .build()
            .apply {
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
                val mediaSource = ProgressiveMediaSource.Factory(cacheDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(videoUrl))
                setMediaSource(mediaSource)
                prepare()
                if (startPositionMs > 0L) {
                    seekTo(clampSeekPositionMs(startPositionMs, expectedDurationMs))
                }
                playWhenReady = active
            }
    }

    val controller = remember(player, seekingTracker, playerViewHolder) {
        object : PlayerController {
            override fun seekTo(positionMs: Long) {
                seekingTracker.onSeekStarted(SystemClock.elapsedRealtime())
                player.seekTo(
                    clampSeekPositionMs(
                        positionMs = positionMs,
                        durationMs = resolvePlayerDurationMs(
                            mediaDurationMs = player.duration,
                            expectedDurationMs = expectedDurationMs
                        )
                    )
                )
            }

            override fun beginScrub() {
                seekingTracker.beginScrub()
            }

            override fun endScrub() {
                seekingTracker.endScrub(SystemClock.elapsedRealtime())
            }

            override fun togglePlayPause() {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
            }

            override fun retry() {
                player.prepare()
                player.play()
            }

            override suspend fun captureFrameJpegBase64(): String? {
                val view = playerViewHolder.playerView ?: return null
                return view.captureJpegBase64(
                    maxWidth = PERSON_FRAME_MAX_WIDTH,
                    quality = PERSON_FRAME_JPEG_QUALITY
                )
            }
        }
    }

    LaunchedEffect(active, player) {
        if (active) {
            if (player.playbackState != Player.STATE_ENDED) player.play()
        } else {
            player.pause()
        }
    }

    LaunchedEffect(active, controller) {
        currentOnControllerReady.value(if (active) controller else null)
    }

    DisposableEffect(player, episodeId) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    currentOnPlaybackErrorChange.value(false)
                }
                if (playbackState == Player.STATE_ENDED) {
                    PlaybackStore.clear(context, episodeId)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    seekingTracker.onSeekStarted(SystemClock.elapsedRealtime())
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                currentOnPlaybackErrorChange.value(true)
            }
        }

        player.addListener(listener)

        onDispose {
            timeBarHolder.timeBar?.removeListener(scrubListener)
            timeBarHolder.timeBar = null
            playerViewHolder.playerView = null
            if (player.playbackState == Player.STATE_ENDED) {
                PlaybackStore.clear(context, episodeId)
            } else {
                PlaybackStore.save(context, episodeId, player.currentPosition)
            }
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner, player, lifecyclePolicy) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    lifecyclePolicy.onStop(wasPlaying = player.playWhenReady)
                    player.pause()
                }

                Lifecycle.Event.ON_START -> {
                    if (
                        lifecyclePolicy.onStart() &&
                        player.playbackState != Player.STATE_ENDED
                    ) {
                        player.play()
                    }
                }

                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(player, episodeId, active) {
        while (isActive) {
            delay(2_000L)
            if (active && player.playbackState != Player.STATE_ENDED) {
                PlaybackStore.save(context, episodeId, player.currentPosition)
            }
        }
    }

    LaunchedEffect(player, active) {
        while (isActive) {
            val nowMs = SystemClock.elapsedRealtime()
            if (active) {
                currentOnSnapshot.value(
                    PlayerSnapshot.normalized(
                        positionMs = player.currentPosition,
                        durationMs = resolvePlayerDurationMs(
                            mediaDurationMs = player.duration,
                            expectedDurationMs = expectedDurationMs
                        ),
                        isPlaying = player.isPlaying,
                        isSeeking = seekingTracker.isSeeking(nowMs),
                        isEnded = player.playbackState == Player.STATE_ENDED
                    )
                )
            }
            delay(100L)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            PlayerView(it).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
                installTapGestures(controller) { currentOnSingleTap.value() }
                playerViewHolder.playerView = this
                timeBarHolder.attach(this, scrubListener)
            }
        },
        update = {
            it.player = player
            it.installTapGestures(controller) { currentOnSingleTap.value() }
            playerViewHolder.playerView = it
            timeBarHolder.attach(it, scrubListener)
        }
    )
}

private fun PlayerView.installTapGestures(
    controller: PlayerController,
    onSingleTap: () -> Unit
) {
    val detector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                onSingleTap()
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                controller.togglePlayPause()
                return true
            }
        }
    )
    setOnTouchListener { _, event ->
        detector.onTouchEvent(event)
    }
}

private suspend fun PlayerView.captureJpegBase64(
    maxWidth: Int,
    quality: Int
): String? {
    val source = findSurfaceView() ?: return null
    if (source.width <= 0 || source.height <= 0) return null
    val fullSize = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val copied = source.copyPixelsInto(fullSize)
    if (!copied) {
        fullSize.recycle()
        return null
    }

    val target = if (source.width > maxWidth) {
        val targetHeight = (source.height * (maxWidth / source.width.toFloat())).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(fullSize, maxWidth, targetHeight, true)
    } else {
        fullSize
    }

    val bytes = ByteArrayOutputStream()
    target.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(40, 92), bytes)
    if (target !== fullSize) target.recycle()
    fullSize.recycle()
    return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
}

private fun View.findSurfaceView(): SurfaceView? {
    if (this is SurfaceView) return this
    val group = this as? ViewGroup ?: return null
    for (index in 0 until group.childCount) {
        val child = group.getChildAt(index).findSurfaceView()
        if (child != null) return child
    }
    return null
}

private suspend fun SurfaceView.copyPixelsInto(bitmap: Bitmap): Boolean =
    suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(
                this,
                bitmap,
                { result: Int ->
                    if (continuation.isActive) {
                        continuation.resume(result == PixelCopy.SUCCESS)
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (_: IllegalArgumentException) {
            if (continuation.isActive) continuation.resume(false)
        }
    }

private class PlayerViewHolder {
    var playerView: PlayerView? = null
}

private class TimeBarHolder {
    var timeBar: TimeBar? = null

    fun attach(playerView: PlayerView, listener: TimeBar.OnScrubListener) {
        val nextTimeBar = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_progress)
            as? TimeBar
        if (timeBar === nextTimeBar) return

        timeBar?.removeListener(listener)
        timeBar = nextTimeBar
        timeBar?.addListener(listener)
    }
}

private object PlaybackStore {
    private const val NAME = "playback-progress"

    fun getPosition(context: Context, episodeId: String): Long =
        normalizePlaybackPositionMs(
            context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
                .getLong(episodeId, 0L)
        )

    fun save(context: Context, episodeId: String, positionMs: Long) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(episodeId, normalizePlaybackPositionMs(positionMs))
            .apply()
    }

    fun clear(context: Context, episodeId: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(episodeId)
            .apply()
    }
}

private object VideoCache {
    private const val CACHE_BYTES = 512L * 1024L * 1024L

    @Volatile
    private var cache: SimpleCache? = null

    fun dataSourceFactory(context: Context): DataSource.Factory {
        val appContext = context.applicationContext
        val simpleCache = cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(appContext.cacheDir, "media3-video-cache"),
                LeastRecentlyUsedCacheEvictor(CACHE_BYTES),
                StandaloneDatabaseProvider(appContext)
            ).also { cache = it }
        }

        val upstream = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(60_000)

        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
