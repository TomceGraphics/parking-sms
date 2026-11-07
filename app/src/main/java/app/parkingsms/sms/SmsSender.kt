package app.parkingsms.sms

import android.util.Log
import app.parkingsms.data.SettingsStore
import kotlinx.coroutines.delay

data class SmsSendResult(
    val success: Boolean,
    val error: String? = null,
    /** False when retrying immediately makes no sense (missing permission, unknown outcome). */
    val retryable: Boolean = true,
    /** True when nothing was really sent (Test mode). */
    val simulated: Boolean = false
)

interface SmsSender {
    suspend fun send(number: String, text: String): SmsSendResult
}

/** Never touches SmsManager: only logs what would have been sent. */
class TestSmsSender : SmsSender {
    override suspend fun send(number: String, text: String): SmsSendResult {
        Log.i("ParkingSms", "TEST MODE: would send \"$text\" to $number")
        delay(400)
        return SmsSendResult(success = true, simulated = true)
    }
}

/** Picks the real or the test sender at send time, based on the Test mode setting. */
class SwitchingSmsSender(
    private val settings: SettingsStore,
    private val real: SmsSender,
    private val test: SmsSender
) : SmsSender {
    override suspend fun send(number: String, text: String): SmsSendResult =
        if (settings.snapshot().testMode) test.send(number, text) else real.send(number, text)
}
