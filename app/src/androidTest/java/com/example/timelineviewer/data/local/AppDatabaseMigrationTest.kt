package com.example.timelineviewer.data.local

import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timelineviewer.data.model.OfflineRegionStatus
import com.example.timelineviewer.data.model.TransportMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private val databaseName = "migration-v4-to-v5-test.db"

    @After
    fun tearDown() {
        ApplicationProvider.getApplicationContext<android.content.Context>().deleteDatabase(databaseName)
    }

    @Test
    fun migrationFromVersion4PreservesArchiveAndInitializesLibraryMetadata() = runBlocking {
        val version4Database = migrationHelper.createDatabase(databaseName, 4)
        seedRepresentativeVersion4Archive(version4Database)
        version4Database.close()

        migrationHelper.runMigrationsAndValidate(
            databaseName,
            5,
            true,
            DatabaseMigrations.MIGRATION_4_5
        ).close()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(DatabaseMigrations.MIGRATION_4_5)
            .build()

        val journey = database.journeyDao().getJourneyById(JOURNEY_ID)
        requireNotNull(journey)
        assertEquals("Alpine archive", journey.title)
        assertEquals("Imported before Library metadata existed", journey.description)
        assertEquals(1_720_000_000_000L, journey.startTime)
        assertEquals(1_720_007_200_000L, journey.endTime)
        assertEquals(43.4, journey.totalDistanceKm, 0.0001)
        assertEquals(7_200L, journey.totalDurationSeconds)
        assertEquals(3, journey.pointCount)
        assertEquals(1, journey.stopCount)
        assertEquals(TransportMode.DRIVING, journey.dominantMode)
        assertEquals("Alpine pass", journey.highlightPlaceName)
        assertFalse(journey.isFavorite)
        assertNull(journey.coverPhotoPath)
        assertNull(journey.coverUpdatedAt)

        val points = database.routePointDao().getPointsListForJourney(JOURNEY_ID)
        assertEquals(3, points.size)
        assertEquals(listOf(0, 1, 2), points.map { it.sequenceOrder })
        assertEquals(46.0, points.first().latitude, 0.0001)
        assertEquals(7.0, points.first().longitude, 0.0001)
        assertEquals(1_720_003_600_000L, points[1].timestamp)
        assertEquals(18.0, points[1].speedKmh, 0.0001)
        assertEquals(90f, points[1].bearing)

        val stops = database.stopDao().getStopsListForJourney(JOURNEY_ID)
        assertEquals(1, stops.size)
        assertEquals("Alpine pass", stops.single().name)
        assertEquals(1_800L, stops.single().durationSeconds)
        assertEquals("Sightseeing", stops.single().category)
        assertEquals(92, stops.single().importanceScore)

        val segments = database.transportSegmentDao().getSegmentsListForJourney(JOURNEY_ID)
        assertEquals(2, segments.size)
        assertEquals(listOf(TransportMode.CYCLING, TransportMode.DRIVING), segments.map { it.mode })
        assertEquals(listOf(0, 1), segments.map { it.startIndex })
        assertEquals(listOf(1, 2), segments.map { it.endIndex })
        assertEquals(10.4, segments.first().distanceKm, 0.0001)
        assertEquals(33.0, segments.last().averageSpeedKmh, 0.0001)

        val offlineRegion = database.offlineMapRegionDao().getForJourney(JOURNEY_ID)
        requireNotNull(offlineRegion)
        assertEquals("alpine-region", offlineRegion.regionId)
        assertEquals(OfflineRegionStatus.AVAILABLE, offlineRegion.status)
        assertEquals(1f, offlineRegion.progress)
        assertEquals("mapbox://styles/mapbox/standard", offlineRegion.styleUri)
        assertEquals(8, offlineRegion.minZoom)
        assertEquals(16, offlineRegion.maxZoom)
        assertEquals(1_720_007_300_000L, offlineRegion.downloadedAt)

        database.close()
    }

    private fun seedRepresentativeVersion4Archive(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            INSERT INTO journeys (
                id, title, description, startTime, endTime, totalDistanceKm, totalDurationSeconds,
                pointCount, stopCount, maxSpeedKmh, averageSpeedKmh, dominantMode, highlightPlaceName, createdAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                JOURNEY_ID,
                "Alpine archive",
                "Imported before Library metadata existed",
                1_720_000_000_000L,
                1_720_007_200_000L,
                43.4,
                7_200L,
                3,
                1,
                48.0,
                21.7,
                "DRIVING",
                "Alpine pass",
                1_720_007_300_000L
            )
        )
        insertPoint(database, 1, 46.0, 7.0, 1_720_000_000_000L, 0.0, 0f, 0)
        insertPoint(database, 2, 46.1, 7.1, 1_720_003_600_000L, 18.0, 90f, 1)
        insertPoint(database, 3, 46.2, 7.2, 1_720_007_200_000L, 33.0, 95f, 2)
        database.execSQL(
            """
            INSERT INTO stops (id, journeyId, latitude, longitude, name, startTime, endTime, durationSeconds, sequenceOrder, importanceScore, category)
            VALUES (1, ?, 46.1, 7.1, 'Alpine pass', 1720001800000, 1720003600000, 1800, 0, 92, 'Sightseeing')
            """.trimIndent(),
            arrayOf(JOURNEY_ID)
        )
        database.execSQL(
            """
            INSERT INTO transport_segments (id, journeyId, startIndex, endIndex, mode, distanceKm, durationSeconds, averageSpeedKmh)
            VALUES
                (1, ?, 0, 1, 'CYCLING', 10.4, 3600, 10.4),
                (2, ?, 1, 2, 'DRIVING', 33.0, 3600, 33.0)
            """.trimIndent(),
            arrayOf(JOURNEY_ID, JOURNEY_ID)
        )
        database.execSQL(
            """
            INSERT INTO offline_map_regions (id, journeyId, regionId, status, progress, styleUri, minZoom, maxZoom, downloadedAt, lastError)
            VALUES (1, ?, 'alpine-region', 'AVAILABLE', 1.0, 'mapbox://styles/mapbox/standard', 8, 16, 1720007300000, NULL)
            """.trimIndent(),
            arrayOf(JOURNEY_ID)
        )
    }

    private fun insertPoint(
        database: SupportSQLiteDatabase,
        id: Int,
        latitude: Double,
        longitude: Double,
        timestamp: Long,
        speed: Double,
        bearing: Float,
        sequenceOrder: Int
    ) {
        database.execSQL(
            """
            INSERT INTO route_points (id, journeyId, latitude, longitude, timestamp, speedKmh, bearing, sequenceOrder)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(id, JOURNEY_ID, latitude, longitude, timestamp, speed, bearing, sequenceOrder)
        )
    }

    private companion object {
        const val JOURNEY_ID = 42L
    }
}
