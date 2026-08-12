package com.example.timelineviewer.data.parser

import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.data.model.RoutePoint
import com.example.timelineviewer.data.model.Stop
import com.example.timelineviewer.data.model.TransportMode
import com.example.timelineviewer.data.model.TransportSegment
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.Reader
import java.io.StringReader
import java.time.Instant
import java.util.ArrayDeque
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class ParsedJourneyResult(
    val journey: Journey,
    val points: List<RoutePoint>,
    val stops: List<Stop>,
    val segments: List<TransportSegment>
)

/**
 * Canonical Google Timeline / GeoJSON import path. The raw JSON is read token by token instead of
 * being expanded into a full DOM tree, which keeps large Takeout imports substantially safer for
 * device memory. All import formats are normalised into [RawPoint] before route analysis.
 */
object TimelineParser {

    private const val MAX_RAW_POINTS = 250_000
    private const val DEFAULT_POINT_INTERVAL_MS = 10_000L
    private const val JITTER_SPIKE_KM = 0.5
    private const val JITTER_RETURN_KM = 0.1
    private const val SIMPLIFICATION_TOLERANCE_KM = 0.007
    private const val STOP_RADIUS_KM = 0.06
    private const val MIN_DWELL_MS = 180_000L

    fun parseTimelineJson(
        jsonString: String,
        defaultTitle: String = "Imported Timeline Journey"
    ): ParsedJourneyResult? = parseTimeline(StringReader(jsonString), defaultTitle)

    fun parseTimeline(reader: Reader, defaultTitle: String = "Imported Timeline Journey"): ParsedJourneyResult? {
        return try {
            val rawPoints = mutableListOf<RawPoint>()
            JsonReader(reader).use { jsonReader ->
                jsonReader.isLenient = true
                when (jsonReader.peek()) {
                    JsonToken.BEGIN_OBJECT -> parseRootObject(jsonReader, rawPoints)
                    JsonToken.BEGIN_ARRAY -> parseGenericPointArray(jsonReader, rawPoints)
                    else -> jsonReader.skipValue()
                }
            }

            val chronological = normaliseAndSort(rawPoints)
            if (chronological.isEmpty()) return null

            val cleaned = removeJitter(chronological)
            val stops = mutableListOf<Stop>()
            detectDwellStops(cleaned, stops)
            val displayPoints = simplifyPointsPreservingVisits(cleaned, SIMPLIFICATION_TOLERANCE_KM)

            buildJourneyResult(
                points = displayPoints,
                stops = stops,
                defaultTitle = defaultTitle
            )
        } catch (exception: Exception) {
            exception.printStackTrace()
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

    private data class LocationData(
        val lat: Double = 0.0,
        val lng: Double = 0.0,
        val name: String? = null
    )

    private data class DurationData(
        val start: Long = 0L,
        val end: Long = 0L
    )

    private fun parseRootObject(reader: JsonReader, output: MutableList<RawPoint>) {
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "timelineObjects" -> parseTimelineObjects(reader, output)
                "locations" -> parseRawLocations(reader, output)
                "features" -> parseGeoJsonFeatures(reader, output)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun parseTimelineObjects(reader: JsonReader, output: MutableList<RawPoint>) {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue()
            return
        }
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue()
                continue
            }
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "activitySegment" -> parseActivitySegment(reader, output)
                    "placeVisit" -> parsePlaceVisit(reader, output)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        reader.endArray()
    }

    private fun parseActivitySegment(reader: JsonReader, output: MutableList<RawPoint>) {
        var startLocation = LocationData()
        var endLocation = LocationData()
        var duration = DurationData()
        var activityType: String? = null
        val waypoints = mutableListOf<LocationData>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "startLocation" -> startLocation = readLocation(reader)
                "endLocation" -> endLocation = readLocation(reader)
                "duration" -> duration = readDuration(reader)
                "activityType" -> activityType = reader.nextNullableString()
                "waypointPath" -> readWaypointPath(reader, waypoints)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val mode = transportModeFor(activityType)
        val startTime = duration.start
        val endTime = duration.end.coerceAtLeast(startTime)
        if (startLocation.isValid()) {
            appendPoint(output, RawPoint(startLocation.lat, startLocation.lng, startTime, modeOverride = mode))
        }
        waypoints.forEachIndexed { index, waypoint ->
            if (waypoint.isValid()) {
                val fraction = (index + 1).toDouble() / (waypoints.size + 1).toDouble()
                val timestamp = startTime + ((endTime - startTime) * fraction).toLong()
                appendPoint(output, RawPoint(waypoint.lat, waypoint.lng, timestamp, modeOverride = mode))
            }
        }
        if (endLocation.isValid()) {
            appendPoint(output, RawPoint(endLocation.lat, endLocation.lng, endTime, modeOverride = mode))
        }
    }

    private fun parsePlaceVisit(reader: JsonReader, output: MutableList<RawPoint>) {
        var location = LocationData()
        var duration = DurationData()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "location" -> location = readLocation(reader)
                "duration" -> duration = readDuration(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (location.isValid()) {
            val name = location.name ?: "Visited place"
            appendPoint(output, RawPoint(location.lat, location.lng, duration.start, true, name))
            appendPoint(
                output,
                RawPoint(
                    location.lat,
                    location.lng,
                    duration.end.coerceAtLeast(duration.start + 1L),
                    true,
                    name
                )
            )
        }
    }

    private fun parseRawLocations(reader: JsonReader, output: MutableList<RawPoint>) {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue()
            return
        }
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue()
                continue
            }
            var lat = 0.0
            var lng = 0.0
            var timestamp = 0L
            var mode: TransportMode? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "latitudeE7" -> lat = reader.nextDoubleOrNull()?.div(1e7) ?: lat
                    "longitudeE7" -> lng = reader.nextDoubleOrNull()?.div(1e7) ?: lng
                    "latitude" -> lat = reader.nextDoubleOrNull() ?: lat
                    "longitude" -> lng = reader.nextDoubleOrNull() ?: lng
                    "timestampMs", "timestamp" -> timestamp = parseTimestamp(reader.nextNullableString())
                    "activity" -> mode = readActivityMode(reader) ?: mode
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            if (lat != 0.0 && lng != 0.0) {
                appendPoint(output, RawPoint(lat, lng, timestamp, modeOverride = mode))
            }
        }
        reader.endArray()
    }

    private fun parseGeoJsonFeatures(reader: JsonReader, output: MutableList<RawPoint>) {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue()
            return
        }
        var sequence = output.size
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue()
                continue
            }
            val coordinates = mutableListOf<LocationData>()
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "geometry" -> readGeoJsonGeometry(reader, coordinates)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            coordinates.forEach { location ->
                appendPoint(
                    output,
                    RawPoint(
                        location.lat,
                        location.lng,
                        System.currentTimeMillis() + (sequence++ * DEFAULT_POINT_INTERVAL_MS)
                    )
                )
            }
        }
        reader.endArray()
    }

    private fun parseGenericPointArray(reader: JsonReader, output: MutableList<RawPoint>) {
        var sequence = output.size
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue()
                continue
            }
            var lat = 0.0
            var lng = 0.0
            var timestamp = 0L
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "lat", "latitude" -> lat = reader.nextDoubleOrNull() ?: lat
                    "lng", "longitude" -> lng = reader.nextDoubleOrNull() ?: lng
                    "timestamp", "timestampMs", "time" -> timestamp = parseTimestamp(reader.nextNullableString())
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (lat != 0.0 && lng != 0.0) {
                appendPoint(
                    output,
                    RawPoint(
                        lat,
                        lng,
                        timestamp.takeIf { it > 0 } ?: System.currentTimeMillis() + (sequence++ * DEFAULT_POINT_INTERVAL_MS)
                    )
                )
            }
        }
        reader.endArray()
    }

    private fun readLocation(reader: JsonReader): LocationData {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return LocationData()
        }
        var lat = 0.0
        var lng = 0.0
        var name: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "latitudeE7" -> lat = reader.nextDoubleOrNull()?.div(1e7) ?: lat
                "longitudeE7" -> lng = reader.nextDoubleOrNull()?.div(1e7) ?: lng
                "latitude" -> lat = reader.nextDoubleOrNull() ?: lat
                "longitude" -> lng = reader.nextDoubleOrNull() ?: lng
                "name" -> name = reader.nextNullableString()
                "address" -> if (name == null) name = reader.nextNullableString() else reader.skipValue()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return LocationData(lat, lng, name)
    }

    private fun readDuration(reader: JsonReader): DurationData {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return DurationData()
        }
        var start = 0L
        var end = 0L
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "startTimestampMs", "startTimestamp" -> start = parseTimestamp(reader.nextNullableString())
                "endTimestampMs", "endTimestamp" -> end = parseTimestamp(reader.nextNullableString())
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return DurationData(start, end)
    }

    private fun readWaypointPath(reader: JsonReader, output: MutableList<LocationData>) {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return
        }
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "waypoints" -> {
                    if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                        reader.skipValue()
                    } else {
                        reader.beginArray()
                        while (reader.hasNext()) output += readLocation(reader)
                        reader.endArray()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun readActivityMode(reader: JsonReader): TransportMode? {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue()
            return null
        }
        var result: TransportMode? = null
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue()
                continue
            }
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "activity" -> {
                        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                            reader.skipValue()
                        } else {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        when (reader.nextName()) {
                                            "type" -> result = transportModeFor(reader.nextNullableString()) ?: result
                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                } else reader.skipValue()
                            }
                            reader.endArray()
                        }
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        reader.endArray()
        return result
    }

    private fun readGeoJsonGeometry(reader: JsonReader, output: MutableList<LocationData>) {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return
        }
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "coordinates" -> output += readCoordinateTree(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun readCoordinateTree(reader: JsonReader): List<LocationData> {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue()
            return emptyList()
        }
        val output = mutableListOf<LocationData>()
        reader.beginArray()
        if (!reader.hasNext()) {
            reader.endArray()
            return output
        }

        if (reader.peek() == JsonToken.NUMBER) {
            val lng = reader.nextDouble()
            val lat = if (reader.hasNext()) reader.nextDouble() else 0.0
            while (reader.hasNext()) reader.skipValue()
            reader.endArray()
            if (lat != 0.0 && lng != 0.0) output += LocationData(lat, lng)
            return output
        }

        while (reader.hasNext()) output += readCoordinateTree(reader)
        reader.endArray()
        return output
    }

    private fun appendPoint(output: MutableList<RawPoint>, point: RawPoint) {
        if (output.size >= MAX_RAW_POINTS) {
            throw IllegalArgumentException("This import exceeds the 250,000 point safety limit. Import a smaller time range.")
        }
        output += point
    }

    private fun normaliseAndSort(points: List<RawPoint>): List<RawPoint> {
        val baseTime = System.currentTimeMillis()
        return points
            .asSequence()
            .filter { it.lat in -90.0..90.0 && it.lng in -180.0..180.0 && !(it.lat == 0.0 && it.lng == 0.0) }
            .mapIndexed { index, point ->
                point.copy(timestamp = point.timestamp.takeIf { it > 0 } ?: baseTime + (index * DEFAULT_POINT_INTERVAL_MS))
            }
            .sortedBy { it.timestamp }
            .toList()
    }

    private fun removeJitter(points: List<RawPoint>): List<RawPoint> {
        if (points.size < 3) return points
        val cleaned = mutableListOf(points.first())
        for (index in 1 until points.lastIndex) {
            val previous = points[index - 1]
            val current = points[index]
            val next = points[index + 1]
            val outAndBack = calculateDistanceKm(previous.lat, previous.lng, next.lat, next.lng)
            val distanceIn = calculateDistanceKm(previous.lat, previous.lng, current.lat, current.lng)
            val distanceOut = calculateDistanceKm(current.lat, current.lng, next.lat, next.lng)
            if (!current.isPlaceVisit && distanceIn > JITTER_SPIKE_KM && distanceOut > JITTER_SPIKE_KM && outAndBack < JITTER_RETURN_KM) {
                continue
            }
            cleaned += current
        }
        cleaned += points.last()
        return cleaned
    }

    private fun simplifyPointsPreservingVisits(points: List<RawPoint>, toleranceKm: Double): List<RawPoint> {
        if (points.size < 3) return points
        val anchorIndexes = buildList {
            add(0)
            points.forEachIndexed { index, point -> if (point.isPlaceVisit && index in 1 until points.lastIndex) add(index) }
            add(points.lastIndex)
        }.distinct().sorted()

        val keptIndexes = linkedSetOf<Int>()
        anchorIndexes.zipWithNext().forEach { (start, end) ->
            simplifyRange(points, start, end, toleranceKm, keptIndexes)
        }
        return keptIndexes.sorted().map { points[it] }
    }

    private fun simplifyRange(
        points: List<RawPoint>,
        start: Int,
        end: Int,
        toleranceKm: Double,
        keptIndexes: MutableSet<Int>
    ) {
        keptIndexes += start
        keptIndexes += end
        if (end - start < 2) return

        val ranges = ArrayDeque<Pair<Int, Int>>()
        ranges.add(start to end)
        while (ranges.isNotEmpty()) {
            val (rangeStart, rangeEnd) = ranges.removeLast()
            var largestDistance = 0.0
            var furthestIndex = -1
            for (index in rangeStart + 1 until rangeEnd) {
                val distance = perpendicularDistanceKm(points[index], points[rangeStart], points[rangeEnd])
                if (distance > largestDistance) {
                    largestDistance = distance
                    furthestIndex = index
                }
            }
            if (furthestIndex >= 0 && largestDistance > toleranceKm) {
                keptIndexes += furthestIndex
                ranges.add(rangeStart to furthestIndex)
                ranges.add(furthestIndex to rangeEnd)
            }
        }
    }

    private fun detectDwellStops(points: List<RawPoint>, stops: MutableList<Stop>) {
        var startIndex = 0
        while (startIndex < points.size) {
            val anchor = points[startIndex]
            var endIndex = startIndex + 1
            var latitudeSum = anchor.lat
            var longitudeSum = anchor.lng
            var count = 1

            while (endIndex < points.size && calculateDistanceKm(anchor.lat, anchor.lng, points[endIndex].lat, points[endIndex].lng) <= STOP_RADIUS_KM) {
                latitudeSum += points[endIndex].lat
                longitudeSum += points[endIndex].lng
                count++
                endIndex++
            }

            val cluster = points.subList(startIndex, endIndex)
            val durationMs = cluster.last().timestamp - cluster.first().timestamp
            val namedVisit = cluster.firstOrNull { it.isPlaceVisit }
            if (durationMs >= MIN_DWELL_MS || namedVisit != null) {
                val importance = when {
                    durationMs >= 3_600_000L -> 90
                    durationMs >= 1_800_000L -> 70
                    namedVisit != null -> 85
                    else -> 45
                }
                stops += Stop(
                    journeyId = 0L,
                    latitude = latitudeSum / count,
                    longitude = longitudeSum / count,
                    name = namedVisit?.placeName ?: "Stop ${stops.size + 1}",
                    startTime = cluster.first().timestamp,
                    endTime = cluster.last().timestamp,
                    durationSeconds = (durationMs / 1000L).coerceAtLeast(1L),
                    sequenceOrder = stops.size,
                    importanceScore = importance,
                    category = if (importance >= 80) "Highlight" else "Waypoint"
                )
            }
            startIndex = max(startIndex + 1, endIndex)
        }
    }

    private fun buildJourneyResult(
        points: List<RawPoint>,
        stops: List<Stop>,
        defaultTitle: String
    ): ParsedJourneyResult {
        val routePoints = mutableListOf<RoutePoint>()
        val segments = mutableListOf<TransportSegment>()
        val modeDurations = mutableMapOf<TransportMode, Long>()
        var totalDistance = 0.0
        var maxSpeed = 0.0
        var currentSegmentStart = 0
        var currentMode = TransportMode.UNKNOWN

        for (index in points.indices) {
            val current = points[index]
            var speed = 0.0
            var distanceFromPrevious = 0.0
            var intervalSeconds = 0L
            if (index > 0) {
                val previous = points[index - 1]
                distanceFromPrevious = calculateDistanceKm(previous.lat, previous.lng, current.lat, current.lng)
                intervalSeconds = ((current.timestamp - previous.timestamp) / 1000L).coerceAtLeast(1L)
                speed = distanceFromPrevious / (intervalSeconds / 3600.0)
                totalDistance += distanceFromPrevious
                if (speed < 300.0) maxSpeed = max(maxSpeed, speed)
            }

            val detectedMode = detectTransportMode(speed, current.modeOverride)
            if (index == 0) {
                currentMode = detectedMode
            } else {
                modeDurations[detectedMode] = (modeDurations[detectedMode] ?: 0L) + intervalSeconds
                if (detectedMode != currentMode) {
                    addSegment(points, currentSegmentStart, index - 1, currentMode, segments)
                    currentSegmentStart = index
                    currentMode = detectedMode
                }
            }

            routePoints += RoutePoint(
                journeyId = 0L,
                latitude = current.lat,
                longitude = current.lng,
                timestamp = current.timestamp,
                speedKmh = speed,
                bearing = if (index > 0) calculateBearing(points[index - 1].lat, points[index - 1].lng, current.lat, current.lng) else 0f,
                sequenceOrder = index
            )
        }
        addSegment(points, currentSegmentStart, points.lastIndex, currentMode, segments)

        val startTime = points.first().timestamp
        val endTime = points.last().timestamp
        val durationSeconds = ((endTime - startTime) / 1000L).coerceAtLeast(1L)
        val dominantMode = modeDurations.maxByOrNull { it.value }?.key ?: currentMode
        val highlight = stops.maxByOrNull { it.importanceScore * 1_000_000L + it.durationSeconds }?.name

        val journey = Journey(
            title = defaultTitle,
            description = "Cinematic route through ${stops.size} key locations via ${dominantMode.label.lowercase()}.",
            startTime = startTime,
            endTime = endTime,
            totalDistanceKm = roundToTwoDecimals(totalDistance),
            totalDurationSeconds = durationSeconds,
            pointCount = routePoints.size,
            stopCount = stops.size,
            maxSpeedKmh = roundToOneDecimal(maxSpeed),
            averageSpeedKmh = roundToOneDecimal(totalDistance / (durationSeconds / 3600.0)),
            dominantMode = dominantMode,
            highlightPlaceName = highlight
        )
        return ParsedJourneyResult(journey, routePoints, stops, segments)
    }

    private fun addSegment(
        points: List<RawPoint>,
        startIndex: Int,
        endIndex: Int,
        mode: TransportMode,
        output: MutableList<TransportSegment>
    ) {
        if (startIndex < 0 || endIndex <= startIndex || endIndex >= points.size) return
        var distance = 0.0
        for (index in startIndex + 1..endIndex) {
            distance += calculateDistanceKm(points[index - 1].lat, points[index - 1].lng, points[index].lat, points[index].lng)
        }
        val duration = ((points[endIndex].timestamp - points[startIndex].timestamp) / 1000L).coerceAtLeast(1L)
        output += TransportSegment(
            journeyId = 0L,
            startIndex = startIndex,
            endIndex = endIndex,
            mode = mode,
            distanceKm = roundToTwoDecimals(distance),
            durationSeconds = duration,
            averageSpeedKmh = roundToOneDecimal(distance / (duration / 3600.0))
        )
    }

    private fun LocationData.isValid(): Boolean = lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0)

    private fun JsonReader.nextNullableString(): String? = when (peek()) {
        JsonToken.NULL -> {
            nextNull()
            null
        }
        else -> nextString()
    }

    private fun JsonReader.nextDoubleOrNull(): Double? = when (peek()) {
        JsonToken.NULL -> {
            nextNull()
            null
        }
        JsonToken.NUMBER, JsonToken.STRING -> nextString().toDoubleOrNull()
        else -> {
            skipValue()
            null
        }
    }

    private fun parseTimestamp(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return value.toLongOrNull() ?: runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
    }

    private fun transportModeFor(activityType: String?): TransportMode? = when (activityType?.uppercase()) {
        "WALKING", "ON_FOOT", "RUNNING" -> TransportMode.WALKING
        "CYCLING" -> TransportMode.CYCLING
        "IN_PASSENGER_VEHICLE", "DRIVING", "MOTORCYCLING" -> TransportMode.DRIVING
        "IN_BUS", "IN_TRAIN", "SUBWAY", "TRAM", "FLYING" -> TransportMode.TRANSIT
        else -> null
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

    private fun perpendicularDistanceKm(point: RawPoint, lineStart: RawPoint, lineEnd: RawPoint): Double {
        val referenceLatitude = Math.toRadians((lineStart.lat + lineEnd.lat + point.lat) / 3.0)
        val x1 = lineStart.lng * 111.320 * cos(referenceLatitude)
        val y1 = lineStart.lat * 110.574
        val x2 = lineEnd.lng * 111.320 * cos(referenceLatitude)
        val y2 = lineEnd.lat * 110.574
        val x = point.lng * 111.320 * cos(referenceLatitude)
        val y = point.lat * 110.574
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0.0 && dy == 0.0) return sqrt((x - x1) * (x - x1) + (y - y1) * (y - y1))
        val projection = ((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy)
        val boundedProjection = min(1.0, max(0.0, projection))
        val closestX = x1 + boundedProjection * dx
        val closestY = y1 + boundedProjection * dy
        return sqrt((x - closestX) * (x - closestX) + (y - closestY) * (y - closestY))
    }

    private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radius = 6371.0
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLon = Math.toRadians(lon2 - lon1)
        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(deltaLon / 2) * sin(deltaLon / 2)
        return radius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val deltaLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val y = sin(deltaLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLon)
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }

    private fun roundToTwoDecimals(value: Double): Double = (value * 100).roundToInt() / 100.0
    private fun roundToOneDecimal(value: Double): Double = (value * 10).roundToInt() / 10.0
}
