package com.example.timelineviewer.data.analysis

import com.example.timelineviewer.data.model.JourneyDetailData
import com.example.timelineviewer.data.model.RoutePoint
import com.example.timelineviewer.data.model.Stop

/** A deterministic, on-device plan for the meaningful beats shown while reliving a journey. */
enum class ReliveMomentKind {
    DEPARTURE,
    HIGHLIGHT,
    ARRIVAL
}

data class ReliveMoment(
    val pointIndex: Int,
    val timestamp: Long,
    val title: String,
    val subtitle: String,
    val kind: ReliveMomentKind
)

object RelivePlanner {
    private const val HIGHLIGHT_SCORE = 70
    private const val MAX_HIGHLIGHTS = 3

    fun moments(detail: JourneyDetailData): List<ReliveMoment> {
        val points = detail.points
        if (points.isEmpty()) return emptyList()

        val output = mutableListOf(
            ReliveMoment(
                pointIndex = 0,
                timestamp = points.first().timestamp,
                title = "Setting out",
                subtitle = "The journey begins here",
                kind = ReliveMomentKind.DEPARTURE
            )
        )
        val occupiedIndexes = mutableSetOf(0)

        detail.stops
            .filter { it.importanceScore >= HIGHLIGHT_SCORE || !isGeneratedStopName(it) }
            .sortedWith(compareBy<Stop> { it.sequenceOrder }.thenBy { it.startTime })
            .take(MAX_HIGHLIGHTS)
            .forEach { stop ->
                val pointIndex = closestPointIndex(points, stop.startTime)
                if (pointIndex !in 1 until points.lastIndex || !occupiedIndexes.add(pointIndex)) return@forEach
                output += ReliveMoment(
                    pointIndex = pointIndex,
                    timestamp = stop.startTime,
                    title = if (isGeneratedStopName(stop)) "A pause along the way" else "Pause at ${stop.name}",
                    subtitle = "Stayed for ${JourneyIntelligence.formatDuration(stop.durationSeconds)}",
                    kind = ReliveMomentKind.HIGHLIGHT
                )
            }

        output += ReliveMoment(
            pointIndex = points.lastIndex,
            timestamp = points.last().timestamp,
            title = "Arrival",
            subtitle = "The story comes to a close",
            kind = ReliveMomentKind.ARRIVAL
        )
        return output.sortedBy { it.pointIndex }
    }

    fun activeMoment(moments: List<ReliveMoment>, pointIndex: Int): ReliveMoment? =
        moments.lastOrNull { it.pointIndex <= pointIndex } ?: moments.firstOrNull()

    fun nextMoment(moments: List<ReliveMoment>, pointIndex: Int): ReliveMoment? =
        moments.firstOrNull { it.pointIndex > pointIndex }

    private fun closestPointIndex(points: List<RoutePoint>, timestamp: Long): Int =
        points.indices.minByOrNull { index -> kotlin.math.abs(points[index].timestamp - timestamp) } ?: 0

    private fun isGeneratedStopName(stop: Stop): Boolean = stop.name.matches(Regex("Stop \\d+"))
}
