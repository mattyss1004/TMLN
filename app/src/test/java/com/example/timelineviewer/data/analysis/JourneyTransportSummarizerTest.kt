package com.example.timelineviewer.data.analysis

import com.example.timelineviewer.data.model.TransportMode
import com.example.timelineviewer.data.model.TransportSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyTransportSummarizerTest {

    @Test
    fun `single meaningful mode keeps its truthful label`() {
        val summary = JourneyTransportSummarizer.fromSegments(
            segments = listOf(segment(0, TransportMode.WALKING)),
            fallbackMode = TransportMode.DRIVING
        )

        assertFalse(summary.isMultimodal)
        assertEquals("Walking", summary.label)
        assertEquals(listOf(TransportMode.WALKING), summary.modes)
    }

    @Test
    fun `multiple meaningful modes become multimodal in route order`() {
        val summary = JourneyTransportSummarizer.fromSegments(
            segments = listOf(
                segment(20, TransportMode.DRIVING),
                segment(0, TransportMode.CYCLING),
                segment(40, TransportMode.WALKING),
                segment(60, TransportMode.CYCLING)
            ),
            fallbackMode = TransportMode.WALKING
        )

        assertTrue(summary.isMultimodal)
        assertEquals("Multimodal", summary.label)
        assertEquals(
            listOf(TransportMode.CYCLING, TransportMode.DRIVING, TransportMode.WALKING),
            summary.modes
        )
    }

    @Test
    fun `unknown segments do not hide real transport modes`() {
        val summary = JourneyTransportSummarizer.fromSegments(
            segments = listOf(
                segment(0, TransportMode.UNKNOWN),
                segment(10, TransportMode.WALKING),
                segment(20, TransportMode.TRANSIT)
            ),
            fallbackMode = TransportMode.UNKNOWN
        )

        assertTrue(summary.isMultimodal)
        assertEquals(listOf(TransportMode.WALKING, TransportMode.TRANSIT), summary.modes)
    }

    @Test
    fun `empty or unknown-only segments use the persisted fallback`() {
        val emptySummary = JourneyTransportSummarizer.fromSegments(emptyList(), TransportMode.DRIVING)
        val unknownSummary = JourneyTransportSummarizer.fromSegments(
            segments = listOf(segment(0, TransportMode.UNKNOWN)),
            fallbackMode = TransportMode.TRANSIT
        )

        assertEquals(listOf(TransportMode.DRIVING), emptySummary.modes)
        assertEquals(listOf(TransportMode.TRANSIT), unknownSummary.modes)
    }

    private fun segment(startIndex: Int, mode: TransportMode) = TransportSegment(
        journeyId = 1,
        startIndex = startIndex,
        endIndex = startIndex + 10,
        mode = mode,
        distanceKm = 1.0,
        durationSeconds = 60,
        averageSpeedKmh = 60.0
    )
}
