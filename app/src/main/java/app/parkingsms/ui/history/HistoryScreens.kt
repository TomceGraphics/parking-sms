@file:OptIn(ExperimentalMaterial3Api::class)

package app.parkingsms.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.parkingsms.R
import app.parkingsms.container
import app.parkingsms.data.Session
import app.parkingsms.data.reason
import app.parkingsms.data.startStatus
import app.parkingsms.data.stopStatus
import app.parkingsms.ui.PanelCard
import app.parkingsms.ui.PlateBadge
import app.parkingsms.ui.SectionLabel
import app.parkingsms.ui.StatusPill
import app.parkingsms.ui.InfoRow
import app.parkingsms.data.EndReason
import app.parkingsms.ui.AppTopBar
import app.parkingsms.ui.ConfirmDialog
import app.parkingsms.ui.UiFormat
import app.parkingsms.util.Formatters
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val repo = context.container.repository
    val scope = rememberCoroutineScope()
    val sessions by repo.sessions.collectAsStateWithLifecycle(initialValue = emptyList())
    var deleting by remember { mutableStateOf<Session?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = stringResource(R.string.history_title), onBack = onBack) }
    ) { padding ->
        if (sessions.isEmpty()) {
            Column(modifier = Modifier.padding(padding).padding(24.dp)) {
                Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = sessions, key = { it.id }) { s ->
                    HistoryItem(
                        session = s,
                        onClick = { onOpen(s.id) },
                        onDelete = { deleting = s }
                    )
                }
            }
        }
    }

    val del = deleting
    if (del != null) {
        ConfirmDialog(
            title = stringResource(R.string.history_delete_title),
            text = stringResource(R.string.history_delete_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                deleting = null
                scope.launch { repo.deleteSession(del.id) }
            },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun EndPill(session: Session) {
    val cs = MaterialTheme.colorScheme
    val reason = if (session.active) null else session.reason
    val (container, content) = when (reason) {
        null -> cs.tertiary to cs.onTertiary
        EndReason.FAILED -> cs.errorContainer to cs.onErrorContainer
        EndReason.AUTO_STOP -> cs.tertiaryContainer to cs.onTertiaryContainer
        else -> cs.primaryContainer to cs.onPrimaryContainer
    }
    StatusPill(text = stringResource(UiFormat.endReasonRes(reason)), container = container, content = content)
}

@Composable
private fun HistoryItem(session: Session, onClick: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val ended = session.endedAt
    PanelCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.notif_zone_plate, session.cityNameSnapshot, session.zoneCode),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    UiFormat.dateTime(context, session.startedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        enabled = !session.active,
                        onClick = {
                            menu = false
                            onDelete()
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlateBadge(session.plateText, large = false)
            Spacer(Modifier.weight(1f))
            Text(
                if (ended != null) Formatters.clock(ended - session.startedAt) else "-",
                style = MaterialTheme.typography.titleLarge
            )
        }
        Spacer(Modifier.height(10.dp))
        EndPill(session)
    }
}

@Composable
fun HistoryDetailScreen(sessionId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = context.container.repository
    val session by repo.session(sessionId).collectAsStateWithLifecycle(initialValue = null)
    val s = session

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = stringResource(R.string.history_detail_title), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (s == null) {
                Text(stringResource(R.string.history_not_found), style = MaterialTheme.typography.bodyLarge)
            } else {
                val ended = s.endedAt
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    PlateBadge(s.plateText)
                    Spacer(Modifier.height(12.dp))
                    EndPill(s)
                    Spacer(Modifier.height(8.dp))
                    InfoRow(stringResource(R.string.label_city), "${s.cityNameSnapshot} (${s.smsNumberSnapshot})")
                    InfoRow(stringResource(R.string.label_zone), s.zoneCode)
                }
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    InfoRow(stringResource(R.string.detail_started), UiFormat.dateTime(context, s.startedAt))
                    InfoRow(stringResource(R.string.detail_stopped), if (ended != null) UiFormat.dateTime(context, ended) else "-")
                    InfoRow(stringResource(R.string.detail_duration), if (ended != null) Formatters.clock(ended - s.startedAt) else "-")
                    InfoRow(
                        stringResource(R.string.detail_timer),
                        s.timerMinutes?.let { stringResource(R.string.minutes_fmt, it) } ?: stringResource(R.string.timer_none)
                    )
                }
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    InfoRow(stringResource(R.string.detail_start_sms), stringResource(UiFormat.smsStatusRes(s.startStatus)))
                    InfoRow(stringResource(R.string.detail_stop_sms), stringResource(UiFormat.smsStatusRes(s.stopStatus)))
                }
                val reply = s.replyText
                if (!reply.isNullOrEmpty()) {
                    PanelCard(modifier = Modifier.fillMaxWidth()) {
                        SectionLabel(stringResource(R.string.label_reply))
                        Spacer(Modifier.height(6.dp))
                        Text(reply, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                val log = s.smsLog
                if (!log.isNullOrEmpty()) {
                    PanelCard(modifier = Modifier.fillMaxWidth()) {
                        SectionLabel(stringResource(R.string.detail_sms_log))
                        Spacer(Modifier.height(6.dp))
                        Text(log, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
