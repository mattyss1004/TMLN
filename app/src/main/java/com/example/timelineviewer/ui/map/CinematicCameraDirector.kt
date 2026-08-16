package com.example.timelineviewer.ui.map

import com.example.timelineviewer.data.model.RoutePoint
import kotlin.math.abs

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
        val active = points[activePointIndex.coerceIn(0, points.lastIndex)]
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
                bearing = normalizeBearing(active.bearing.toDouble())
            )
            JourneyCameraMode.CINEMA -> JourneyCameraPose(
                latitude = active.latitude,
                longitude = active.longitude,
                zoom = 15.2,
                pitch = 72.0,
                bearing = normalizeBearing(active.bearing - 16.0)
            )
            JourneyCameraMode.ORBIT -> JourneyCameraPose(
                latitude = active.latitude,
                longitude = active.longitude,
                zoom = 15.4,
                pitch = 66.0,
                bearing = normalizeBearing(active.bearing + 62.0)
            )
        }
    }

    private fun overviewZoom(points: List<RoutePoint>): Double {
        val latSpan = points.maxOf { it.latitude } - points.minOf { it.latitude }
        val lonSpan = points.maxOf { it.longitude } - points.minOf { it.longitude }
        return when (maxOf(abs(latSpan), abs(lonSpan))) {
            in 0.0..<0.003 -> 15.5
            in 0.003..<0.01 -> 14.0
            in 0.01..<0.04 -> 12.5
            in 0.04..<0.12 -> 11.0
            in 0.12..<0.4 -> 9.5
            in 0.4..<1.2 -> 8.0
            in 1.2..<4.0 -> 6.5
            else -> 5.0
        }
    }

    private fun normalizeBearing(value: Double): Double = ((value % 360.0) + 360.0) % 360.0
}
