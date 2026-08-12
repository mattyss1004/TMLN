package com.example.timelineviewer.data.seed

import com.example.timelineviewer.data.local.AppDatabase
import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.data.model.RoutePoint
import com.example.timelineviewer.data.model.Stop
import com.example.timelineviewer.data.model.TransportMode
import com.example.timelineviewer.data.model.TransportSegment
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

object SampleDataSeeder {

    suspend fun seedIfEmpty(database: AppDatabase) {
        val journeyDao = database.journeyDao()

        // Check if database has journeys
        val existing = journeyDao.getJourneyById(1L)
        if (existing != null) return

        val now = System.currentTimeMillis()
        val dayMs = 86400000L

        // Journey 1: Prague Historic City Walk & Transit
        createSampleJourney(
            database = database,
            title = "Prague Historic City Exploration",
            description = "A breathtaking walk through Old Town Square, Charles Bridge, and Prague Castle.",
            startTime = now - (dayMs * 2),
            waypoints = listOf(
                Waypoint(50.0875, 14.4213, "Old Town Square", TransportMode.WALKING, 95, "Sightseeing"),
                Waypoint(50.0862, 14.4139, "Charles Bridge", TransportMode.WALKING, 90, "Highlight"),
                Waypoint(50.0888, 14.4045, "Malostranská Station", TransportMode.TRANSIT, 40, "Transit Hub"),
                Waypoint(50.0908, 14.4008, "Prague Castle Entrance", TransportMode.WALKING, 85, "Highlight"),
                Waypoint(50.0912, 14.3980, "St. Vitus Cathedral", TransportMode.WALKING, 92, "Sightseeing"),
                Waypoint(50.0833, 14.3930, "Petřín Lookout Tower", TransportMode.WALKING, 88, "Sightseeing")
            )
        )

        // Journey 2: Tokyo Yamanote Loop & Shinjuku Night Drive
        createSampleJourney(
            database = database,
            title = "Tokyo Metro Express & Night Drive",
            description = "Neon lights from Shibuya Crossing to the heights of Tokyo Tower.",
            startTime = now - (dayMs * 5),
            waypoints = listOf(
                Waypoint(35.6595, 139.7004, "Shibuya Scramble Crossing", TransportMode.WALKING, 98, "Highlight"),
                Waypoint(35.6895, 139.6917, "Shinjuku Station South", TransportMode.TRANSIT, 50, "Transit Hub"),
                Waypoint(35.6997, 139.7714, "Akihabara Electric Town", TransportMode.TRANSIT, 80, "Sightseeing"),
                Waypoint(35.6719, 139.7648, "Ginza Six Shopping District", TransportMode.DRIVING, 75, "Rest"),
                Waypoint(35.6586, 139.7454, "Tokyo Tower Observatory", TransportMode.DRIVING, 94, "Highlight")
            )
        )

        // Journey 3: Alpine Scenic Pass Drive
        createSampleJourney(
            database = database,
            title = "Scenic Alpine Mountain Highway",
            description = "High altitude drive through mountain passes, lakes, and panoramas.",
            startTime = now - (dayMs * 8),
            waypoints = listOf(
                Waypoint(46.5500, 8.5600, "Andermatt Valley Center", TransportMode.CYCLING, 60, "Rest"),
                Waypoint(46.5610, 8.4200, "Furka Pass Peak", TransportMode.DRIVING, 96, "Highlight"),
                Waypoint(46.5770, 8.3800, "Rhône Glacier Ice Grotto", TransportMode.WALKING, 89, "Sightseeing"),
                Waypoint(46.6800, 8.0300, "Interlaken Lakeside Resort", TransportMode.DRIVING, 85, "Rest")
            )
        )
    }

    private data class Waypoint(
        val lat: Double,
        val lng: Double,
        val stopName: String?,
        val mode: TransportMode,
        val importance: Int = 50,
        val category: String = "Waypoint"
    )

    private suspend fun createSampleJourney(
        database: AppDatabase,
        title: String,
        description: String,
        startTime: Long,
        waypoints: List<Waypoint>
    ) {
        val points = mutableListOf<RoutePoint>()
        val stops = mutableListOf<Stop>()
        val segments = mutableListOf<TransportSegment>()

        var currentSeq = 0
        var totalDistKm = 0.0
        var maxSpeed = 0.0
        var currentTime = startTime
        val modeDurationMap = mutableMapOf<TransportMode, Long>()

        // Insert dummy journey first to get ID
        var journeyId = database.journeyDao().insertJourney(
            Journey(
                title = title,
                description = description,
                startTime = startTime,
                endTime = startTime,
                totalDistanceKm = 0.0,
                totalDurationSeconds = 0L,
                pointCount = 0,
                stopCount = 0
            )
        )

        for (i in 0 until waypoints.size - 1) {
            val startWp = waypoints[i]
            val endWp = waypoints[i + 1]

            val segmentPointsCount = 20
            val segmentStartIdx = currentSeq

            val segmentDist = calculateDistanceKm(startWp.lat, startWp.lng, endWp.lat, endWp.lng)
            totalDistKm += segmentDist

            val speed = when (startWp.mode) {
                TransportMode.WALKING -> 4.5
                TransportMode.CYCLING -> 18.0
                TransportMode.TRANSIT -> 45.0
                TransportMode.DRIVING -> 65.0
                TransportMode.UNKNOWN -> 20.0
            }
            if (speed > maxSpeed) maxSpeed = speed

            val durationSec = (segmentDist / speed * 3600).toLong().coerceAtLeast(300L)
            modeDurationMap[startWp.mode] = (modeDurationMap[startWp.mode] ?: 0L) + durationSec

            val timePerStep = (durationSec * 1000L) / segmentPointsCount

            for (step in 0 until segmentPointsCount) {
                val fraction = step.toDouble() / segmentPointsCount
                val lat = startWp.lat + (endWp.lat - startWp.lat) * fraction
                val lng = startWp.lng + (endWp.lng - startWp.lng) * fraction

                points.add(
                    RoutePoint(
                        journeyId = journeyId,
                        latitude = lat,
                        longitude = lng,
                        timestamp = currentTime,
                        speedKmh = speed,
                        bearing = calculateBearing(startWp.lat, startWp.lng, endWp.lat, endWp.lng),
                        sequenceOrder = currentSeq++
                    )
                )
                currentTime += timePerStep
            }

            segments.add(
                TransportSegment(
                    journeyId = journeyId,
                    startIndex = segmentStartIdx,
                    endIndex = currentSeq - 1,
                    mode = startWp.mode,
                    distanceKm = (segmentDist * 100).roundToInt() / 100.0,
                    durationSeconds = durationSec,
                    averageSpeedKmh = speed
                )
            )

            if (startWp.stopName != null) {
                stops.add(
                    Stop(
                        journeyId = journeyId,
                        latitude = startWp.lat,
                        longitude = startWp.lng,
                        name = startWp.stopName,
                        startTime = currentTime - timePerStep,
                        endTime = currentTime,
                        durationSeconds = 600L,
                        sequenceOrder = stops.size,
                        importanceScore = startWp.importance,
                        category = startWp.category
                    )
                )
            }
        }

        // Add last stop
        val lastWp = waypoints.last()
        if (lastWp.stopName != null) {
            stops.add(
                Stop(
                    journeyId = journeyId,
                    latitude = lastWp.lat,
                    longitude = lastWp.lng,
                    name = lastWp.stopName,
                    startTime = currentTime,
                    endTime = currentTime + 600000L,
                    durationSeconds = 600L,
                    sequenceOrder = stops.size,
                    importanceScore = lastWp.importance,
                    category = lastWp.category
                )
            )
        }

        val totalDurationSec = (currentTime - startTime) / 1000L
        val dominantMode = modeDurationMap.maxByOrNull { it.value }?.key ?: TransportMode.UNKNOWN
        val highlightPlace = stops.maxByOrNull { it.importanceScore }?.name

        // Final Update
        database.journeyDao().insertJourney(
            Journey(
                id = journeyId,
                title = title,
                description = description,
                startTime = startTime,
                endTime = currentTime,
                totalDistanceKm = (totalDistKm * 100).roundToInt() / 100.0,
                totalDurationSeconds = totalDurationSec,
                pointCount = points.size,
                stopCount = stops.size,
                maxSpeedKmh = maxSpeed,
                averageSpeedKmh = (totalDistKm / (totalDurationSec / 3600.0) * 10).roundToInt() / 10.0,
                dominantMode = dominantMode,
                highlightPlaceName = highlightPlace
            )
        )

        database.routePointDao().insertPoints(points)
        database.stopDao().insertStops(stops)
        database.transportSegmentDao().insertSegments(segments)
    }

    private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
        val brng = Math.toDegrees(atan2(y, x))
        return ((brng + 360) % 360).toFloat()
    }
}
