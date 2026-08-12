package com.example.timelineviewer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.data.model.RoutePoint
import com.example.timelineviewer.data.model.Stop
import com.example.timelineviewer.data.model.TransportSegment

@Database(
    entities = [Journey::class, RoutePoint::class, Stop::class, TransportSegment::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun journeyDao(): JourneyDao
    abstract fun routePointDao(): RoutePointDao
    abstract fun stopDao(): StopDao
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
                .addMigrations(
                    DatabaseMigrations.MIGRATION_1_2,
                    DatabaseMigrations.MIGRATION_2_3
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
