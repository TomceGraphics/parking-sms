package app.parkingsms.session

import app.parkingsms.data.Session
import app.parkingsms.data.SessionDao
import app.parkingsms.data.SettingsStore

class RoomSessionStore(private val dao: SessionDao) : SessionStore {
    override suspend fun insert(session: Session): Long = dao.insert(session)
    override suspend fun update(session: Session) = dao.update(session)
    override suspend fun getActive(): Session? = dao.getActive()
    override suspend fun getLatest(): Session? = dao.getLatest()
}

class SettingsSessionConfig(private val settings: SettingsStore) : SessionConfig {
    override suspend fun startTemplate(): String = settings.snapshot().startTemplate
    override suspend fun stopTemplate(): String = settings.snapshot().stopTemplate
    override suspend fun snoozeMinutes(): Int = settings.snapshot().snoozeMinutes
}
