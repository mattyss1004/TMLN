
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
        val activeSpeed = active.speedKmh.toDouble().coerceAtLeast(0.0)

        // Dynamic zoom adjustment based on travel velocity
        val speedZoomOffset = (activeSpeed / 80.0).coerceIn(0.0, 1.8)

        return when (mode) {
            JourneyCameraMode.OVERVIEW -> JourneyCameraPose(
                latitude = points.map { it.latitude }.average(),
                longitude = points.map { it.longitude }.average(),
                zoom = overviewZoom(points),
                pitch = 50.0,
                bearing = 0.0
            )
            JourneyCameraMode.FOLLOW -> JourneyCameraPose(
                latitude = active.latitude,
                longitude = active.longitude,
                zoom = (16.4 - speedZoomOffset).coerceIn(13.5, 17.5),
                pitch = 58.0,
                bearing = normalizeBearing(effectiveBearing)
            )
            JourneyCameraMode.CINEMA -> JourneyCameraPose(
                latitude = active.latitude,
                longitude = active.longitude,
                zoom = (15.5 - speedZoomOffset * 0.8).coerceIn(13.0, 16.8),
                pitch = (70.0 + speedZoomOffset * 3.0).coerceAtMost(78.0),
                bearing = normalizeBearing(effectiveBearing - 18.0)
            )
            JourneyCameraMode.ORBIT -> JourneyCameraPose(
                latitude = active.latitude,
                longitude = active.longitude,
                zoom = (15.6 - speedZoomOffset * 0.5).coerceIn(13.2, 17.0),
                pitch = 64.0,
                bearing = normalizeBearing(effectiveBearing + 65.0 + (safeIndex % 360) * 0.4)
            )
        }
    }

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
