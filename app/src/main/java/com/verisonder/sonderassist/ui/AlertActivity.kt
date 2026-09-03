package com.verisonder.sonderassist.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.verisonder.sonderassist.Settings
import com.verisonder.sonderassist.ui.theme.SonderAssistTheme

/**
 * What the phone shows once it has decided it was taken.
 *
 * Drawn over the keyguard with `setShowWhenLocked`, so whoever is holding it sees the
 * message without unlocking anything. It shows the message and nothing else — no
 * settings, no way back into the app, nothing that would be worth someone's time.
 *
 * **Whatever is written here is readable by anyone holding the phone.** That is the
 * point, and it is also the warning: a message is a message to a stranger, not a private
 * note.
 */
class AlertActivity : ComponentActivity() {

    private var player: MediaPlayer? = null
    private var previousAlarmVolume: Int? = null

    /**
     * The alarm waits, and the wait is the whole design.
     *
     * The detector fires on thin evidence because a wrong lock costs one fingerprint
     * touch. A wrong lock that immediately blares in a quiet room costs far more, and
     * would silently invert the trade the thresholds were chosen under. Someone who knows
     * the PIN stops it before it makes a sound; someone who does not, cannot.
     */
    private val unlocked = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        androidx.core.content.ContextCompat.registerReceiver(
            this,
            unlocked,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val message = Settings.message(this)
        setContent {
            SonderAssistTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            message,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        if (Settings.alarmEnabled(this)) scheduleAlarm()
    }

    private fun scheduleAlarm() {
        val delayMs = Settings.graceSeconds(this) * 1000L
        window.decorView.postDelayed({ if (!isFinishing) startAlarm() }, delayMs)
    }

    private fun startAlarm() {
        val audio = getSystemService(AudioManager::class.java)
        // The alarm stream, not the ringer, because a phone worth taking is very often on
        // silent and a siren nobody can hear is decoration.
        previousAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        runCatching {
            audio.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0,
            )
        }

        val uri = Settings.alarmUri(this)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlertActivity, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
    }

    /** Back does not dismiss this. Only unlocking does. */
    @Deprecated("Back is deliberately inert here")
    override fun onBackPressed() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(unlocked) }
        player?.runCatching { stop(); release() }
        player = null
        previousAlarmVolume?.let { volume ->
            runCatching {
                getSystemService(AudioManager::class.java)
                    .setStreamVolume(AudioManager.STREAM_ALARM, volume, 0)
            }
        }
        super.onDestroy()
    }
}
