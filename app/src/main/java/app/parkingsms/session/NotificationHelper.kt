package app.parkingsms.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.provider.Settings
import androidx.core.app.NotificationCompat
import app.parkingsms.MainActivity
import app.parkingsms.R
import app.parkingsms.data.Session
import app.parkingsms.data.SmsStatus
import app.parkingsms.data.startStatus
import app.parkingsms.data.stopStatus
import app.parkingsms.receiver.ActionReceiver
import app.parkingsms.ui.alarm.AlarmActivity

class NotificationHelper(private val context: Context) {
    private val nm: NotificationManager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        val session = NotificationChannel(
            CH_SESSION, context.getString(R.string.channel_session_name), NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_session_desc)
            setShowBadge(false)
        }

        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val alarm = NotificationChannel(
            CH_ALARM, context.getString(R.string.channel_alarm_name), NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_alarm_desc)
            setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, alarmAttrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }

        val reminder = NotificationChannel(
            CH_REMINDER, context.getString(R.string.channel_reminder_name), NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_reminder_desc)
        }

        val info = NotificationChannel(
            CH_INFO, context.getString(R.string.channel_info_name), NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_info_desc)
        }

        nm.createNotificationChannel(session)
        nm.createNotificationChannel(alarm)
        nm.createNotificationChannel(reminder)
        nm.createNotificationChannel(info)
    }

    fun showOngoing(session: Session) {
        val sub = when {
            session.stopStatus == SmsStatus.FAILED -> context.getString(R.string.notif_stop_failed)
            session.startStatus == SmsStatus.SENDING -> context.getString(R.string.notif_starting)
            else -> null
        }
        val b = NotificationCompat.Builder(context, CH_SESSION)
            .setSmallIcon(R.drawable.ic_stat_parking)
            .setContentTitle(context.getString(R.string.notif_ongoing_title, session.cityNameSnapshot))
            .setContentText(context.getString(R.string.notif_zone_plate, session.zoneCode, session.plateText))
            .setWhen(session.startedAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent())
            .addAction(0, context.getString(R.string.action_stop), actionIntent(ActionReceiver.ACTION_STOP, 1))
        if (sub != null) b.setSubText(sub)
        post(ID_SESSION, b.build())
    }

    fun cancelOngoing() = nm.cancel(ID_SESSION)

    fun showAlarm(session: Session, stopFailed: Boolean) {
        val title = context.getString(if (stopFailed) R.string.alarm_title_stop_failed else R.string.alarm_title)
        val text = context.getString(
            R.string.notif_city_zone_plate, session.cityNameSnapshot, session.zoneCode, session.plateText
        )
        val fullScreen = PendingIntent.getActivity(
            context,
            20,
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(context, CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat_parking)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
        if (stopFailed) {
            b.addAction(0, context.getString(R.string.alarm_retry), actionIntent(ActionReceiver.ACTION_ALARM_STOP, 3))
        } else {
            b.addAction(0, context.getString(R.string.action_stop), actionIntent(ActionReceiver.ACTION_ALARM_STOP, 3))
            b.addAction(0, context.getString(R.string.action_snooze), actionIntent(ActionReceiver.ACTION_SNOOZE, 4))
        }
        val n = b.build()
        // Repeat sound and vibration until the notification is cancelled.
        n.flags = n.flags or Notification.FLAG_INSISTENT
        post(ID_ALARM, n)
    }

    fun cancelAlarm() = nm.cancel(ID_ALARM)

    fun showReminder(session: Session) {
        val minutes = session.remindMinutesBefore ?: 0
        val b = NotificationCompat.Builder(context, CH_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_parking)
            .setContentTitle(context.getString(R.string.notif_reminder_title, minutes))
            .setContentText(
                context.getString(R.string.notif_city_zone_plate, session.cityNameSnapshot, session.zoneCode, session.plateText)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .addAction(0, context.getString(R.string.action_stop), actionIntent(ActionReceiver.ACTION_STOP, 2))
        post(ID_REMINDER, b.build())
    }

    fun showAutoStopped(session: Session) {
        showInfo(
            context.getString(R.string.notif_autostop_title),
            context.getString(R.string.notif_autostop_text, session.zoneCode, session.plateText)
        )
    }

    fun showInfo(title: String, text: String) {
        val b = NotificationCompat.Builder(context, CH_INFO)
            .setSmallIcon(R.drawable.ic_stat_parking)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
        post(ID_INFO, b.build())
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun actionIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, ActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun post(id: Int, notification: Notification) {
        try {
            nm.notify(id, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS missing; the dashboard shows a warning about it.
        }
    }

    companion object {
        const val CH_SESSION = "session"
        const val CH_ALARM = "alarm"
        const val CH_REMINDER = "reminder"
        const val CH_INFO = "info"
        const val ID_SESSION = 1001
        const val ID_ALARM = 1002
        const val ID_REMINDER = 1003
        const val ID_INFO = 1004
    }
}
