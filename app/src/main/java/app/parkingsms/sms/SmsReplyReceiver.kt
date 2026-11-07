package app.parkingsms.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.parkingsms.receiver.launchAsync

/** Only does anything when "Reply parsing" is on (and RECEIVE_SMS has been granted). */
class SmsReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages == null || messages.isEmpty()) return
        val from = messages[0].originatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }
        launchAsync(context) { c ->
            if (c.settingsStore.snapshot().replyParsing) {
                c.sessionManager.onReply(from, body)
            }
        }
    }
}
