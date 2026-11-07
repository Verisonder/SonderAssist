package com.verisonder.sonderassist.command

import android.content.Context
import android.os.SystemClock
import com.verisonder.sonderassist.Settings
import com.verisonder.sonderassist.media.Alarm
import com.verisonder.sonderassist.media.Friday
import com.verisonder.sonderassist.report.Reporter
import com.verisonder.sonderassist.security.DeviceAdminLocker
import com.verisonder.sonderassist.sensor.WatchService

/**
 * One place that reads a command and does it, whichever way it arrived: a plain SMS
 * ([SmsCommandReceiver]) or a chat/RCS message seen in a notification ([ChatCommandListener]).
 *
 * The same text can arrive both ways - an SMS also raises the messaging app's notification -
 * so the same message within 15 seconds is handled once.
 */
object Commands {

    private const val DEDUP_MS = 15_000L

    private var lastBody: String? = null
    private var lastAt = 0L

    /**
     * @param number the sender's number, when known (always for SMS; sometimes for chat).
     * @param name the sender's display name, for chat messages where only that is known.
     * @param via "sms" or "chat", for the trail.
     * @param reply sends the answer back the way the command came. Null when it cannot.
     */
    @Synchronized
    fun handle(
        context: Context,
        body: String,
        number: String?,
        name: String?,
        via: String,
        reply: ((String) -> Boolean)?,
    ): Boolean {
        if (!Settings.smsCommands(context)) return false
        val word = Settings.secretWord(context)
        // No word means no gate, and no gate means the phone must not act on anything.
        if (word.isBlank()) return false
        val text = body.trim()
        // Case-insensitive on the word and the command, so a keyboard that capitalises the
        // first letter of a message does not silently break it.
        if (!text.lowercase().startsWith(word.lowercase())) return false

        val now = SystemClock.elapsedRealtime()
        val key = text.lowercase()
        if (key == lastBody && now - lastAt < DEDUP_MS) return true
        lastBody = key
        lastAt = now

        val who = number ?: name ?: "unknown"
        // The word matched, so this is meant for the phone: worth a line even when refused,
        // or "texted from someone not on the list" looks the same as "never arrived".
        if (!Settings.commandAllowed(context, number, name)) {
            Settings.noteCommand(context, "ignored $who ($via) - not a chosen contact")
            return true
        }

        val rest = text.substring(word.length).trim()
        val w = Settings.commandWords(context)
        val parts = rest.lowercase().split(Regex("\\s+"))
        val command = parts.getOrNull(0).orEmpty()
        // Live is "locate live": the locate command with the live word after it.
        val live = command == w.locate && parts.getOrNull(1) == w.live
        // "10sec" or "10 sec": the number, then the countdown word.
        val seconds = Regex("""^(\d+)\s*${Regex.escape(w.countdown)}""")
            .find(rest.lowercase())?.groupValues?.get(1)?.toIntOrNull()
            ?.takeIf { it > 0 }?.coerceAtMost(3_600)
        val list = listOfNotNull(
            w.locate, "${w.locate} ${w.live}", w.stop, w.lock, w.find, "10${w.countdown}", w.play.ifEmpty { null }, "help",
        )
            .joinToString(" | ")

        // Answered later, once a fresh position has come in.
        var later: ((String) -> Unit) -> Unit = { }
        var deferred = false

        val answer = when {
            seconds != null -> {
                Friday.command(context, Friday.Command.Countdown(seconds))
                "SonderAssist: siren in ${seconds}s. Unlock to stop it."
            }
            w.play.isNotEmpty() && command == w.play ->
                if (Friday.command(context, Friday.Command.Play)) {
                    "SonderAssist: playing F.R.I.D.A.Y."
                } else {
                    "SonderAssist: F.R.I.D.A.Y already played for this alert."
                }
            command == "" || command == "help" -> "SonderAssist commands: $word $list"
            live -> {
                val to = reply
                if (to == null) {
                    "SonderAssist: live cannot answer this chat - send it as SMS."
                } else {
                    // A few seconds first, so the first update carries a fresh fix. Every
                    // update goes back the way this came: SMS, or the same RCS chat.
                    Reporter.start(context, delaySeconds = 5, alsoTo = { text -> to(text) })
                    "SonderAssist: live location on - ${Settings.reportCount(context)} updates, " +
                        "one every ${Settings.reportIntervalSeconds(context)}s. Text \"$word ${w.stop}\" to end it."
                }
            }
            command == w.locate -> {
                deferred = true
                later = { send -> Reporter.locateFresh(context, send) }
                ""
            }
            command == w.stop -> {
                Reporter.stop(context)
                "SonderAssist: location stopped."
            }
            command == w.find -> {
                Alarm.ringNow(context)
                "SonderAssist: ringing now for 30s."
            }
            // Theft mode, as if it had been grabbed, with the F.R.I.D.A.Y clip. If the
            // service cannot be reached, a plain lock is still better than nothing.
            command == w.lock -> when {
                WatchService.theftByText(context) -> "SonderAssist: locked, theft mode on."
                DeviceAdminLocker.lockNow(context) -> "SonderAssist: locked (theft mode could not start)."
                else -> "SonderAssist: could not lock - device admin is off or no screen lock is set."
            }
            else -> "SonderAssist: unknown command '$command'. Try $list."
        }

        // Arrival is logged before the reply is attempted, so "got the command but could not
        // answer" is never confused with "never got the command".
        Settings.noteCommand(context, "${command.ifEmpty { "help" }} from $who ($via)")
        val send = { text: String ->
            when {
                reply == null -> Settings.noteCommand(context, "cannot reply - no number or reply button")
                reply(text) -> Settings.noteCommand(context, "replied to $who")
                else -> Settings.noteCommand(context, "reply failed")
            }
        }
        if (deferred) later(send) else send(answer)
        return true
    }
}
