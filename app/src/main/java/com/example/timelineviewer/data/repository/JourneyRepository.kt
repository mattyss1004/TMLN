package com.example.timelineviewer.data.repository

import androidx.room.withTransaction
import com.example.timelineviewer.data.local.AppDatabase
import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.data.model.JourneyDetailData
import com.example.timelineviewer.data.model.RoutePoint
import com.example.timelineviewer.data.model.Stop
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

    /**
     * Child records are removed by the database foreign keys. Keeping this operation in a single
     * transaction ensures the UI never observes an orphaned or half-deleted travel story.
     */
    suspend fun deleteJourney(id: Long) = database.withTransaction {
        database.journeyDao().deleteJourney(id)
    }

    suspend fun deleteJourneys(ids: List<Long>) = database.withTransaction {
        if (ids.isNotEmpty()) database.journeyDao().deleteJourneys(ids)
    }

    suspend fun deleteAllJourneys() = database.withTransaction {
        database.journeyDao().deleteAllJourneys()
    }

    /**
     * Parse first, then persist the complete journey atomically. If any insertion fails, Room
     * rolls back the parent record and all children instead of leaving a partial import behind.
     */
    suspend fun importTimelineJson(jsonString: String, title: String): Boolean {
        val parsed = TimelineParser.parseTimelineJson(jsonString, title) ?: return false

        database.withTransaction {
            val journeyId = database.journeyDao().insertJourney(parsed.journey)
            database.routePointDao().insertPoints(parsed.points.map { it.copy(journeyId = journeyId) })
            database.stopDao().insertStops(parsed.stops.map { it.copy(journeyId = journeyId) })
            database.transportSegmentDao().insertSegments(parsed.segments.map { it.copy(journeyId = journeyId) })
        }
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

        stopNames.forEachIndexed { index, name ->
            val fraction = (index + 1).toDouble() / (stopNames.size + 1)
            stops.add(
                Stop(
                    journeyId = 0L,
                    latitude = startLat + (endLat - startLat) * fraction,
                    longitude = startLng + (endLng - startLng) * fraction,
                    name = name,
                    startTime = startTime + (index * 1200000L),
                    endTime = startTime + (index * 1200000L) + 600000L,
                    durationSeconds = 600L,
                    sequenceOrder = index
                )
            )
        }

        return database.withTransaction {
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
            database.routePointDao().insertPoints(points.map { it.copy(journeyId = journeyId) })
            database.stopDao().insertStops(stops.map { it.copy(journeyId = journeyId) })
            journeyId
        }
    }
}
