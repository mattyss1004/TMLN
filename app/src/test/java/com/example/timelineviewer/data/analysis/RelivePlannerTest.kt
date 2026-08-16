package com.example.timelineviewer.data.analysis

import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.data.model.JourneyDetailData
import com.example.timelineviewer.data.model.RoutePoint
import com.example.timelineviewer.data.model.Stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelivePlannerTest {

    @Test
    fun `builds ordered departure highlight and arrival moments from local journey data`() {
        val detail = JourneyDetailData(
            journey = journey(),
            points = listOf(
                point(0, 1_000L),
                point(1, 2_000L),
                point(2, 3_000L),
                point(3, 4_000L),
                point(4, 5_000L)
            ),
            stops = listOf(
                stop("Museum", 3_050L, 2, 90),
                stop("Coffee", 1_950L, 1, 50)
            ),
            segments = emptyList()
        )

        val moments = RelivePlanner.moments(detail)

        assertEquals(listOf("Setting out", "Pause at Coffee", "Pause at Museum", "Arrival"), moments.map { it.title })
        assertEquals(listOf(0, 1, 2, 4), moments.map { it.pointIndex })
        assertEquals(ReliveMomentKind.HIGHLIGHT, moments[1].kind)
    }

    @Test
    fun `finds active and next moments as playback advances`() {
        val moments = listOf(
            ReliveMoment(0, 1_000L, "Setting out", "", ReliveMomentKind.DEPARTURE),
            ReliveMoment(4, 5_000L, "Arrival", "", ReliveMomentKind.ARRIVAL)
        )

        assertEquals("Setting out", RelivePlanner.activeMoment(moments, 2)?.title)
        assertEquals("Arrival", RelivePlanner.nextMoment(moments, 2)?.title)
        assertEquals("Arrival", RelivePlanner.activeMoment(moments, 4)?.title)
        assertNull(RelivePlanner.nextMoment(moments, 4))
    }

    private fun journey() = Journey(
        id = 1L,
        title = "Test journey",
        startTime = 1_000L,
        endTime = 5_000L,
        totalDistanceKm = 1.0,
        totalDurationSeconds = 4L,
        pointCount = 5,
        stopCount = 2
    )

    private fun point(order: Int, timestamp: Long) = RoutePoint(
        journeyId = 1L,
        latitude = 50.0 + order,
        longitude = 14.0 + order,
        timestamp = timestamp,
        sequenceOrder = order
    )

    private fun stop(name: String, timestamp: Long, order: Int, score: Int) = Stop(
        journeyId = 1L,
        latitude = 50.0,
        longitude = 14.0,
        name = name,
        startTime = timestamp,
        endTime = timestamp + 600L,
        durationSeconds = 600L,
        sequenceOrder = order,
        importanceScore = score
    )
}
