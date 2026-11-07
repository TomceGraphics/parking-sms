package app.parkingsms.session

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import app.parkingsms.data.AlarmKind
import app.parkingsms.receiver.AlarmReceiver

class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {
    private val alarmManager: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= 31) alarmManager.canScheduleExactAlarms() else true

    override fun schedule(kind: AlarmKind, sessionId: Long, atMillis: Long) {
        val pi = pendingIntent(kind, sessionId)
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                // Visible warning is shown in the UI; the alarm may be late but is not lost.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    override fun cancel(kind: AlarmKind, sessionId: Long) {
        alarmManager.cancel(pendingIntent(kind, sessionId))
    }

    private fun pendingIntent(kind: AlarmKind, sessionId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_ALARM)
            .setData(Uri.parse("parkingsms://alarm/${kind.name}/$sessionId"))
            .putExtra(AlarmReceiver.EXTRA_KIND, kind.name)
            .putExtra(AlarmReceiver.EXTRA_SESSION_ID, sessionId)
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
