package com.sdinteractive.app.player

data class PlayerSnapshot(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    val isSeeking: Boolean = false,
    val isEnded: Boolean = false
) {
    val positionSec: Double
        get() = positionMs.coerceAtLeast(0) / 1_000.0

    val durationSec: Double
        get() = durationMs.coerceAtLeast(0) / 1_000.0

    companion object {
        fun normalized(
            positionMs: Long,
            durationMs: Long,
            isPlaying: Boolean,
            isSeeking: Boolean,
            isEnded: Boolean
        ): PlayerSnapshot = PlayerSnapshot(
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            isPlaying = isPlaying,
            isSeeking = isSeeking,
            isEnded = isEnded
        )
    }
}

class SeekingStateTracker(
    private val settleWindowMs: Long = 250L
) {
    init {
        require(settleWindowMs >= 0L) { "settleWindowMs must not be negative" }
    }

    private var seekingUntilMs = Long.MIN_VALUE
    private var isScrubbing = false

    fun onSeekStarted(nowMs: Long) {
        seekingUntilMs = saturatedAdd(nowMs, settleWindowMs)
    }

    fun beginScrub() {
        isScrubbing = true
    }

    fun endScrub(nowMs: Long) {
        isScrubbing = false
        onSeekStarted(nowMs)
    }

    fun isSeeking(nowMs: Long): Boolean = isScrubbing || nowMs < seekingUntilMs

    private fun saturatedAdd(value: Long, increment: Long): Long =
        if (value > Long.MAX_VALUE - increment) Long.MAX_VALUE else value + increment
}

class PlaybackLifecyclePolicy {
    private var resumeOnStart = false

    fun onStop(wasPlaying: Boolean): Boolean {
        resumeOnStart = wasPlaying
        return true
    }

    fun onStart(): Boolean {
        val shouldResume = resumeOnStart
        resumeOnStart = false
        return shouldResume
    }
}

fun normalizePlaybackPositionMs(positionMs: Long): Long =
    positionMs.coerceAtLeast(0L)

fun resolvePlayerDurationMs(
    mediaDurationMs: Long,
    expectedDurationMs: Long
): Long = when {
    mediaDurationMs > 0L -> mediaDurationMs
    expectedDurationMs > 0L -> expectedDurationMs
    else -> 0L
}

fun clampSeekPositionMs(
    positionMs: Long,
    durationMs: Long
): Long {
    val nonNegativePosition = positionMs.coerceAtLeast(0L)
    return if (durationMs > 0L) {
        nonNegativePosition.coerceAtMost(durationMs)
    } else {
        nonNegativePosition
    }
}

enum class PlaybackCommand {
    None,
    Play,
    Pause,
    TogglePlayback
}

class PlaybackControlsState(
    visible: Boolean = false
) {
    var visible: Boolean = visible
        private set
    var scrubbing: Boolean = false
        private set
    var pendingAutoHide: Boolean = false
        private set

    val showPageChrome: Boolean
        get() = !scrubbing

    fun onSingleTap() {
        visible = !visible
        pendingAutoHide = false
    }

    fun onDoubleTap(): PlaybackCommand {
        pendingAutoHide = false
        return PlaybackCommand.TogglePlayback
    }

    fun onCenterButtonTap(isPlaying: Boolean): PlaybackCommand {
        visible = true
        return if (isPlaying) {
            pendingAutoHide = false
            PlaybackCommand.Pause
        } else {
            pendingAutoHide = true
            PlaybackCommand.Play
        }
    }

    fun consumeAutoHide() {
        pendingAutoHide = false
        visible = false
    }

    fun onScrubStart() {
        scrubbing = true
        visible = true
        pendingAutoHide = false
    }

    fun onScrubStop() {
        scrubbing = false
        visible = true
        pendingAutoHide = false
    }
}

class ProgressScrubState(
    durationMs: Long,
    widthPx: Float
) {
    var durationMs: Long = durationMs.coerceAtLeast(1L)
        private set
    var widthPx: Float = widthPx.coerceAtLeast(1f)
        private set
    var dragging: Boolean = false
        private set
    var previewPositionMs: Long = 0L
        private set
    private var currentXPx: Float = 0f

    fun updateBounds(durationMs: Long, widthPx: Float) {
        this.durationMs = durationMs.coerceAtLeast(1L)
        this.widthPx = widthPx.coerceAtLeast(1f)
        currentXPx = currentXPx.coerceIn(0f, this.widthPx)
        previewPositionMs = positionForTap(currentXPx)
    }

    fun start(xPx: Float) {
        dragging = true
        currentXPx = xPx.coerceIn(0f, widthPx)
        previewPositionMs = positionForTap(currentXPx)
    }

    fun dragBy(deltaPx: Float) {
        currentXPx = (currentXPx + deltaPx).coerceIn(0f, widthPx)
        previewPositionMs = positionForTap(currentXPx)
    }

    fun stop(): Long {
        dragging = false
        return previewPositionMs
    }

    fun cancel() {
        dragging = false
    }

    fun positionForTap(xPx: Float): Long {
        val fraction = (xPx / widthPx).coerceIn(0f, 1f)
        return (fraction * durationMs).toLong().coerceIn(0L, durationMs)
    }
}

fun formatPlaybackTime(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}
