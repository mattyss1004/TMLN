package com.example.timelineviewer.data.analysis

import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.data.model.JourneyDetailData
import com.example.timelineviewer.data.model.Stop
import com.example.timelineviewer.data.model.TransportMode
import com.example.timelineviewer.data.model.TransportSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class JourneyIntelligenceTest {

    @Test
    fun `builds a concise brief with transport mix and named-stop chapter`() {
        val detail = JourneyDetailData(
            journey = Journey(
                id = 1L,
                title = "City to coast",
                startTime = 1_700_000_000_000L,
                endTime = 1_700_007_200_000L,
                totalDistanceKm = 20.0,
                totalDurationSeconds = 7_200L,
                pointCount = 24,
                stopCount = 1,
                dominantMode = TransportMode.DRIVING
            ),
            points = emptyList(),
            stops = listOf(
                Stop(
                    journeyId = 1L,
                    latitude = 50.0,
                    longitude = 14.0,
                    name = "Old Town Square",
                    startTime = 1_700_003_600_000L,
                    endTime = 1_700_004_500_000L,
                    durationSeconds = 900L,
                    sequenceOrder = 0,
                    importanceScore = 90,
                    category = "Highlight"
                )
            ),
            segments = listOf(
                TransportSegment(
                    journeyId = 1L,
                    startIndex = 0,
                    endIndex = 12,
                    mode = TransportMode.DRIVING,
                    distanceKm = 18.0,
                    durationSeconds = 5_400L,
                    averageSpeedKmh = 12.0
                ),
                TransportSegment(
                    journeyId = 1L,
                    startIndex = 12,
                    endIndex = 23,
                    mode = TransportMode.WALKING,
                    distanceKm = 2.0,
                    durationSeconds = 1_800L,
                    averageSpeedKmh = 4.0
                )
            )
        )

        val brief = JourneyIntelligence.build(detail, ZoneId.of("UTC"), Locale.US)

        assertEquals("A journey to Old Town Square", brief.headline)
        assertEquals("20.0 km", brief.distanceLabel)
        assertEquals("2h", brief.durationLabel)
        assertTrue(brief.narrative.contains("mostly by car"))
        assertEquals(2, brief.transportMix.size)
        assertEquals(TransportMode.DRIVING, brief.transportMix.first().mode)
        assertEquals(90, brief.transportMix.first().sharePercent)
        assertEquals(listOf("Setting out", "Pause at Old Town Square", "Arrival"), brief.chapters.map { it.title })
    }

    @Test
    fun `keeps an unclassified route useful without artificial highlights`() {
        val detail = JourneyDetailData(
            journey = Journey(
                id = 2L,
                title = "Evening route",
                startTime = 1_700_000_000_000L,
                endTime = 1_700_000_030_000L,
                totalDistanceKm = 0.4,
                totalDurationSeconds = 30L,
                pointCount = 2,
                stopCount = 0,
                dominantMode = TransportMode.UNKNOWN
            ),
            points = emptyList(),
            stops = emptyList(),
            segments = emptyList()
        )

        val brief = JourneyIntelligence.build(detail, ZoneId.of("UTC"), Locale.US)

        assertTrue(brief.highlights.isEmpty())
        assertTrue(brief.transportMix.isEmpty())
        assertEquals(listOf("Setting out", "Arrival"), brief.chapters.map { it.title })
        assertTrue(brief.narrative.contains("from start to finish"))
        assertEquals("less than a minute", brief.durationLabel)
    }
}
