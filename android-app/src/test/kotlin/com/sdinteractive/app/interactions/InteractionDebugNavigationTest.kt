package com.sdinteractive.app.interactions

import com.sdinteractive.app.interactions.debug.interactionReplayPositionMs
import com.sdinteractive.app.interactions.debug.interactionTriggerPositionMs
import com.sdinteractive.app.interactions.model.InteractionTrigger
import org.junit.Assert.assertEquals
import org.junit.Test

class InteractionDebugNavigationTest {
    @Test
    fun `fixed trigger replays three seconds before event`() {
        val trigger = InteractionTrigger.Fixed(86.0)

        assertEquals(86_000L, interactionTriggerPositionMs(trigger, episodeDurationMs = 300_000L))
        assertEquals(83_000L, interactionReplayPositionMs(trigger, episodeDurationMs = 300_000L))
    }

    @Test
    fun `fixed trigger is not clamped by stale episode metadata`() {
        val trigger = InteractionTrigger.Fixed(194.0)

        assertEquals(194_000L, interactionTriggerPositionMs(trigger, episodeDurationMs = 90_000L))
        assertEquals(191_000L, interactionReplayPositionMs(trigger, episodeDurationMs = 90_000L))
    }

    @Test
    fun `range trigger replays before range start`() {
        val trigger = InteractionTrigger.Range(startSec = 8.0, endSec = 16.0)

        assertEquals(5_000L, interactionReplayPositionMs(trigger, episodeDurationMs = 300_000L))
    }

    @Test
    fun `near start trigger clamps replay to zero`() {
        val trigger = InteractionTrigger.Fixed(2.0)

        assertEquals(0L, interactionReplayPositionMs(trigger, episodeDurationMs = 300_000L))
    }

    @Test
    fun `episode ending trigger resolves from duration before applying lead in`() {
        val trigger = InteractionTrigger.EpisodeEnding(remainingSec = 4.0)

        assertEquals(296_000L, interactionTriggerPositionMs(trigger, episodeDurationMs = 300_000L))
        assertEquals(293_000L, interactionReplayPositionMs(trigger, episodeDurationMs = 300_000L))
    }

    @Test
    fun `ending trigger with unavailable duration safely replays from start`() {
        val trigger = InteractionTrigger.EpisodeEnding(remainingSec = 4.0)

        assertEquals(0L, interactionReplayPositionMs(trigger, episodeDurationMs = 0L))
    }
}
