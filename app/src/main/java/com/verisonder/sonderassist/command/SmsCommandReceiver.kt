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
import com.verisonder.sonderassist.media.Alarm
import com.verisonder.sonderassist.report.Reporter
import com.verisonder.sonderassist.security.DeviceAdminLocker

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
 * It reuses the parts that already exist: [Reporter.locateText] for the position and
 * battery, [Alarm.ringNow] for the sound, [DeviceAdminLocker] for the lock. Nothing new
 * reaches the phone's hardware from here.
 */
class SmsCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!Settings.smsCommands(context)) return

        val word = Settings.secretWord(context)
        // No word means no gate, and no gate means the phone must not act on anything.
        if (word.isBlank()) return

        val messages = runCatching {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        }.getOrNull() ?: return
        if (messages.isEmpty()) return

        val sender = messages.first().originatingAddress ?: return
        // A long text arrives as several parts; join them back before reading the command.
        val body = messages.joinToString("") { it.messageBody.orEmpty() }.trim()

        // Case-insensitive on the word and the command, so a keyboard that capitalises the
        // first letter of a message does not silently break it.
        if (!body.lowercase().startsWith(word.lowercase())) return
        val rest = body.substring(word.length).trim()
        val command = rest.substringBefore(' ').lowercase()

        val reply = when (command) {
            "", "help" -> "SonderAssist commands: $word locate | ring | lock | help"
            "locate" -> Reporter.locateText(context)
            "ring" -> {
                Alarm.ringNow(context)
                "SonderAssist: ringing now for 30s."
            }
            "lock" ->
                if (DeviceAdminLocker.lockNow(context)) {
                    "SonderAssist: locked."
                } else {
                    "SonderAssist: could not lock - device admin is off or no screen lock is set."
                }
            else -> "SonderAssist: unknown command '$command'. Try locate, ring, lock or help."
        }

        // Arrival is logged before the reply is attempted, so "got the command but could not
        // text back" is never confused with "never got the command" (an empty trail).
        Settings.noteCommand(context, "$command from $sender")
        sendReply(context, sender, reply)
    }

    private fun sendReply(context: Context, to: String, text: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Settings.noteCommand(context, "cannot text back - sending not allowed")
            return
        }
        runCatching {
            val sms = context.getSystemService(android.telephony.SmsManager::class.java)
            // Over 160 characters a plain send is silently truncated, and on a locate reply
            // the map link is at the end - the part worth having.
            sms.sendMultipartTextMessage(to, null, sms.divideMessage(text), null, null)
            Settings.noteCommand(context, "replied to $to")
        }.onFailure {
            CrashLog.record(context, "The command reply could not be sent", it)
            Settings.noteCommand(context, "reply failed")
        }
    }
}
