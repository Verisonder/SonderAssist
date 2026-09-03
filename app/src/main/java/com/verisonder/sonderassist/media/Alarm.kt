package com.verisonder.sonderassist.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
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
        if (!Settings.alarmEnabled(context)) return
        val app = context.applicationContext
        appContext = app
        cancelPending()
        val task = Runnable { start(app) }
        pending = task
        // The wait is the whole design: the screen locks at once, the noise does not, so
        // a false alarm can be unlocked before it wakes anyone.
        handler.postDelayed(task, Settings.graceSeconds(app) * 1000L)
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
