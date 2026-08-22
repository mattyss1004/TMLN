
import com.example.timelineviewer.data.model.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CinematicCameraDirectorTest {

    private val route = listOf(
        point(latitude = 50.087, longitude = 14.420, timestamp = 1_000L, bearing = 10f, sequenceOrder = 0),
        point(latitude = 50.090, longitude = 14.438, timestamp = 2_000L, bearing = 350f, sequenceOrder = 1),
        point(latitude = 50.094, longitude = 14.452, timestamp = 3_000L, bearing = 35f, sequenceOrder = 2)
    )

    private fun point(
        latitude: Double,
        longitude: Double,
        timestamp: Long,
        bearing: Float,
        sequenceOrder: Int
    ) = RoutePoint(
        id = 0,
        journeyId = 1,
        latitude = latitude,
        longitude = longitude,
        timestamp = timestamp,
        speedKmh = 0.0,
        bearing = bearing,
        sequenceOrder = sequenceOrder
    )

    @Test
    fun `overview frames the route rather than the active traveller`() {
        val overview = CinematicCameraDirector.poseFor(JourneyCameraMode.OVERVIEW, route, 1)!!

        assertEquals(0.0, overview.bearing, 0.001)
        assertEquals(50.0, overview.pitch, 0.001)
        assertTrue(overview.latitude in route.first().latitude..route.last().latitude)
        assertTrue(overview.longitude in route.first().longitude..route.last().longitude)
    }

    @Test
    fun `follow cinema and orbit have distinct visual framing`() {
        val follow = CinematicCameraDirector.poseFor(JourneyCameraMode.FOLLOW, route, 1)!!
        val cinema = CinematicCameraDirector.poseFor(JourneyCameraMode.CINEMA, route, 1)!!
        val orbit = CinematicCameraDirector.poseFor(JourneyCameraMode.ORBIT, route, 1)!!

        assertEquals(route[1].latitude, follow.latitude, 0.000001)
        assertEquals(route[1].longitude, follow.longitude, 0.000001)
        assertNotEquals(follow.pitch, cinema.pitch)
        assertNotEquals(cinema.bearing, orbit.bearing)
        assertTrue(orbit.bearing in 0.0..359.999)
    }

    @Test
    fun `empty route produces no invalid camera pose`() {
        assertEquals(null, CinematicCameraDirector.poseFor(JourneyCameraMode.CINEMA, emptyList(), 0))
    }
}
