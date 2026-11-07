package app.parkingsms.sms

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import app.parkingsms.R
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/** Sends a text through the default SMS subscription and waits for the "sent" result. */
class AndroidSmsSender(private val context: Context) : SmsSender {
    private val counter = AtomicInteger(0)

    override suspend fun send(number: String, text: String): SmsSendResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return SmsSendResult(false, context.getString(R.string.sms_err_no_permission), retryable = false)
        }
        val requestId = counter.incrementAndGet()
        val action = "app.parkingsms.SMS_SENT.$requestId.${System.nanoTime()}"
        var receiver: BroadcastReceiver? = null
        return try {
            withTimeout(SEND_TIMEOUT_MS) {
                suspendCancellableCoroutine<SmsSendResult> { cont ->
                    val r = object : BroadcastReceiver() {
                        override fun onReceive(c: Context, intent: Intent) {
                            val code = resultCode
                            val result = if (code == Activity.RESULT_OK) {
                                SmsSendResult(true)
                            } else {
                                SmsSendResult(false, describe(code), retryable = true)
                            }
                            if (cont.isActive) cont.resume(result)
                        }
                    }
                    receiver = r
                    ContextCompat.registerReceiver(
                        context, r, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                    val sentIntent = PendingIntent.getBroadcast(
                        context,
                        requestId,
                        Intent(action).setPackage(context.packageName),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
                    )
                    try {
                        manager().sendTextMessage(number, null, text, sentIntent, null)
                    } catch (e: Exception) {
                        if (cont.isActive) {
                            cont.resume(
                                SmsSendResult(false, e.message ?: e.javaClass.simpleName, retryable = false)
                            )
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            SmsSendResult(false, context.getString(R.string.sms_err_timeout), retryable = false)
        } finally {
            receiver?.let { runCatching { context.unregisterReceiver(it) } }
        }
    }

    private fun manager(): SmsManager {
        if (Build.VERSION.SDK_INT >= 31) {
            val sm = context.getSystemService(SmsManager::class.java)
            if (sm != null) return sm
        }
        val subId = SmsManager.getDefaultSmsSubscriptionId()
        return if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            SmsManager.getSmsManagerForSubscriptionId(subId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    private fun describe(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> context.getString(R.string.sms_err_generic)
        SmsManager.RESULT_ERROR_RADIO_OFF -> context.getString(R.string.sms_err_radio_off)
        SmsManager.RESULT_ERROR_NO_SERVICE -> context.getString(R.string.sms_err_no_service)
        else -> context.getString(R.string.sms_err_unknown, code)
    }

    private companion object {
        const val SEND_TIMEOUT_MS = 45_000L
    }
}
