package com.example.timelineviewer.data.local

import androidx.room.*
import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.data.model.OfflineMapRegion
import com.example.timelineviewer.data.model.OfflineRegionStatus
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

    @TypeConverter
    fun fromOfflineRegionStatus(status: OfflineRegionStatus): String = status.name

    @TypeConverter
    fun toOfflineRegionStatus(value: String): OfflineRegionStatus = try {
        OfflineRegionStatus.valueOf(value)
    } catch (e: Exception) {
        OfflineRegionStatus.NOT_DOWNLOADED
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

    @Query("UPDATE journeys SET title = :title, description = :description WHERE id = :id")
    suspend fun updateJourneyMetadata(id: Long, title: String, description: String): Int

    @Query("UPDATE journeys SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateJourneyFavorite(id: Long, isFavorite: Boolean): Int

    @Query("UPDATE journeys SET coverPhotoPath = :coverPhotoPath, coverUpdatedAt = :updatedAt WHERE id = :id")
    suspend fun updateJourneyCover(id: Long, coverPhotoPath: String?, updatedAt: Long?): Int

    @Query("SELECT coverPhotoPath FROM journeys WHERE id = :id")
    suspend fun getJourneyCoverPath(id: Long): String?

    @Query("SELECT coverPhotoPath FROM journeys WHERE coverPhotoPath IS NOT NULL")
    suspend fun getAllCoverPaths(): List<String>

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
interface OfflineMapRegionDao {
    @Query("SELECT * FROM offline_map_regions WHERE journeyId = :journeyId LIMIT 1")
    fun observeForJourney(journeyId: Long): Flow<OfflineMapRegion?>

    @Query("SELECT * FROM offline_map_regions WHERE journeyId = :journeyId LIMIT 1")
    suspend fun getForJourney(journeyId: Long): OfflineMapRegion?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(region: OfflineMapRegion): Long

    @Query("DELETE FROM offline_map_regions WHERE journeyId = :journeyId")
    suspend fun deleteForJourney(journeyId: Long)
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
