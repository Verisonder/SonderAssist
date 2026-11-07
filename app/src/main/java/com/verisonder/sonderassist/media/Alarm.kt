package com.verisonder.sonderassist.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.verisonder.sonderassist.R
import com.verisonder.sonderassist.CrashLog
import com.verisonder.sonderassist.Settings

/**
 * The sound, owned by the service rather than by the screen.
 *
 * It lived inside AlertActivity, which was a mistake with a visible symptom: Android
 * blocks background activity starts, so on a real theft — where the app is not in the
 * foreground — the alert screen never opened and the alarm went silent with it. The
 * screen locked and nothing else happened.
 *
 * The sound is the part most likely to reach someone across the room, so it must not
 * depend on a window being allowed to open. The service starts it; unlocking stops it.
 */
object Alarm {

    private val handler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var pending: Runnable? = null
    private var previousVolume: Int? = null

    /** Held so the volume can be put back without the caller having to remember to. */
    private var appContext: Context? = null

    @Synchronized
    fun scheduleAfterGrace(context: Context) {
        // F.R.I.D.A.Y mode is silent until the owner asks; Friday owns that sound.
        if (Settings.friday(context)) return
        if (!Settings.alarmEnabled(context)) return
        val app = context.applicationContext
        appContext = app
        cancelPending()
        // Anything already playing is released first. This is now reached on every return
        // of the alert screen, and without it a second player would be built over the
        // first and both would sound.
        player?.runCatching { stop(); release() }
        player = null
        val task = Runnable { start(app) }
        pending = task
        // The wait is the whole design: the screen locks at once, the noise does not, so
        // a false alarm can be unlocked before it wakes anyone.
        handler.postDelayed(task, Settings.graceSeconds(app) * 1000L)
    }

    /**
     * Ring now, on demand — the "find my phone" sound, not the theft siren.
     *
     * Unlike [scheduleAfterGrace] it ignores the grace period and the alarm-after-theft
     * switch, because the owner asked for it deliberately from another phone; there is no
     * false alarm to guard against. Loud on the alarm stream so a silenced phone still
     * sounds, and it stops itself after [seconds] so a phone left ringing somewhere does
     * not run its battery flat. [stop] ends it early and puts the volume back — the same
     * [stop] the theft path uses, so the two never leave a player or a raised volume behind.
     */
    @Synchronized
    fun ringNow(context: Context, seconds: Int = 30) {
        val app = context.applicationContext
        appContext = app
        cancelPending()
        player?.runCatching { stop(); release() }
        player = null

        val audio = app.getSystemService(AudioManager::class.java)
        previousVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        runCatching {
            audio.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0,
            )
        }

        val uri = Settings.alarmUri(app)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(app, uri)
                // Looped on purpose here: it rings until the timer below stops it, so you
                // can follow the sound to the phone.
                isLooping = true
                prepare()
                start()
            }
        }.onFailure {
            CrashLog.record(app, "The ring could not play $uri", it)
        }.getOrNull()

        // Stops itself, so a phone ringing in a drawer somewhere does not drain the battery.
        val task = Runnable { stop() }
        pending = task
        handler.postDelayed(task, seconds * 1000L)
    }

    /**
     * The F.R.I.D.A.Y clip, once, at full alarm volume, then [done] on the main thread.
     * Volume is put back when it ends.
     */
    @Synchronized
    fun playFriday(context: Context, done: () -> Unit = {}) {
        val app = context.applicationContext
        val uri = Uri.parse("android.resource://${app.packageName}/${R.raw.friday_alert}")
        play(app, uri, loop = false) { done() }
    }

    /** "OK boss" or "OK sir", one of the two at random, once, at full alarm volume. */
    @Synchronized
    fun playAck(context: Context) {
        val app = context.applicationContext
        val res = if (kotlin.random.Random.nextBoolean()) R.raw.friday_ok_boss else R.raw.friday_ok_sir
        play(app, Uri.parse("android.resource://${app.packageName}/$res"), loop = false) {}
    }

    /**
     * The siren at the end of a F.R.I.D.A.Y countdown: the chosen sound or the phone's
     * alarm, looped at full volume until the phone is unlocked (five minutes at most).
     */
    @Synchronized
    fun siren(context: Context) {
        val app = context.applicationContext
        val uri = Settings.alarmUri(app) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        play(app, uri, loop = true) {}
        // Unlocking is what stops it; this is the backstop for a phone nobody gets back
        // to, so it does not scream until the battery dies.
        val task = Runnable { stop() }
        pending = task
        handler.postDelayed(task, SIREN_MAX_MS)
    }

    private const val SIREN_MAX_MS = 5 * 60_000L

    private fun play(app: Context, uri: Uri, loop: Boolean, done: () -> Unit) {
        appContext = app
        cancelPending()
        player?.runCatching { stop(); release() }
        player = null
        val audio = app.getSystemService(AudioManager::class.java)
        if (previousVolume == null) previousVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        runCatching {
            audio.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0,
            )
        }
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(app, uri)
                isLooping = loop
                if (!loop) {
                    setOnCompletionListener {
                        this@Alarm.stop()
                        done()
                    }
                }
                prepare()
                start()
            }
        }.onFailure {
            CrashLog.record(app, "Could not play $uri", it)
            handler.post { done() }
        }.getOrNull()
    }

    @Synchronized
    fun stop() {
        cancelPending()
        player?.runCatching { stop(); release() }
        player = null
        restoreVolume()
    }

    @Synchronized
    private fun start(context: Context) {
        pending = null
        val audio = context.getSystemService(AudioManager::class.java)
        // The alarm stream, not the ringer: a phone worth taking is very often on silent
        // and a siren nobody can hear is decoration.
        previousVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        runCatching {
            audio.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0,
            )
        }

        val uri = Settings.alarmUri(context)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val repeats = Settings.alarmRepeats(context)
        var played = 0

        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                // Counted, not looped. isLooping runs until something stops it, and the
                // only thing that stopped it was unlocking the phone — so a false alarm
                // nobody was near carried on until the battery gave out.
                isLooping = false
                setOnCompletionListener {
                    played++
                    if (played < repeats) {
                        runCatching { seekTo(0); start() }
                    } else {
                        // Restored when the sound ends, not only when the alert is
                        // dismissed: the screen can sit there long afterwards and leaving
                        // the phone's alarm volume at maximum is not this app's business.
                        this@Alarm.stop()
                    }
                }
                prepare()
                start()
            }
        }.onFailure {
            // Silence and no trace was the old behaviour, and silence is indistinguishable
            // from the sound being switched off. Recorded so the phone can say which.
            CrashLog.record(context, "The alarm could not play $uri", it)
        }.getOrNull()
    }

    private fun cancelPending() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }

    private fun restoreVolume() {
        val context = appContext ?: return
        previousVolume?.let { volume ->
            runCatching {
                context.getSystemService(AudioManager::class.java)
                    .setStreamVolume(AudioManager.STREAM_ALARM, volume, 0)
            }
        }
        previousVolume = null
    }
}
