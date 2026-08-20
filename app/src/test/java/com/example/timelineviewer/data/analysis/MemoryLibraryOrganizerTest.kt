package com.example.timelineviewer.data.analysis

import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.data.model.JourneySource
import com.example.timelineviewer.data.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class MemoryLibraryOrganizerTest {

    private val walkingFavorite = journey(
        id = 1,
        title = "Prague at dawn",
        description = "Old town walk",
        startTime = 1_725_235_200_000L,
        mode = TransportMode.WALKING,
        favorite = true,
        distance = 4.2,
        duration = 3_600L
    )
    private val driving = journey(
        id = 2,
        title = "Mountain road",
        description = "",
        startTime = 1_724_150_800_000L,
        mode = TransportMode.DRIVING,
        favorite = false,
        distance = 110.0,
        duration = 7_200L
    )
    private val transit = journey(
        id = 3,
        title = "Coastal weekend",
        description = "Train to the coast",
        startTime = 1_709_424_000_000L,
        mode = TransportMode.TRANSIT,
        favorite = false,
        distance = 220.0,
        duration = 5_400L
    )

    @Test
    fun `favorites filter returns only memories the user curated`() {
        val result = MemoryLibraryOrganizer.apply(
            listOf(driving, walkingFavorite, transit),
            MemoryLibraryFilter(favoritesOnly = true)
        )

        assertEquals(listOf(walkingFavorite.id), result.map { it.id })
    }

    @Test
    fun `transport and query filters compose without mutating original archive`() {
        val result = MemoryLibraryOrganizer.apply(
            listOf(walkingFavorite, driving, transit),
            MemoryLibraryFilter(query = "coast", transportMode = TransportMode.TRANSIT)
        )

        assertEquals(listOf(transit.id), result.map { it.id })
    }

    @Test
    fun `distance sort ranks largest memories first`() {
        val result = MemoryLibraryOrganizer.apply(
            listOf(walkingFavorite, driving, transit),
            MemoryLibraryFilter(sort = MemoryLibrarySort.DISTANCE)
        )

        assertEquals(listOf(transit.id, driving.id, walkingFavorite.id), result.map { it.id })
    }

    @Test
    fun `source sections keep demo records separate from imported daily journeys`() {
        val demo = walkingFavorite.copy(source = JourneySource.DEMO)
        val importedA = driving.copy(source = JourneySource.IMPORTED)
        val importedB = transit.copy(source = JourneySource.IMPORTED)

        val sections = MemoryLibraryOrganizer.sourceSections(
            listOf(importedA, demo, importedB),
            ZoneOffset.UTC
        )

        assertEquals(listOf("Test journeys (1)", "Imported journeys (2)"), sections.map { it.title })
        assertEquals(listOf(demo.id), sections.first().sections.flatMap { it.journeys }.map { it.id })
        assertEquals(setOf(importedA.id, importedB.id), sections.last().sections.flatMap { it.journeys }.map { it.id }.toSet())
    }

    @Test
    fun `sections group the current archive by the month in which it happened`() {
        val sections = MemoryLibraryOrganizer.sections(
            listOf(walkingFavorite, driving, transit),
            ZoneOffset.UTC
        )

        assertTrue(sections.isNotEmpty())
        assertEquals(3, sections.sumOf { it.journeys.size })
        assertEquals(walkingFavorite.id, sections.first().journeys.first().id)
    }

    private fun journey(
        id: Long,
        title: String,
        description: String,
        startTime: Long,
        mode: TransportMode,
        favorite: Boolean,
        distance: Double,
        duration: Long
    ) = Journey(
        id = id,
        title = title,
        description = description,
        startTime = startTime,
        endTime = startTime + duration * 1_000,
        totalDistanceKm = distance,
        totalDurationSeconds = duration,
        pointCount = 2,
        stopCount = 0,
        dominantMode = mode,
        isFavorite = favorite
    )
}
