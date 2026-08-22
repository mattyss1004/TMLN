package com.example.timelineviewer.data.analysis

import com.example.timelineviewer.data.model.RoutePoint

object RelivePlaybackClock {

    fun catmullRom(p0: Double, p1: Double, p2: Double, p3: Double, t: Double): Double {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5 * (
            (2.0 * p1) +
            (-p0 + p2) * t +
            (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 +
            (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3
        )
    }

    fun catmullRom(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        return catmullRom(p0.toDouble(), p1.toDouble(), p2.toDouble(), p3.toDouble(), t.toDouble()).toFloat()
    }

    fun interpolate(
        p0: RoutePoint,
        p1: RoutePoint,
        p2: RoutePoint,
        p3: RoutePoint,
        t: Float
    ): RoutePoint {
        val clampedT = t.coerceIn(0f, 1f)
        val clampedTDouble = clampedT.toDouble()

        val lat = catmullRom(p0.latitude, p1.latitude, p2.latitude, p3.latitude, clampedTDouble)
        val lon = catmullRom(p0.longitude, p1.longitude, p2.longitude, p3.longitude, clampedTDouble)
        val speed = catmullRom(
            p0.speedKmh.toDouble(),
            p1.speedKmh.toDouble(),
            p2.speedKmh.toDouble(),
            p3.speedKmh.toDouble(),
            clampedTDouble
        ).toFloat().coerceAtLeast(0f)

        val timeDelta = (p2.timestamp - p1.timestamp).toDouble()
        val interpolatedTime = p1.timestamp + (timeDelta * clampedTDouble).toLong()

        return p1.copy(
            latitude = lat,
            longitude = lon,
            speedKmh = speed,
            timestamp = interpolatedTime
        )
    }

    fun delayForNextPoint(
        currentPoint: RoutePoint?,
        nextPoint: RoutePoint?,
        speedMultiplier: Float = 1.0f,
        minDelayMs: Long = 16L,
        maxDelayMs: Long = 2000L
    ): Long {
        if (currentPoint == null || nextPoint == null) return minDelayMs
        val rawDelta = nextPoint.timestamp - currentPoint.timestamp
        val validDelta = if (rawDelta in 1..600_000) rawDelta else 1000L
        val adjusted = (validDelta.toDouble() / speedMultiplier.toDouble().coerceAtLeast(0.1)).toLong()
        return adjusted.coerceIn(minDelayMs, maxDelayMs)
    }

    fun delayForNextPoint(
        currentTimestamp: Long,
        nextTimestamp: Long,
        speedMultiplier: Float = 1.0f,
        minDelayMs: Long = 16L,
        maxDelayMs: Long = 2000L
    ): Long {
        val rawDelta = nextTimestamp - currentTimestamp
        val validDelta = if (rawDelta in 1..600_000) rawDelta else 1000L
        val adjusted = (validDelta.toDouble() / speedMultiplier.toDouble().coerceAtLeast(0.1)).toLong()
        return adjusted.coerceIn(minDelayMs, maxDelayMs)
    }
}
