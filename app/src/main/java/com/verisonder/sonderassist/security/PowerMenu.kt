package com.verisonder.sonderassist.security

import android.content.Context
import com.verisonder.sonderassist.CrashLog
import com.verisonder.sonderassist.Settings
import rikka.shizuku.Shizuku
import java.io.BufferedReader

/**
 * Closes the two ways into the power menu while the phone is locked by a theft.
 *
 * Android decides what the power button does by reading two global settings, so the menu
 * does not have to be hidden after it appears - it can be stopped from being built at
 * all:
 *
 *  - `key_chord_power_volume_up`  0 nothing, 1 mute, 2 the power menu
 *  - `power_button_long_press`    0 nothing, 1 the power menu
 *
 * Both are written, not just the one currently in use. HyperOS offers the two as an
 * either/or in Settings, so which one is live depends on a choice that can change, and a
 * suppression that only covered one of them would quietly stop working the day it did.
 *
 * **Why this can fail, and it must fail loudly.** Writing these needs shell UID, which an
 * ordinary app does not have. Shizuku supplies it, and on a phone without root Shizuku
 * has to be started again after every reboot. So this is an addition to the lock, never
 * a part of it: the screen still locks and the alarm still sounds whether or not any of
 * this works.
 *
 * **The dangerous half is putting it back.** Suppressed and then killed - which this
 * phone's vendor does to foreground services - would leave a phone with no power menu and
 * nothing running to restore one. So the original values are recorded before anything is
 * written, and [restore] is called from three places: unlocking, booting, and opening the
 * app. Restoring when nothing was suppressed costs one read and does nothing.
 */
object PowerMenu {

    private const val CHORD = "key_chord_power_volume_up"
    private const val LONG_PRESS = "power_button_long_press"

    /** Whether Shizuku is alive and has said yes. Cheap, and safe to call anywhere. */
    fun available(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** Alive but not yet permitted, which is a different thing to say to the person. */
    fun needsPermission(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /**
     * Ask Shizuku for permission.
     *
     * No result listener: the answer is read back by [available] the next time the screen
     * resumes, which happens anyway when the dialog closes. One less thing to unregister.
     */
    fun requestPermission() {
        runCatching { Shizuku.requestPermission(0) }
    }

    /**
     * Close both routes, remembering what was there first.
     *
     * Returns false if nothing was done, so the caller can say so rather than assume.
     */
    @Synchronized
    fun suppress(context: Context): Boolean {
        if (!Settings.blockPowerMenu(context)) return false
        if (Settings.powerMenuSuppressed(context)) return true
        if (!available()) return false

        val chord = read(CHORD)
        val longPress = read(LONG_PRESS)
        // Nothing readable means nothing is understood, and writing into that is how a
        // phone ends up with a power menu that cannot be brought back.
        if (chord == null && longPress == null) return false

        // Recorded before either write. If the process dies between the two, restore
        // still knows what to put back.
        Settings.setSavedPowerMenu(context, chord, longPress)
        Settings.setPowerMenuSuppressed(context, true)

        val ok = write(context, CHORD, "0") or write(context, LONG_PRESS, "0")
        if (!ok) {
            Settings.setPowerMenuSuppressed(context, false)
            return false
        }
        return true
    }

    /**
     * Put both back to whatever they were.
     *
     * Called on unlock, on boot, and when the app is opened. It is deliberately cheap and
     * silent when there is nothing to undo, because the whole point is that it runs more
     * often than it is needed.
     */
    @Synchronized
    fun restore(context: Context) {
        if (!Settings.powerMenuSuppressed(context)) return
        if (!available()) {
            // Left suppressed on purpose: clearing the flag here would lose the only
            // record that the phone is in this state, and the next boot would not know
            // to put it right.
            return
        }
        Settings.savedChord(context)?.let { write(context, CHORD, it) }
        Settings.savedLongPress(context)?.let { write(context, LONG_PRESS, it) }
        Settings.setPowerMenuSuppressed(context, false)
    }

    private fun read(key: String): String? = runCatching {
        val process = exec(arrayOf("settings", "get", "global", key))
        val value = process.inputStream.bufferedReader().use(BufferedReader::readText).trim()
        process.waitFor()
        // "null" is what settings prints for a key that is not set, and it is a string.
        if (value.isEmpty() || value == "null") null else value
    }.getOrNull()

    private fun write(context: Context, key: String, value: String): Boolean = runCatching {
        val process = exec(arrayOf("settings", "put", "global", key, value))
        process.waitFor() == 0
    }.onFailure {
        // Worth recording rather than swallowing: a write that fails during suppression
        // is a feature that did not happen, and one that fails during restore is a phone
        // left without a power menu.
        CrashLog.record(context, "The power menu key $key could not be written", it)
    }.getOrDefault(false)

    /**
     * Shizuku's process API is not part of its public surface, so it is reached by name.
     *
     * A hard dependency on a hidden method would stop the app compiling the day it moves;
     * reflection turns that into this one feature reporting itself unavailable.
     */
    private fun exec(command: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, command, null, null) as Process
    }
}
