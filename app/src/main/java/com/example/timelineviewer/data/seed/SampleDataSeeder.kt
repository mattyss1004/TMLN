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

object SampleDataSeeder {

    suspend fun seedIfEmpty(database: AppDatabase) {
        val journeyDao = database.journeyDao()
        val routePointDao = database.routePointDao()
        val stopDao = database.stopDao()
        val segmentDao = database.transportSegmentDao()

        // Check if database has journeys
        val existing = journeyDao.getJourneyById(1L)
        if (existing != null) return

        val now = System.currentTimeMillis()
        val dayMs = 86400000L

        // Journey 1: Prague Historic City Walk & Transit
        createSampleJourney(
            database = database,
            title = "Prague Historic City Exploration",
            description = "Walk through Old Town Square, Charles Bridge, and Prague Castle with transit legs",
            startTime = now - (dayMs * 2),
            waypoints = listOf(
                Waypoint(50.0875, 14.4213, "Old Town Square", TransportMode.WALKING),
                Waypoint(50.0862, 14.4139, "Charles Bridge", TransportMode.WALKING),
                Waypoint(50.0888, 14.4045, "Malostranská Station", TransportMode.TRANSIT),
                Waypoint(50.0908, 14.4008, "Prague Castle Entrance", TransportMode.WALKING),
                Waypoint(50.0912, 14.3980, "St. Vitus Cathedral", TransportMode.WALKING),
                Waypoint(50.0833, 14.3930, "Petřín Lookout Tower", TransportMode.WALKING)
            )
        )

        // Journey 2: Tokyo Yamanote Loop & Shinjuku Night Drive
        createSampleJourney(
            database = database,
            title = "Tokyo Metro Express & Night Drive",
            description = "Shibuya Crossing to Shinjuku, Akihabara, and Ginza boulevard",
            startTime = now - (dayMs * 5),
            waypoints = listOf(
                Waypoint(35.6595, 139.7004, "Shibuya Scramble Crossing", TransportMode.WALKING),
                Waypoint(35.6895, 139.6917, "Shinjuku Station South", TransportMode.TRANSIT),
                Waypoint(35.6997, 139.7714, "Akihabara Electric Town", TransportMode.TRANSIT),
                Waypoint(35.6719, 139.7648, "Ginza Six Shopping District", TransportMode.DRIVING),
                Waypoint(35.6586, 139.7454, "Tokyo Tower Observatory", TransportMode.DRIVING)
            )
        )

        // Journey 3: Alpine Scenic Pass Drive
        createSampleJourney(
            database = database,
            title = "Scenic Alpine Mountain Highway",
            description = "High altitude drive through mountain passes, lakes, and panoramas",
            startTime = now - (dayMs * 8),
            waypoints = listOf(
                Waypoint(46.5500, 8.5600, "Andermatt Valley Center", TransportMode.CYCLING),
                Waypoint(46.5610, 8.4200, "Furka Pass Peak", TransportMode.DRIVING),
                Waypoint(46.5770, 8.3800, "Rhône Glacier Ice Grotto", TransportMode.WALKING),
                Waypoint(46.6800, 8.0300, "Interlaken Lakeside Resort", TransportMode.DRIVING)
            )
        )
    }

    private data class Waypoint(
        val lat: Double,
        val lng: Double,
        val stopName: String?,
        val mode: TransportMode
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
        var currentTime = startTime

        val journeyId = database.journeyDao().insertJourney(
            Journey(
                title = title,
                description = description,
                startTime = startTime,
                endTime = startTime + 3600000L * waypoints.size,
                totalDistanceKm = 0.0,
                totalDurationSeconds = 0L,
                pointCount = 0,
                stopCount = waypoints.count { it.stopName != null }
            )
        )

        for (i in 0 until waypoints.size - 1) {
            val startWp = waypoints[i]
            val endWp = waypoints[i + 1]

            val segmentPointsCount = 15
            val segmentStartIdx = currentSeq

            val segmentDist = calculateDistanceKm(startWp.lat, startWp.lng, endWp.lat, endWp.lng)
            totalDistKm += segmentDist

            val durationSec = when (startWp.mode) {
                TransportMode.WALKING -> (segmentDist / 4.5 * 3600).toLong().coerceAtLeast(300L)
                TransportMode.CYCLING -> (segmentDist / 18.0 * 3600).toLong().coerceAtLeast(200L)
                TransportMode.TRANSIT -> (segmentDist / 35.0 * 3600).toLong().coerceAtLeast(180L)
                TransportMode.DRIVING -> (segmentDist / 50.0 * 3600).toLong().coerceAtLeast(120L)
                TransportMode.UNKNOWN -> (segmentDist / 20.0 * 3600).toLong().coerceAtLeast(200L)
            }

            val timePerStep = (durationSec * 1000L) / segmentPointsCount

            for (step in 0 until segmentPointsCount) {
                val fraction = step.toDouble() / segmentPointsCount
                val lat = startWp.lat + (endWp.lat - startWp.lat) * fraction
                val lng = startWp.lng + (endWp.lng - startWp.lng) * fraction
                val speed = when (startWp.mode) {
                    TransportMode.WALKING -> 4.5
                    TransportMode.CYCLING -> 18.0
                    TransportMode.TRANSIT -> 35.0
                    TransportMode.DRIVING -> 55.0
                    TransportMode.UNKNOWN -> 15.0
                }

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
                    distanceKm = segmentDist,
                    durationSeconds = durationSec,
                    averageSpeedKmh = if (durationSec > 0) (segmentDist / (durationSec / 3600.0)) else 0.0
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
                        sequenceOrder = stops.size
                    )
                )
            }
        }

        // Add last stop if present
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
                    sequenceOrder = stops.size
                )
            )
        }

        val totalDurationSec = (currentTime - startTime) / 1000L

        // Update journey summary
        database.journeyDao().insertJourney(
            Journey(
                id = journeyId,
                title = title,
                description = description,
                startTime = startTime,
                endTime = currentTime,
                totalDistanceKm = (totalDistKm * 100).toInt() / 100.0,
                totalDurationSeconds = totalDurationSec,
                pointCount = points.size,
                stopCount = stops.size
            )
        )

        database.routePointDao().insertPoints(points)
        database.stopDao().insertStops(stops)
        database.transportSegmentDao().insertSegments(segments)
    }

    private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth radius in km
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
