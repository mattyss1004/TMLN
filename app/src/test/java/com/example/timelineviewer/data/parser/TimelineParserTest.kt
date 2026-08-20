package com.example.timelineviewer.data.parser

import com.example.timelineviewer.data.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineParserTest {

    @Test
    fun `parses semantic Timeline activities and preserves named visits as highlights`() {
        val json = """
            {
              "timelineObjects": [
                {
                  "activitySegment": {
                    "startLocation": {"latitudeE7": 500750000, "longitudeE7": 144370000},
                    "endLocation": {"latitudeE7": 500850000, "longitudeE7": 144450000},
                    "duration": {"startTimestampMs": "1000000", "endTimestampMs": "1600000"},
                    "activityType": "WALKING"
                  }
                },
                {
                  "placeVisit": {
                    "location": {"latitudeE7": 500850000, "longitudeE7": 144450000, "name": "Historic Square"},
                    "duration": {"startTimestampMs": "1600000", "endTimestampMs": "2200000"}
                  }
                }
              ]
            }
        """.trimIndent()

        val result = TimelineParser.parseTimelineJson(json, "Prague walk")

        assertNotNull(result)
        requireNotNull(result).also { parsed ->
            assertTrue(parsed.journey.title.startsWith("Prague walk —"))
            // The parser intentionally de-duplicates and simplifies coincident activity/visit
            // endpoints, so the semantic guarantee is a navigable route rather than raw count.
            assertTrue(parsed.points.size >= 2)
            assertTrue(parsed.points.zipWithNext().all { (left, right) -> left.timestamp <= right.timestamp })
            assertEquals(1, parsed.stops.size)
            assertEquals("Historic Square", parsed.stops.single().name)
            assertTrue(parsed.stops.single().importanceScore >= 80)
            assertEquals(TransportMode.WALKING, parsed.journey.dominantMode)
        }
    }

    @Test
    fun `parses semanticSegments exports with paths visits and current transit names`() {
        val json = """
            {
              "semanticSegments": [
                {
                  "startTime": "2026-07-25T01:00:00.000+02:00",
                  "endTime": "2026-07-25T01:10:00.000+02:00",
                  "activity": {
                    "start": {"latLng": "50.1000°, 14.4000°"},
                    "end": {"latLng": "50.1100°, 14.4100°"},
                    "topCandidate": {"type": "IN_SUBWAY"}
                  }
                },
                {
                  "startTime": "2026-07-25T01:00:00.000+02:00",
                  "endTime": "2026-07-25T01:10:00.000+02:00",
                  "timelinePath": [
                    {"point": "50.1000°, 14.4000°", "time": "2026-07-25T01:00:00.000+02:00"},
                    {"point": "50.1050°, 14.4050°", "time": "2026-07-25T01:05:00.000+02:00"},
                    {"point": "50.1100°, 14.4100°", "time": "2026-07-25T01:10:00.000+02:00"}
                  ]
                },
                {
                  "startTime": "2026-07-25T01:10:00.000+02:00",
                  "endTime": "2026-07-25T01:40:00.000+02:00",
                  "visit": {
                    "topCandidate": {
                      "semanticType": "HOME",
                      "placeLocation": {"latLng": "50.1100°, 14.4100°"}
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val result = TimelineParser.parseTimelineJson(json, "Semantic export")

        assertNotNull(result)
        requireNotNull(result).also { parsed ->
            assertTrue(parsed.journey.title.startsWith("Semantic export —"))
            assertTrue(parsed.points.size >= 3)
            assertTrue(parsed.points.zipWithNext().all { (left, right) -> left.timestamp <= right.timestamp })
            assertTrue(parsed.segments.any { it.mode == TransportMode.TRANSIT })
            assertEquals("Home", parsed.stops.single().name)
        }
    }

    @Test
    fun `splits a multi-day export into one journey per local calendar day`() {
        val json = """
            {
              "locations": [
                {"latitude": 50.10, "longitude": 14.40, "timestamp": "2026-07-25T10:00:00Z"},
                {"latitude": 50.11, "longitude": 14.41, "timestamp": "2026-07-25T10:10:00Z"},
                {"latitude": 50.12, "longitude": 14.42, "timestamp": "2026-07-26T10:00:00Z"},
                {"latitude": 50.13, "longitude": 14.43, "timestamp": "2026-07-26T10:10:00Z"}
              ]
            }
        """.trimIndent()

        val journeys = TimelineParser.parseTimelineJourneys(
            reader = java.io.StringReader(json),
            defaultTitle = "My timeline",
            zoneId = java.time.ZoneId.of("UTC")
        )

        assertEquals(2, journeys.size)
        assertEquals("My timeline — Jul 25, 2026", journeys[0].journey.title)
        assertEquals("My timeline — Jul 26, 2026", journeys[1].journey.title)
        assertTrue(journeys.all { it.points.size >= 2 })
    }

    @Test
    fun `parses GeoJSON LineString geometry as an ordered route`() {
        val json = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[14.40, 50.08], [14.41, 50.081], [14.42, 50.082]]
                  }
                }
              ]
            }
        """.trimIndent()

        val result = TimelineParser.parseTimelineJson(json, "GeoJSON route")

        assertNotNull(result)
        requireNotNull(result).also { parsed ->
            assertTrue(parsed.points.size >= 2)
            assertEquals(14.40, parsed.points.first().longitude, 0.00001)
            assertEquals(50.082, parsed.points.last().latitude, 0.00001)
        }
    }
}
