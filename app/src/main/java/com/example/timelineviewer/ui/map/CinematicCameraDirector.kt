package com.example.timelineviewer.ui.map

import com.example.timelineviewer.data.analysis.RelivePlaybackClock
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
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun poseFor(
        mode: JourneyCameraMode,
        points: List<RoutePoint>,
        activePointIndex: Int,
        showThreeDObjects: Boolean = false
    ): JourneyCameraPose? {
        return poseForInterpolated(mode, points, activePointIndex, 0f, null, showThreeDObjects)
    }

    fun poseForInterpolated(
        mode: JourneyCameraMode,
        points: List<RoutePoint>,
        activePointIndex: Int,
        subIndexProgress: Float = 0f,
        previousBearing: Double? = null,
        showThreeDObjects: Boolean = false
    ): JourneyCameraPose? {
        if (points.isEmpty()) return null
        val safeIndex = activePointIndex.coerceIn(0, points.lastIndex)

        val interpolatedActivePoint = if (points.size >= 4 && safeIndex < points.lastIndex) {
            val p0 = points[(safeIndex - 1).coerceAtLeast(0)]
            val p1 = points[safeIndex]
            val p2 = points[(safeIndex + 1).coerceAtMost(points.lastIndex)]
            val p3 = points[(safeIndex + 2).coerceAtMost(points.lastIndex)]
            RelivePlaybackClock.interpolatePoint(p0, p1, p2, p3, subIndexProgress)
        } else {
            points[safeIndex]
        }

        val rawBearing = calculateSmoothedBearing(points, safeIndex)
        val activeSpeed = interpolatedActivePoint.speedKmh.toDouble().coerceAtLeast(0.0)

        // Angular damping: pan turns smoothly rather than snapping abruptly
        val effectiveBearing = if (previousBearing != null && mode != JourneyCameraMode.OVERVIEW) {
            dampBearing(previousBearing, rawBearing, smoothingFactor = 0.25)
        } else {
            rawBearing
        }

        // Dynamic zoom and altitude scaling based on travel velocity
        val speedZoomOffset = (activeSpeed / 75.0).coerceIn(0.0, 2.2)

        // 3D Asset Pacing & Tile Loading Buffer:
        // Project camera target ahead along bearing to give Mapbox's vector rendering engine
        // sufficient frame time and frustum lookahead to pre-stream 3D building meshes and terrain tiles.
        val tileBufferDistanceMeters = if (showThreeDObjects && mode != JourneyCameraMode.OVERVIEW) {
            (activeSpeed * 1.5 + 25.0).coerceIn(30.0, 250.0)
        } else {
            0.0
        }

        val bufferedTarget = if (tileBufferDistanceMeters > 0.0) {
            offsetCoordinate(
                lat = interpolatedActivePoint.latitude,
                lon = interpolatedActivePoint.longitude,
                bearingDeg = effectiveBearing,
                distanceMeters = tileBufferDistanceMeters
            )
        } else {
            Pair(interpolatedActivePoint.latitude, interpolatedActivePoint.longitude)
        }

        val targetLat = bufferedTarget.first
        val targetLon = bufferedTarget.second

        return when (mode) {
            JourneyCameraMode.OVERVIEW -> JourneyCameraPose(
                latitude = points.map { it.latitude }.average(),
                longitude = points.map { it.longitude }.average(),
                zoom = overviewZoom(points),
                pitch = 50.0,
                bearing = 0.0
            )
            JourneyCameraMode.FOLLOW -> JourneyCameraPose(
                latitude = targetLat,
                longitude = targetLon,
                zoom = (16.2 - speedZoomOffset).coerceIn(13.0, 17.2),
                pitch = if (showThreeDObjects) 55.0 else 52.0,
                bearing = normalizeBearing(effectiveBearing)
            )
            JourneyCameraMode.CINEMA -> JourneyCameraPose(
                latitude = targetLat,
                longitude = targetLon,
                zoom = (15.2 - speedZoomOffset * 0.8).coerceIn(12.8, 16.5),
                pitch = (60.0 + speedZoomOffset * 2.5).coerceAtMost(68.0),
                bearing = normalizeBearing(effectiveBearing - 18.0)
            )
            JourneyCameraMode.ORBIT -> JourneyCameraPose(
                latitude = targetLat,
                longitude = targetLon,
                zoom = (15.3 - speedZoomOffset * 0.5).coerceIn(13.0, 16.8),
                pitch = 60.0,
                bearing = normalizeBearing(effectiveBearing + 65.0 + (safeIndex % 360) * 0.4)
            )
        }
    }

    private fun dampBearing(currentBearing: Double, targetBearing: Double, smoothingFactor: Double): Double {
        var diff = targetBearing - currentBearing
        while (diff < -180.0) diff += 360.0
        while (diff > 180.0) diff -= 360.0
        return normalizeBearing(currentBearing + diff * smoothingFactor.coerceIn(0.05, 1.0))
    }

    private fun offsetCoordinate(
        lat: Double,
        lon: Double,
        bearingDeg: Double,
        distanceMeters: Double
    ): Pair<Double, Double> {
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        val bearingRad = Math.toRadians(bearingDeg)
        val angularDistance = distanceMeters / EARTH_RADIUS_METERS

        val newLatRad = asinSafe(
            sin(latRad) * cos(angularDistance) +
                    cos(latRad) * sin(angularDistance) * cos(bearingRad)
        )
        val newLonRad = lonRad + atan2(
            sin(bearingRad) * sin(angularDistance) * cos(latRad),
            cos(angularDistance) - sin(latRad) * sin(newLatRad)
        )

        return Pair(Math.toDegrees(newLatRad), Math.toDegrees(newLonRad))
    }

    private fun asinSafe(x: Double): Double = kotlin.math.asin(x.coerceIn(-1.0, 1.0))

    private fun calculateSmoothedBearing(points: List<RoutePoint>, index: Int): Double {
        val current = points[index]
        
        // Multi-point window for smooth bearing estimation without position jitter
        val lookaround = 4
        val prevIndex = (index - lookaround).coerceAtLeast(0)
        val nextIndex = (index + lookaround).coerceAtMost(points.lastIndex)

        if (prevIndex == nextIndex) {
            return current.bearing.toDouble().takeIf { it != 0.0 } ?: 0.0
        }

        val prevPoint = points[prevIndex]
        val nextPoint = points[nextIndex]

        val latDiff = nextPoint.latitude - prevPoint.latitude
        val lonDiff = nextPoint.longitude - prevPoint.longitude

        if (abs(latDiff) < 0.000008 && abs(lonDiff) < 0.000008) {
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
