package com.example.timelineviewer.ui.map

import com.example.timelineviewer.data.model.RoutePoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** A renderer-independent camera pose so every camera mode can be unit-tested without Mapbox. */
data class JourneyCameraPose(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val pitch: Double,
    val bearing: Double
)

object CinematicCameraDirector {
    fun poseFor(
        mode: JourneyCameraMode,
        points: List<RoutePoint>,
        activePointIndex: Int
    ): JourneyCameraPose? {
        if (points.isEmpty()) return null
        val safeIndex = activePointIndex.coerceIn(0, points.lastIndex)
        val active = points[safeIndex]
        val effectiveBearing = calculateSmoothedBearing(points, safeIndex)

        return when (mode) {
            JourneyCameraMode.OVERVIEW -> JourneyCameraPose(
                latitude = points.map { it.latitude }.average(),
                longitude = points.map { it.longitude }.average(),
                zoom = overviewZoom(points),
                pitch = 52.0,
                bearing = 0.0
            )
            JourneyCameraMode.FOLLOW -> JourneyCameraPose(
                latitude = active.latitude,
                longitude = active.longitude,
                zoom = 16.2,
                pitch = 58.0,
                bearing = normalizeBearing(effectiveBearing)
            )
            JourneyCameraMode.CINEMA -> JourneyCameraPose(
                latitude = active.latitude,
                longitude = active.longitude,
                zoom = 15.2,
                pitch = 72.0,
                bearing = normalizeBearing(effectiveBearing - 16.0)
            )
            JourneyCameraMode.ORBIT -> JourneyCameraPose(
                latitude = active.latitude,
                longitude = active.longitude,
                zoom = 15.4,
                pitch = 66.0,
                bearing = normalizeBearing(effectiveBearing + 62.0)
            )
        }
    }

    private fun calculateSmoothedBearing(points: List<RoutePoint>, index: Int): Double {
        val current = points[index]
        
        // Look ahead and behind up to 3 points to derive a stable, non-jittery travel direction
        val window = 3
        val prevIndex = (index - window).coerceAtLeast(0)
        val nextIndex = (index + window).coerceAtMost(points.lastIndex)

        val prevPoint = points[prevIndex]
        val nextPoint = points[nextIndex]

        val latDiff = nextPoint.latitude - prevPoint.latitude
        val lonDiff = nextPoint.longitude - prevPoint.longitude

        // If movement within the window is negligible, fallback to point's explicit bearing or 0.0
        if (abs(latDiff) < 0.00001 && abs(lonDiff) < 0.00001) {
            return current.bearing.toDouble().takeIf { it != 0.0 } ?: 0.0
        }

        val dLon = Math.toRadians(lonDiff)
        val lat1 = Math.toRadians(prevPoint.latitude)
        val lat2 = Math.toRadians(nextPoint.latitude)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val radians = atan2(y, x)

        return (Math.toDegrees(radians) + 360.0) % 360.0
    }

    private fun overviewZoom(points: List<RoutePoint>): Double {
        val latSpan = points.maxOf { it.latitude } - points.minOf { it.latitude }
        val lonSpan = points.maxOf { it.longitude } - points.minOf { it.longitude }
        val maxSpan = maxOf(abs(latSpan), abs(lonSpan))
        return when {
            maxSpan < 0.003 -> 15.5
            maxSpan < 0.01 -> 14.0
            maxSpan < 0.04 -> 12.5
            maxSpan < 0.12 -> 11.0
            maxSpan < 0.4 -> 9.5
            maxSpan < 1.2 -> 8.0
            maxSpan < 4.0 -> 6.5
            else -> 5.0
        }
    }

    private fun normalizeBearing(value: Double): Double = ((value % 360.0) + 360.0) % 360.0
}
