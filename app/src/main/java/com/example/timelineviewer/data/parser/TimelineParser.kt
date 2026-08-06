package com.example.timelineviewer.data.parser

import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.data.model.RoutePoint
import com.example.timelineviewer.data.model.Stop
import com.example.timelineviewer.data.model.TransportMode
import com.example.timelineviewer.data.model.TransportSegment
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class ParsedJourneyResult(
    val journey: Journey,
    val points: List<RoutePoint>,
    val stops: List<Stop>,
    val segments: List<TransportSegment>
)

object TimelineParser {

    fun parseTimelineJson(jsonString: String, defaultTitle: String = "Imported Timeline Journey"): ParsedJourneyResult? {
        return try {
            val jsonElement = JsonParser.parseString(jsonString)
            val rawPoints = mutableListOf<RawPoint>()

            if (jsonElement.isJsonObject) {
                val root = jsonElement.asJsonObject
                if (root.has("timelineObjects")) {
                    parseGoogleTakeoutFormat(root.getAsJsonArray("timelineObjects"), rawPoints)
                } else if (root.has("locations")) {
                    parseGoogleLocationsFormat(root.getAsJsonArray("locations"), rawPoints)
                } else if (root.has("features")) {
                    parseGeoJsonFormat(root.getAsJsonArray("features"), rawPoints)
                }
            } else if (jsonElement.isJsonArray) {
                parsePointArray(jsonElement.asJsonArray, rawPoints)
            }

            if (rawPoints.isEmpty()) return null

            // Sort points by timestamp
            rawPoints.sortBy { it.timestamp }

            val startTime = rawPoints.first().timestamp
            val endTime = rawPoints.last().timestamp
            val durationSec = ((endTime - startTime) / 1000L).coerceAtLeast(10L)

            val routePoints = mutableListOf<RoutePoint>()
            val stops = mutableListOf<Stop>()
            val segments = mutableListOf<TransportSegment>()

            var totalDistKm = 0.0
            var currentSegmentStart = 0
            var currentMode = TransportMode.UNKNOWN

            for (i in 0 until rawPoints.size) {
                val pt = rawPoints[i]
                var speed = 0.0
                var distFromPrev = 0.0

                if (i > 0) {
                    val prev = rawPoints[i - 1]
                    distFromPrev = calculateDistanceKm(prev.lat, prev.lng, pt.lat, pt.lng)
                    totalDistKm += distFromPrev

                    val timeDiffSec = ((pt.timestamp - prev.timestamp) / 1000.0).coerceAtLeast(0.1)
                    speed = (distFromPrev / timeDiffSec) * 3600.0 // km/h
                }

                val detectedMode = detectTransportMode(speed, pt.modeOverride)

                // Detect segment change
                if (i > 0 && (detectedMode != currentMode || i == rawPoints.size - 1)) {
                    val segmentDist = calculateDistanceKm(
                        rawPoints[currentSegmentStart].lat,
                        rawPoints[currentSegmentStart].lng,
                        pt.lat,
                        pt.lng
                    )
                    val segmentDuration = ((pt.timestamp - rawPoints[currentSegmentStart].timestamp) / 1000L).coerceAtLeast(1L)

                    segments.add(
                        TransportSegment(
                            journeyId = 0L,
                            startIndex = currentSegmentStart,
                            endIndex = i,
                            mode = currentMode,
                            distanceKm = (segmentDist * 100).toInt() / 100.0,
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
                    calculateBearing(rawPoints[i - 1].lat, rawPoints[i - 1].lng, pt.lat, pt.lng)
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

                // Detect stop if velocity is zero/low or marked as place visit
                if (pt.isPlaceVisit || (i > 0 && distFromPrev < 0.02 && speed < 1.0 && i % 10 == 0)) {
                    stops.add(
                        Stop(
                            journeyId = 0L,
                            latitude = pt.lat,
                            longitude = pt.lng,
                            name = pt.placeName ?: "Waypoint Stop #${stops.size + 1}",
                            startTime = pt.timestamp - 300000L,
                            endTime = pt.timestamp,
                            durationSeconds = 300L,
                            sequenceOrder = stops.size
                        )
                    )
                }
            }

            val journey = Journey(
                title = defaultTitle,
                description = "Parsed timeline containing ${routePoints.size} GPS track points",
                startTime = startTime,
                endTime = endTime,
                totalDistanceKm = (totalDistKm * 100).toInt() / 100.0,
                totalDurationSeconds = durationSec,
                pointCount = routePoints.size,
                stopCount = stops.size
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
                val ts = parseTimestamp(visit.getAsJsonObject("duration")?.get("startTimestampMs")?.asString)

                if (lat != 0.0 && lng != 0.0) {
                    points.add(RawPoint(lat, lng, ts, isPlaceVisit = true, placeName = name))
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
            speedKmh <= 0.0 -> TransportMode.UNKNOWN
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
