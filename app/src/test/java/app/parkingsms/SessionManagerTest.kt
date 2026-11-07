package app.parkingsms

import app.parkingsms.data.AlarmKind
import app.parkingsms.data.EndReason
import app.parkingsms.data.Session
import app.parkingsms.data.SmsStatus
import app.parkingsms.data.TimerMode
import app.parkingsms.session.AlarmScheduler
import app.parkingsms.session.Clock
import app.parkingsms.session.SessionConfig
import app.parkingsms.session.SessionEffects
import app.parkingsms.session.SessionManager
import app.parkingsms.session.SessionStore
import app.parkingsms.session.StartRequest
import app.parkingsms.session.StartResult
import app.parkingsms.session.StopResult
import app.parkingsms.sms.SmsSendResult
import app.parkingsms.sms.SmsSender
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeClock(var t: Long = 10_000_000L) : Clock {
    override fun now(): Long = t
}

private class FakeStore : SessionStore {
    val rows = mutableListOf<Session>()
    private var nextId = 1L
    override suspend fun insert(session: Session): Long {
        val id = nextId++
        rows.add(session.copy(id = id))
        return id
    }

    override suspend fun update(session: Session) {
        val i = rows.indexOfFirst { it.id == session.id }
        if (i >= 0) rows[i] = session
    }

    override suspend fun getActive(): Session? = rows.lastOrNull { it.active }
    override suspend fun getLatest(): Session? = rows.lastOrNull()
}

private class FakeSms : SmsSender {
    val sent = mutableListOf<Pair<String, String>>()
    val results = ArrayDeque<SmsSendResult>()
    override suspend fun send(number: String, text: String): SmsSendResult {
        sent.add(number to text)
        return if (results.isEmpty()) SmsSendResult(true) else results.removeFirst()
    }
}

private class FakeScheduler : AlarmScheduler {
    val scheduled = mutableMapOf<AlarmKind, Long>()
    override fun canScheduleExact(): Boolean = true
    override fun schedule(kind: AlarmKind, sessionId: Long, atMillis: Long) {
        scheduled[kind] = atMillis
    }

    override fun cancel(kind: AlarmKind, sessionId: Long) {
        scheduled.remove(kind)
    }
}

private class FakeEffects : SessionEffects {
    var ringCount = 0
    var lastRingStopFailed: Boolean? = null
    var dismissCount = 0
    var reminders = 0
    var autoStopped = false
    var lastChanged: Session? = null
    override suspend fun sessionChanged(session: Session?) {
        lastChanged = session
    }

    override suspend fun ringAlarm(session: Session, stopFailed: Boolean) {
        ringCount++
        lastRingStopFailed = stopFailed
    }

    override suspend fun dismissAlarm() {
        dismissCount++
    }

    override suspend fun showReminder(session: Session) {
        reminders++
    }

    override suspend fun showAutoStopped(session: Session) {
        autoStopped = true
    }
}

private class FakeConfig : SessionConfig {
    override suspend fun startTemplate(): String = "{zone} {plate}"
    override suspend fun stopTemplate(): String = "S"
    override suspend fun snoozeMinutes(): Int = 5
}

private class Harness {
    val clock = FakeClock()
    val store = FakeStore()
    val sms = FakeSms()
    val scheduler = FakeScheduler()
    val effects = FakeEffects()
    val manager = SessionManager(store, sms, scheduler, effects, clock, FakeConfig())

    fun request(
        timerMinutes: Int? = 60,
        mode: TimerMode = TimerMode.ALARM_ONLY,
        remind: Int? = null
    ) = StartRequest(
        cityId = 1, cityName = "Ohrid", smsNumber = "1234", zoneCode = "b9", plateText = "bt-1110 ab",
        timerMinutes = timerMinutes, mode = mode, graceMinutes = 5, remindMinutesBefore = remind
    )
}

class SessionManagerTest {
    private val minute = 60_000L

    @Test
    fun start_sendsRenderedMessage_andSchedulesRing() = runTest {
        val h = Harness()
        val r = h.manager.start(h.request(timerMinutes = 60, mode = TimerMode.ALARM_ONLY))
        assertTrue(r is StartResult.Started)
        assertEquals("1234" to "B9 BT1110AB", h.sms.sent.single())
        assertEquals(h.clock.t + 60 * minute, h.scheduler.scheduled[AlarmKind.RING])
        val s = h.store.getActive()!!
        assertEquals(SmsStatus.SENT.name, s.startSmsStatus)
        assertEquals("B9", s.zoneCode)
        assertEquals("BT1110AB", s.plateText)
    }

    @Test
    fun start_withoutTimer_schedulesNothing() = runTest {
        val h = Harness()
        h.manager.start(h.request(timerMinutes = null))
        assertTrue(h.scheduler.scheduled.isEmpty())
        assertNull(h.store.getActive()!!.alarmKind)
    }

    @Test
    fun start_withReminder_schedulesReminderBeforeEnd() = runTest {
        val h = Harness()
        h.manager.start(h.request(timerMinutes = 60, remind = 10))
        assertEquals(h.clock.t + 50 * minute, h.scheduler.scheduled[AlarmKind.REMINDER])
        h.clock.t += 50 * minute
        h.manager.onAlarm(AlarmKind.REMINDER, 1)
        assertEquals(1, h.effects.reminders)
    }

    @Test
    fun secondStart_isIgnored_whileActive() = runTest {
        val h = Harness()
        h.manager.start(h.request())
        val second = h.manager.start(h.request())
        assertTrue(second is StartResult.AlreadyActive)
        assertEquals(1, h.sms.sent.size)
    }

    @Test
    fun startSmsFailure_retriesOnce_thenDoesNotStartSession() = runTest {
        val h = Harness()
        h.sms.results.add(SmsSendResult(false, "boom"))
        h.sms.results.add(SmsSendResult(false, "boom"))
        val r = h.manager.start(h.request())
        assertTrue(r is StartResult.SmsFailed)
        assertEquals(2, h.sms.sent.size)
        assertNull(h.store.getActive())
        val row = h.store.rows.single()
        assertFalse(row.active)
        assertEquals(EndReason.FAILED.name, row.endReason)
        assertTrue(h.scheduler.scheduled.isEmpty())
    }

    @Test
    fun startSmsFailure_firstAttemptOnly_retrySucceeds() = runTest {
        val h = Harness()
        h.sms.results.add(SmsSendResult(false, "boom"))
        val r = h.manager.start(h.request())
        assertTrue(r is StartResult.Started)
        assertEquals(2, h.sms.sent.size)
    }

    @Test
    fun nonRetryableFailure_isNotRetried() = runTest {
        val h = Harness()
        h.sms.results.add(SmsSendResult(false, "no permission", retryable = false))
        val r = h.manager.start(h.request())
        assertTrue(r is StartResult.SmsFailed)
        assertEquals(1, h.sms.sent.size)
    }

    @Test
    fun manualStop_sendsS_endsSession_cancelsAlarms() = runTest {
        val h = Harness()
        h.manager.start(h.request(remind = 10))
        h.clock.t += 20 * minute
        val r = h.manager.stop(EndReason.MANUAL)
        assertTrue(r is StopResult.Stopped)
        assertEquals("1234" to "S", h.sms.sent.last())
        assertNull(h.store.getActive())
        val row = h.store.rows.single()
        assertEquals(EndReason.MANUAL.name, row.endReason)
        assertEquals(h.clock.t, row.endedAt)
        assertTrue(h.scheduler.scheduled.isEmpty())
        assertNull(h.effects.lastChanged)
    }

    @Test
    fun stop_withoutActiveSession_doesNothing() = runTest {
        val h = Harness()
        assertTrue(h.manager.stop() is StopResult.NoActive)
        assertTrue(h.sms.sent.isEmpty())
    }

    @Test
    fun stopFailure_keepsSessionActive_andRingsFailureAlarm() = runTest {
        val h = Harness()
        h.manager.start(h.request())
        h.sms.results.add(SmsSendResult(false, "boom"))
        h.sms.results.add(SmsSendResult(false, "boom"))
        val r = h.manager.stop(EndReason.MANUAL)
        assertTrue(r is StopResult.Failed)
        val s = h.store.getActive()!!
        assertEquals(SmsStatus.FAILED.name, s.stopSmsStatus)
        assertEquals(true, h.effects.lastRingStopFailed)
        // Retry succeeds and finishes the session.
        val again = h.manager.stop(EndReason.MANUAL)
        assertTrue(again is StopResult.Stopped)
        assertNull(h.store.getActive())
    }

    @Test
    fun alarmOnly_ringsAtEnd_andDoesNotAutoStop() = runTest {
        val h = Harness()
        h.manager.start(h.request(mode = TimerMode.ALARM_ONLY))
        h.clock.t += 60 * minute
        h.manager.onAlarm(AlarmKind.RING, 1)
        assertEquals(1, h.effects.ringCount)
        assertEquals(false, h.effects.lastRingStopFailed)
        assertNull(h.scheduler.scheduled[AlarmKind.AUTO_STOP])
        assertEquals(1, h.sms.sent.size)
    }

    @Test
    fun autoStopMode_sendsStopAtEnd() = runTest {
        val h = Harness()
        h.manager.start(h.request(mode = TimerMode.AUTO_STOP))
        assertEquals(h.clock.t + 60 * minute, h.scheduler.scheduled[AlarmKind.AUTO_STOP])
        h.clock.t += 60 * minute
        h.manager.onAlarm(AlarmKind.AUTO_STOP, 1)
        assertEquals("1234" to "S", h.sms.sent.last())
        assertNull(h.store.getActive())
        assertEquals(EndReason.AUTO_STOP.name, h.store.rows.single().endReason)
        assertTrue(h.effects.autoStopped)
    }

    @Test
    fun alarmThenAutoStop_ringsThenStopsAfterGrace() = runTest {
        val h = Harness()
        h.manager.start(h.request(mode = TimerMode.ALARM_THEN_AUTO_STOP))
        h.clock.t += 60 * minute
        h.manager.onAlarm(AlarmKind.RING, 1)
        assertEquals(1, h.effects.ringCount)
        assertEquals(h.clock.t + 5 * minute, h.scheduler.scheduled[AlarmKind.AUTO_STOP])
        h.clock.t += 5 * minute
        h.manager.onAlarm(AlarmKind.AUTO_STOP, 1)
        assertNull(h.store.getActive())
        assertEquals(EndReason.AUTO_STOP.name, h.store.rows.single().endReason)
    }

    @Test
    fun userStopBeforeGrace_makesLateAutoStopHarmless() = runTest {
        val h = Harness()
        h.manager.start(h.request(mode = TimerMode.ALARM_THEN_AUTO_STOP))
        h.clock.t += 60 * minute
        h.manager.onAlarm(AlarmKind.RING, 1)
        h.manager.stop(EndReason.ALARM_STOPPED)
        val sentBefore = h.sms.sent.size
        h.clock.t += 5 * minute
        h.manager.onAlarm(AlarmKind.AUTO_STOP, 1)
        assertEquals(sentBefore, h.sms.sent.size)
        assertEquals(EndReason.ALARM_STOPPED.name, h.store.rows.single().endReason)
    }

    @Test
    fun snooze_cancelsAutoStop_andReschedulesRing() = runTest {
        val h = Harness()
        h.manager.start(h.request(mode = TimerMode.ALARM_THEN_AUTO_STOP))
        h.clock.t += 60 * minute
        h.manager.onAlarm(AlarmKind.RING, 1)
        assertTrue(h.manager.snooze())
        assertNull(h.scheduler.scheduled[AlarmKind.AUTO_STOP])
        assertEquals(h.clock.t + 5 * minute, h.scheduler.scheduled[AlarmKind.RING])
        // Ringing again after the snooze restarts the grace period.
        h.clock.t += 5 * minute
        h.manager.onAlarm(AlarmKind.RING, 1)
        assertEquals(2, h.effects.ringCount)
        assertEquals(h.clock.t + 5 * minute, h.scheduler.scheduled[AlarmKind.AUTO_STOP])
    }

    @Test
    fun duplicateRing_isIgnored() = runTest {
        val h = Harness()
        h.manager.start(h.request(mode = TimerMode.ALARM_ONLY))
        h.clock.t += 60 * minute
        h.manager.onAlarm(AlarmKind.RING, 1)
        h.manager.onAlarm(AlarmKind.RING, 1)
        assertEquals(1, h.effects.ringCount)
    }

    @Test
    fun rearm_afterReboot_reschedulesPendingAlarms() = runTest {
        val h = Harness()
        h.manager.start(h.request(timerMinutes = 60, remind = 10))
        val end = h.scheduler.scheduled[AlarmKind.RING]
        h.scheduler.scheduled.clear() // simulate reboot wiping AlarmManager
        h.clock.t += 5 * minute
        h.manager.rearm()
        assertEquals(end, h.scheduler.scheduled[AlarmKind.RING])
        assertTrue(h.scheduler.scheduled.containsKey(AlarmKind.REMINDER))
        assertEquals(1L, h.effects.lastChanged?.id)
    }

    @Test
    fun rearm_overdueAlarm_firesImmediately() = runTest {
        val h = Harness()
        h.manager.start(h.request(timerMinutes = 30, mode = TimerMode.ALARM_ONLY))
        h.clock.t += 45 * minute // phone was off past the end time
        h.manager.rearm()
        assertEquals(1, h.effects.ringCount)
    }

    @Test
    fun invalidInput_isRejected_withoutSending() = runTest {
        val h = Harness()
        val r = h.manager.start(h.request().copy(zoneCode = " - "))
        assertTrue(r is StartResult.Invalid)
        assertTrue(h.sms.sent.isEmpty())
        assertTrue(h.store.rows.isEmpty())
    }

    @Test
    fun reply_isStoredOnMatchingSession() = runTest {
        val h = Harness()
        h.manager.start(h.request())
        h.manager.onReply("1234", "Parking started until 12:00")
        h.manager.onReply("9999", "spam")
        assertEquals("Parking started until 12:00", h.store.getActive()!!.replyText)
    }

    @Test
    fun smsLog_recordsBothMessages() = runTest {
        val h = Harness()
        h.manager.start(h.request())
        h.manager.stop()
        val log = h.store.rows.single().smsLog!!
        assertTrue(log.contains("START"))
        assertTrue(log.contains("STOP"))
        assertTrue(log.contains("B9 BT1110AB"))
    }
}
