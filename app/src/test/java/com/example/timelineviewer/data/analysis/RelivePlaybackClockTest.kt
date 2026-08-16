package com.example.timelineviewer.data.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelivePlaybackClockTest {

    @Test
    fun `uses a bounded delay for short and long recorded gaps`() {
        val shortGap = RelivePlaybackClock.delayForNextPoint(1_000L, 1_001L, 1f)
        val longGap = RelivePlaybackClock.delayForNextPoint(1_000L, 10_000_000L, 1f)

        assertEquals(110L, shortGap)
        assertEquals(1_100L, longGap)
    }

    @Test
    fun `slower and faster pace changes the replay delay`() {
        val slow = RelivePlaybackClock.delayForNextPoint(1_000L, 351_000L, 0.5f)
        val normal = RelivePlaybackClock.delayForNextPoint(1_000L, 351_000L, 1f)
        val fast = RelivePlaybackClock.delayForNextPoint(1_000L, 351_000L, 2f)

        assertTrue(slow > normal)
        assertTrue(normal > fast)
    }
}
