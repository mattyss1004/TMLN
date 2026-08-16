package com.example.timelineviewer.data.analysis

import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.data.model.TransportMode
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class MemoryLibrarySort(val label: String) {
    RECENT("Recent"),
    LONGEST("Longest"),
    DISTANCE("Distance"),
    FAVORITES_FIRST("Favorites first")
}

data class MemoryLibraryFilter(
    val query: String = "",
    val favoritesOnly: Boolean = false,
    val transportMode: TransportMode? = null,
    val sort: MemoryLibrarySort = MemoryLibrarySort.RECENT
)

data class MemoryLibrarySection(
    val title: String,
    val journeys: List<Journey>
)

/** Pure, on-device archive organizing logic shared by the ViewModel and unit tests. */
object MemoryLibraryOrganizer {
    fun apply(journeys: List<Journey>, filter: MemoryLibraryFilter): List<Journey> {
        val query = filter.query.trim()
        return journeys
            .asSequence()
            .filter { journey ->
                !filter.favoritesOnly || journey.isFavorite
            }
            .filter { journey ->
                filter.transportMode == null || journey.dominantMode == filter.transportMode
            }
            .filter { journey ->
                query.isBlank() || matchesQuery(journey, query)
            }
            .sortedWith(sortComparator(filter.sort))
            .toList()
    }

    fun sections(journeys: List<Journey>, zoneId: ZoneId = ZoneId.systemDefault()): List<MemoryLibrarySection> =
        journeys
            .groupBy { YearMonth.from(Instant.ofEpochMilli(it.startTime).atZone(zoneId)) }
            .toSortedMap(compareByDescending { it })
            .map { (month, monthJourneys) ->
                MemoryLibrarySection(
                    title = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                    journeys = monthJourneys
                )
            }

    private fun matchesQuery(journey: Journey, query: String): Boolean = listOfNotNull(
        journey.title,
        journey.description.takeIf { it.isNotBlank() },
        journey.highlightPlaceName
    ).any { value -> value.contains(query, ignoreCase = true) }

    private fun sortComparator(sort: MemoryLibrarySort): Comparator<Journey> = when (sort) {
        MemoryLibrarySort.RECENT -> compareByDescending<Journey> { it.startTime }
        MemoryLibrarySort.LONGEST -> compareByDescending<Journey> { it.totalDurationSeconds }
        MemoryLibrarySort.DISTANCE -> compareByDescending<Journey> { it.totalDistanceKm }
        MemoryLibrarySort.FAVORITES_FIRST -> compareByDescending<Journey> { it.isFavorite }
            .thenByDescending { it.startTime }
    }
}
