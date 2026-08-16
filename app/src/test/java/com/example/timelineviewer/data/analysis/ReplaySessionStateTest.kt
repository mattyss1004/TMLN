package com.example.timelineviewer.data.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplaySessionStateTest {

    private val highlight = ReliveMoment(
        pointIndex = 2,
        timestamp = 2_000L,
        title = "Pause at the river",
        subtitle = "Stayed for 15 min",
        kind = ReliveMomentKind.HIGHLIGHT
    )

    @Test
    fun `begin starts at departure and clamps invalid playback speed`() {
        val session = ReplaySessionReducer.begin(pointCount = 8, playbackSpeed = 12f)

        assertEquals(ReplayStatus.PLAYING, session.status)
        assertEquals(0, session.currentPointIndex)
        assertEquals(4f, session.playbackSpeed)
    }

    @Test
    fun `a new highlight pauses replay at the actual point`() {
        val initial = ReplaySessionReducer.begin(pointCount = 6, playbackSpeed = 1f)
        val beforeStop = ReplaySessionReducer.advance(initial, 6, highlightAtNextPoint = null)
        val atStop = ReplaySessionReducer.advance(beforeStop, 6, highlightAtNextPoint = highlight)

        assertEquals(ReplayStatus.PAUSED_AT_STOP, atStop.status)
        assertEquals(2, atStop.currentPointIndex)
        assertEquals(highlight, atStop.activeMoment)
        assertTrue(2 in atStop.visitedStopPointIndexes)
    }

    @Test
    fun `resuming a stop clears its card and never gates at the same point again`() {
        val paused = ReplaySessionState(
            status = ReplayStatus.PAUSED_AT_STOP,
            currentPointIndex = 2,
            activeMoment = highlight,
            visitedStopPointIndexes = setOf(2)
        )
        val resumed = ReplaySessionReducer.resume(paused)
        val advanced = ReplaySessionReducer.advance(resumed, 6, highlightAtNextPoint = null)

        assertEquals(ReplayStatus.PLAYING, resumed.status)
        assertNull(resumed.activeMoment)
        assertEquals(3, advanced.currentPointIndex)
    }

    @Test
    fun `arrival finishes rather than looping`() {
        val nearArrival = ReplaySessionState(status = ReplayStatus.PLAYING, currentPointIndex = 4)
        val finished = ReplaySessionReducer.advance(nearArrival, pointCount = 6, highlightAtNextPoint = null)

        assertEquals(ReplayStatus.FINISHED, finished.status)
        assertEquals(5, finished.currentPointIndex)
    }

    @Test
    fun `seeking after finish returns to a paused inspectable point`() {
        val finished = ReplaySessionState(status = ReplayStatus.FINISHED, currentPointIndex = 7)
        val sought = ReplaySessionReducer.seek(finished, pointCount = 8, requestedIndex = 3)

        assertEquals(ReplayStatus.PAUSED_MANUALLY, sought.status)
        assertEquals(3, sought.currentPointIndex)
    }
}
