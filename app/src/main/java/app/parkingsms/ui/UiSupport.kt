package app.parkingsms.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import app.parkingsms.R
import app.parkingsms.data.EndReason
import app.parkingsms.data.SmsStatus
import kotlinx.coroutines.delay
import java.util.Date

/** One-shot message for a snackbar. */
data class UiEvent(@StringRes val resId: Int, val args: List<Any> = emptyList())

@Composable
fun rememberNow(): State<Long> = produceState(initialValue = System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(1000)
    }
}

object UiFormat {
    fun timeOfDay(context: Context, millis: Long): String =
        android.text.format.DateFormat.getTimeFormat(context).format(Date(millis))

    fun dateTime(context: Context, millis: Long): String =
        android.text.format.DateFormat.getMediumDateFormat(context).format(Date(millis)) + " " +
            android.text.format.DateFormat.getTimeFormat(context).format(Date(millis))

    @StringRes
    fun smsStatusRes(status: SmsStatus): Int = when (status) {
        SmsStatus.NONE -> R.string.sms_status_none
        SmsStatus.SENDING -> R.string.sms_status_sending
        SmsStatus.SENT -> R.string.sms_status_sent
        SmsStatus.FAILED -> R.string.sms_status_failed
    }

    @StringRes
    fun endReasonRes(reason: EndReason?): Int = when (reason) {
        EndReason.MANUAL -> R.string.end_manual
        EndReason.AUTO_STOP -> R.string.end_auto
        EndReason.ALARM_STOPPED -> R.string.end_alarm
        EndReason.FAILED -> R.string.end_failed
        null -> R.string.end_in_progress
    }
}
