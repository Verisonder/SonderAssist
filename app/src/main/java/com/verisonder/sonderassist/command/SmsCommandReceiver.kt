package com.verisonder.sonderassist.command

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.verisonder.sonderassist.CrashLog
import com.verisonder.sonderassist.Settings

/**
 * Control the phone by text, from any handset.
 *
 * The owner texts their chosen word and a command — "FRIDAY locate" — and the phone does it
 * and texts the answer back. The word is the whole gate: nothing runs unless the message
 * begins with it, so an unset word means the feature does nothing even when switched on.
 * That gate is what makes it usable from a stranger's phone, which is the point — a lost
 * phone is found by borrowing someone else's and texting your own.
 *
 * SMS_RECEIVED is a protected broadcast — only the system can send it — and the receiver is
 * further guarded by BROADCAST_SMS in the manifest, so no other app can forge a command.
 * The secret word is what gates the owner's own texts on top of that.
 *
 * The command itself is read and carried out by [Commands], shared with chat/RCS messages.
 */
class SmsCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!Settings.smsCommands(context)) return

        val messages = runCatching {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        }.getOrNull() ?: return
        if (messages.isEmpty()) return

        val sender = messages.first().originatingAddress ?: return
        // A long text arrives as several parts; join them back before reading the command.
        val body = messages.joinToString("") { it.messageBody.orEmpty() }

        Commands.handle(context, body, sender, null, "sms") { text -> sendSms(context, sender, text) }
    }

    companion object {
        /** True if the text went out. */
        fun sendSms(context: Context, to: String, text: String): Boolean {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Settings.noteCommand(context, "cannot text back - sending not allowed")
                return false
            }
            return runCatching {
                val sms = context.getSystemService(android.telephony.SmsManager::class.java)
                // Over 160 characters a plain send is silently truncated, and on a locate
                // reply the map link is at the end - the part worth having.
                sms.sendMultipartTextMessage(to, null, sms.divideMessage(text), null, null)
            }.onFailure {
                CrashLog.record(context, "The command reply could not be sent", it)
            }.isSuccess
        }
    }
}
