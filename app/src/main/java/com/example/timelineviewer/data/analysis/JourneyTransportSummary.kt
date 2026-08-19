package com.example.timelineviewer.data.analysis

import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.data.model.TransportMode
import com.example.timelineviewer.data.model.TransportSegment

/**
 * The concise, truthful transport label used by a journey card. It is derived only from the
 * locally persisted transport segments and deliberately does not replace Journey.dominantMode,
 * which remains useful for filters and fallback behaviour.
 */
data class JourneyTransportSummary(
    val modes: List<TransportMode>
) {
    init {
        require(modes.isNotEmpty()) { "A journey transport summary needs at least one mode." }
    }

    val isMultimodal: Boolean = modes.size > 1
    val label: String = if (isMultimodal) "Multimodal" else modes.single().label
    val primaryMode: TransportMode = modes.first()
}

data class JourneyLibraryItem(
    val journey: Journey,
    val transportSummary: JourneyTransportSummary
)

object JourneyTransportSummarizer {
    fun fromSegments(
        segments: List<TransportSegment>,
        fallbackMode: TransportMode
    ): JourneyTransportSummary {
        val meaningfulModes = segments
            .asSequence()
            .sortedBy { it.startIndex }
            .map { it.mode }
            .filter { it != TransportMode.UNKNOWN }
            .distinct()
            .toList()

        return JourneyTransportSummary(
            modes = meaningfulModes.ifEmpty { listOf(fallbackMode) }
        )
    }
}
