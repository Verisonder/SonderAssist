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
    private const val REPORT_NOTE = "report_note"
    private const val REPORT_RUNNING = "report_running"
    private const val ALERT_LIVE = "alert_live"
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

    fun setAlertLive(context: Context, value: Boolean) {
        of(context).edit().putBoolean(ALERT_LIVE, value).commit()
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
