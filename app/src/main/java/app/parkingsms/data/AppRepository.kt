package app.parkingsms.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class AppRepository(private val db: AppDatabase) {
    private val cityDao = db.cityDao()
    private val plateDao = db.plateDao()
    private val zoneDao = db.recentZoneDao()
    private val sessionDao = db.sessionDao()

    val cities: Flow<List<City>> = cityDao.observeAll()
    val plates: Flow<List<Plate>> = plateDao.observeAll()
    val sessions: Flow<List<Session>> = sessionDao.observeAll()
    val activeSession: Flow<Session?> = sessionDao.observeActive()

    fun session(id: Long): Flow<Session?> = sessionDao.observeById(id)

    // Cities
    suspend fun addCity(name: String, smsNumber: String): Long = cityDao.insert(City(name = name, smsNumber = smsNumber))
    suspend fun updateCity(city: City) = cityDao.update(city)
    suspend fun getCity(id: Long): City? = cityDao.getById(id)

    /** History keeps its own snapshots, so only the city and its remembered zones are removed. */
    suspend fun deleteCity(id: Long) {
        db.withTransaction {
            zoneDao.deleteForCity(id)
            cityDao.deleteById(id)
        }
    }

    // Plates
    suspend fun addPlate(plateText: String, label: String?): Long = plateDao.insert(Plate(plateText = plateText, label = label))
    suspend fun updatePlate(plate: Plate) = plateDao.update(plate)
    suspend fun deletePlate(id: Long) = plateDao.deleteById(id)

    // Zones
    fun recentZones(cityId: Long): Flow<List<RecentZone>> = zoneDao.observeForCity(cityId)
    suspend fun latestZone(cityId: Long): RecentZone? = zoneDao.latestForCity(cityId)

    suspend fun recordZone(cityId: Long, zoneCode: String, now: Long) {
        db.withTransaction {
            val existing = zoneDao.find(cityId, zoneCode)
            if (existing != null) {
                zoneDao.update(existing.copy(lastUsedAt = now))
            } else {
                zoneDao.insert(RecentZone(cityId = cityId, zoneCode = zoneCode, lastUsedAt = now))
            }
        }
    }

    suspend fun removeZone(id: Long) = zoneDao.deleteById(id)

    // Sessions
    suspend fun deleteSession(id: Long) = sessionDao.deleteInactiveById(id)
    suspend fun allSessions(): List<Session> = sessionDao.getAll()
}
