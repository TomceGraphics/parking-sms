package app.parkingsms.util

import app.parkingsms.data.Session
import java.time.Instant

object CsvExporter {
    private val header = listOf(
        "id", "city", "sms_number", "zone", "plate", "started_at", "ended_at", "duration_seconds",
        "timer_minutes", "timer_mode", "end_reason", "start_sms_status", "stop_sms_status", "reply", "sms_log"
    )

    fun toCsv(sessions: List<Session>): String {
        val sb = StringBuilder()
        sb.append(header.joinToString(",")).append("\r\n")
        for (s in sessions) {
            val ended = s.endedAt
            val row = listOf(
                s.id.toString(),
                s.cityNameSnapshot,
                s.smsNumberSnapshot,
                s.zoneCode,
                s.plateText,
                Instant.ofEpochMilli(s.startedAt).toString(),
                if (ended != null) Instant.ofEpochMilli(ended).toString() else "",
                if (ended != null) ((ended - s.startedAt) / 1000L).toString() else "",
                s.timerMinutes?.toString() ?: "",
                s.timerMode,
                s.endReason ?: "",
                s.startSmsStatus,
                s.stopSmsStatus,
                s.replyText ?: "",
                s.smsLog ?: ""
            )
            sb.append(row.joinToString(",") { escape(it) }).append("\r\n")
        }
        return sb.toString()
    }

    fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }
}
