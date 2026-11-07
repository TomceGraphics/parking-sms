package app.parkingsms.ui.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object PermissionChecks {
    fun smsGranted(c: Context): Boolean =
        ContextCompat.checkSelfPermission(c, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun receiveSmsGranted(c: Context): Boolean =
        ContextCompat.checkSelfPermission(c, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    fun notificationsEnabled(c: Context): Boolean = NotificationManagerCompat.from(c).areNotificationsEnabled()

    fun exactAlarmsAllowed(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 31) c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() else true

    fun fullScreenAllowed(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 34) c.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() else true

    fun batteryUnrestricted(c: Context): Boolean =
        c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

    private fun packageUri(c: Context): Uri = Uri.parse("package:" + c.packageName)

    fun appDetailsIntent(c: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(c))

    fun notificationSettingsIntent(c: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)

    fun exactAlarmIntent(c: Context): Intent =
        if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(c))
        else appDetailsIntent(c)

    fun fullScreenIntent(c: Context): Intent =
        if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri(c))
        else appDetailsIntent(c)

    /** Starts a settings page; falls back to the app info page if the device has no such screen. */
    fun open(c: Context, intent: Intent) {
        try {
            c.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            try {
                c.startActivity(appDetailsIntent(c))
            } catch (e2: ActivityNotFoundException) {
                // Nothing else we can do.
            }
        }
    }
}
