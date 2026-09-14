package com.verisonder.sonderassist

import android.content.Context
import android.net.Uri

/**
 * Everything the person has chosen, kept in plain SharedPreferences.
 *
 * It has to be readable by a boot receiver before any UI exists, and nothing here is
 * secret — a sensitivity number and a message the phone is going to display on its own
 * lock screen anyway.
 */
object Settings {

    private const val FILE = "settings"

    private const val ARMED = "armed"
    private const val SENSITIVITY = "sensitivity"
    private const val ALARM_ENABLED = "alarm_enabled"
    private const val ALARM_URI = "alarm_uri"
    private const val GRACE_SECONDS = "grace_seconds"
    private const val ALARM_REPEATS = "alarm_repeats"
    private const val MESSAGE = "message"
    private const val BACKGROUND_URI = "background_uri"
    private const val JARVIS = "jarvis"
    private const val TILE_NOTE = "tile_note"
    private const val ALERT_NOTE = "alert_note"
    private const val GUARD_ALERT = "guard_alert"
    private const val REPORT = "report"
    private const val SMS_NUMBER = "sms_number"
    private const val TG_TOKEN = "tg_token"
    private const val TG_CHAT = "tg_chat"
    private const val REPORT_DELAY = "report_delay"
    private const val REPORT_INTERVAL = "report_interval"
    private const val REPORT_COUNT = "report_count"
    private const val STATUS_EVERY = "status_every"
    private const val REPORT_NOTE = "report_note"
    private const val REPORT_RUNNING = "report_running"
    private const val ALERT_LIVE = "alert_live"
    private const val ALERT_QUIET = "alert_quiet"
    private const val TETHER = "tether"
    private const val TETHER_ADDRESS = "tether_address"
    private const val TETHER_NAME = "tether_name"
    private const val TETHER_GRACE = "tether_grace"
    private const val STRAP_NOTE = "strap_note"
    private const val TYPE_SPEED = "type_speed"
    private const val STRAP_MESSAGE = "strap_message"
    private const val STRAP_VIBRATE = "strap_vibrate"
    private const val STRAP_DELAY = "strap_delay"
    private const val CONNECTED = "connected"
    private const val UPRIGHT_GUARD = "upright_guard"
    private const val VIBRATE_ON_ALERT = "vibrate_on_alert"
    private const val BLOCK_POWER_MENU = "block_power_menu"
    private const val POWER_MENU_SUPPRESSED = "power_menu_suppressed"
    private const val SAVED_CHORD = "saved_chord"
    private const val SAVED_LONG_PRESS = "saved_long_press"

    /** 0 is the most cautious, 1 the most eager. Middle is the shipped default. */
    const val DEFAULT_SENSITIVITY = 0.5f

    /**
     * How long the phone waits, locked and silent, before the alarm sounds.
     *
     * This exists because of a trade the detector is built on: it fires on thin evidence
     * because a wrong lock costs one fingerprint touch. A wrong lock that also blares in
     * a quiet room costs a great deal more, and adding a siren without a grace period
     * would quietly invert the reasoning the thresholds were chosen under. Someone who
     * knows the PIN can stop it before it makes a sound; someone who does not, cannot.
     */
    const val DEFAULT_GRACE_SECONDS = 5

    /** A minute: long enough to unlock a false alarm, short enough to beat a power-off. */
    const val DEFAULT_REPORT_DELAY = 60
    const val DEFAULT_REPORT_INTERVAL = 120
    const val DEFAULT_REPORT_COUNT = 5

    /** An hour. */
    const val DEFAULT_STATUS_EVERY = 60

    const val DEFAULT_MESSAGE = "This phone is not yours."

    /** How many times the sound plays through before it stops on its own. */
    const val DEFAULT_ALARM_REPEATS = 3
    const val MAX_ALARM_REPEATS = 15

    private fun of(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Whether the person wants the watch running — not whether it is running.
     *
     * The distinction matters: the service can be killed by the system without anyone
     * changing their mind about it, and the boot receiver needs to know the intent rather
     * than the last observed state.
     */
    fun armed(context: Context): Boolean = of(context).getBoolean(ARMED, false)

    fun setArmed(context: Context, value: Boolean) {
        of(context).edit().putBoolean(ARMED, value).apply()
    }

    fun sensitivity(context: Context): Float =
        of(context).getFloat(SENSITIVITY, DEFAULT_SENSITIVITY).coerceIn(0f, 1f)

    fun setSensitivity(context: Context, value: Float) {
        of(context).edit().putFloat(SENSITIVITY, value.coerceIn(0f, 1f)).apply()
    }

    fun alarmEnabled(context: Context): Boolean = of(context).getBoolean(ALARM_ENABLED, false)

    fun setAlarmEnabled(context: Context, value: Boolean) {
        of(context).edit().putBoolean(ALARM_ENABLED, value).apply()
    }

    /** Null means the device's own alarm sound. */
    fun alarmUri(context: Context): Uri? =
        of(context).getString(ALARM_URI, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun setAlarmUri(context: Context, uri: Uri?) {
        of(context).edit().apply {
            if (uri == null) remove(ALARM_URI) else putString(ALARM_URI, uri.toString())
        }.apply()
    }

    fun alarmRepeats(context: Context): Int =
        of(context).getInt(ALARM_REPEATS, DEFAULT_ALARM_REPEATS).coerceIn(1, MAX_ALARM_REPEATS)

    fun setAlarmRepeats(context: Context, value: Int) {
        of(context).edit().putInt(ALARM_REPEATS, value.coerceIn(1, MAX_ALARM_REPEATS)).apply()
    }

    fun graceSeconds(context: Context): Int =
        of(context).getInt(GRACE_SECONDS, DEFAULT_GRACE_SECONDS).coerceIn(0, 60)

    fun setGraceSeconds(context: Context, value: Int) {
        of(context).edit().putInt(GRACE_SECONDS, value.coerceIn(0, 60)).apply()
    }

    /** Null means the plain colour background rather than a picture. */
    fun backgroundUri(context: Context): Uri? =
        of(context).getString(BACKGROUND_URI, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun setBackgroundUri(context: Context, uri: Uri?) {
        of(context).edit().apply {
            if (uri == null) remove(BACKGROUND_URI) else putString(BACKGROUND_URI, uri.toString())
        }.apply()
    }

    /**
     * J.A.R.V.I.S mode.
     *
     * It takes over the sound outright rather than sitting alongside it: the bundled clip
     * instead of the chosen one, played once instead of counted. Leaving the sound picker
     * live while the mode overrides it would show one thing and do another.
     *
     * The grace period is deliberately left alone. It is the reason the detector is
     * allowed to fire on thin evidence, and a mode that skipped it would quietly change
     * the trade the thresholds were chosen under.
     */
    fun jarvis(context: Context): Boolean = of(context).getBoolean(JARVIS, false)

    fun setJarvis(context: Context, value: Boolean) {
        of(context).edit().putBoolean(JARVIS, value).apply()
    }

    /**
     * The last thing the quick settings tile did, for the line on the main screen.
     *
     * A tap that does nothing and records nothing cannot be told apart from a tap that
     * never arrived. This is what tells them apart.
     */
    fun tileNote(context: Context): String? = of(context).getString(TILE_NOTE, null)

    fun setTileNote(context: Context, value: String) {
        of(context).edit().putString(TILE_NOTE, value).apply()
    }

    /** Whether to close the power menu during a theft. Off until Shizuku is set up. */
    /**
     * What happened the last time the alert fired, step by step.
     *
     * A blocked background activity start is dropped in silence rather than throwing, so
     * the code cannot tell whether the screen appeared by asking. The only honest answer
     * is whether the screen itself says it opened.
     */
    fun alertNote(context: Context): String? = of(context).getString(ALERT_NOTE, null)

    fun noteAlert(context: Context, what: String, fresh: Boolean = false) {
        val at = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        val kept = if (fresh) {
            emptyList()
        } else {
            alertNote(context).orEmpty().lines().filter { it.isNotBlank() }
        }
        // commit: this is written while the phone is locking and the process may not
        // survive long enough to flush it.
        of(context).edit()
            .putString(ALERT_NOTE, (kept + "$at $what").takeLast(6).joinToString("\n"))
            .commit()
    }

    /**
     * Whether to lock again when something covers the alert screen.
     *
     * Off until asked for. It fights the phone for the front of the display, and that is
     * not something to start doing on its own.
     */
    fun guardAlert(context: Context): Boolean = of(context).getBoolean(GUARD_ALERT, false)

    fun setGuardAlert(context: Context, value: Boolean) {
        of(context).edit().putBoolean(GUARD_ALERT, value).apply()
    }

    /**
     * Ignore a transient while the phone is upside down.
     *
     * Off until asked for, like everything else optional here. It is the answer to the
     * pocket firing — the phone goes in top edge first, so the shove down the pocket is a
     * shove toward its own top edge and reads as a grab — but it is still a rule about
     * when not to fire, and this app does not start refusing to fire on its own.
     */
    fun uprightGuard(context: Context): Boolean = of(context).getBoolean(UPRIGHT_GUARD, false)

    fun setUprightGuard(context: Context, value: Boolean) {
        of(context).edit().putBoolean(UPRIGHT_GUARD, value).apply()
    }

    /**
     * Buzz when the watch fires.
     *
     * On by default, and it is the one part of an alert that reaches you when the phone
     * is already in a pocket and the alarm has not started yet. It is also how a false
     * lock gets noticed at all rather than being found later.
     */
    fun vibrateOnAlert(context: Context): Boolean =
        of(context).getBoolean(VIBRATE_ON_ALERT, true)

    fun setVibrateOnAlert(context: Context, value: Boolean) {
        of(context).edit().putBoolean(VIBRATE_ON_ALERT, value).apply()
    }

    /**
     * How long a disconnection has to last before it counts as the phone being gone.
     *
     * Bluetooth drops for reasons that are not theft — a wrist turned the wrong way, a
     * doorway, the watch rebooting for its own update. Almost all of them come back
     * within a few seconds. Without this the feature would lock the phone and start
     * sending the location several times a day, which is not a smaller failure than
     * missing a theft: it is the failure that gets the whole thing switched off.
     */
    const val DEFAULT_TETHER_GRACE = 30

    /**
     * What the strap last saw, most recent first.
     *
     * Its own trail rather than the alert one. The strap notices things that never become
     * an alert — a drop that came back, a device going away that was not the chosen one —
     * and writing those into the alert trail would wipe the record of the last real theft
     * every time a pair of earbuds went flat.
     */
    fun strapNote(context: Context): String? = of(context).getString(STRAP_NOTE, null)

    fun noteStrap(context: Context, what: String) {
        val at = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        val kept = strapNote(context)?.lines().orEmpty()
        of(context).edit()
            .putString(STRAP_NOTE, (listOf("$at $what") + kept).take(6).joinToString("\n"))
            .commit()
    }

    /**
     * Which devices the service has actually watched connect, by address.
     *
     * **This is a readout before it is a feature.** The picker showing a device as
     * connected means the app received a Bluetooth broadcast about it — which is the one
     * thing that has to be true for the strap to work at all. A list where nothing is ever
     * marked connected is the answer to why nothing fires, and it is visible without a
     * build or a log.
     *
     * Kept only while the service lives, and cleared when it starts, because a device
     * marked connected from yesterday is worse than no mark at all.
     */
    fun connectedSet(context: Context): Set<String> =
        of(context).getString(CONNECTED, "").orEmpty()
            .split(",").filter { it.isNotBlank() }.toSet()

    fun setConnected(context: Context, address: String, connected: Boolean) {
        val next = connectedSet(context).toMutableSet()
        if (connected) next.add(address.uppercase()) else next.remove(address.uppercase())
        of(context).edit().putString(CONNECTED, next.joinToString(",")).commit()
    }

    fun clearConnected(context: Context) {
        of(context).edit().putString(CONNECTED, "").commit()
    }

    /** Lock when a paired device goes away. Off until asked for. */
    fun tetherEnabled(context: Context): Boolean = of(context).getBoolean(TETHER, false)

    fun setTetherEnabled(context: Context, value: Boolean) {
        of(context).edit().putBoolean(TETHER, value).apply()
    }

    /**
     * The one device that counts, by hardware address.
     *
     * One, not any. Headphones, a car and a speaker all disconnect constantly and none of
     * them is strapped to a wrist, so "something disconnected" is not the signal — "the
     * thing that is on you disconnected" is. Empty means nothing is chosen and the
     * feature does nothing however it is switched.
     */
    fun tetherAddress(context: Context): String = of(context).getString(TETHER_ADDRESS, "").orEmpty()

    /** Only for showing on the settings screen; the address is what is matched. */
    fun tetherName(context: Context): String = of(context).getString(TETHER_NAME, "").orEmpty()

    fun setTether(context: Context, address: String, name: String) {
        of(context).edit().putString(TETHER_ADDRESS, address).putString(TETHER_NAME, name).apply()
    }

    fun tetherGraceSeconds(context: Context): Int =
        of(context).getInt(TETHER_GRACE, DEFAULT_TETHER_GRACE)

    fun setTetherGraceSeconds(context: Context, value: Int) {
        // Zero is allowed: a drop you already trust needs no wait.
        of(context).edit().putInt(TETHER_GRACE, value.coerceIn(0, 300)).apply()
    }

    const val DEFAULT_STRAP_MESSAGE = "Wait for the owner to come back"

    /**
     * Milliseconds between letters as the message types itself out. Zero is off.
     *
     * Under about 20 it stops reading as typing and becomes a slow paint; over 80 it is
     * just a wait. The default sits where it looks deliberate.
     */
    const val DEFAULT_TYPE_SPEED = 45

    fun typeSpeedMs(context: Context): Int = of(context).getInt(TYPE_SPEED, DEFAULT_TYPE_SPEED)

    fun setTypeSpeedMs(context: Context, value: Int) {
        of(context).edit().putInt(TYPE_SPEED, value.coerceIn(0, 150)).apply()
    }

    /**
     * What a strap alert shows, separate from the grab message.
     *
     * A strap alert fires on a Bluetooth drop, which is a guess about where your watch
     * went rather than a claim that someone is holding your phone — so it should not be
     * able to accuse whoever reads it. Blank is a real choice and shows nothing at all.
     */
    fun strapMessage(context: Context): String =
        of(context).getString(STRAP_MESSAGE, DEFAULT_STRAP_MESSAGE).orEmpty()

    fun setStrapMessage(context: Context, value: String) {
        of(context).edit().putString(STRAP_MESSAGE, value.take(200)).apply()
    }

    /** Buzz when the strap fires. Separate from the grab buzz — this one is silent work. */
    fun strapVibrate(context: Context): Boolean = of(context).getBoolean(STRAP_VIBRATE, true)

    fun setStrapVibrate(context: Context, value: Boolean) {
        of(context).edit().putBoolean(STRAP_VIBRATE, value).apply()
    }

    /** Seconds before the location goes out on a strap alert. */
    fun strapDelaySeconds(context: Context): Int =
        of(context).getInt(STRAP_DELAY, DEFAULT_REPORT_DELAY)

    fun setStrapDelaySeconds(context: Context, value: Int) {
        of(context).edit().putInt(STRAP_DELAY, value.coerceIn(0, 600)).apply()
    }

    /**
     * Whether the alert now standing is the quiet kind.
     *
     * Stored rather than passed, for the same reason [alertLive] is: the screen is
     * destroyed and rebuilt, the service can be killed and restarted, and every part that
     * has to stay quiet — the alarm on the next screen-on, the words on the alert — is
     * reached long after whatever decided it. Cleared wherever [alertLive] is cleared, and
     * a stale true would silence a real theft.
     */
    fun alertQuiet(context: Context): Boolean = of(context).getBoolean(ALERT_QUIET, false)

    /** Send the location after a theft. Off until asked for. */
    fun reportEnabled(context: Context): Boolean = of(context).getBoolean(REPORT, false)

    fun setReportEnabled(context: Context, value: Boolean) {
        of(context).edit().putBoolean(REPORT, value).apply()
    }

    fun smsNumber(context: Context): String = of(context).getString(SMS_NUMBER, "").orEmpty()

    fun setSmsNumber(context: Context, value: String) {
        of(context).edit().putString(SMS_NUMBER, value.trim()).apply()
    }

    /**
     * The bot token, kept here and nowhere else.
     *
     * The repository is public. A token in a commit is a token that gets scraped, so this is
     * typed on the phone and never leaves it.
     */
    fun telegramToken(context: Context): String = of(context).getString(TG_TOKEN, "").orEmpty()

    fun setTelegramToken(context: Context, value: String) {
        of(context).edit().putString(TG_TOKEN, value.trim()).apply()
    }

    fun telegramChat(context: Context): String = of(context).getString(TG_CHAT, "").orEmpty()

    /**
     * Take the id out of whatever was pasted.
     *
     * A chat id is copied from Telegram in several shapes - the bare number, the web URL it
     * sits at the end of, or a markdown link carrying both. The API wants only the number,
     * and a URL sent as a chat id fails with an error nobody would connect to a stray
     * bracket. So it is pulled out here, once, rather than being something to remember.
     *
     * A leading @ is left alone: Telegram accepts a public channel's name in place of an id,
     * and that is a deliberate choice rather than a mistake to correct.
     */
    fun setTelegramChat(context: Context, value: String) {
        val text = value.trim()
        val id = when {
            text.startsWith("@") -> text.takeWhile { !it.isWhitespace() }
            // Long enough not to match a stray digit in a hostname, and the minus sign
            // matters: group ids carry it and the API rejects the number without it.
            else -> Regex("-?\\d{5,}").find(text)?.value ?: text
        }
        of(context).edit().putString(TG_CHAT, id).apply()
    }

    /**
     * How long to wait before the first report.
     *
     * Deliberately short. Ten seconds on the power button switches the phone off and nothing
     * in software can stop that, so a long wait guarantees nothing is ever sent.
     */
    fun reportDelaySeconds(context: Context): Int =
        of(context).getInt(REPORT_DELAY, DEFAULT_REPORT_DELAY).coerceIn(15, 600)

    fun setReportDelaySeconds(context: Context, value: Int) {
        of(context).edit().putInt(REPORT_DELAY, value.coerceIn(15, 600)).apply()
    }

    fun reportIntervalSeconds(context: Context): Int =
        of(context).getInt(REPORT_INTERVAL, DEFAULT_REPORT_INTERVAL).coerceIn(30, 1800)

    fun setReportIntervalSeconds(context: Context, value: Int) {
        of(context).edit().putInt(REPORT_INTERVAL, value.coerceIn(30, 1800)).apply()
    }

    /**
     * How many text messages to send.
     *
     * Only the texts are counted. Moving the Telegram pin costs nothing and carries on
     * until it is stopped, but every message is a real one to a real number.
     */
    fun reportCount(context: Context): Int =
        of(context).getInt(REPORT_COUNT, DEFAULT_REPORT_COUNT).coerceIn(1, 30)

    fun setReportCount(context: Context, value: Int) {
        of(context).edit().putInt(REPORT_COUNT, value.coerceIn(1, 30)).apply()
    }

    /**
     * Whether a report is in progress.
     *
     * Kept on disk rather than in memory so the screen can offer to stop it even if the
     * app was closed and reopened while it ran.
     */
    fun reportRunning(context: Context): Boolean =
        of(context).getBoolean(REPORT_RUNNING, false)

    fun setReportRunning(context: Context, value: Boolean) {
        of(context).edit().putBoolean(REPORT_RUNNING, value).commit()
    }

    /**
     * Whether an alert is still standing.
     *
     * Not the same as the alert screen existing. The home gesture sends the whole task to
     * the background, so waking the phone lands on the keyguard with nothing to resume -
     * the screen has to be put up again rather than left to come back. This is what says
     * whether it should be.
     *
     * Cleared by unlocking, and by the gesture that dismisses the alert on purpose.
     */
    fun alertLive(context: Context): Boolean = of(context).getBoolean(ALERT_LIVE, false)

    /**
     * @param quiet the kind of alert being raised. Written in the same commit as the flag
     *   it qualifies, so the two can never be read apart — and forced back to false on
     *   every clear, because a quiet flag left standing from a tether alert would take the
     *   sound off the next real grab.
     */
    fun setAlertLive(context: Context, value: Boolean, quiet: Boolean = false) {
        of(context).edit()
            .putBoolean(ALERT_LIVE, value)
            .putBoolean(ALERT_QUIET, value && quiet)
            .commit()
    }

    /**
     * How often a written update goes out, in minutes.
     *
     * Separate from the pin, which moves on the report interval. The pin carries no words,
     * so this is the only thing that can say what the battery is doing.
     */
    fun statusEveryMinutes(context: Context): Int =
        of(context).getInt(STATUS_EVERY, DEFAULT_STATUS_EVERY).coerceIn(5, 240)

    fun setStatusEveryMinutes(context: Context, value: Int) {
        of(context).edit().putInt(STATUS_EVERY, value.coerceIn(5, 240)).apply()
    }

    fun reportNote(context: Context): String? = of(context).getString(REPORT_NOTE, null)

    fun noteReport(context: Context, what: String, fresh: Boolean = false) {
        val at = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        val kept = if (fresh) {
            emptyList()
        } else {
            reportNote(context).orEmpty().lines().filter { it.isNotBlank() }
        }
        of(context).edit()
            .putString(REPORT_NOTE, (kept + "$at $what").takeLast(6).joinToString("\n"))
            .commit()
    }

    fun blockPowerMenu(context: Context): Boolean =
        of(context).getBoolean(BLOCK_POWER_MENU, false)

    fun setBlockPowerMenu(context: Context, value: Boolean) {
        of(context).edit().putBoolean(BLOCK_POWER_MENU, value).apply()
    }

    /**
     * Whether the power menu is closed right now.
     *
     * The most important thing this file holds. It survives the process being killed,
     * which is the whole reason it is here rather than in memory: a phone left with no
     * power menu and no record of it is a phone that cannot be put right.
     */
    fun powerMenuSuppressed(context: Context): Boolean =
        of(context).getBoolean(POWER_MENU_SUPPRESSED, false)

    fun setPowerMenuSuppressed(context: Context, value: Boolean) {
        // commit, not apply: this is written immediately before the screen locks and the
        // process may not survive to flush it.
        of(context).edit().putBoolean(POWER_MENU_SUPPRESSED, value).commit()
    }

    fun savedChord(context: Context): String? = of(context).getString(SAVED_CHORD, null)

    fun savedLongPress(context: Context): String? =
        of(context).getString(SAVED_LONG_PRESS, null)

    /** What the two keys held before anything was written. Null means it was not set. */
    fun setSavedPowerMenu(context: Context, chord: String?, longPress: String?) {
        of(context).edit().apply {
            if (chord == null) remove(SAVED_CHORD) else putString(SAVED_CHORD, chord)
            if (longPress == null) {
                remove(SAVED_LONG_PRESS)
            } else {
                putString(SAVED_LONG_PRESS, longPress)
            }
        }.commit()
    }

    fun message(context: Context): String =
        of(context).getString(MESSAGE, DEFAULT_MESSAGE).orEmpty().ifBlank { DEFAULT_MESSAGE }

    fun setMessage(context: Context, value: String) {
        of(context).edit().putString(MESSAGE, value.take(200)).apply()
    }
}
