package com.example.timelineviewer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.data.model.OfflineMapRegion
import com.example.timelineviewer.data.model.RoutePoint
import com.example.timelineviewer.data.model.Stop
import com.example.timelineviewer.data.model.TransportSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Journey::class, RoutePoint::class, Stop::class, TransportSegment::class, OfflineMapRegion::class],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun journeyDao(): JourneyDao
    abstract fun routePointDao(): RoutePointDao
    abstract fun stopDao(): StopDao
    abstract fun offlineMapRegionDao(): OfflineMapRegionDao
    abstract fun transportSegmentDao(): TransportSegmentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "timeline_viewer_database"
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Auto-seed demo journeys asynchronously on fresh database creation
                        INSTANCE?.let { database ->
                            CoroutineScope(Dispatchers.IO).launch {
                                seedDemoJourneys(context.applicationContext, database)
                            }
                        }
                    }
                })
                .addMigrations(
                    DatabaseMigrations.MIGRATION_1_2,
                    DatabaseMigrations.MIGRATION_2_3,
                    DatabaseMigrations.MIGRATION_3_4,
                    DatabaseMigrations.MIGRATION_4_5,
                    DatabaseMigrations.MIGRATION_5_6
                )
                .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun seedDemoJourneys(context: Context, database: AppDatabase) {
            try {
                val seedClass = Class.forName("com.example.timelineviewer.data.demo.DemoJourneySeeder")
                val seedMethod = seedClass.getMethod("seedIfEmpty", Context::class.java, AppDatabase::class.java)
                seedMethod.invoke(null, context, database)
            } catch (_: Exception) {
                // Seeder class or method optional if demo package structure differs
            }
        }
    }
}
