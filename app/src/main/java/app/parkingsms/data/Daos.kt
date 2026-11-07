package app.parkingsms.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CityDao {
    @Query("SELECT * FROM cities ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<City>>

    @Query("SELECT * FROM cities WHERE id = :id")
    suspend fun getById(id: Long): City?

    @Insert
    suspend fun insert(city: City): Long

    @Update
    suspend fun update(city: City)

    @Query("DELETE FROM cities WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface PlateDao {
    @Query("SELECT * FROM plates ORDER BY id")
    fun observeAll(): Flow<List<Plate>>

    @Insert
    suspend fun insert(plate: Plate): Long

    @Update
    suspend fun update(plate: Plate)

    @Query("DELETE FROM plates WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface RecentZoneDao {
    @Query("SELECT * FROM recent_zones WHERE cityId = :cityId ORDER BY lastUsedAt DESC")
    fun observeForCity(cityId: Long): Flow<List<RecentZone>>

    @Query("SELECT * FROM recent_zones WHERE cityId = :cityId ORDER BY lastUsedAt DESC LIMIT 1")
    suspend fun latestForCity(cityId: Long): RecentZone?

    @Query("SELECT * FROM recent_zones WHERE cityId = :cityId AND zoneCode = :zone LIMIT 1")
    suspend fun find(cityId: Long, zone: String): RecentZone?

    @Insert
    suspend fun insert(zone: RecentZone): Long

    @Update
    suspend fun update(zone: RecentZone)

    @Query("DELETE FROM recent_zones WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM recent_zones WHERE cityId = :cityId")
    suspend fun deleteForCity(cityId: Long)
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: Session): Long

    @Update
    suspend fun update(session: Session)

    @Query("SELECT * FROM sessions WHERE active = 1 ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): Session?

    @Query("SELECT * FROM sessions WHERE active = 1 ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<Session?>

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatest(): Session?

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<Session>>

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    suspend fun getAll(): List<Session>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeById(id: Long): Flow<Session?>

    @Query("DELETE FROM sessions WHERE id = :id AND active = 0")
    suspend fun deleteInactiveById(id: Long)
}
