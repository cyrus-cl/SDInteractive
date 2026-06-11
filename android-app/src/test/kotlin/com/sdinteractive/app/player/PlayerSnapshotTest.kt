package com.sdinteractive.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSnapshotTest {
    @Test
    fun `snapshot normalization clamps unavailable player times`() {
        val snapshot = PlayerSnapshot.normalized(
            positionMs = -1L,
            durationMs = Long.MIN_VALUE,
            isPlaying = true,
            isSeeking = false,
            isEnded = false
        )

        assertEquals(0L, snapshot.positionMs)
        assertEquals(0L, snapshot.durationMs)
        assertEquals(0.0, snapshot.positionSec, 0.0)
        assertEquals(0.0, snapshot.durationSec, 0.0)
    }

    @Test
    fun `seek starts immediately`() {
        val tracker = SeekingStateTracker(settleWindowMs = 250L)

        tracker.onSeekStarted(nowMs = 1_000L)

        assertTrue(tracker.isSeeking(nowMs = 1_000L))
    }

    @Test
    fun `seek remains active before settle window ends`() {
        val tracker = SeekingStateTracker(settleWindowMs = 250L)
        tracker.onSeekStarted(nowMs = 1_000L)

        assertTrue(tracker.isSeeking(nowMs = 1_249L))
    }

    @Test
    fun `seek settles when window ends`() {
        val tracker = SeekingStateTracker(settleWindowMs = 250L)
        tracker.onSeekStarted(nowMs = 1_000L)

        assertFalse(tracker.isSeeking(nowMs = 1_250L))
    }

    @Test
    fun `consecutive seeks extend settle window`() {
        val tracker = SeekingStateTracker(settleWindowMs = 250L)
        tracker.onSeekStarted(nowMs = 1_000L)
        tracker.onSeekStarted(nowMs = 1_200L)

        assertTrue(tracker.isSeeking(nowMs = 1_449L))
        assertFalse(tracker.isSeeking(nowMs = 1_450L))
    }

    @Test
    fun `scrub remains seeking until scrub stops and settles`() {
        val tracker = SeekingStateTracker(settleWindowMs = 250L)

        tracker.beginScrub()

        assertTrue(tracker.isSeeking(nowMs = 10_000L))
        tracker.endScrub(nowMs = 10_000L)
        assertTrue(tracker.isSeeking(nowMs = 10_249L))
        assertFalse(tracker.isSeeking(nowMs = 10_250L))
    }

    @Test
    fun `new scrub cancels previous settle and starts a new settle window`() {
        val tracker = SeekingStateTracker(settleWindowMs = 250L)
        tracker.beginScrub()
        tracker.endScrub(nowMs = 1_000L)

        tracker.beginScrub()
        assertTrue(tracker.isSeeking(nowMs = 2_000L))
        tracker.endScrub(nowMs = 2_000L)

        assertTrue(tracker.isSeeking(nowMs = 2_249L))
        assertFalse(tracker.isSeeking(nowMs = 2_250L))
    }

    @Test
    fun `lifecycle resumes only when playback was active before stop`() {
        val policy = PlaybackLifecyclePolicy()

        assertTrue(policy.onStop(wasPlaying = true))
        assertTrue(policy.onStart())
        assertFalse(policy.onStart())
    }

    @Test
    fun `lifecycle preserves user paused state across stop and start`() {
        val policy = PlaybackLifecyclePolicy()

        assertTrue(policy.onStop(wasPlaying = false))
        assertFalse(policy.onStart())
    }

    @Test
    fun `progress normalization stores start position as zero`() {
        assertEquals(0L, normalizePlaybackPositionMs(-500L))
        assertEquals(0L, normalizePlaybackPositionMs(0L))
        assertEquals(500L, normalizePlaybackPositionMs(500L))
    }

    @Test
    fun `single tap toggles custom controls`() {
        val controls = PlaybackControlsState()

        controls.onSingleTap()
        assertTrue(controls.visible)

        controls.onSingleTap()
        assertFalse(controls.visible)
    }

    @Test
    fun `double tap toggles playback without showing center controls`() {
        val controls = PlaybackControlsState(visible = false)

        assertEquals(PlaybackCommand.TogglePlayback, controls.onDoubleTap())

        assertFalse(controls.visible)
        assertFalse(controls.pendingAutoHide)
    }

    @Test
    fun `center button pauses and remains visible when playback is active`() {
        val controls = PlaybackControlsState(visible = true)

        val command = controls.onCenterButtonTap(isPlaying = true)

        assertEquals(PlaybackCommand.Pause, command)
        assertTrue(controls.visible)
        assertFalse(controls.pendingAutoHide)
    }

    @Test
    fun `center button plays and schedules fade out when playback is paused`() {
        val controls = PlaybackControlsState(visible = true)

        val command = controls.onCenterButtonTap(isPlaying = false)

        assertEquals(PlaybackCommand.Play, command)
        assertTrue(controls.pendingAutoHide)
    }

    @Test
    fun `scrubbing hides chrome but keeps progress controls active`() {
        val controls = PlaybackControlsState(visible = true)

        controls.onScrubStart()

        assertTrue(controls.scrubbing)
        assertFalse(controls.showPageChrome)
        assertTrue(controls.visible)
    }

    @Test
    fun `scrub stop preserves controls until user dismisses them`() {
        val controls = PlaybackControlsState(visible = false)

        controls.onScrubStart()
        controls.onScrubStop()

        assertFalse(controls.scrubbing)
        assertTrue(controls.visible)
    }

    @Test
    fun `playback time uses minute second format`() {
        assertEquals("00:00", formatPlaybackTime(0L))
        assertEquals("01:05", formatPlaybackTime(65_000L))
        assertEquals("10:03", formatPlaybackTime(603_999L))
    }

    @Test
    fun `progress scrub previews position and seeks once on stop`() {
        val scrub = ProgressScrubState(durationMs = 100_000L, widthPx = 200f)

        scrub.start(xPx = 20f)
        scrub.dragBy(deltaPx = 80f)

        assertEquals(50_000L, scrub.previewPositionMs)
        assertEquals(50_000L, scrub.stop())
        assertFalse(scrub.dragging)
    }

    @Test
    fun `progress tap clamps seek position`() {
        val scrub = ProgressScrubState(durationMs = 100_000L, widthPx = 200f)

        assertEquals(0L, scrub.positionForTap(-10f))
        assertEquals(100_000L, scrub.positionForTap(240f))
    }
}
