@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.parkingsms.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.parkingsms.R
import app.parkingsms.container
import app.parkingsms.data.City
import app.parkingsms.data.RecentZone
import app.parkingsms.data.Session
import app.parkingsms.data.SmsStatus
import app.parkingsms.data.TimerMode
import app.parkingsms.data.endsAt
import app.parkingsms.data.startStatus
import app.parkingsms.data.stopStatus
import app.parkingsms.ui.BigButton
import app.parkingsms.ui.CityEditDialog
import app.parkingsms.ui.ConfirmDialog
import app.parkingsms.ui.HeaderIconButton
import app.parkingsms.ui.HeaderPanel
import app.parkingsms.ui.InfoRow
import app.parkingsms.ui.LogoMark
import app.parkingsms.ui.NumberInputDialog
import app.parkingsms.ui.PanelCard
import app.parkingsms.ui.PlateBadge
import app.parkingsms.ui.SectionLabel
import app.parkingsms.ui.StatusPill
import app.parkingsms.ui.TimerRing
import app.parkingsms.ui.UiFormat
import app.parkingsms.ui.permissions.PermissionChecks
import app.parkingsms.ui.rememberNow
import app.parkingsms.util.Formatters
import app.parkingsms.util.TimerMath

private val PRESETS = listOf(30, 60, 120)

@Composable
fun DashboardScreen(
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    onPermissions: () -> Unit
) {
    val context = LocalContext.current
    val container = context.container
    val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(container))
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val smsOk = remember(tick) { PermissionChecks.smsGranted(context) }
    val notifOk = remember(tick) { PermissionChecks.notificationsEnabled(context) }
    val exactOk = remember(tick) { PermissionChecks.exactAlarmsAllowed(context) }

    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            snackbar.showSnackbar(context.getString(ev.resId, *ev.args.toTypedArray()))
        }
    }

    var confirmPay by remember { mutableStateOf(false) }
    var showAddCity by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var zoneToRemove by remember { mutableStateOf<RecentZone?>(null) }

    val state = ui

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DashboardHeader(onHistory = onHistory, onSettings = onSettings)
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state != null) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.navigationBarsPadding()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            val active = state.active
                            if (active != null) {
                                StopBar(session = active, onStop = vm::stop)
                            } else {
                                PayBar(
                                    state = state,
                                    onPay = { if (state.settings.confirmBeforePay) confirmPay = true else vm.pay() }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (state == null) return@Scaffold
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (state.settings.testMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.tertiary)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.test_mode_banner),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiary
                    )
                }
            }
            if (!smsOk && !state.settings.testMode) {
                WarningCard(stringResource(R.string.warn_sms_permission), onPermissions)
            }
            if (!notifOk) {
                WarningCard(stringResource(R.string.warn_notifications), onPermissions)
            }
            val timerInUse = state.active?.timerMinutes != null || (state.active == null && state.form.timerMinutes != null)
            if (!exactOk && timerInUse) {
                WarningCard(stringResource(R.string.warn_exact_alarms), onPermissions)
            }

            val active = state.active
            if (active == null) {
                CityCard(
                    cities = state.cities,
                    selected = state.selectedCity,
                    onSelect = vm::selectCity,
                    onAddCity = { showAddCity = true }
                )
                ZoneCard(
                    zone = state.form.zone,
                    zones = state.zones,
                    onZone = vm::setZone,
                    onLongPress = { zoneToRemove = it }
                )
                if (state.plates.isEmpty()) {
                    WarningCard(stringResource(R.string.warn_no_plate), onSettings)
                } else {
                    PlateCard(
                        plates = state.plates.map { it.id to it.plateText },
                        selectedId = state.selectedPlate?.id,
                        onSelect = vm::selectPlate
                    )
                }
                TimerCard(
                    timerMinutes = state.form.timerMinutes,
                    mode = state.form.mode,
                    graceMinutes = state.settings.graceMinutes,
                    remind = state.form.remind,
                    remindMinutes = state.form.remindMinutes,
                    onTimer = vm::setTimer,
                    onCustom = { showCustom = true },
                    onMode = vm::setMode,
                    onRemind = vm::setRemind,
                    onRemindMinutes = vm::setRemindMinutes
                )
            } else {
                ActiveBody(session = active)
            }
        }
    }

    if (confirmPay && state != null) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_pay_title),
            text = stringResource(
                R.string.confirm_pay_text,
                state.selectedCity?.name ?: "",
                state.form.zone,
                state.selectedPlate?.plateText ?: ""
            ),
            confirmLabel = stringResource(R.string.pay),
            onConfirm = {
                confirmPay = false
                vm.pay()
            },
            onDismiss = { confirmPay = false }
        )
    }
    if (showAddCity) {
        CityEditDialog(
            initial = null,
            onDismiss = { showAddCity = false },
            onSave = { name, number ->
                showAddCity = false
                vm.addCity(name, number)
            }
        )
    }
    if (showCustom) {
        NumberInputDialog(
            title = stringResource(R.string.custom_timer_title),
            label = stringResource(R.string.custom_timer_label),
            initial = state?.form?.timerMinutes?.takeIf { it !in PRESETS },
            max = 1440,
            onDismiss = { showCustom = false },
            onConfirm = {
                showCustom = false
                vm.setTimer(it)
            }
        )
    }
    val removing = zoneToRemove
    if (removing != null) {
        ConfirmDialog(
            title = stringResource(R.string.zone_remove_title),
            text = stringResource(R.string.zone_remove_text, removing.zoneCode),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = {
                vm.removeZone(removing)
                zoneToRemove = null
            },
            onDismiss = { zoneToRemove = null }
        )
    }
}

// ---------- Header ----------

@Composable
private fun DashboardHeader(onHistory: () -> Unit, onSettings: () -> Unit) {
    HeaderPanel {
        Row(modifier = Modifier.height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoMark()
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            HeaderIconButton(onHistory, Icons.AutoMirrored.Filled.List, stringResource(R.string.cd_history))
            Spacer(Modifier.width(8.dp))
            HeaderIconButton(onSettings, Icons.Filled.Settings, stringResource(R.string.cd_settings))
        }
    }
}

@Composable
private fun CityCard(
    cities: List<City>,
    selected: City?,
    onSelect: (Long) -> Unit,
    onAddCity: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        PanelCard(modifier = Modifier.fillMaxWidth(), onClick = { expanded = true }) {
            SectionLabel(stringResource(R.string.label_city))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        selected?.name ?: stringResource(R.string.add_city_item),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1
                    )
                    if (selected != null) {
                        Text(
                            selected.smsNumber,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    stringResource(R.string.action_change),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            cities.forEach { city ->
                DropdownMenuItem(
                    text = { Text("${city.name} (${city.smsNumber})") },
                    onClick = {
                        expanded = false
                        onSelect(city.id)
                    }
                )
            }
            if (cities.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_city_item)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    onAddCity()
                }
            )
        }
    }
}

// ---------- Idle cards ----------

@Composable
private fun WarningCard(text: String, onFix: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onFix, shape = RoundedCornerShape(14.dp)) {
                Text(stringResource(R.string.action_fix))
            }
        }
    }
}

@Composable
private fun ZoneCard(
    zone: String,
    zones: List<RecentZone>,
    onZone: (String) -> Unit,
    onLongPress: (RecentZone) -> Unit
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.label_zone))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = zone,
            onValueChange = onZone,
            placeholder = {
                Text(
                    stringResource(R.string.zone_placeholder),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            },
            textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 3.sp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        )
        if (zones.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items = zones, key = { it.id }) { z ->
                    ZoneChip(
                        label = z.zoneCode,
                        selected = z.zoneCode == zone,
                        onClick = { onZone(z.zoneCode) },
                        onLongClick = { onLongPress(z) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.zone_chip_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ZoneChip(label: String, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val bg = if (selected) cs.primaryContainer else Color.Transparent
    val fg = if (selected) cs.onPrimaryContainer else cs.onSurface
    val borderColor = if (selected) cs.primaryContainer else cs.outlineVariant
    val longLabel = stringResource(R.string.cd_zone_chip_remove)
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(bg)
            .border(2.dp, borderColor, shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = longLabel,
                role = Role.Button
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = fg, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun PlateCard(
    plates: List<Pair<Long, String>>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = plates.firstOrNull { it.first == selectedId }?.second ?: plates.firstOrNull()?.second ?: ""
    val multiple = plates.size > 1
    Box {
        PanelCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = if (multiple) { { expanded = true } } else null
        ) {
            SectionLabel(stringResource(R.string.label_plate))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlateBadge(selectedText)
                Spacer(Modifier.weight(1f))
                if (multiple) {
                    Text(
                        stringResource(R.string.action_change),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            plates.forEach { (id, text) ->
                DropdownMenuItem(
                    text = { Text(text, style = MaterialTheme.typography.titleMedium) },
                    onClick = {
                        expanded = false
                        onSelect(id)
                    }
                )
            }
        }
    }
}

@Composable
private fun TimerCard(
    timerMinutes: Int?,
    mode: TimerMode,
    graceMinutes: Int,
    remind: Boolean,
    remindMinutes: Int,
    onTimer: (Int?) -> Unit,
    onCustom: () -> Unit,
    onMode: (TimerMode) -> Unit,
    onRemind: (Boolean) -> Unit,
    onRemindMinutes: (Int) -> Unit
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.label_timer))
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimerTile(stringResource(R.string.timer_none), timerMinutes == null) { onTimer(null) }
            TimerTile(stringResource(R.string.timer_30m), timerMinutes == 30) { onTimer(30) }
            TimerTile(stringResource(R.string.timer_1h), timerMinutes == 60) { onTimer(60) }
            TimerTile(stringResource(R.string.timer_2h), timerMinutes == 120) { onTimer(120) }
            val isCustom = timerMinutes != null && timerMinutes !in PRESETS
            TimerTile(
                text = if (isCustom) stringResource(R.string.timer_custom_value, timerMinutes ?: 0)
                else stringResource(R.string.timer_custom),
                selected = isCustom,
                onClick = onCustom
            )
        }
        if (timerMinutes != null) {
            Spacer(Modifier.height(18.dp))
            SectionLabel(stringResource(R.string.label_timer_mode))
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceRow(mode == TimerMode.ALARM_ONLY, stringResource(R.string.mode_alarm_only)) {
                    onMode(TimerMode.ALARM_ONLY)
                }
                ChoiceRow(mode == TimerMode.AUTO_STOP, stringResource(R.string.mode_auto_stop)) {
                    onMode(TimerMode.AUTO_STOP)
                }
                ChoiceRow(
                    mode == TimerMode.ALARM_THEN_AUTO_STOP,
                    stringResource(R.string.mode_alarm_then_auto, graceMinutes)
                ) { onMode(TimerMode.ALARM_THEN_AUTO_STOP) }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(selected = remind, role = Role.Switch, onClick = { onRemind(!remind) }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.remind_toggle),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = remind, onCheckedChange = null)
            }
            if (remind) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = remindMinutes.toString(),
                    onValueChange = { text -> text.filter { it.isDigit() }.take(3).toIntOrNull()?.let(onRemindMinutes) },
                    label = { Text(stringResource(R.string.remind_minutes_label)) },
                    singleLine = true,
                    isError = remindMinutes >= timerMinutes,
                    supportingText = if (remindMinutes >= timerMinutes) {
                        { Text(stringResource(R.string.remind_too_long)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun TimerTile(text: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val bg = if (selected) cs.primaryContainer else Color.Transparent
    val fg = if (selected) cs.onPrimaryContainer else cs.onSurface
    val borderColor = if (selected) cs.primaryContainer else cs.outlineVariant
    Row(
        modifier = Modifier
            .widthIn(min = 76.dp)
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(bg)
            .border(2.dp, borderColor, shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val borderColor = if (selected) cs.primary else cs.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 8.dp))
    }
}

// ---------- Pay / Stop ----------

@Composable
private fun PayBar(state: DashboardUi, onPay: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    BigButton(
        label = stringResource(R.string.pay),
        sublabel = if (state.canPay) {
            stringResource(
                R.string.pay_summary,
                state.form.zone,
                state.selectedPlate?.plateText ?: "",
                state.selectedCity?.name ?: ""
            )
        } else null,
        onClick = onPay,
        enabled = state.canPay,
        container = cs.primary,
        content = cs.onPrimary
    )
}

@Composable
private fun StopBar(session: Session, onStop: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val stopFailed = session.stopStatus == SmsStatus.FAILED
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (stopFailed) {
            Text(
                stringResource(R.string.active_stop_unconfirmed),
                style = MaterialTheme.typography.titleSmall,
                color = cs.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        BigButton(
            label = stringResource(if (stopFailed) R.string.alarm_retry else R.string.stop_parking),
            onClick = onStop,
            container = cs.error,
            content = cs.onError
        )
    }
}

// ---------- Active session ----------

@Composable
private fun ActiveBody(session: Session) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val now by rememberNow()
    val elapsed = TimerMath.elapsedMillis(session.startedAt, now)
    val end = session.endsAt
    val total = session.timerMinutes?.let { it * 60_000L }
    val progress = if (total != null && total > 0L) elapsed.toFloat() / total.toFloat()
    else (elapsed % 60_000L).toFloat() / 60_000f
    val ended = end != null && now >= end
    val ringColor = if (ended) cs.error else cs.primary

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        StatusPill(
            text = stringResource(R.string.active_title),
            container = cs.primaryContainer,
            content = cs.onPrimaryContainer,
            dot = true
        )
        TimerRing(
            progress = progress,
            color = ringColor,
            track = cs.outlineVariant,
            diameter = 292.dp,
            stroke = 20.dp
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SectionLabel(stringResource(R.string.active_elapsed_label))
                Text(
                    Formatters.clock(elapsed),
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 44.sp),
                    color = cs.onBackground
                )
                Spacer(Modifier.height(4.dp))
                if (end != null) {
                    val remaining = end - now
                    Text(
                        if (remaining > 0) stringResource(R.string.active_ends_in, Formatters.clock(remaining))
                        else stringResource(R.string.active_timer_ended),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (ended) cs.error else cs.onSurfaceVariant
                    )
                } else {
                    Text(
                        stringResource(R.string.active_no_timer),
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }

        PanelCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlateBadge(session.plateText)
            }
            Spacer(Modifier.height(8.dp))
            InfoRow(stringResource(R.string.label_city), session.cityNameSnapshot)
            HorizontalDivider(color = cs.outlineVariant)
            InfoRow(stringResource(R.string.label_zone), session.zoneCode)
            HorizontalDivider(color = cs.outlineVariant)
            InfoRow(
                stringResource(R.string.detail_start_sms),
                stringResource(UiFormat.smsStatusRes(session.startStatus))
            )
            if (end != null) {
                HorizontalDivider(color = cs.outlineVariant)
                InfoRow(
                    stringResource(R.string.active_ends_at_label),
                    UiFormat.timeOfDay(context, end)
                )
            }
        }

        val reply = session.replyText
        if (!reply.isNullOrEmpty()) {
            PanelCard(modifier = Modifier.fillMaxWidth()) {
                SectionLabel(stringResource(R.string.label_reply))
                Spacer(Modifier.height(6.dp))
                Text(reply, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
