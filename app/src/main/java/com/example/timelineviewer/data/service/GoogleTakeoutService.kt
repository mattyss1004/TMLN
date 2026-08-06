package com.example.timelineviewer.data.service

import com.example.timelineviewer.data.model.RoutePoint
import com.example.timelineviewer.data.model.TransportMode
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Data model representing an individual location extracted from Google Takeout export.
 */
data class TakeoutLocation(
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long,
    val accuracyMeters: Float? = null,
    val altitudeMeters: Double? = null,
    val placeName: String? = null,
    val activityType: String? = null,
    val isPlaceVisit: Boolean = false
)

/**
 * Summary result container for parsed Google Takeout location datasets.
 */
data class TakeoutParseSummary(
    val locations: List<TakeoutLocation>,
    val totalPoints: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val detectedActivities: List<String>
)

/**
 * Service to parse JSON exported from Google Takeout (Semantic Location History / Locations.json)
 * and convert it into a structured list of location objects for rendering on the map.
 */
class GoogleTakeoutService {

    /**
     * Parses Google Takeout JSON string content into a [TakeoutParseSummary].
     * Supports both Semantic Location History (timelineObjects) and Raw Location History (locations array).
     */
    fun parseTakeoutJson(jsonString: String): TakeoutParseSummary {
        val locations = mutableListOf<TakeoutLocation>()
        val activitiesSet = mutableSetOf<String>()

        try {
            val element = JsonParser.parseString(jsonString)

            if (element.isJsonObject) {
                val root = element.asJsonObject
                when {
                    root.has("timelineObjects") -> {
                        parseSemanticTimelineObjects(root.getAsJsonArray("timelineObjects"), locations, activitiesSet)
                    }
                    root.has("locations") -> {
                        parseRawLocationsArray(root.getAsJsonArray("locations"), locations, activitiesSet)
                    }
                    root.has("features") -> {
                        parseGeoJsonFeatures(root.getAsJsonArray("features"), locations)
                    }
                }
            } else if (element.isJsonArray) {
                parseGenericCoordinateArray(element.asJsonArray, locations)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sort chronologically
        locations.sortBy { it.timestampMs }

        val startTime = locations.firstOrNull()?.timestampMs ?: 0L
        val endTime = locations.lastOrNull()?.timestampMs ?: 0L

        return TakeoutParseSummary(
            locations = locations,
            totalPoints = locations.size,
            startTimeMs = startTime,
            endTimeMs = endTime,
            detectedActivities = activitiesSet.toList().sorted()
        )
    }

    private fun parseSemanticTimelineObjects(
        timelineObjects: JsonArray,
        locations: MutableList<TakeoutLocation>,
        activitiesSet: MutableSet<String>
    ) {
        for (item in timelineObjects) {
            if (!item.isJsonObject) continue
            val obj = item.asJsonObject

            if (obj.has("activitySegment")) {
                val act = obj.getAsJsonObject("activitySegment")
                val startLoc = act.getAsJsonObject("startLocation")
                val endLoc = act.getAsJsonObject("endLocation")
                val activityType = act.get("activityType")?.asString ?: "UNKNOWN"

                activitiesSet.add(activityType)

                val duration = act.getAsJsonObject("duration")
                val startTs = parseTimestampMs(duration?.get("startTimestampMs")?.asString)
                val endTs = parseTimestampMs(duration?.get("endTimestampMs")?.asString)

                val startLat = extractLatitude(startLoc)
                val startLng = extractLongitude(startLoc)
                if (startLat != 0.0 && startLng != 0.0) {
                    locations.add(
                        TakeoutLocation(
                            latitude = startLat,
                            longitude = startLng,
                            timestampMs = startTs,
                            activityType = activityType
                        )
                    )
                }

                val endLat = extractLatitude(endLoc)
                val endLng = extractLongitude(endLoc)
                if (endLat != 0.0 && endLng != 0.0) {
                    locations.add(
                        TakeoutLocation(
                            latitude = endLat,
                            longitude = endLng,
                            timestampMs = if (endTs > startTs) endTs else startTs + 60000L,
                            activityType = activityType
                        )
                    )
                }

                // Extract intermediate path points if available in Takeout
                if (act.has("waypointPath")) {
                    val waypoints = act.getAsJsonObject("waypointPath").getAsJsonArray("waypoints")
                    waypoints?.forEach { wp ->
                        if (wp.isJsonObject) {
                            val wpObj = wp.asJsonObject
                            val wLat = extractLatitude(wpObj)
                            val wLng = extractLongitude(wpObj)
                            if (wLat != 0.0 && wLng != 0.0) {
                                locations.add(
                                    TakeoutLocation(
                                        latitude = wLat,
                                        longitude = wLng,
                                        timestampMs = (startTs + endTs) / 2,
                                        activityType = activityType
                                    )
                                )
                            }
                        }
                    }
                }

            } else if (obj.has("placeVisit")) {
                val visit = obj.getAsJsonObject("placeVisit")
                val loc = visit.getAsJsonObject("location")
                val lat = extractLatitude(loc)
                val lng = extractLongitude(loc)
                val name = loc.get("name")?.asString ?: loc.get("address")?.asString ?: "Visited Place"
                val ts = parseTimestampMs(visit.getAsJsonObject("duration")?.get("startTimestampMs")?.asString)

                if (lat != 0.0 && lng != 0.0) {
                    locations.add(
                        TakeoutLocation(
                            latitude = lat,
                            longitude = lng,
                            timestampMs = ts,
                            placeName = name,
                            isPlaceVisit = true,
                            activityType = "STATIONARY"
                        )
                    )
                    activitiesSet.add("STATIONARY")
                }
            }
        }
    }

    private fun parseRawLocationsArray(
        locationsArray: JsonArray,
        locations: MutableList<TakeoutLocation>,
        activitiesSet: MutableSet<String>
    ) {
        for (item in locationsArray) {
            if (!item.isJsonObject) continue
            val obj = item.asJsonObject

            val latE7 = obj.get("latitudeE7")?.asLong ?: 0L
            val lngE7 = obj.get("longitudeE7")?.asLong ?: 0L
            val tsMs = parseTimestampMs(obj.get("timestampMs")?.asString)

            if (latE7 != 0L && lngE7 != 0L) {
                val accuracy = obj.get("accuracy")?.asFloat
                val altitude = obj.get("altitude")?.asDouble

                var topActivity: String? = null
                if (obj.has("activity")) {
                    val activityList = obj.getAsJsonArray("activity")
                    if (activityList != null && activityList.size() > 0) {
                        val firstAct = activityList[0].asJsonObject
                        val innerActivities = firstAct.getAsJsonArray("activity")
                        if (innerActivities != null && innerActivities.size() > 0) {
                            topActivity = innerActivities[0].asJsonObject.get("type")?.asString
                            if (topActivity != null) {
                                activitiesSet.add(topActivity)
                            }
                        }
                    }
                }

                locations.add(
                    TakeoutLocation(
                        latitude = latE7 / 1e7,
                        longitude = lngE7 / 1e7,
                        timestampMs = tsMs,
                        accuracyMeters = accuracy,
                        altitudeMeters = altitude,
                        activityType = topActivity
                    )
                )
            }
        }
    }

    private fun parseGeoJsonFeatures(
        features: JsonArray,
        locations: MutableList<TakeoutLocation>
    ) {
        var seq = 0
        val baseTs = System.currentTimeMillis()
        for (f in features) {
            if (!f.isJsonObject) continue
            val geom = f.asJsonObject.getAsJsonObject("geometry") ?: continue
            val coords = geom.getAsJsonArray("coordinates") ?: continue
            if (coords.size() >= 2) {
                val lng = coords[0].asDouble
                val lat = coords[1].asDouble
                locations.add(
                    TakeoutLocation(
                        latitude = lat,
                        longitude = lng,
                        timestampMs = baseTs + (seq * 5000L)
                    )
                )
                seq++
            }
        }
    }

    private fun parseGenericCoordinateArray(
        arr: JsonArray,
        locations: MutableList<TakeoutLocation>
    ) {
        var seq = 0
        val baseTs = System.currentTimeMillis()
        for (e in arr) {
            if (e.isJsonObject) {
                val obj = e.asJsonObject
                val lat = obj.get("lat")?.asDouble ?: obj.get("latitude")?.asDouble ?: 0.0
                val lng = obj.get("lng")?.asDouble ?: obj.get("longitude")?.asDouble ?: 0.0
                if (lat != 0.0 && lng != 0.0) {
                    locations.add(
                        TakeoutLocation(
                            latitude = lat,
                            longitude = lng,
                            timestampMs = baseTs + (seq * 5000L)
                        )
                    )
                    seq++
                }
            }
        }
    }

    private fun extractLatitude(obj: JsonObject?): Double {
        if (obj == null) return 0.0
        if (obj.has("latitudeE7")) return obj.get("latitudeE7").asLong / 1e7
        if (obj.has("latitude")) return obj.get("latitude").asDouble
        return 0.0
    }

    private fun extractLongitude(obj: JsonObject?): Double {
        if (obj == null) return 0.0
        if (obj.has("longitudeE7")) return obj.get("longitudeE7").asLong / 1e7
        if (obj.has("longitude")) return obj.get("longitude").asDouble
        return 0.0
    }

    private fun parseTimestampMs(str: String?): Long {
        if (str.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            str.toLong()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * Converts a list of parsed [TakeoutLocation] objects into domain [RoutePoint] list.
     */
    fun convertToRoutePoints(locations: List<TakeoutLocation>, journeyId: Long = 0L): List<RoutePoint> {
        return locations.mapIndexed { index, loc ->
            RoutePoint(
                journeyId = journeyId,
                latitude = loc.latitude,
                longitude = loc.longitude,
                timestamp = loc.timestampMs,
                speedKmh = 0.0,
                bearing = 0f,
                sequenceOrder = index
            )
        }
    }
}
