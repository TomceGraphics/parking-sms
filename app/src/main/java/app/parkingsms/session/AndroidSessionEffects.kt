package app.parkingsms.session

import android.content.Context
import app.parkingsms.data.Session
import app.parkingsms.widget.updateParkingWidgets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AlarmUi(val sessionId: Long, val stopFailed: Boolean)

class AndroidSessionEffects(
    private val context: Context,
    private val notifications: NotificationHelper
) : SessionEffects {
    private val _alarmState = MutableStateFlow<AlarmUi?>(null)

    /** Non-null while the alarm alert is ringing; the alarm screen closes itself when it goes null. */
    val alarmState: StateFlow<AlarmUi?> = _alarmState

    override suspend fun sessionChanged(session: Session?) {
        if (session != null && session.active) notifications.showOngoing(session) else notifications.cancelOngoing()
        updateParkingWidgets(context)
    }

    override suspend fun ringAlarm(session: Session, stopFailed: Boolean) {
        _alarmState.value = AlarmUi(session.id, stopFailed)
        notifications.showAlarm(session, stopFailed)
    }

    override suspend fun dismissAlarm() {
        _alarmState.value = null
        notifications.cancelAlarm()
    }

    override suspend fun showReminder(session: Session) = notifications.showReminder(session)

    override suspend fun showAutoStopped(session: Session) = notifications.showAutoStopped(session)
}
