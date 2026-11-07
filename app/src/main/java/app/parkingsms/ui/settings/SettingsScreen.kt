@file:OptIn(ExperimentalMaterial3Api::class)

package app.parkingsms.ui.settings

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.parkingsms.AppContainer
import app.parkingsms.R
import app.parkingsms.container
import app.parkingsms.data.AppSettings
import app.parkingsms.data.ThemeMode
import app.parkingsms.data.TimerMode
import app.parkingsms.ui.AppTopBar
import app.parkingsms.ui.PanelCard
import app.parkingsms.ui.SectionLabel
import app.parkingsms.ui.StepperRow
import app.parkingsms.ui.SwitchRow
import app.parkingsms.ui.UiEvent
import app.parkingsms.ui.permissions.PermissionChecks
import app.parkingsms.util.CsvExporter
import app.parkingsms.util.MessageTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    private val store = c.settingsStore
    val settings = store.settings
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    fun setTheme(m: ThemeMode) {
        viewModelScope.launch { store.setThemeMode(m) }
    }
    fun setDynamic(b: Boolean) {
        viewModelScope.launch { store.setDynamicColor(b) }
    }
    fun setDefaultMode(m: TimerMode) {
        viewModelScope.launch { store.setDefaultMode(m) }
    }
    fun setGrace(v: Int) {
        viewModelScope.launch { store.setGraceMinutes(v) }
    }
    fun setSnooze(v: Int) {
        viewModelScope.launch { store.setSnoozeMinutes(v) }
    }
    fun setConfirm(b: Boolean) {
        viewModelScope.launch { store.setConfirmBeforePay(b) }
    }
    fun setTestMode(b: Boolean) {
        viewModelScope.launch { store.setTestMode(b) }
    }
    fun setReplyParsing(b: Boolean) {
        viewModelScope.launch { store.setReplyParsing(b) }
    }

    fun saveTemplates(start: String, stop: String) {
        viewModelScope.launch {
            if (MessageTemplate.render(start, "X", "Y").isEmpty() || stop.trim().isEmpty()) {
                _events.tryEmit(UiEvent(R.string.error_bad_template))
            } else {
                store.setTemplates(start.trim(), stop.trim())
                _events.tryEmit(UiEvent(R.string.templates_saved))
            }
        }
    }

    fun exportCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val sessions = c.repository.allSessions()
                val csv = CsvExporter.toCsv(sessions)
                withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("No output stream")
                    out.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                }
                _events.tryEmit(UiEvent(R.string.export_done, listOf(sessions.size)))
            } catch (e: Exception) {
                _events.tryEmit(UiEvent(R.string.export_failed, listOf(e.message ?: "")))
            }
        }
    }

    companion object {
        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(c) }
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCities: () -> Unit,
    onPlates: () -> Unit,
    onPermissions: () -> Unit
) {
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(context.container))
    val s by vm.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.events.collect { ev -> snackbar.showSnackbar(context.getString(ev.resId, *ev.args.toTypedArray())) }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(context, uri)
    }
    val receiveSmsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // RECEIVE_SMS is requested only when the toggle is switched on.
        vm.setReplyParsing(granted)
    }

    var startTemplate by remember(s.startTemplate) { mutableStateOf(s.startTemplate) }
    var stopTemplate by remember(s.stopTemplate) { mutableStateOf(s.stopTemplate) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = stringResource(R.string.settings_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionLabel(stringResource(R.string.settings_section_data), Modifier.padding(start = 8.dp, top = 4.dp))
            PanelCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                LinkRow(stringResource(R.string.settings_cities), stringResource(R.string.settings_cities_desc), onCities)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LinkRow(stringResource(R.string.settings_plates), stringResource(R.string.settings_plates_desc), onPlates)
            }

            SectionLabel(stringResource(R.string.settings_section_appearance), Modifier.padding(start = 8.dp, top = 12.dp))
            PanelCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeChip(stringResource(R.string.theme_system), s.themeMode == ThemeMode.SYSTEM) { vm.setTheme(ThemeMode.SYSTEM) }
                    ThemeChip(stringResource(R.string.theme_light), s.themeMode == ThemeMode.LIGHT) { vm.setTheme(ThemeMode.LIGHT) }
                    ThemeChip(stringResource(R.string.theme_dark), s.themeMode == ThemeMode.DARK) { vm.setTheme(ThemeMode.DARK) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    title = stringResource(R.string.settings_dynamic),
                    subtitle = stringResource(R.string.settings_dynamic_desc),
                    checked = s.dynamicColor,
                    onCheckedChange = vm::setDynamic
                )
            }

            SectionLabel(stringResource(R.string.settings_section_timer), Modifier.padding(start = 8.dp, top = 12.dp))
            PanelCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                Text(
                    stringResource(R.string.settings_default_mode),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp)
                )
                ModeRow(s.defaultMode == TimerMode.ALARM_ONLY, stringResource(R.string.mode_alarm_only)) { vm.setDefaultMode(TimerMode.ALARM_ONLY) }
                ModeRow(s.defaultMode == TimerMode.AUTO_STOP, stringResource(R.string.mode_auto_stop)) { vm.setDefaultMode(TimerMode.AUTO_STOP) }
                ModeRow(
                    s.defaultMode == TimerMode.ALARM_THEN_AUTO_STOP,
                    stringResource(R.string.mode_alarm_then_auto, s.graceMinutes)
                ) { vm.setDefaultMode(TimerMode.ALARM_THEN_AUTO_STOP) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                StepperRow(stringResource(R.string.settings_grace), s.graceMinutes, 1, 60, vm::setGrace)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                StepperRow(stringResource(R.string.settings_snooze), s.snoozeMinutes, 1, 60, vm::setSnooze)
            }

            SectionLabel(stringResource(R.string.settings_section_behavior), Modifier.padding(start = 8.dp, top = 12.dp))
            PanelCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                SwitchRow(
                    title = stringResource(R.string.settings_confirm),
                    subtitle = stringResource(R.string.settings_confirm_desc),
                    checked = s.confirmBeforePay,
                    onCheckedChange = vm::setConfirm
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    title = stringResource(R.string.settings_test_mode),
                    subtitle = stringResource(R.string.settings_test_mode_desc),
                    checked = s.testMode,
                    onCheckedChange = vm::setTestMode
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    title = stringResource(R.string.settings_reply),
                    subtitle = stringResource(R.string.settings_reply_desc),
                    checked = s.replyParsing,
                    onCheckedChange = { on ->
                        if (!on) {
                            vm.setReplyParsing(false)
                        } else if (PermissionChecks.receiveSmsGranted(context)) {
                            vm.setReplyParsing(true)
                        } else {
                            receiveSmsLauncher.launch(Manifest.permission.RECEIVE_SMS)
                        }
                    }
                )
            }

            SectionLabel(stringResource(R.string.settings_section_advanced), Modifier.padding(start = 8.dp, top = 12.dp))
            PanelCard(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = startTemplate,
                    onValueChange = { startTemplate = it },
                    label = { Text(stringResource(R.string.settings_start_template)) },
                    supportingText = { Text(stringResource(R.string.settings_template_help)) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = stopTemplate,
                    onValueChange = { stopTemplate = it },
                    label = { Text(stringResource(R.string.settings_stop_template)) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.saveTemplates(startTemplate, stopTemplate) },
                        shape = RoundedCornerShape(16.dp)
                    ) { Text(stringResource(R.string.action_save)) }
                    OutlinedButton(
                        onClick = {
                            startTemplate = MessageTemplate.DEFAULT_START
                            stopTemplate = MessageTemplate.DEFAULT_STOP
                        },
                        shape = RoundedCornerShape(16.dp)
                    ) { Text(stringResource(R.string.action_reset)) }
                }
            }

            SectionLabel(stringResource(R.string.settings_section_more), Modifier.padding(start = 8.dp, top = 12.dp))
            PanelCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                LinkRow(stringResource(R.string.settings_permissions), stringResource(R.string.settings_permissions_desc), onPermissions)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LinkRow(stringResource(R.string.settings_export), stringResource(R.string.settings_export_desc)) {
                    exportLauncher.launch("parking-sms-history.csv")
                }
            }

            SectionLabel(stringResource(R.string.settings_section_about), Modifier.padding(start = 8.dp, top = 12.dp))
            PanelCard(modifier = Modifier.fillMaxWidth()) {
                AboutBlock(context)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AboutBlock(context: Context) {
    val version = remember {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.about_source), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ThemeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.heightIn(min = 48.dp)
    )
}

@Composable
private fun ModeRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
