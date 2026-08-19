package com.example.timelineviewer.data.repository

import androidx.room.withTransaction
import com.example.timelineviewer.data.local.AppDatabase
import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.data.model.JourneyDetailData
import com.example.timelineviewer.data.model.JourneyMetadata
import com.example.timelineviewer.data.model.OfflineMapRegion
import com.example.timelineviewer.data.model.RoutePoint
import com.example.timelineviewer.data.model.Stop
import com.example.timelineviewer.data.parser.ParsedJourneyResult
import com.example.timelineviewer.data.parser.TimelineParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.Reader

class JourneyRepository(private val database: AppDatabase) {

    val allJourneys: Flow<List<Journey>> = database.journeyDao().getAllJourneys()
    val allTransportSegments = database.transportSegmentDao().getAllSegments()

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

    /** Updates only the user-authored story metadata; route and local map data remain untouched. */
    suspend fun updateJourneyMetadata(id: Long, metadata: JourneyMetadata): Boolean = database.withTransaction {
        database.journeyDao().updateJourneyMetadata(
            id = id,
            title = metadata.title,
            description = metadata.description
        ) == 1
    }

    /** Marks a journey as a user-curated memory without rewriting any imported Timeline data. */
    suspend fun updateJourneyFavorite(id: Long, isFavorite: Boolean): Boolean = database.withTransaction {
        database.journeyDao().updateJourneyFavorite(id, isFavorite) == 1
    }

    /** Persists only a private app-storage path, never an external picker URI. */
    suspend fun updateJourneyCover(id: Long, coverPhotoPath: String?, updatedAt: Long?): Boolean = database.withTransaction {
        database.journeyDao().updateJourneyCover(id, coverPhotoPath, updatedAt) == 1
    }

    suspend fun getJourneyCoverPath(id: Long): String? = database.journeyDao().getJourneyCoverPath(id)

    suspend fun getAllJourneyCoverPaths(): List<String> = database.journeyDao().getAllCoverPaths()

    suspend fun deleteJourneys(ids: List<Long>) = database.withTransaction {
        if (ids.isNotEmpty()) database.journeyDao().deleteJourneys(ids)
    }

    suspend fun deleteAllJourneys() = database.withTransaction {
        database.journeyDao().deleteAllJourneys()
    }

    fun observeOfflineMapRegion(journeyId: Long) = database.offlineMapRegionDao().observeForJourney(journeyId)

    suspend fun upsertOfflineMapRegion(region: OfflineMapRegion) {
        database.offlineMapRegionDao().upsert(region)
    }

    suspend fun getOfflineMapRegion(journeyId: Long): OfflineMapRegion? =
        database.offlineMapRegionDao().getForJourney(journeyId)

    suspend fun deleteOfflineMapRegion(journeyId: Long) {
        database.offlineMapRegionDao().deleteForJourney(journeyId)
    }

    /** Parses pasted text on a background dispatcher, then writes the full journey atomically. */
    suspend fun importTimelineJson(jsonString: String, title: String): Boolean {
        val parsed = withContext(Dispatchers.Default) {
            TimelineParser.parseTimelineJson(jsonString, title)
        } ?: return false
        persistParsedJourney(parsed)
        return true
    }

    /**
     * Parses a file stream without first loading a Takeout/GeoJSON document into an in-memory
     * String. The caller owns and closes the reader after this operation returns.
     */
    suspend fun importTimelineReader(reader: Reader, title: String): Boolean {
        val parsed = withContext(Dispatchers.Default) {
            TimelineParser.parseTimeline(reader, title)
        } ?: return false
        persistParsedJourney(parsed)
        return true
    }

    private suspend fun persistParsedJourney(parsed: ParsedJourneyResult) = database.withTransaction {
        val journeyId = database.journeyDao().insertJourney(parsed.journey)
        database.routePointDao().insertPoints(parsed.points.map { it.copy(journeyId = journeyId) })
        database.stopDao().insertStops(parsed.stops.map { it.copy(journeyId = journeyId) })
        database.transportSegmentDao().insertSegments(parsed.segments.map { it.copy(journeyId = journeyId) })
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
            val fraction = i.toDouble() / numSteps
            points += RoutePoint(
                journeyId = 0L,
                latitude = startLat + (endLat - startLat) * fraction,
                longitude = startLng + (endLng - startLng) * fraction,
                timestamp = startTime + (i * 300000L),
                speedKmh = 25.0,
                bearing = 45f,
                sequenceOrder = i
            )
        }

        stopNames.forEachIndexed { index, name ->
            val fraction = (index + 1).toDouble() / (stopNames.size + 1)
            stops += Stop(
                journeyId = 0L,
                latitude = startLat + (endLat - startLat) * fraction,
                longitude = startLng + (endLng - startLng) * fraction,
                name = name,
                startTime = startTime + (index * 1200000L),
                endTime = startTime + (index * 1200000L) + 600000L,
                durationSeconds = 600L,
                sequenceOrder = index
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
