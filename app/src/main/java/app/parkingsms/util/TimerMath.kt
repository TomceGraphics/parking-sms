package app.parkingsms.util

object TimerMath {
    const val MINUTE_MS = 60_000L

    fun endTime(startedAt: Long, timerMinutes: Int?): Long? =
        if (timerMinutes == null || timerMinutes <= 0) null else startedAt + timerMinutes * MINUTE_MS

    /** Pre-end reminder time, or null if not applicable (no timer, or reminder not shorter than timer). */
    fun reminderTime(startedAt: Long, timerMinutes: Int?, remindBeforeMinutes: Int?): Long? {
        if (timerMinutes == null || timerMinutes <= 0) return null
        if (remindBeforeMinutes == null || remindBeforeMinutes <= 0 || remindBeforeMinutes >= timerMinutes) return null
        return startedAt + (timerMinutes - remindBeforeMinutes) * MINUTE_MS
    }

    fun elapsedMillis(startedAt: Long, now: Long): Long = (now - startedAt).coerceAtLeast(0L)

    fun remainingMillis(endAt: Long, now: Long): Long = (endAt - now).coerceAtLeast(0L)

    fun graceDeadline(now: Long, graceMinutes: Int): Long = now + graceMinutes * MINUTE_MS

    fun snoozeTime(now: Long, snoozeMinutes: Int): Long = now + snoozeMinutes * MINUTE_MS
}
