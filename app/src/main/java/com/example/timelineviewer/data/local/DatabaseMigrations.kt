package com.example.timelineviewer.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit migrations preserve a user's imported personal location history. Child-table rebuilds
 * in v3 add foreign-key cascades and indexes without discarding any valid journey records.
 */
object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE journeys ADD COLUMN maxSpeedKmh REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE journeys ADD COLUMN averageSpeedKmh REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE journeys ADD COLUMN dominantMode TEXT NOT NULL DEFAULT 'UNKNOWN'")
            db.execSQL("ALTER TABLE journeys ADD COLUMN highlightPlaceName TEXT")
            db.execSQL("ALTER TABLE stops ADD COLUMN importanceScore INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE stops ADD COLUMN category TEXT NOT NULL DEFAULT 'Waypoint'")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            rebuildRoutePoints(db)
            rebuildStops(db)
            rebuildTransportSegments(db)
        }

        private fun rebuildRoutePoints(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE route_points_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    journeyId INTEGER NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    timestamp INTEGER NOT NULL,
                    speedKmh REAL NOT NULL,
                    bearing REAL NOT NULL,
                    sequenceOrder INTEGER NOT NULL,
                    FOREIGN KEY(journeyId) REFERENCES journeys(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO route_points_new (id, journeyId, latitude, longitude, timestamp, speedKmh, bearing, sequenceOrder)
                SELECT p.id, p.journeyId, p.latitude, p.longitude, p.timestamp, p.speedKmh, p.bearing, p.sequenceOrder
                FROM route_points p INNER JOIN journeys j ON j.id = p.journeyId
                """.trimIndent()
            )
            db.execSQL("DROP TABLE route_points")
            db.execSQL("ALTER TABLE route_points_new RENAME TO route_points")
            db.execSQL("CREATE INDEX index_route_points_journeyId_sequenceOrder ON route_points (journeyId, sequenceOrder)")
        }

        private fun rebuildStops(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE stops_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    journeyId INTEGER NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    name TEXT NOT NULL,
                    startTime INTEGER NOT NULL,
                    endTime INTEGER NOT NULL,
                    durationSeconds INTEGER NOT NULL,
                    sequenceOrder INTEGER NOT NULL,
                    importanceScore INTEGER NOT NULL,
                    category TEXT NOT NULL,
                    FOREIGN KEY(journeyId) REFERENCES journeys(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO stops_new (id, journeyId, latitude, longitude, name, startTime, endTime, durationSeconds, sequenceOrder, importanceScore, category)
                SELECT s.id, s.journeyId, s.latitude, s.longitude, s.name, s.startTime, s.endTime, s.durationSeconds, s.sequenceOrder, s.importanceScore, s.category
                FROM stops s INNER JOIN journeys j ON j.id = s.journeyId
                """.trimIndent()
            )
            db.execSQL("DROP TABLE stops")
            db.execSQL("ALTER TABLE stops_new RENAME TO stops")
            db.execSQL("CREATE INDEX index_stops_journeyId_sequenceOrder ON stops (journeyId, sequenceOrder)")
        }

        private fun rebuildTransportSegments(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE transport_segments_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    journeyId INTEGER NOT NULL,
                    startIndex INTEGER NOT NULL,
                    endIndex INTEGER NOT NULL,
                    mode TEXT NOT NULL,
                    distanceKm REAL NOT NULL,
                    durationSeconds INTEGER NOT NULL,
                    averageSpeedKmh REAL NOT NULL,
                    FOREIGN KEY(journeyId) REFERENCES journeys(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO transport_segments_new (id, journeyId, startIndex, endIndex, mode, distanceKm, durationSeconds, averageSpeedKmh)
                SELECT t.id, t.journeyId, t.startIndex, t.endIndex, t.mode, t.distanceKm, t.durationSeconds, t.averageSpeedKmh
                FROM transport_segments t INNER JOIN journeys j ON j.id = t.journeyId
                """.trimIndent()
            )
            db.execSQL("DROP TABLE transport_segments")
            db.execSQL("ALTER TABLE transport_segments_new RENAME TO transport_segments")
            db.execSQL("CREATE INDEX index_transport_segments_journeyId_startIndex ON transport_segments (journeyId, startIndex)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS offline_map_regions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    journeyId INTEGER NOT NULL,
                    regionId TEXT NOT NULL,
                    status TEXT NOT NULL,
                    progress REAL NOT NULL,
                    styleUri TEXT NOT NULL,
                    minZoom INTEGER NOT NULL,
                    maxZoom INTEGER NOT NULL,
                    downloadedAt INTEGER,
                    lastError TEXT,
                    FOREIGN KEY(journeyId) REFERENCES journeys(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_offline_map_regions_journeyId ON offline_map_regions (journeyId)")
        }
    }
}
