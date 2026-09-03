package com.verisonder.sonderassist

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the last uncaught exception to a file so it can be read after the fact.
 *
 * This app is developed against a real device with no debugger attached and no way to
 * pull logcat, so a crash otherwise arrives as "it crashed" and gets diagnosed by
 * guesswork. One file in private storage turns that into a stack trace. Carried over from
 * SonderVault, where it found a dependency conflict in one round trip.
 */
object CrashLog {

    private const val FILE_NAME = "last-crash.txt"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
                val at = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                file(context).writeText("$at on ${thread.name}\n\n$stack")
            }
            // Always hand back to the platform: swallowing this would leave the app in a
            // half-dead state rather than closing, which is worse than crashing.
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? {
        val file = file(context)
        return if (file.exists()) file.readText() else null
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
}
