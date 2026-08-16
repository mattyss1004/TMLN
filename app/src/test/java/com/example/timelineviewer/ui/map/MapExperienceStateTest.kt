package com.example.timelineviewer.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapExperienceStateTest {

    @Test
    fun `journey detail defaults to a clear overview`() {
        val state = MapExperienceReducer.forJourneyDetail()

        assertEquals(JourneyCameraMode.OVERVIEW, state.cameraMode)
        assertEquals(JourneyBaseStyle.STANDARD, state.baseStyle)
        assertTrue(state.showThreeDObjects)
        assertTrue(state.showStops)
        assertFalse(state.progressiveRouteEnabled)
    }

    @Test
    fun `relive defaults to a cinematic progressive scene`() {
        val state = MapExperienceReducer.forRelive()

        assertEquals(JourneyCameraMode.CINEMA, state.cameraMode)
        assertEquals(JourneySceneMood.DUSK, state.sceneMood)
        assertTrue(state.progressiveRouteEnabled)
        assertFalse(state.showStops)
    }

    @Test
    fun `camera and base-style commands affect only their target state`() {
        val initial = MapExperienceReducer.forRelive()
        val orbit = MapExperienceReducer.selectCamera(initial, JourneyCameraMode.ORBIT)
        val satellite = MapExperienceReducer.toggleBaseStyle(orbit)

        assertEquals(JourneyCameraMode.ORBIT, orbit.cameraMode)
        assertEquals(JourneyBaseStyle.STANDARD, orbit.baseStyle)
        assertEquals(JourneyBaseStyle.SATELLITE, satellite.baseStyle)
        assertEquals(JourneyCameraMode.ORBIT, satellite.cameraMode)
    }

    @Test
    fun `layer toggles change only their declared rendering state`() {
        val initial = MapExperienceReducer.forJourneyDetail()
        val withoutThreeD = MapExperienceReducer.toggleThreeDObjects(initial)
        val withLabels = MapExperienceReducer.toggleLabels(withoutThreeD)
        val withoutStops = MapExperienceReducer.toggleStops(withLabels)

        assertFalse(withoutThreeD.showThreeDObjects)
        assertFalse(withoutThreeD.showLabels)
        assertTrue(withLabels.showLabels)
        assertTrue(withLabels.showStops)
        assertFalse(withoutStops.showStops)
        assertTrue(withoutStops.showLabels)
    }

    @Test
    fun `scene mood cycles through all supported lighting states`() {
        val day = MapExperienceState(sceneMood = JourneySceneMood.DAY)
        val dusk = MapExperienceReducer.nextMood(day)
        val night = MapExperienceReducer.nextMood(dusk)
        val backToDay = MapExperienceReducer.nextMood(night)

        assertEquals(JourneySceneMood.DUSK, dusk.sceneMood)
        assertEquals(JourneySceneMood.NIGHT, night.sceneMood)
        assertEquals(JourneySceneMood.DAY, backToDay.sceneMood)
    }
}
