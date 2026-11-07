package app.parkingsms.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Migrations strategy: version 1 is the first release. When the schema changes, bump [version],
 * add a Migration object (never use fallbackToDestructiveMigration, history must survive) and
 * register it in AppContainer with Room.databaseBuilder(...).addMigrations(...).
 * Schema export is disabled to keep the build simple; enable it with the room.schemaLocation
 * KSP argument if you want migration tests.
 */
@Database(
    entities = [City::class, Plate::class, RecentZone::class, Session::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cityDao(): CityDao
    abstract fun plateDao(): PlateDao
    abstract fun recentZoneDao(): RecentZoneDao
    abstract fun sessionDao(): SessionDao
}
