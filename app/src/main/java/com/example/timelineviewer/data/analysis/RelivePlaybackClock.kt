package com.example.timelineviewer.data.analysis

import com.example.timelineviewer.data.model.RoutePoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/**
 * Converts real gaps between recorded Timeline points into a short, readable replay cadence.
 * The original chronology is preserved while long real-world journeys remain practical to relive.
 * Includes dynamic corner-easing to automatically adapt replay pace during sharp bends.
 */
object RelivePlaybackClock {
    private const val BASE_COMPRESSION = 500L
    private const val MIN_FRAME_DELAY_MS = 80L
    private const val MAX_FRAME_DELAY_MS = 800L

    fun delayForNextPoint(
        currentTimestamp: Long,
        nextTimestamp: Long,
        playbackSpeed: Float
    ): Long {
        return delayForNextPointWithCorner(currentTimestamp, nextTimestamp, playbackSpeed, 0.0)
    }

    fun delayForNextPointWithCorner(
        currentTimestamp: Long,
        nextTimestamp: Long,
        playbackSpeed: Float,
        cornerAngleDegrees: Double
    ): Long {
        val realGapMs = (nextTimestamp - currentTimestamp).coerceAtLeast(0L)
        
        // Logarithmic scaling for real gaps to prevent jarring jumps while preserving rhythm
        val scaledMs = if (realGapMs <= 1000L) {
            realGapMs.toFloat() / 3f
        } else {
            333f + (ln(realGapMs.toFloat() / 1000f) * 120f)
        }

        // Corner-easing multiplier: slowing down replay speed on sharp turns (up to 1.75x delay at 90+ deg)
        val cornerFactor = 1.0 + (abs(cornerAngleDegrees).coerceAtMost(180.0) / 180.0) * 0.75
        val cornerAdjustedMs = scaledMs * cornerFactor

        val effectiveSpeed = playbackSpeed.coerceIn(0.25f, 8.0f)
        val adjustedDelay = (cornerAdjustedMs / effectiveSpeed).toLong()

        return adjustedDelay.coerceIn(MIN_FRAME_DELAY_MS, MAX_FRAME_DELAY_MS)
    }

    /**
     * Calculates turn angle in degrees between three consecutive route points.
     * Higher values indicate sharper corners.
     */
    fun calculateCornerAngleDegrees(p1: RoutePoint, p2: RoutePoint, p3: RoutePoint): Double {
        val b1 = bearingBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
        val b2 = bearingBetween(p2.latitude, p2.longitude, p3.latitude, p3.longitude)
        var diff = b2 - b1
        while (diff < -180.0) diff += 360.0
        while (diff > 180.0) diff -= 360.0
        return abs(diff)
    }

    private fun bearingBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val l1 = Math.toRadians(lat1)
        val l2 = Math.toRadians(lat2)
        val y = sin(dLon) * cos(l2)
        val x = cos(l1) * sin(l2) - sin(l1) * cos(l2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /**
     * Performs Centripetal Catmull-Rom Spline interpolation across four points p0, p1, p2, p3 at parameter t in [0.0, 1.0].
     */
    fun interpolatePoint(
        p0: RoutePoint,
        p1: RoutePoint,
        p2: RoutePoint,
        p3: RoutePoint,
        t: Float
    ): RoutePoint {
        val clampedT = t.coerceIn(0f, 1f).toDouble()
        val lat = catmullRom(p0.latitude, p1.latitude, p2.latitude, p3.latitude, clampedT)
        val lon = catmullRom(p0.longitude, p1.longitude, p2.longitude, p3.longitude, clampedT)
        val speed = catmullRom(p0.speedKmh.toDouble(), p1.speedKmh.toDouble(), p2.speedKmh.toDouble(), p3.speedKmh.toDouble(), clampedT)
        val timestamp = (p1.timestamp + (p2.timestamp - p1.timestamp) * t).toLong()

        return p1.copy(
            latitude = lat,
            longitude = lon,
            speedKmh = speed.toFloat().coerceAtLeast(0f),
            timestamp = timestamp
        )
    }

    private fun catmullRom(p0: Double, p1: Double, p2: Double, p3: Double, t: Double): Double {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5 * (
            (2.0 * p1) +
            (-p0 + p2) * t +
            (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 +
            (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3
        )
    }
}
