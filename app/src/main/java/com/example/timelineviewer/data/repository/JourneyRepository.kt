package com.example.timelineviewer.data.repository

import com.example.timelineviewer.data.local.AppDatabase
import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.data.model.JourneyDetailData
import com.example.timelineviewer.data.model.RoutePoint
import com.example.timelineviewer.data.model.Stop
import com.example.timelineviewer.data.model.TransportSegment
import com.example.timelineviewer.data.parser.TimelineParser
import kotlinx.coroutines.flow.Flow

class JourneyRepository(private val database: AppDatabase) {

    val allJourneys: Flow<List<Journey>> = database.journeyDao().getAllJourneys()

    suspend fun getJourneyDetail(id: Long): JourneyDetailData? {
        val journey = database.journeyDao().getJourneyById(id) ?: return null
        val points = database.routePointDao().getPointsListForJourney(id)
        val stops = database.stopDao().getStopsListForJourney(id)
        val segments = database.transportSegmentDao().getSegmentsListForJourney(id)
        return JourneyDetailData(journey, points, stops, segments)
    }

    suspend fun deleteJourney(id: Long) {
        database.journeyDao().deleteJourney(id)
        database.routePointDao().deletePointsForJourney(id)
        database.stopDao().deleteStopsForJourney(id)
        database.transportSegmentDao().deleteSegmentsForJourney(id)
    }

    suspend fun deleteJourneys(ids: List<Long>) {
        database.journeyDao().deleteJourneys(ids)
        ids.forEach { id ->
            database.routePointDao().deletePointsForJourney(id)
            database.stopDao().deleteStopsForJourney(id)
            database.transportSegmentDao().deleteSegmentsForJourney(id)
        }
    }

    suspend fun deleteAllJourneys() {
        database.journeyDao().deleteAllJourneys()
    }

    suspend fun importTimelineJson(jsonString: String, title: String): Boolean {
        val parsed = TimelineParser.parseTimelineJson(jsonString, title) ?: return false

        val journeyId = database.journeyDao().insertJourney(parsed.journey)

        val pointsWithId = parsed.points.map { it.copy(journeyId = journeyId) }
        val stopsWithId = parsed.stops.map { it.copy(journeyId = journeyId) }
        val segmentsWithId = parsed.segments.map { it.copy(journeyId = journeyId) }

        database.routePointDao().insertPoints(pointsWithId)
        database.stopDao().insertStops(stopsWithId)
        database.transportSegmentDao().insertSegments(segmentsWithId)

        return true
    }

    suspend fun addCustomJourney(
        title: String,
        description: String,
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        stopNames: List<String>
    ): Long {
        val startTime = System.currentTimeMillis() - 7200000L
        val endTime = System.currentTimeMillis()

        val points = mutableListOf<RoutePoint>()
        val stops = mutableListOf<Stop>()

        val numSteps = 20
        for (i in 0..numSteps) {
            val frac = i.toDouble() / numSteps
            val lat = startLat + (endLat - startLat) * frac
            val lng = startLng + (endLng - startLng) * frac
            points.add(
                RoutePoint(
                    journeyId = 0L,
                    latitude = lat,
                    longitude = lng,
                    timestamp = startTime + (i * 300000L),
                    speedKmh = 25.0,
                    bearing = 45f,
                    sequenceOrder = i
                )
            )
        }

        stopNames.forEachIndexed { idx, name ->
            val frac = (idx + 1).toDouble() / (stopNames.size + 1)
            stops.add(
                Stop(
                    journeyId = 0L,
                    latitude = startLat + (endLat - startLat) * frac,
                    longitude = startLng + (endLng - startLng) * frac,
                    name = name,
                    startTime = startTime + (idx * 1200000L),
                    endTime = startTime + (idx * 1200000L) + 600000L,
                    durationSeconds = 600L,
                    sequenceOrder = idx
                )
            )
        }

        val journeyId = database.journeyDao().insertJourney(
            Journey(
                title = title,
                description = description,
                startTime = startTime,
                endTime = endTime,
                totalDistanceKm = 15.4,
                totalDurationSeconds = 7200L,
                pointCount = points.size,
                stopCount = stops.size
            )
        )

        val updatedPoints = points.map { it.copy(journeyId = journeyId) }
        val updatedStops = stops.map { it.copy(journeyId = journeyId) }

        database.routePointDao().insertPoints(updatedPoints)
        database.stopDao().insertStops(updatedStops)

        return journeyId
    }
}
