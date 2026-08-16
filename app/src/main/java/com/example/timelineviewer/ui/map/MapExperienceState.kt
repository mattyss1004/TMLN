package com.example.timelineviewer.ui.map

/**
 * The complete visible state of a journey map. Keeping this separate from Compose and Mapbox
 * prevents screens from fighting over the active camera or scene configuration.
 */
enum class JourneyCameraMode(val label: String) {
    OVERVIEW("Overview"),
    FOLLOW("Follow"),
    CINEMA("Cinema"),
    ORBIT("Orbit")
}

enum class JourneyBaseStyle {
    STANDARD,
    SATELLITE
}

enum class JourneySceneMood(val label: String) {
    DAY("Day"),
    DUSK("Dusk"),
    NIGHT("Night")
}

data class MapExperienceState(
    val cameraMode: JourneyCameraMode = JourneyCameraMode.OVERVIEW,
    val baseStyle: JourneyBaseStyle = JourneyBaseStyle.STANDARD,
    val sceneMood: JourneySceneMood = JourneySceneMood.DAY,
    val showThreeDObjects: Boolean = true,
    val showLabels: Boolean = false,
    val showStops: Boolean = true,
    val progressiveRouteEnabled: Boolean = false
)

object MapExperienceReducer {
    fun forJourneyDetail(): MapExperienceState = MapExperienceState()

    fun forRelive(): MapExperienceState = MapExperienceState(
        cameraMode = JourneyCameraMode.CINEMA,
        sceneMood = JourneySceneMood.DUSK,
        showStops = false,
        progressiveRouteEnabled = true
    )

    fun selectCamera(state: MapExperienceState, mode: JourneyCameraMode): MapExperienceState =
        state.copy(cameraMode = mode)

    fun toggleBaseStyle(state: MapExperienceState): MapExperienceState = state.copy(
        baseStyle = if (state.baseStyle == JourneyBaseStyle.STANDARD) {
            JourneyBaseStyle.SATELLITE
        } else {
            JourneyBaseStyle.STANDARD
        }
    )

    fun nextMood(state: MapExperienceState): MapExperienceState = state.copy(
        sceneMood = when (state.sceneMood) {
            JourneySceneMood.DAY -> JourneySceneMood.DUSK
            JourneySceneMood.DUSK -> JourneySceneMood.NIGHT
            JourneySceneMood.NIGHT -> JourneySceneMood.DAY
        }
    )

    fun toggleThreeDObjects(state: MapExperienceState): MapExperienceState =
        state.copy(showThreeDObjects = !state.showThreeDObjects)

    fun toggleLabels(state: MapExperienceState): MapExperienceState =
        state.copy(showLabels = !state.showLabels)

    fun toggleStops(state: MapExperienceState): MapExperienceState =
        state.copy(showStops = !state.showStops)
}
