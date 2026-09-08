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
