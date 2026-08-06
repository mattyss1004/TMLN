package com.example.timelineviewer.data.local

import androidx.room.*
import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.data.model.RoutePoint
import com.example.timelineviewer.data.model.Stop
import com.example.timelineviewer.data.model.TransportMode
import com.example.timelineviewer.data.model.TransportSegment
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter
    fun fromTransportMode(mode: TransportMode): String = mode.name

    @TypeConverter
    fun toTransportMode(value: String): TransportMode = try {
        TransportMode.valueOf(value)
    } catch (e: Exception) {
        TransportMode.UNKNOWN
    }
}

@Dao
interface JourneyDao {
    @Query("SELECT * FROM journeys ORDER BY startTime DESC")
    fun getAllJourneys(): Flow<List<Journey>>

    @Query("SELECT * FROM journeys WHERE id = :id")
    suspend fun getJourneyById(id: Long): Journey?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJourney(journey: Journey): Long

    @Query("DELETE FROM journeys WHERE id = :id")
    suspend fun deleteJourney(id: Long)

    @Query("DELETE FROM journeys WHERE id IN (:ids)")
    suspend fun deleteJourneys(ids: List<Long>)

    @Query("DELETE FROM journeys")
    suspend fun deleteAllJourneys()
}

@Dao
interface RoutePointDao {
    @Query("SELECT * FROM route_points WHERE journeyId = :journeyId ORDER BY sequenceOrder ASC")
    fun getPointsForJourney(journeyId: Long): Flow<List<RoutePoint>>

    @Query("SELECT * FROM route_points WHERE journeyId = :journeyId ORDER BY sequenceOrder ASC")
    suspend fun getPointsListForJourney(journeyId: Long): List<RoutePoint>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<RoutePoint>)

    @Query("DELETE FROM route_points WHERE journeyId = :journeyId")
    suspend fun deletePointsForJourney(journeyId: Long)
}

@Dao
interface StopDao {
    @Query("SELECT * FROM stops WHERE journeyId = :journeyId ORDER BY sequenceOrder ASC")
    fun getStopsForJourney(journeyId: Long): Flow<List<Stop>>

    @Query("SELECT * FROM stops WHERE journeyId = :journeyId ORDER BY sequenceOrder ASC")
    suspend fun getStopsListForJourney(journeyId: Long): List<Stop>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<Stop>)

    @Query("DELETE FROM stops WHERE journeyId = :journeyId")
    suspend fun deleteStopsForJourney(journeyId: Long)
}

@Dao
interface TransportSegmentDao {
    @Query("SELECT * FROM transport_segments WHERE journeyId = :journeyId ORDER BY startIndex ASC")
    fun getSegmentsForJourney(journeyId: Long): Flow<List<TransportSegment>>

    @Query("SELECT * FROM transport_segments WHERE journeyId = :journeyId ORDER BY startIndex ASC")
    suspend fun getSegmentsListForJourney(journeyId: Long): List<TransportSegment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<TransportSegment>)

    @Query("DELETE FROM transport_segments WHERE journeyId = :journeyId")
    suspend fun deleteSegmentsForJourney(journeyId: Long)
}
