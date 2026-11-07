package app.parkingsms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.parkingsms.AppContainer
import app.parkingsms.container
import app.parkingsms.data.AlarmKind
import app.parkingsms.data.EndReason
import kotlinx.coroutines.launch

/** Runs [block] on the app scope while keeping the receiver alive (goAsync). */
fun BroadcastReceiver.launchAsync(context: Context, block: suspend (AppContainer) -> Unit) {
    val pending = goAsync()
    val container = context.container
    container.appScope.launch {
        try {
            block(container)
        } catch (t: Throwable) {
            Log.e("ParkingSms", "Receiver work failed", t)
        } finally {
            pending.finish()
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM) return
        val kind = AlarmKind.fromName(intent.getStringExtra(EXTRA_KIND)) ?: return
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        launchAsync(context) { c -> c.sessionManager.onAlarm(kind, sessionId) }
    }

    companion object {
        const val ACTION_ALARM = "app.parkingsms.action.ALARM"
        const val EXTRA_KIND = "kind"
        const val EXTRA_SESSION_ID = "session_id"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            launchAsync(context) { c ->
                c.notifications.ensureChannels()
                c.sessionManager.rearm()
            }
        }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        launchAsync(context) { c ->
            when (action) {
                ACTION_STOP -> c.sessionManager.stop(EndReason.MANUAL)
                ACTION_ALARM_STOP -> c.sessionManager.stop(EndReason.ALARM_STOPPED)
                ACTION_SNOOZE -> c.sessionManager.snooze()
                else -> Unit
            }
        }
    }

    companion object {
        const val ACTION_STOP = "app.parkingsms.action.STOP"
        const val ACTION_ALARM_STOP = "app.parkingsms.action.ALARM_STOP"
        const val ACTION_SNOOZE = "app.parkingsms.action.SNOOZE"
    }
}
