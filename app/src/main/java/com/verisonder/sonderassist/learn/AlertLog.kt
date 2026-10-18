package com.verisonder.sonderassist.learn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.io.File

/**
 * The record of every grab alert and whether it was a real theft.
 *
 * One file per alert in the app's own storage, and the answers in their own small
 * preferences file, so answering never rewrites a recording. Sharing joins them into a
 * single text file.
 *
 * Nothing here leaves the phone on its own. The file goes wherever the person shares it.
 */
object AlertLog {

    const val THEFT = "theft"
    const val FALSE = "false"

    private const val DIR = "alerts"
    private const val LABELS = "alert_labels"
    private const val PENDING = "pending"
    private const val ASKED = "asked"

    /** Enough for months of alerts at about 20 KB each. The oldest go first. */
    private const val MAX_RECORDS = 300

    private const val ASK_CHANNEL = "ask"
    private const val ASK_ID = 3

    const val ACTION_LABEL = "com.verisonder.sonderassist.LABEL"
    const val EXTRA_ID = "id"
    const val EXTRA_LABEL = "label"

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    private fun labels(context: Context) =
        context.getSharedPreferences(LABELS, Context.MODE_PRIVATE)

    private fun files(context: Context): List<File> =
        dir(context).listFiles { f -> f.name.endsWith(".txt") }
            ?.sortedBy { it.name }
            .orEmpty()

    /**
     * Write an alert down. Called at the moment of firing, so the write goes to its own
     * thread: the lock is what matters at that moment, not the disk.
     */
    fun record(context: Context, record: AlertRecord) {
        val app = context.applicationContext
        labels(app).edit()
            .putLong(PENDING, record.id)
            .putBoolean(ASKED, false)
            .apply()
        Thread {
            runCatching {
                File(dir(app), "${record.id}.txt").writeText(record.serialise())
                files(app).dropLast(MAX_RECORDS).forEach { old ->
                    old.delete()
                    labels(app).edit().remove(old.nameWithoutExtension).apply()
                }
            }
        }.start()
    }

    /** The newest alert still waiting for an answer, or null. */
    fun pending(context: Context): Long? =
        labels(context).getLong(PENDING, 0L).takeIf { it != 0L }

    fun label(context: Context, id: Long, label: String) {
        val edit = labels(context).edit().putString(id.toString(), label)
        if (pending(context) == id) edit.remove(PENDING)
        edit.apply()
        context.getSystemService(NotificationManager::class.java)?.cancel(ASK_ID)
    }

    data class Counts(val theft: Int, val falseAlarm: Int, val unanswered: Int)

    fun counts(context: Context): Counts {
        val prefs = labels(context)
        var theft = 0
        var falseAlarm = 0
        var unanswered = 0
        for (f in files(context)) {
            when (prefs.getString(f.nameWithoutExtension, null)) {
                THEFT -> theft++
                FALSE -> falseAlarm++
                else -> unanswered++
            }
        }
        return Counts(theft, falseAlarm, unanswered)
    }

    /**
     * Every alert in one text file, each with its answer above it, ready to share.
     * Written to the cache, where the share sheet's provider can reach it.
     */
    fun export(context: Context): File {
        val prefs = labels(context)
        val all = files(context)
        val c = counts(context)
        val out = File(context.cacheDir, "shared").apply { mkdirs() }
            .resolve("sonderassist-alerts.txt")
        out.bufferedWriter().use { w ->
            w.write("SONDERASSIST-ALERTS 1\n")
            w.write("records ${all.size} theft ${c.theft} false ${c.falseAlarm} unanswered ${c.unanswered}\n")
            for (f in all) {
                w.write("\n===== ALERT\n")
                w.write("label ${prefs.getString(f.nameWithoutExtension, null) ?: "unanswered"}\n")
                w.write(runCatching { f.readText() }.getOrDefault(""))
            }
            w.write("\n===== END\n")
        }
        return out
    }

    fun clear(context: Context) {
        files(context).forEach { it.delete() }
        labels(context).edit().clear().apply()
        context.getSystemService(NotificationManager::class.java)?.cancel(ASK_ID)
    }

    /**
     * Ask, once, after the phone is unlocked following an alert.
     *
     * A notification with two buttons rather than a screen: a background app may not open
     * an activity, and a notification survives being ignored until there is time for it.
     * If it is swiped away the question is still on the app's home screen.
     */
    fun askIfPending(context: Context) {
        val id = pending(context) ?: return
        val prefs = labels(context)
        if (prefs.getBoolean(ASKED, false)) return
        prefs.edit().putBoolean(ASKED, true).apply()

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(ASK_CHANNEL, "After an alert", NotificationManager.IMPORTANCE_HIGH)
        )
        val at = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(id))
        manager.notify(
            ASK_ID,
            NotificationCompat.Builder(context, ASK_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("Was it a theft?")
                .setContentText("Locked at $at.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(0, "Theft", answer(context, id, THEFT))
                .addAction(0, "False alarm", answer(context, id, FALSE))
                .build(),
        )
    }

    private fun answer(context: Context, id: Long, label: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            if (label == THEFT) 1 else 2,
            Intent(context, LabelReceiver::class.java)
                .setAction(ACTION_LABEL)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_LABEL, label),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
