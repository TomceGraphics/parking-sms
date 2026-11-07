package app.parkingsms.session

import app.parkingsms.data.AlarmKind
import app.parkingsms.data.EndReason
import app.parkingsms.data.Session
import app.parkingsms.data.SmsStatus
import app.parkingsms.data.TimerMode
import app.parkingsms.data.mode
import app.parkingsms.data.startStatus
import app.parkingsms.data.stopStatus
import app.parkingsms.sms.SmsSendResult
import app.parkingsms.sms.SmsSender
import app.parkingsms.util.MessageTemplate
import app.parkingsms.util.PhoneNumbers
import app.parkingsms.util.TextCleaner
import app.parkingsms.util.TimerMath
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

interface Clock {
    fun now(): Long
}

object RealClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}

interface SessionStore {
    suspend fun insert(session: Session): Long
    suspend fun update(session: Session)
    suspend fun getActive(): Session?
    suspend fun getLatest(): Session?
}

interface AlarmScheduler {
    fun canScheduleExact(): Boolean
    fun schedule(kind: AlarmKind, sessionId: Long, atMillis: Long)
    fun cancel(kind: AlarmKind, sessionId: Long)
}

interface SessionConfig {
    suspend fun startTemplate(): String
    suspend fun stopTemplate(): String
    suspend fun snoozeMinutes(): Int
}

/** Everything the manager does that is visible to the user (notifications, widget, tile...). */
interface SessionEffects {
    /** Called whenever the active session changes. null = nothing active. */
    suspend fun sessionChanged(session: Session?)
    suspend fun ringAlarm(session: Session, stopFailed: Boolean)
    suspend fun dismissAlarm()
    suspend fun showReminder(session: Session)
    suspend fun showAutoStopped(session: Session)
}

data class StartRequest(
    val cityId: Long?,
    val cityName: String,
    val smsNumber: String,
    val zoneCode: String,
    val plateText: String,
    val timerMinutes: Int?,
    val mode: TimerMode,
    val graceMinutes: Int,
    val remindMinutesBefore: Int?
)

enum class InvalidField { CITY, ZONE, PLATE, TEMPLATE }

sealed interface StartResult {
    data class Started(val session: Session) : StartResult
    data object AlreadyActive : StartResult
    data class Invalid(val field: InvalidField) : StartResult
    data class SmsFailed(val error: String?) : StartResult
}

sealed interface StopResult {
    data class Stopped(val session: Session) : StopResult
    data object NoActive : StopResult
    data class Failed(val error: String?) : StopResult
}

/**
 * Single owner of all start/stop logic. UI, notification actions, widget, quick tile and alarm
 * receivers all call into this class.
 *
 * EXTENSION POINT (future car-Bluetooth feature): add a small component that observes the car
 * Bluetooth connection and calls [stop] (auto-stop) or effects.showReminder(...) while a session is
 * active. Nothing in this class needs to change; Plate.carBluetoothAddress is already reserved.
 */
class SessionManager(
    private val store: SessionStore,
    private val smsSender: SmsSender,
    private val scheduler: AlarmScheduler,
    private val effects: SessionEffects,
    private val clock: Clock,
    private val config: SessionConfig
) {
    /** Serialises start/stop/alarm handling: guards against double taps and alarm-vs-user races. */
    private val mutex = Mutex()

    suspend fun start(request: StartRequest): StartResult = mutex.withLock {
        if (store.getActive() != null) return@withLock StartResult.AlreadyActive
        val number = request.smsNumber.trim()
        val zone = TextCleaner.cleanZone(request.zoneCode)
        val plate = TextCleaner.cleanPlate(request.plateText)
        if (number.isEmpty()) return@withLock StartResult.Invalid(InvalidField.CITY)
        if (zone.isEmpty()) return@withLock StartResult.Invalid(InvalidField.ZONE)
        if (plate.isEmpty()) return@withLock StartResult.Invalid(InvalidField.PLATE)
        val text = MessageTemplate.render(config.startTemplate(), zone, plate)
        if (text.isEmpty()) return@withLock StartResult.Invalid(InvalidField.TEMPLATE)

        val now = clock.now()
        val minutes = request.timerMinutes?.takeIf { it > 0 }
        val draft = Session(
            cityId = request.cityId,
            cityNameSnapshot = request.cityName,
            smsNumberSnapshot = number,
            zoneCode = zone,
            plateText = plate,
            startedAt = now,
            timerMinutes = minutes,
            timerMode = request.mode.name,
            graceMinutes = request.graceMinutes,
            remindMinutesBefore = if (minutes != null) request.remindMinutesBefore else null,
            startSmsStatus = SmsStatus.SENDING.name,
            active = true
        )
        val id = store.insert(draft)
        var session = draft.copy(id = id)

        val result = sendWithRetry(number, text)
        session = session.copy(smsLog = appendLog(session.smsLog, "START", number, text, result))
        if (!result.success) {
            session = session.copy(
                active = false,
                endedAt = clock.now(),
                endReason = EndReason.FAILED.name,
                startSmsStatus = SmsStatus.FAILED.name
            )
            store.update(session)
            effects.sessionChanged(null)
            return@withLock StartResult.SmsFailed(result.error)
        }

        session = session.copy(startSmsStatus = SmsStatus.SENT.name)
        session = arm(session)
        effects.sessionChanged(session)
        StartResult.Started(session)
    }

    suspend fun stop(reason: EndReason = EndReason.MANUAL): StopResult {
        // Silence the alarm immediately, even before the SMS result is known.
        effects.dismissAlarm()
        return mutex.withLock {
            val active = store.getActive() ?: return@withLock StopResult.NoActive
            var session = active.copy(stopSmsStatus = SmsStatus.SENDING.name)
            store.update(session)
            val text = MessageTemplate.render(config.stopTemplate(), session.zoneCode, session.plateText)
            val number = session.smsNumberSnapshot
            val result = sendWithRetry(number, text)
            session = session.copy(smsLog = appendLog(session.smsLog, "STOP", number, text, result))
            if (result.success) {
                val ended = session.copy(
                    active = false,
                    endedAt = clock.now(),
                    endReason = reason.name,
                    stopSmsStatus = SmsStatus.SENT.name,
                    alarmKind = null,
                    alarmAt = null,
                    reminderAt = null
                )
                store.update(ended)
                cancelAlarms(ended.id)
                effects.dismissAlarm()
                effects.sessionChanged(null)
                StopResult.Stopped(ended)
            } else {
                val failed = session.copy(stopSmsStatus = SmsStatus.FAILED.name)
                store.update(failed)
                effects.sessionChanged(failed)
                effects.ringAlarm(failed, stopFailed = true)
                StopResult.Failed(result.error)
            }
        }
    }

    /** Silences the alarm, cancels a pending auto-stop and rings again after the snooze length. */
    suspend fun snooze(): Boolean {
        effects.dismissAlarm()
        val snoozeMinutes = config.snoozeMinutes()
        return mutex.withLock {
            val s = store.getActive() ?: return@withLock false
            scheduler.cancel(AlarmKind.AUTO_STOP, s.id)
            scheduler.cancel(AlarmKind.RING, s.id)
            val at = TimerMath.snoozeTime(clock.now(), snoozeMinutes)
            val updated = s.copy(alarmKind = AlarmKind.RING.name, alarmAt = at)
            store.update(updated)
            scheduler.schedule(AlarmKind.RING, s.id, at)
            effects.sessionChanged(updated)
            true
        }
    }

    /** Entry point for AlarmManager broadcasts. Stale or duplicate alarms are ignored. */
    suspend fun onAlarm(kind: AlarmKind, sessionId: Long) {
        when (kind) {
            AlarmKind.REMINDER -> {
                val s = mutex.withLock<Session?> {
                    val cur = store.getActive()?.takeIf { it.id == sessionId && it.reminderAt != null }
                        ?: return@withLock null
                    val updated = cur.copy(reminderAt = null)
                    store.update(updated)
                    updated
                }
                if (s != null) effects.showReminder(s)
            }
            AlarmKind.RING -> ring(sessionId)
            AlarmKind.AUTO_STOP -> autoStop(sessionId)
        }
    }

    /**
     * Re-creates alarms and the ongoing notification for the active session. Called after boot,
     * after an app update and whenever the app is opened. Overdue alarms fire immediately.
     */
    suspend fun rearm() {
        var overdue: Pair<AlarmKind, Long>? = null
        mutex.withLock {
            val s0 = store.getActive()
            if (s0 == null) {
                effects.sessionChanged(null)
                return@withLock
            }
            var s = s0
            val now = clock.now()
            if (s.startStatus == SmsStatus.SENDING && now - s.startedAt > STUCK_SENDING_MS) {
                s = s.copy(startSmsStatus = SmsStatus.FAILED.name)
                store.update(s)
            }
            effects.sessionChanged(s)
            if (s.startStatus != SmsStatus.SENT) return@withLock

            val kind = AlarmKind.fromName(s.alarmKind)
            val at = s.alarmAt
            if (kind != null && at != null) {
                if (at <= now) overdue = Pair(kind, s.id) else scheduler.schedule(kind, s.id, at)
            }
            val reminderAt = s.reminderAt
            if (reminderAt != null) {
                if (reminderAt > now) {
                    scheduler.schedule(AlarmKind.REMINDER, s.id, reminderAt)
                } else {
                    store.update(s.copy(reminderAt = null))
                }
            }
        }
        val due = overdue
        if (due != null) onAlarm(due.first, due.second)
    }

    /** Stores the raw reply text on the latest session (active, or ended within 30 minutes). */
    suspend fun onReply(fromNumber: String, text: String) {
        mutex.withLock {
            val s = store.getLatest() ?: return@withLock
            if (!PhoneNumbers.matches(s.smsNumberSnapshot, fromNumber)) return@withLock
            val reference = s.endedAt ?: clock.now()
            if (clock.now() - reference > REPLY_WINDOW_MS) return@withLock
            val old = s.replyText
            val combined = if (old.isNullOrEmpty()) text else old + "\n---\n" + text
            val updated = s.copy(replyText = combined)
            store.update(updated)
            if (updated.active) effects.sessionChanged(updated)
        }
    }

    // ---- internals ----

    private suspend fun ring(sessionId: Long) {
        val s = mutex.withLock<Session?> {
            val cur = store.getActive()?.takeIf { it.id == sessionId && it.alarmKind == AlarmKind.RING.name }
                ?: return@withLock null
            var updated = cur.copy(alarmKind = null, alarmAt = null)
            if (cur.mode == TimerMode.ALARM_THEN_AUTO_STOP) {
                val at = TimerMath.graceDeadline(clock.now(), cur.graceMinutes)
                updated = cur.copy(alarmKind = AlarmKind.AUTO_STOP.name, alarmAt = at)
                scheduler.schedule(AlarmKind.AUTO_STOP, cur.id, at)
            }
            store.update(updated)
            updated
        }
        if (s != null) effects.ringAlarm(s, stopFailed = s.stopStatus == SmsStatus.FAILED)
    }

    private suspend fun autoStop(sessionId: Long) {
        val cur = mutex.withLock { store.getActive() }
        if (cur == null || cur.id != sessionId || cur.alarmKind != AlarmKind.AUTO_STOP.name) return
        val result = stop(EndReason.AUTO_STOP)
        if (result is StopResult.Stopped) effects.showAutoStopped(result.session)
    }

    private suspend fun arm(session: Session): Session {
        val end = TimerMath.endTime(session.startedAt, session.timerMinutes)
        val kind: AlarmKind? = when {
            end == null -> null
            session.mode == TimerMode.AUTO_STOP -> AlarmKind.AUTO_STOP
            else -> AlarmKind.RING
        }
        val reminderAt = TimerMath.reminderTime(session.startedAt, session.timerMinutes, session.remindMinutesBefore)
        val updated = session.copy(
            alarmKind = kind?.name,
            alarmAt = if (kind != null) end else null,
            reminderAt = reminderAt
        )
        store.update(updated)
        if (kind != null && end != null) scheduler.schedule(kind, updated.id, end)
        if (reminderAt != null) scheduler.schedule(AlarmKind.REMINDER, updated.id, reminderAt)
        return updated
    }

    private fun cancelAlarms(sessionId: Long) {
        for (kind in AlarmKind.values()) scheduler.cancel(kind, sessionId)
    }

    private suspend fun sendWithRetry(number: String, text: String): SmsSendResult {
        var result = smsSender.send(number, text)
        if (!result.success && result.retryable) {
            delay(RETRY_DELAY_MS)
            result = smsSender.send(number, text)
        }
        return result
    }

    private fun appendLog(existing: String?, label: String, number: String, text: String, r: SmsSendResult): String {
        val tag = if (r.simulated) " [TEST]" else ""
        val outcome = if (r.success) "OK" else "FAILED (${r.error ?: "unknown"})"
        val line = "${Instant.ofEpochMilli(clock.now())} $label$tag -> $number: $text = $outcome"
        return if (existing.isNullOrEmpty()) line else existing + "\n" + line
    }

    private companion object {
        const val RETRY_DELAY_MS = 1_500L
        const val STUCK_SENDING_MS = 120_000L
        const val REPLY_WINDOW_MS = 30 * 60_000L
    }
}
