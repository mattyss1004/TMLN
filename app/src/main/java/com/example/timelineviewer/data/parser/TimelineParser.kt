package com.example.timelineviewer.data.parser

import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.data.model.RoutePoint
import com.example.timelineviewer.data.model.Stop
import com.example.timelineviewer.data.model.TransportMode
import com.example.timelineviewer.data.model.TransportSegment
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.math.*

data class ParsedJourneyResult(
    val journey: Journey,
    val points: List<RoutePoint>,
    val stops: List<Stop>,
    val segments: List<TransportSegment>
)

object TimelineParser {

    /**
     * Enhanced parser with dwell-time stop detection and Douglas-Peucker smoothing.
     */
    fun parseTimelineJson(jsonString: String, defaultTitle: String = "Imported Timeline Journey"): ParsedJourneyResult? {
        return try {
            val jsonElement = JsonParser.parseString(jsonString)
            val rawPoints = mutableListOf<RawPoint>()

            // 1. Ingestion Phase
            when {
                jsonElement.isJsonObject -> {
                    val root = jsonElement.asJsonObject
                    when {
                        root.has("timelineObjects") -> parseGoogleTakeoutFormat(root.getAsJsonArray("timelineObjects"), rawPoints)
                        root.has("locations") -> parseGoogleLocationsFormat(root.getAsJsonArray("locations"), rawPoints)
                        root.has("features") -> parseGeoJsonFormat(root.getAsJsonArray("features"), rawPoints)
                    }
                }
                jsonElement.isJsonArray -> parsePointArray(jsonElement.asJsonArray, rawPoints)
            }

            if (rawPoints.isEmpty()) return null

            // 2. Pre-processing: Sort and Clean
            rawPoints.sortBy { it.timestamp }
            val cleanedPoints = removeJitter(rawPoints)

            // 3. Douglas-Peucker Smoothing (Epsilon ~ 5 meters)
            val smoothedPoints = simplifyPoints(cleanedPoints, 0.00005)

            // 4. Analysis Phase
            val routePoints = mutableListOf<RoutePoint>()
            val stops = mutableListOf<Stop>()
            val segments = mutableListOf<TransportSegment>()

            var totalDistKm = 0.0
            var maxSpeed = 0.0
            var currentSegmentStart = 0
            var currentMode = TransportMode.UNKNOWN
            val modeDurationMap = mutableMapOf<TransportMode, Long>()

            for (i in smoothedPoints.indices) {
                val pt = smoothedPoints[i]
                var speed = 0.0
                var distFromPrev = 0.0

                if (i > 0) {
                    val prev = smoothedPoints[i - 1]
                    distFromPrev = calculateDistanceKm(prev.lat, prev.lng, pt.lat, pt.lng)
                    totalDistKm += distFromPrev

                    val timeDiffSec = ((pt.timestamp - prev.timestamp) / 1000.0).coerceAtLeast(0.1)
                    speed = (distFromPrev / timeDiffSec) * 3600.0 // km/h
                    if (speed > maxSpeed && speed < 300.0) maxSpeed = speed // Ignore supersonic GPS spikes
                }

                val detectedMode = detectTransportMode(speed, pt.modeOverride)

                // Track dominant mode duration
                if (i > 0) {
                    val duration = (pt.timestamp - smoothedPoints[i-1].timestamp) / 1000L
                    modeDurationMap[detectedMode] = (modeDurationMap[detectedMode] ?: 0L) + duration
                }

                // Detect segment change
                if (i > 0 && (detectedMode != currentMode || i == smoothedPoints.size - 1)) {
                    val segmentDist = calculateDistanceKm(
                        smoothedPoints[currentSegmentStart].lat,
                        smoothedPoints[currentSegmentStart].lng,
                        pt.lat,
                        pt.lng
                    )
                    val segmentDuration = ((pt.timestamp - smoothedPoints[currentSegmentStart].timestamp) / 1000L).coerceAtLeast(1L)

                    segments.add(
                        TransportSegment(
                            journeyId = 0L,
                            startIndex = currentSegmentStart,
                            endIndex = i,
                            mode = currentMode,
                            distanceKm = (segmentDist * 100).roundToInt() / 100.0,
                            durationSeconds = segmentDuration,
                            averageSpeedKmh = if (segmentDuration > 0) (segmentDist / (segmentDuration / 3600.0)) else 0.0
                        )
                    )
                    currentSegmentStart = i
                    currentMode = detectedMode
                } else if (i == 0) {
                    currentMode = detectedMode
                }

                val bearing = if (i > 0) {
                    calculateBearing(smoothedPoints[i - 1].lat, smoothedPoints[i - 1].lng, pt.lat, pt.lng)
                } else 0f

                routePoints.add(
                    RoutePoint(
                        journeyId = 0L,
                        latitude = pt.lat,
                        longitude = pt.lng,
                        timestamp = pt.timestamp,
                        speedKmh = speed,
                        bearing = bearing,
                        sequenceOrder = i
                    )
                )
            }

            // 5. Dwell-Time Stop Detection
            detectDwellStops(smoothedPoints, stops)

            // 6. Final Metadata Assembly
            val startTime = smoothedPoints.first().timestamp
            val endTime = smoothedPoints.last().timestamp
            val durationSec = ((endTime - startTime) / 1000L).coerceAtLeast(1L)
            val dominantMode = modeDurationMap.maxByOrNull { it.value }?.key ?: TransportMode.UNKNOWN
            val highlightStop = stops.maxByOrNull { it.durationSeconds }?.name

            val journey = Journey(
                title = defaultTitle,
                description = "Cinematic route through ${stops.size} key locations via ${dominantMode.label.lowercase()}.",
                startTime = startTime,
                endTime = endTime,
                totalDistanceKm = (totalDistKm * 100).roundToInt() / 100.0,
                totalDurationSeconds = durationSec,
                pointCount = routePoints.size,
                stopCount = stops.size,
                maxSpeedKmh = (maxSpeed * 10).roundToInt() / 10.0,
                averageSpeedKmh = if (durationSec > 0) ((totalDistKm / (durationSec / 3600.0)) * 10).roundToInt() / 10.0 else 0.0,
                dominantMode = dominantMode,
                highlightPlaceName = highlightStop
            )

            ParsedJourneyResult(journey, routePoints, stops, segments)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private data class RawPoint(
        val lat: Double,
        val lng: Double,
        val timestamp: Long,
        val isPlaceVisit: Boolean = false,
        val placeName: String? = null,
        val modeOverride: TransportMode? = null
    )

    private fun removeJitter(points: List<RawPoint>): List<RawPoint> {
        if (points.size < 3) return points
        val result = mutableListOf<RawPoint>()
        result.add(points.first())

        for (i in 1 until points.size - 1) {
            val prev = points[i - 1]
            val curr = points[i]
            val next = points[i + 1]

            val distPrev = calculateDistanceKm(prev.lat, prev.lng, curr.lat, curr.lng)
            val distNext = calculateDistanceKm(curr.lat, curr.lng, next.lat, next.lng)

            // If it's a sudden spike (> 500m) that returns immediately, filter it
            if (distPrev > 0.5 && distNext > 0.5 && calculateDistanceKm(prev.lat, prev.lng, next.lat, next.lng) < 0.1) {
                continue
            }
            result.add(curr)
        }
        result.add(points.last())
        return result
    }

    private fun simplifyPoints(points: List<RawPoint>, epsilon: Double): List<RawPoint> {
        if (points.size < 3) return points

        var dmax = 0.0
        var index = 0
        val end = points.size - 1

        for (i in 1 until end) {
            val d = perpendicularDistance(points[i], points[0], points[end])
            if (d > dmax) {
                index = i
                dmax = d
            }
        }

        return if (dmax > epsilon) {
            val recResults1 = simplifyPoints(points.subList(0, index + 1), epsilon)
            val recResults2 = simplifyPoints(points.subList(index, points.size), epsilon)
            recResults1.dropLast(1) + recResults2
        } else {
            listOf(points.first(), points.last())
        }
    }

    private fun perpendicularDistance(pt: RawPoint, lineStart: RawPoint, lineEnd: RawPoint): Double {
        val dx = lineEnd.lng - lineStart.lng
        val dy = lineEnd.lat - lineStart.lat

        val mag = sqrt(dx * dx + dy * dy)
        if (mag == 0.0) return calculateDistanceKm(pt.lat, pt.lng, lineStart.lat, lineStart.lng)

        val u = ((pt.lng - lineStart.lng) * dx + (pt.lat - lineStart.lat) * dy) / (mag * mag)
        val pLat: Double
        val pLng: Double

        if (u < 0) {
            pLat = lineStart.lat
            pLng = lineStart.lng
        } else if (u > 1) {
            pLat = lineEnd.lat
            pLng = lineEnd.lng
        } else {
            pLat = lineStart.lat + u * dy
            pLng = lineStart.lng + u * dx
        }

        return sqrt((pt.lat - pLat).pow(2) + (pt.lng - pLng).pow(2))
    }

    private fun detectDwellStops(points: List<RawPoint>, stops: MutableList<Stop>) {
        var i = 0
        val minDwellMs = 180000L // 3 minutes

        while (i < points.size) {
            var j = i + 1
            var clusterLat = points[i].lat
            var clusterLng = points[i].lng
            var count = 1

            while (j < points.size) {
                val dist = calculateDistanceKm(points[i].lat, points[i].lng, points[j].lat, points[j].lng)
                if (dist < 0.05) { // 50 meters radius
                    clusterLat += points[j].lat
                    clusterLng += points[j].lng
                    count++
                    j++
                } else {
                    break
                }
            }

            val duration = points[j - 1].timestamp - points[i].timestamp
            if (duration >= minDwellMs || points[i].isPlaceVisit) {
                val avgLat = clusterLat / count
                val avgLng = clusterLng / count
                val name = points[i].placeName ?: "Spot #${stops.size + 1}"

                val score = when {
                    duration > 3600000L -> 90 // 1 hour+
                    duration > 1800000L -> 70 // 30 mins+
                    points[i].isPlaceVisit -> 85
                    else -> 40
                }

                stops.add(Stop(
                    journeyId = 0L,
                    latitude = avgLat,
                    longitude = avgLng,
                    name = name,
                    startTime = points[i].timestamp,
                    endTime = points[j - 1].timestamp,
                    durationSeconds = duration / 1000L,
                    sequenceOrder = stops.size,
                    importanceScore = score,
                    category = if (score > 80) "Highlight" else "Waypoint"
                ))
                i = j
            } else {
                i++
            }
        }
    }

    private fun parseGoogleTakeoutFormat(timelineObjects: JsonArray, points: MutableList<RawPoint>) {
        for (element in timelineObjects) {
            val obj = element.asJsonObject
            if (obj.has("activitySegment")) {
                val act = obj.getAsJsonObject("activitySegment")
                val startLoc = act.getAsJsonObject("startLocation")
                val endLoc = act.getAsJsonObject("endLocation")
                val activityType = act.get("activityType")?.asString

                val mode = when (activityType?.uppercase()) {
                    "WALKING", "ON_FOOT" -> TransportMode.WALKING
                    "CYCLING" -> TransportMode.CYCLING
                    "IN_PASSENGER_VEHICLE", "DRIVING" -> TransportMode.DRIVING
                    "IN_BUS", "IN_TRAIN", "SUBWAY" -> TransportMode.TRANSIT
                    else -> null
                }

                val startLat = extractLat(startLoc)
                val startLng = extractLng(startLoc)
                val endLat = extractLat(endLoc)
                val endLng = extractLng(endLoc)

                val startTs = parseTimestamp(act.getAsJsonObject("duration")?.get("startTimestampMs")?.asString)
                val endTs = parseTimestamp(act.getAsJsonObject("duration")?.get("endTimestampMs")?.asString)

                if (startLat != 0.0 && startLng != 0.0) {
                    points.add(RawPoint(startLat, startLng, startTs, modeOverride = mode))
                }
                if (endLat != 0.0 && endLng != 0.0) {
                    points.add(RawPoint(endLat, endLng, endTs, modeOverride = mode))
                }
            } else if (obj.has("placeVisit")) {
                val visit = obj.getAsJsonObject("placeVisit")
                val loc = visit.getAsJsonObject("location")
                val lat = extractLat(loc)
                val lng = extractLng(loc)
                val name = loc.get("name")?.asString ?: loc.get("address")?.asString
                val durationObj = visit.getAsJsonObject("duration")
                val startTs = parseTimestamp(durationObj?.get("startTimestampMs")?.asString)
                val endTs = parseTimestamp(durationObj?.get("endTimestampMs")?.asString)

                if (lat != 0.0 && lng != 0.0) {
                    points.add(RawPoint(lat, lng, startTs, isPlaceVisit = true, placeName = name))
                    points.add(RawPoint(lat, lng, endTs, isPlaceVisit = true, placeName = name))
                }
            }
        }
    }

    private fun parseGoogleLocationsFormat(locations: JsonArray, points: MutableList<RawPoint>) {
        for (element in locations) {
            val obj = element.asJsonObject
            val latE7 = obj.get("latitudeE7")?.asLong ?: 0L
            val lngE7 = obj.get("longitudeE7")?.asLong ?: 0L
            val tsMs = obj.get("timestampMs")?.asLong ?: System.currentTimeMillis()

            if (latE7 != 0L && lngE7 != 0L) {
                points.add(RawPoint(latE7 / 1e7, lngE7 / 1e7, tsMs))
            }
        }
    }

    private fun parseGeoJsonFormat(features: JsonArray, points: MutableList<RawPoint>) {
        var seq = 0
        val baseTs = System.currentTimeMillis()
        for (f in features) {
            val geom = f.asJsonObject.getAsJsonObject("geometry") ?: continue
            val coords = geom.getAsJsonArray("coordinates") ?: continue
            if (coords.size() >= 2) {
                val lng = coords[0].asDouble
                val lat = coords[1].asDouble
                points.add(RawPoint(lat, lng, baseTs + (seq * 10000L)))
                seq++
            }
        }
    }

    private fun parsePointArray(arr: JsonArray, points: MutableList<RawPoint>) {
        var seq = 0
        val baseTs = System.currentTimeMillis()
        for (e in arr) {
            if (e.isJsonObject) {
                val obj = e.asJsonObject
                val lat = obj.get("lat")?.asDouble ?: obj.get("latitude")?.asDouble ?: 0.0
                val lng = obj.get("lng")?.asDouble ?: obj.get("longitude")?.asDouble ?: 0.0
                if (lat != 0.0 && lng != 0.0) {
                    points.add(RawPoint(lat, lng, baseTs + (seq * 10000L)))
                    seq++
                }
            }
        }
    }

    private fun extractLat(obj: JsonObject?): Double {
        if (obj == null) return 0.0
        if (obj.has("latitudeE7")) return obj.get("latitudeE7").asLong / 1e7
        if (obj.has("latitude")) return obj.get("latitude").asDouble
        return 0.0
    }

    private fun extractLng(obj: JsonObject?): Double {
        if (obj == null) return 0.0
        if (obj.has("longitudeE7")) return obj.get("longitudeE7").asLong / 1e7
        if (obj.has("longitude")) return obj.get("longitude").asDouble
        return 0.0
    }

    private fun parseTimestamp(str: String?): Long {
        if (str == null) return System.currentTimeMillis()
        return try {
            str.toLong()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun detectTransportMode(speedKmh: Double, override: TransportMode?): TransportMode {
        if (override != null) return override
        return when {
            speedKmh <= 0.5 -> TransportMode.UNKNOWN
            speedKmh < 7.0 -> TransportMode.WALKING
            speedKmh < 30.0 -> TransportMode.CYCLING
            speedKmh < 65.0 -> TransportMode.TRANSIT
            else -> TransportMode.DRIVING
        }
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
