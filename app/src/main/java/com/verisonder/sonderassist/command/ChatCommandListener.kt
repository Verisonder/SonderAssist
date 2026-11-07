package com.verisonder.sonderassist.command

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.verisonder.sonderassist.Settings

/**
 * Commands that arrive as RCS or chat messages.
 *
 * Android hands apps plain SMS only; an RCS message lives inside the messaging app and never
 * reaches [SmsCommandReceiver]. What does reach this app, with notification access, is the
 * notification that message raises - sender and text. So the command is read from there,
 * and the answer goes back through the notification's own Reply button, which keeps it in
 * the same RCS conversation. If there is no Reply button and the sender's number is known,
 * it goes back as SMS.
 *
 * Only messaging apps are read: the phone's default SMS app and the usual ones. Nothing
 * else in the notification shade is looked at.
 *
 * A handled command's notification is removed, so whoever has the phone does not see the
 * secret word sitting in the shade.
 */
class ChatCommandListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val context: Context = this
        if (!Settings.chatCommands(context) || !Settings.smsCommands(context)) return
        if (sbn.packageName !in messagingApps(context)) return
        val n = sbn.notification ?: return
        // Group summaries repeat the newest message; reading them would run it twice.
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras ?: return
        val (body, name, number) = latestMessage(extras) ?: return

        val replyAction = n.actions?.firstOrNull { action ->
            action.remoteInputs?.any { it.allowFreeFormInput } == true
        }
        val reply: ((String) -> Boolean)? = when {
            replyAction != null -> { text -> sendInline(replyAction, text) }
            number != null -> { text -> SmsCommandReceiver.sendSms(context, number, text) }
            else -> null
        }

        val handled = Commands.handle(context, body, number, name, "chat", reply)
        if (handled) runCatching { cancelNotification(sbn.key) }
    }

    /** Text, sender name, and number when the notification gives one. */
    private fun latestMessage(extras: Bundle): Triple<String, String?, String?>? {
        // MessagingStyle: the last message is the newest.
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        val last = messages?.lastOrNull() as? Bundle
        if (last != null) {
            val text = last.getCharSequence("text")?.toString()
            if (!text.isNullOrBlank()) {
                val person = last.get("sender_person") as? android.app.Person
                val name = person?.name?.toString() ?: last.getCharSequence("sender")?.toString()
                val number = person?.uri?.let { uri ->
                    uri.removePrefix("tel:").takeIf { uri.startsWith("tel:") }
                } ?: name?.takeIf { looksLikeNumber(it) }
                return Triple(text, name, number)
            }
        }
        // Plain notification: the title is the sender, the text is the message.
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        return Triple(text, title, title?.takeIf { looksLikeNumber(it) })
    }

    private fun looksLikeNumber(s: String): Boolean {
        val digits = s.count { it.isDigit() }
        return digits >= 6 && s.all { it.isDigit() || it in "+ -()." }
    }

    private fun sendInline(action: Notification.Action, text: String): Boolean = runCatching {
        val inputs = action.remoteInputs ?: return false
        val results = Bundle().apply {
            inputs.forEach { putCharSequence(it.resultKey, text) }
        }
        val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        RemoteInput.addResultsToIntent(inputs, intent, results)
        action.actionIntent.send(this, 0, intent)
    }.isSuccess

    companion object {
        private val KNOWN = setOf(
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.samsung.android.messaging",
            "com.android.messaging",
        )

        fun messagingApps(context: Context): Set<String> =
            KNOWN + listOfNotNull(Telephony.Sms.getDefaultSmsPackage(context))

        fun hasAccess(context: Context): Boolean {
            val me = ComponentName(context, ChatCommandListener::class.java).flattenToString()
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
            return enabled.split(':').any { it == me }
        }

        fun accessIntent(): Intent =
            Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
}
