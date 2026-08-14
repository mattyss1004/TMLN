package com.example.timelineviewer.data.analysis

import com.example.timelineviewer.data.model.JourneyDetailData
import com.example.timelineviewer.data.model.Stop
import com.example.timelineviewer.data.model.TransportMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A privacy-first narrative generated only from information already stored on the device.
 * Nothing here is sent to a service or inferred from external personal data.
 */
data class JourneyBrief(
    val dateLabel: String,
    val headline: String,
    val narrative: String,
    val distanceLabel: String,
    val durationLabel: String,
    val transportMix: List<TransportShare>,
    val highlights: List<JourneyHighlight>,
    val chapters: List<JourneyChapter>
)

data class TransportShare(
    val mode: TransportMode,
    val distanceKm: Double,
    val durationSeconds: Long,
    val sharePercent: Int
)

data class JourneyHighlight(
    val name: String,
    val durationSeconds: Long,
    val importanceScore: Int,
    val category: String
)

data class JourneyChapter(
    val order: Int,
    val title: String,
    val subtitle: String,
    val timestamp: Long,
    val mode: TransportMode? = null,
    val isHighlight: Boolean = false
)

object JourneyIntelligence {

    fun build(
        detail: JourneyDetailData,
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): JourneyBrief {
        val journey = detail.journey
        val highlights = detail.stops
            .filter { it.importanceScore >= HIGHLIGHT_SCORE || !isGeneratedStopName(it) }
            .sortedWith(
                compareByDescending<Stop> { it.importanceScore }
                    .thenByDescending { it.durationSeconds }
                    .thenBy { it.sequenceOrder }
            )
            .take(MAX_HIGHLIGHTS)
            .map {
                JourneyHighlight(
                    name = it.name,
                    durationSeconds = it.durationSeconds,
                    importanceScore = it.importanceScore,
                    category = it.category
                )
            }

        val transportMix = buildTransportMix(detail)
        val dominantMode = transportMix.firstOrNull()?.mode ?: journey.dominantMode
        val distanceLabel = formatDistance(journey.totalDistanceKm, locale)
        val durationLabel = formatDuration(journey.totalDurationSeconds)

        return JourneyBrief(
            dateLabel = formatDateRange(journey.startTime, journey.endTime, zoneId, locale),
            headline = buildHeadline(journey.title, highlights),
            narrative = buildNarrative(
                distanceLabel = distanceLabel,
                durationLabel = durationLabel,
                dominantMode = dominantMode,
                totalStops = detail.stops.size,
                highlights = highlights
            ),
            distanceLabel = distanceLabel,
            durationLabel = durationLabel,
            transportMix = transportMix,
            highlights = highlights,
            chapters = buildChapters(detail, highlights, dominantMode, zoneId, locale)
        )
    }

    private fun buildTransportMix(detail: JourneyDetailData): List<TransportShare> {
        val grouped = detail.segments
            .filter { it.mode != TransportMode.UNKNOWN && it.durationSeconds > 0L }
            .groupBy { it.mode }
            .map { (mode, segments) ->
                val distance = segments.sumOf { it.distanceKm }
                val duration = segments.sumOf { it.durationSeconds }
                TransportShare(
                    mode = mode,
                    distanceKm = distance,
                    durationSeconds = duration,
                    sharePercent = 0
                )
            }
            .sortedWith(compareByDescending<TransportShare> { it.distanceKm }.thenByDescending { it.durationSeconds })

        val totalDistance = grouped.sumOf { it.distanceKm }
        val totalDuration = grouped.sumOf { it.durationSeconds }
        return grouped.map { share ->
            val ratio = when {
                totalDistance > 0.0 -> share.distanceKm / totalDistance
                totalDuration > 0L -> share.durationSeconds.toDouble() / totalDuration
                else -> 0.0
            }
            share.copy(sharePercent = (ratio * 100).roundToInt().coerceIn(0, 100))
        }
    }

    private fun buildHeadline(title: String, highlights: List<JourneyHighlight>): String {
        val firstNamedHighlight = highlights.firstOrNull { !it.name.isBlank() && !isGeneratedStopName(it.name) }
        return when {
            firstNamedHighlight == null -> title
            highlights.size == 1 -> "A journey to ${firstNamedHighlight.name}"
            else -> "A journey through ${firstNamedHighlight.name} and more"
        }
    }

    private fun buildNarrative(
        distanceLabel: String,
        durationLabel: String,
        dominantMode: TransportMode,
        totalStops: Int,
        highlights: List<JourneyHighlight>
    ): String {
        val modePhrase = when (dominantMode) {
            TransportMode.WALKING -> "on foot"
            TransportMode.CYCLING -> "by bicycle"
            TransportMode.DRIVING -> "by car"
            TransportMode.TRANSIT -> "by public transport"
            TransportMode.UNKNOWN -> "along the route"
        }
        val stopPhrase = when {
            highlights.any { !isGeneratedStopName(it.name) } -> {
                val lead = highlights.first { !isGeneratedStopName(it.name) }.name
                if (highlights.size == 1) "with a memorable stop at $lead"
                else "with highlights including $lead"
            }
            highlights.isNotEmpty() -> "with ${highlights.size} highlights along the way"
            totalStops == 1 -> "with one stop along the way"
            totalStops > 1 -> "with $totalStops stops along the way"
            else -> "from start to finish"
        }
        return "Covered $distanceLabel over $durationLabel, mostly $modePhrase, $stopPhrase."
    }

    private fun buildChapters(
        detail: JourneyDetailData,
        highlights: List<JourneyHighlight>,
        dominantMode: TransportMode,
        zoneId: ZoneId,
        locale: Locale
    ): List<JourneyChapter> {
        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", locale).withZone(zoneId)
        val chapters = mutableListOf<JourneyChapter>()
        val startTime = detail.journey.startTime
        val endTime = detail.journey.endTime

        chapters += JourneyChapter(
            order = chapters.size,
            title = "Setting out",
            subtitle = "Started at ${timeFormatter.format(Instant.ofEpochMilli(startTime))}",
            timestamp = startTime,
            mode = dominantMode
        )

        val highlightNames = highlights.map { it.name }.toSet()
        detail.stops
            .filter { it.name in highlightNames }
            .sortedBy { it.sequenceOrder }
            .take(MAX_CHAPTER_STOPS)
            .forEach { stop ->
                val named = !isGeneratedStopName(stop)
                chapters += JourneyChapter(
                    order = chapters.size,
                    title = if (named) "Pause at ${stop.name}" else "A pause along the way",
                    subtitle = if (named) {
                        "Stayed for ${formatDuration(stop.durationSeconds)}"
                    } else {
                        "A ${formatDuration(stop.durationSeconds)} stop"
                    },
                    timestamp = stop.startTime,
                    isHighlight = stop.importanceScore >= HIGHLIGHT_SCORE
                )
            }

        chapters += JourneyChapter(
            order = chapters.size,
            title = "Arrival",
            subtitle = "Finished at ${timeFormatter.format(Instant.ofEpochMilli(endTime))}",
            timestamp = endTime,
            mode = dominantMode
        )
        return chapters
    }

    private fun formatDateRange(startTime: Long, endTime: Long, zoneId: ZoneId, locale: Locale): String {
        val start = Instant.ofEpochMilli(startTime).atZone(zoneId)
        val end = Instant.ofEpochMilli(endTime).atZone(zoneId)
        val singleDay = DateTimeFormatter.ofPattern("MMM d, yyyy", locale)
        val shortDay = DateTimeFormatter.ofPattern("MMM d", locale)
        return if (start.toLocalDate() == end.toLocalDate()) {
            singleDay.format(start)
        } else {
            "${shortDay.format(start)} – ${singleDay.format(end)}"
        }
    }

    private fun formatDistance(distanceKm: Double, locale: Locale): String = when {
        distanceKm >= 100.0 -> String.format(locale, "%.0f km", distanceKm)
        else -> String.format(locale, "%.1f km", distanceKm)
    }

    fun formatDuration(totalSeconds: Long): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0L)
        val hours = safeSeconds / 3600L
        val minutes = (safeSeconds % 3600L) / 60L
        return when {
            hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
            hours > 0L -> "${hours}h"
            minutes > 0L -> "${minutes} min"
            else -> "less than a minute"
        }
    }

    private fun isGeneratedStopName(stop: Stop): Boolean = isGeneratedStopName(stop.name)

    private fun isGeneratedStopName(name: String): Boolean = name.matches(Regex("Stop \\d+"))

    private const val HIGHLIGHT_SCORE = 70
    private const val MAX_HIGHLIGHTS = 3
    private const val MAX_CHAPTER_STOPS = 3
}
