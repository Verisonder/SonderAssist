package com.verisonder.sonderassist.report

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.verisonder.sonderassist.CrashLog
import com.verisonder.sonderassist.Settings
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

    private val handler = Handler(Looper.getMainLooper())
    private val network = Executors.newSingleThreadExecutor()

    private var pending: Runnable? = null
    private var sent = 0

    /** The live-location message being edited in place, once Telegram has accepted one. */
    private var liveMessageId: Long? = null

    /**
     * Start reporting, unless the person unlocks first.
     *
     * Called at the lock, not after it: the countdown is the point.
     */
    @Synchronized
    fun start(context: Context) {
        if (!Settings.reportEnabled(context)) return
        val app = context.applicationContext
        cancel()
        sent = 0
        liveMessageId = null
        schedule(app, Settings.reportDelaySeconds(app))
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
            send(context, location)
        }

        // Repeats are worth having - a single fix taken indoors can be a long way out - but
        // each SMS is a real message with a real cost, so they are counted rather than endless.
        if (sent < Settings.reportCount(context)) {
            schedule(context, Settings.reportIntervalSeconds(context))
        } else {
            // Told to stop rather than left to expire. A live pin that has stopped moving
            // but still shows a countdown reads as a phone being tracked, and it is not.
            val id = liveMessageId
            if (id != null) {
                network.execute {
                    call(
                        Settings.telegramToken(context),
                        "stopMessageLiveLocation",
                        "chat_id=${enc(Settings.telegramChat(context))}&message_id=$id",
                    )
                }
            }
            Settings.noteReport(context, "done, $sent sent")
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
                "chat_id=${enc(chat)}&text=${enc("SonderAssist test - this is where the location would go.")}",
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

    private fun send(context: Context, where: Location) {
        val link = "https://maps.google.com/?q=${where.latitude},${where.longitude}"
        val text = "SonderAssist: this phone was taken. ${where.latitude}, " +
            "${where.longitude} (±${where.accuracy.toInt()}m) $link"

        sendSms(context, text)
        network.execute { sendTelegram(context, where, text) }
    }

    @SuppressLint("MissingPermission")
    private fun sendSms(context: Context, text: String) {
        val number = Settings.smsNumber(context)
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

        val id = liveMessageId
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

        val period = (Settings.reportIntervalSeconds(context) * Settings.reportCount(context))
            .coerceIn(60, 86_400)
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

        liveMessageId = runCatching {
            JSONObject(body).getJSONObject("result").getLong("message_id")
        }.getOrNull()
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

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}
