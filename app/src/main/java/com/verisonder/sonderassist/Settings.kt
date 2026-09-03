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

    fun message(context: Context): String =
        of(context).getString(MESSAGE, DEFAULT_MESSAGE).orEmpty().ifBlank { DEFAULT_MESSAGE }

    fun setMessage(context: Context, value: String) {
        of(context).edit().putString(MESSAGE, value.take(200)).apply()
    }
}
