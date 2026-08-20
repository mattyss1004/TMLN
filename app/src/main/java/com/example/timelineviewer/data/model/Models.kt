package com.example.timelineviewer.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class TransportMode(val label: String, val hexColor: Long) {
    WALKING("Walking", 0xFF3B82F6),   // Blue
    CYCLING("Cycling", 0xFFF59E0B),   // Amber/Yellow
    DRIVING("Driving", 0xFFEF4444),   // Red
    TRANSIT("Transit", 0xFF10B981),   // Green
    UNKNOWN("Unknown", 0xFF6B7280)    // Gray
}

/** Identifies app-provided test content separately from journeys created from personal imports. */
enum class JourneySource {
    DEMO,
    IMPORTED
}

@Serializable
@Entity(tableName = "journeys")
data class Journey(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val startTime: Long,
    val endTime: Long,
    val totalDistanceKm: Double,
    val totalDurationSeconds: Long,
    val pointCount: Int,
    val stopCount: Int,
    val maxSpeedKmh: Double = 0.0,
    val averageSpeedKmh: Double = 0.0,
    val dominantMode: TransportMode = TransportMode.UNKNOWN,
    val highlightPlaceName: String? = null,
    /** User-authored archive metadata. Cover paths always point inside the app's private storage. */
    val isFavorite: Boolean = false,
    val coverPhotoPath: String? = null,
    val coverUpdatedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val source: JourneySource = JourneySource.IMPORTED
)

@Serializable
@Entity(
    tableName = "route_points",
    foreignKeys = [
        ForeignKey(
            entity = Journey::class,
            parentColumns = ["id"],
            childColumns = ["journeyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["journeyId", "sequenceOrder"])]
)
data class RoutePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val journeyId: Long,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val speedKmh: Double = 0.0,
    val bearing: Float = 0f,
    val sequenceOrder: Int
)

@Serializable
@Entity(
    tableName = "stops",
    foreignKeys = [
        ForeignKey(
            entity = Journey::class,
            parentColumns = ["id"],
            childColumns = ["journeyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["journeyId", "sequenceOrder"])]
)
data class Stop(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val journeyId: Long,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val sequenceOrder: Int,
    val importanceScore: Int = 0, // 0-100 based on dwell time and metadata
    val category: String = "Waypoint" // e.g., "Sightseeing", "Rest", "Transit Hub"
)

@Serializable
@Entity(
    tableName = "transport_segments",
    foreignKeys = [
        ForeignKey(
            entity = Journey::class,
            parentColumns = ["id"],
            childColumns = ["journeyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["journeyId", "startIndex"])]
)
data class TransportSegment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val journeyId: Long,
    val startIndex: Int,
    val endIndex: Int,
    val mode: TransportMode,
    val distanceKm: Double,
    val durationSeconds: Long,
    val averageSpeedKmh: Double
)

data class JourneyDetailData(
    val journey: Journey,
    val points: List<RoutePoint>,
    val stops: List<Stop>,
    val segments: List<TransportSegment>
)


enum class OfflineRegionStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    AVAILABLE,
    FAILED
}

@Entity(
    tableName = "offline_map_regions",
    foreignKeys = [
        ForeignKey(
            entity = Journey::class,
            parentColumns = ["id"],
            childColumns = ["journeyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["journeyId"], unique = true)]
)
data class OfflineMapRegion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val journeyId: Long,
    val regionId: String,
    val status: OfflineRegionStatus = OfflineRegionStatus.NOT_DOWNLOADED,
    val progress: Float = 0f,
    val styleUri: String,
    val minZoom: Int,
    val maxZoom: Int,
    val downloadedAt: Long? = null,
    val lastError: String? = null
)
