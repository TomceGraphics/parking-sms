package app.parkingsms.ui.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.parkingsms.R
import app.parkingsms.ui.PanelCard
import app.parkingsms.ui.StatusPill
import app.parkingsms.ui.AppTopBar

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    Scaffold(topBar = { AppTopBar(title = stringResource(R.string.perm_title), onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            PermissionChecklist()
        }
    }
}

/** Live checklist, shared by onboarding step 3 and the Settings screen. */
@Composable
fun PermissionChecklist() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    val sms = remember(tick) { PermissionChecks.smsGranted(context) }
    val notif = remember(tick) { PermissionChecks.notificationsEnabled(context) }
    val exact = remember(tick) { PermissionChecks.exactAlarmsAllowed(context) }
    val fullScreen = remember(tick) { PermissionChecks.fullScreenAllowed(context) }
    val battery = remember(tick) { PermissionChecks.batteryUnrestricted(context) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PermissionCard(
            title = stringResource(R.string.perm_sms_title),
            description = stringResource(R.string.perm_sms_desc),
            granted = sms,
            actionLabel = stringResource(R.string.perm_action_grant),
            onAction = { smsLauncher.launch(Manifest.permission.SEND_SMS) }
        )
        PermissionCard(
            title = stringResource(R.string.perm_notif_title),
            description = stringResource(R.string.perm_notif_desc),
            granted = notif,
            actionLabel = stringResource(R.string.perm_action_grant),
            onAction = {
                if (Build.VERSION.SDK_INT >= 33) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    PermissionChecks.open(context, PermissionChecks.notificationSettingsIntent(context))
                }
            }
        )
        PermissionCard(
            title = stringResource(R.string.perm_exact_title),
            description = stringResource(R.string.perm_exact_desc),
            granted = exact,
            actionLabel = stringResource(R.string.perm_action_open),
            onAction = { PermissionChecks.open(context, PermissionChecks.exactAlarmIntent(context)) }
        )
        PermissionCard(
            title = stringResource(R.string.perm_fullscreen_title),
            description = stringResource(R.string.perm_fullscreen_desc),
            granted = fullScreen,
            actionLabel = stringResource(R.string.perm_action_open),
            onAction = { PermissionChecks.open(context, PermissionChecks.fullScreenIntent(context)) }
        )
        PermissionCard(
            title = stringResource(R.string.perm_battery_title),
            description = stringResource(R.string.perm_battery_desc),
            granted = battery,
            actionLabel = stringResource(R.string.perm_action_open_battery),
            onAction = { PermissionChecks.open(context, PermissionChecks.appDetailsIntent(context)) }
        )
        // Sideloaded apps on Android 13+: SMS permission can be blocked until "Allow restricted settings".
        PanelCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.perm_restricted_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.perm_restricted_desc), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { PermissionChecks.open(context, PermissionChecks.appDetailsIntent(context)) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(20.dp)
            ) { Text(stringResource(R.string.perm_action_open_app_info)) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { smsLauncher.launch(Manifest.permission.SEND_SMS) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(20.dp)
            ) { Text(stringResource(R.string.perm_action_retry_sms)) }
        }
        Text(
            stringResource(R.string.perm_samsung_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            StatusPill(
                text = stringResource(if (granted) R.string.perm_status_granted else R.string.perm_status_missing),
                container = if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                content = if (granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium)
        if (!granted) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(20.dp)
            ) { Text(actionLabel) }
        }
    }
}
