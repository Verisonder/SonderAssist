package com.verisonder.sonderassist.report

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.verisonder.sonderassist.CrashLog
import androidx.core.app.NotificationCompat
import com.verisonder.sonderassist.Settings
import com.verisonder.sonderassist.sensor.WatchService
import com.verisonder.sonderassist.ui.MainActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Where the phone is, sent out after a theft.
 *
 * **The delay has to be short, and that is the whole design.** A generous wait protects against
 * false alarms and guarantees nothing is ever sent, because holding the power button for ten
 * seconds turns the phone off and no software on it can prevent that. Whatever goes out has to
 * go out before then. A minute is the honest compromise: long enough to unlock a false alarm,
 * short enough to usually beat someone who knows about the hold.
 *
 * Two channels on purpose, because they fail differently:
 *
 *  - **SMS** needs no account and no internet, only a signal and a SIM. It survives a data
 *    outage and a Telegram outage.
 *  - **Telegram** is free, carries a real live-location pin that updates in place, and reaches a
 *    group rather than one handset. It needs data.
 *
 * Pull the SIM and both die. That is the limit, and it is not fixable from inside the phone.
 */
object Reporter {

    private const val CHANNEL_ID = "report"

    /** 1 is the watch and 2 is the alert. */
    private const val NOTIFICATION_ID = 3

    private val handler = Handler(Looper.getMainLooper())
    private val network = Executors.newSingleThreadExecutor()

    private var pending: Runnable? = null
    private var sent = 0

    /** The number that asked for live location by text, sent to alongside the usual one. */
    private var extraTo: String? = null

    /** Keeps the last-known position fresh for as long as a run lasts. */
    private var follower: android.location.LocationListener? = null

    /** When the last written update went out, as opposed to the pin being moved. */
    private var lastStatusAt = 0L

    /**
     * Start reporting, unless the person unlocks first.
     *
     * Called at the lock, not after it: the countdown is the point.
     */
    @Synchronized
    fun start(context: Context, delaySeconds: Int? = null, alsoTo: String? = null) {
        // A live request by text runs even when theft reports are switched off: the owner
        // asked for it, from the number it answers.
        if (!Settings.reportEnabled(context) && alsoTo == null) return
        val app = context.applicationContext
        cancel()
        sent = 0
        extraTo = alsoTo
        follow(app)
        // A pin left live from an earlier run is reused and moved, not left behind.
        lastStatusAt = 0L
        Settings.setReportRunning(app, true)
        // The strap can want a different wait from a grab: one is a guess about a
        // Bluetooth drop, the other is a phone that just left your hand.
        val delay = delaySeconds ?: Settings.reportDelaySeconds(app)
        show(app, "Starting in $delay seconds")
        schedule(app, delay)
    }

    /**
     * Stop, and take the pin down with it.
     *
     * Reached from unlocking and from the button on the screen. On a phone that has been
     * taken the button is out of reach, so unlocking is the one that matters; the button is
     * for afterwards, when it is back.
     */
    @Synchronized
    fun stop(context: Context, done: ((Boolean) -> Unit)? = null) {
        val app = context.applicationContext
        val wasRunning = Settings.reportRunning(app)
        cancel()
        unfollow(app)
        extraTo = null
        Settings.setReportRunning(app, false)
        hide(app)
        val pin = Settings.livePin(app)
        if (pin == null) {
            if (wasRunning) Settings.noteReport(app, "stopped")
            done?.let { handler.post { it(true) } }
            return
        }
        // Read from disk, so a pin started before the process was killed can still be
        // stopped. Forgotten only once Telegram says it has stopped.
        network.execute {
            val code = status(
                Settings.telegramToken(app),
                "stopMessageLiveLocation",
                "chat_id=${enc(pin.chat)}&message_id=${pin.messageId}",
            )
            // 400 is Telegram saying the pin is already stopped, expired or deleted.
            val stopped = code == 200 || code == 400
            if (stopped) {
                Settings.clearLivePin(app)
                Settings.noteReport(app, "stopped, pin taken down")
            } else {
                Settings.noteReport(app, "could not stop the pin - no connection?")
            }
            done?.let { handler.post { it(stopped) } }
        }
    }

    /**
     * Put the notification back if a pin is still live but nothing is running any more -
     * after the process was killed, or a restart. Otherwise it would be live with nothing
     * on the phone saying so.
     */
    @Synchronized
    fun remind(context: Context) {
        val app = context.applicationContext
        if (pending != null || Settings.livePin(app) == null) return
        show(app, "The Telegram pin is still live.")
    }

    /**
     * Stop, and forget the live message.
     *
     * Called on unlock. The Telegram pin stops updating on its own when its period runs out;
     * there is no point telling it to stop early and every point in not sending more.
     */
    @Synchronized
    fun cancel() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }

    /**
     * A standing notification for as long as this is running.
     *
     * The pin in the group cannot say whether it is still being moved - a live location that
     * has stopped updating looks exactly like one that has not. Only the phone knows, so the
     * phone is what says so, and it says so continuously rather than once.
     *
     * Ongoing, so it cannot be swiped away by accident, and carrying the stop action so the
     * answer to "is this still running" and the way to end it are the same thing.
     */
    private fun show(context: Context, detail: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Sending the location",
                // Low: this is a state to be able to check, not an interruption. It is
                // already accompanied by an alarm.
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val stop = PendingIntent.getBroadcast(
            context,
            0,
            Intent(WatchService.ACTION_STOP_REPORT).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Live location is being sent")
                .setContentText(detail)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                )
                .addAction(0, "Stop", stop)
                .build(),
        )
    }

    private fun hide(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun schedule(context: Context, seconds: Int) {
        val task = Runnable { report(context) }
        pending = task
        handler.postDelayed(task, seconds * 1000L)
    }

    @Synchronized
    private fun report(context: Context) {
        pending = null
        sent++

        val location = lastKnown(context)
        if (location == null) {
            Settings.noteReport(context, "no position yet")
        } else {
            // The two channels part company here. Moving the pin costs nothing, so it keeps
            // going until it is stopped. Every text is a real message to a real number, so
            // those are counted.
            val text = textFor(context, location)
            if (sent <= Settings.reportCount(context)) {
                sendSms(context, text)
                extraTo?.let { sendSmsTo(context, it, text) }
            }
            network.execute { sendTelegram(context, location, text) }

            // The pin carries no words, so the battery has nowhere to go on it. Once an
            // hour it goes out as a message of its own, next to the pin that is moving.
            val now = SystemClock.elapsedRealtime()
            if (now - lastStatusAt >= Settings.statusEveryMinutes(context) * 60_000L) {
                lastStatusAt = now
                network.execute {
                    val token = Settings.telegramToken(context)
                    val chat = Settings.telegramChat(context)
                    if (token.isNotBlank() && chat.isNotBlank()) {
                        call(token, "sendMessage", "chat_id=${enc(chat)}&text=${enc(text)}")
                        Settings.noteReport(context, "hourly update sent")
                    }
                }
            }
        }

        if (Settings.reportRunning(context)) {
            val next = Settings.reportIntervalSeconds(context)
            show(
                context,
                "$sent sent, the next in $next seconds. Unlocking the phone stops it.",
            )
            schedule(context, next)
        } else {
            unfollow(context)
            Settings.noteReport(context, "stopped after $sent")
        }
    }

    /**
     * Ask every provider for updates while a run lasts, so each report carries a position
     * that is seconds old rather than whatever was last stored. Nothing waits on it.
     */
    @SuppressLint("MissingPermission")
    private fun follow(context: Context) {
        unfollow(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(LocationManager::class.java) ?: return
        val listener = android.location.LocationListener { }
        follower = listener
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            runCatching {
                if (manager.isProviderEnabled(provider)) {
                    manager.requestLocationUpdates(provider, 5_000L, 0f, listener, Looper.getMainLooper())
                }
            }
        }
    }

    private fun unfollow(context: Context) {
        val listener = follower ?: return
        follower = null
        runCatching { context.getSystemService(LocationManager::class.java)?.removeUpdates(listener) }
    }

    /**
     * A fresh position for a text reply: whatever arrives first from GPS or the network,
     * or the stored one after [timeoutMs]. [done] runs once, on the main thread.
     */
    @SuppressLint("MissingPermission")
    fun freshPosition(context: Context, timeoutMs: Long, done: (Location?, Boolean) -> Unit) {
        val app = context.applicationContext
        var finished = false
        val manager = app.getSystemService(LocationManager::class.java)
        lateinit var listener: android.location.LocationListener
        fun finish(location: Location?, fresh: Boolean) {
            if (finished) return
            finished = true
            runCatching { manager?.removeUpdates(listener) }
            done(location ?: lastKnown(app), fresh && location != null)
        }
        listener = android.location.LocationListener { finish(it, true) }
        val allowed = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (manager == null || !allowed) {
            handler.post { finish(null, false) }
            return
        }
        var asked = false
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            runCatching {
                if (manager.isProviderEnabled(provider)) {
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                    asked = true
                }
            }
        }
        if (!asked) {
            handler.post { finish(null, false) }
            return
        }
        handler.postDelayed({ finish(null, false) }, timeoutMs)
    }

    /** [locateText], but with a fresh position when one comes within 20 seconds. */
    fun locateFresh(context: Context, done: (String) -> Unit) {
        val app = context.applicationContext
        freshPosition(app, 20_000L) { where, fresh ->
            done(
                if (where == null) {
                    "SonderAssist: no position yet - location may be off. ${battery(app)}"
                } else {
                    val link = "https://maps.google.com/?q=${where.latitude},${where.longitude}"
                    val age = if (fresh) "now" else "${(System.currentTimeMillis() - where.time) / 60_000} min ago"
                    "SonderAssist: ${where.latitude}, ${where.longitude} " +
                        "(±${where.accuracy.toInt()}m, $age) ${battery(app)} $link"
                },
            )
        }
    }

    /**
     * Check everything before it matters.
     *
     * Every part of this can only fail at the worst possible moment, and by then nobody is
     * watching. So each piece is asked directly: the token against getMe, the chat id by
     * sending a real message to it, and the two permissions by reading them.
     */
    fun test(context: Context) {
        val app = context.applicationContext
        Settings.noteReport(app, "checking", fresh = true)

        val sms = ContextCompat.checkSelfPermission(app, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
        val where = ContextCompat.checkSelfPermission(
            app,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        Settings.noteReport(
            app,
            when {
                Settings.smsNumber(app).isBlank() -> "no number, so no text will be sent"
                !sms -> "a number is set, but sending messages is not allowed"
                else -> "the number and permission are in place"
            },
        )
        Settings.noteReport(
            app,
            if (where) {
                "location is allowed - check it is allowed all the time"
            } else {
                "location is not allowed, so there is nothing to send"
            },
        )
        if (lastKnown(app) == null) {
            Settings.noteReport(app, "the phone has no position stored yet")
        }

        network.execute {
            val token = Settings.telegramToken(app)
            if (token.isBlank()) {
                Settings.noteReport(app, "no bot token")
                return@execute
            }
            val me = call(token, "getMe", "")
            if (me == null) {
                Settings.noteReport(app, "the bot token was rejected")
                return@execute
            }
            val name = runCatching {
                JSONObject(me).getJSONObject("result").optString("username")
            }.getOrNull().orEmpty()

            val chat = Settings.telegramChat(app)
            if (chat.isBlank()) {
                Settings.noteReport(app, "bot @$name works, but no chat id")
                return@execute
            }
            val ok = call(
                token,
                "sendMessage",
                // The battery goes in here too, so the check proves that part reads
                // as well as everything else.
                "chat_id=${enc(chat)}&text=${enc(
                    "SonderAssist test - this is where the location would go. " +
                        battery(app),
                )}",
            )
            Settings.noteReport(
                app,
                if (ok != null) {
                    "bot @$name reached $chat"
                } else {
                    // The usual cause, and the one nobody thinks of.
                    "the chat id was rejected - is the bot in that group?"
                },
            )
        }
    }

    /**
     * The best position available right now, without waiting for a fix.
     *
     * A cold GPS fix can take a minute outdoors and never arrive indoors, and this runs while
     * the phone is being carried away. The last known position is worth far more delivered
     * immediately than a perfect one delivered after the phone is switched off. The providers
     * are read newest-first and the freshest wins.
     */
    @SuppressLint("MissingPermission")
    private fun lastKnown(context: Context): Location? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Settings.noteReport(context, "no permission to read the location")
            return null
        }
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        return runCatching {
            manager.allProviders
                .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull()
    }

    private fun textFor(context: Context, where: Location): String {
        val link = "https://maps.google.com/?q=${where.latitude},${where.longitude}"
        // Asked for by text, so not "taken": the owner wanted to follow it.
        val lead = if (extraTo != null) "SonderAssist live:" else "SonderAssist: this phone was taken."
        return "$lead ${where.latitude}, " +
            "${where.longitude} (±${where.accuracy.toInt()}m) ${battery(context)} $link"
    }

    /**
     * Where the phone is, composed for a reply to a text command.
     *
     * Reuses the same last-known read, battery line and map link as a theft report, but
     * without the "this phone was taken" framing — this is the owner asking on purpose. It
     * never waits for a fix, for the same reason the report does not: a position delivered
     * now beats a better one delivered after the phone is off.
     */
    fun locateText(context: Context): String {
        val where = lastKnown(context)
            ?: return "SonderAssist: no position stored yet. ${battery(context)}"
        val link = "https://maps.google.com/?q=${where.latitude},${where.longitude}"
        return "SonderAssist: ${where.latitude}, ${where.longitude} " +
            "(±${where.accuracy.toInt()}m) ${battery(context)} $link"
    }

    /**
     * Battery, and whether it is going up.
     *
     * Worth as much as the position over a long run: it says how long the phone has left to
     * be found at all, and a phone that has started charging is a phone that has been taken
     * somewhere rather than dropped in a street.
     */
    private fun battery(context: Context): String {
        val manager = context.getSystemService(BatteryManager::class.java)
        val level = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val charging = manager?.isCharging == true
        return when {
            level < 0 -> "battery unknown"
            charging -> "battery $level%, charging"
            else -> "battery $level%"
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendSms(context: Context, text: String) {
        sendSmsTo(context, Settings.smsNumber(context), text)
    }

    @SuppressLint("MissingPermission")
    private fun sendSmsTo(context: Context, number: String, text: String) {
        if (number.isBlank()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Settings.noteReport(context, "no permission to send a message")
            return
        }
        runCatching {
            val sms = context.getSystemService(android.telephony.SmsManager::class.java)
            // Over 160 characters an ordinary send is silently truncated, and the link is at
            // the end - which is the part worth having.
            sms.sendMultipartTextMessage(number, null, sms.divideMessage(text), null, null)
            Settings.noteReport(context, "message sent")
        }.onFailure {
            CrashLog.record(context, "The location message could not be sent", it)
            Settings.noteReport(context, "the message failed")
        }
    }

    /**
     * A live pin, edited in place rather than a trail of separate messages.
     *
     * The first call creates it with a period covering the whole run; every later one moves it.
     * If an edit fails - the message deleted, the period expired - it falls back to sending a
     * fresh one rather than going quiet.
     */
    private fun sendTelegram(context: Context, where: Location, text: String) {
        val token = Settings.telegramToken(context)
        val chat = Settings.telegramChat(context)
        if (token.isBlank() || chat.isBlank()) return

        val id = Settings.livePin(context)?.takeIf { it.chat == chat }?.messageId
        val moved = id != null && call(
            token,
            "editMessageLiveLocation",
            "chat_id=${enc(chat)}&message_id=$id" +
                "&latitude=${where.latitude}&longitude=${where.longitude}" +
                "&horizontal_accuracy=${where.accuracy}",
        ) != null

        if (moved) {
            Settings.noteReport(context, "live pin moved")
            return
        }

        // The longest Telegram allows. The pin is meant to keep moving until it is
        // stopped, so tying its life to a message count would have it expire mid-run.
        val period = 86_400
        val body = call(
            token,
            "sendLocation",
            "chat_id=${enc(chat)}&latitude=${where.latitude}&longitude=${where.longitude}" +
                "&horizontal_accuracy=${where.accuracy}&live_period=$period",
        )

        if (body == null) {
            // Worth one plain message rather than nothing: a group that gets coordinates as
            // text is still a group that knows where the phone is.
            call(token, "sendMessage", "chat_id=${enc(chat)}&text=${enc(text)}")
            Settings.noteReport(context, "sent as text, not a pin")
            return
        }

        runCatching {
            JSONObject(body).getJSONObject("result").getLong("message_id")
        }.getOrNull()?.let { Settings.setLivePin(context, it, chat, period) }
        Settings.noteReport(context, "live pin sent")
    }

    /** Returns the response body, or null if the call did not succeed. */
    private fun call(token: String, method: String, query: String): String? = runCatching {
        val connection = URL("https://api.telegram.org/bot$token/$method?$query")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        try {
            if (connection.responseCode != 200) return@runCatching null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /** The HTTP status alone, or -1 if there was no answer at all. */
    private fun status(token: String, method: String, query: String): Int = runCatching {
        val connection = URL("https://api.telegram.org/bot$token/$method?$query")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        try {
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(-1)

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}
