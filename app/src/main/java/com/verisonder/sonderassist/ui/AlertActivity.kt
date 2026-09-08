package com.verisonder.sonderassist.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import com.verisonder.sonderassist.Settings
import com.verisonder.sonderassist.sensor.WatchService
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

    private val MATCH get() = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
    )

    /**
     * Unlocking is the only way out of this screen.
     *
     * Not a button, because a button is one a thief can press too. Only someone who knows
     * the PIN can dismiss it, which is the same test the alarm uses to fall silent.
     *
     * J.A.R.V.I.S mode adds a second way out, and it is a different kind of secret: the
     * gesture is not knowledge the phone can check, only something a stranger is unlikely
     * to guess. Unlocking still works, and still works faster.
     */
    private val unlocked = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) finish()
        }
    }

    /**
     * The alarm is not started here.
     *
     * It used to be, and that coupled the sound to a window Android often refuses to
     * open in the background. WatchService owns it now; this screen is only the message.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The only statement in the app that proves the screen actually appeared. Every
        // other step can succeed while this never runs.
        Settings.noteAlert(this, "the alert screen opened")

        setShowWhenLocked(true)
        setTurnScreenOn(true)

        androidx.core.content.ContextCompat.registerReceiver(
            this,
            unlocked,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        if (Settings.jarvis(this)) {
            showJarvis(savedInstanceState == null)
            return
        }

        val message = Settings.message(this)
        // Decoded once, here, rather than in composition: this screen appears at the
        // worst possible moment and must not be waiting on a decode to draw.
        val background = Settings.backgroundUri(this)?.let { uri ->
            runCatching {
                contentResolver.openInputStream(uri).use { android.graphics.BitmapFactory.decodeStream(it) }
            }.getOrNull()?.asImageBitmap()
        }

        setContent {
            SonderAssistTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (background != null) {
                        Image(
                            bitmap = background,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {}
                    }
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
                            // Over a picture the theme colour is a coin toss, so the text
                            // carries its own contrast: white on a dark scrim behind it.
                            color = if (background != null) Color.White else MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center,
                            modifier = if (background != null) {
                                Modifier
                                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
        }

    }

    /**
     * J.A.R.V.I.S mode: the screen from BlackFriday, carried over whole.
     *
     * The message and nothing else. A touch brings the rings: two fingers tapped once
     * arm it, two fingers up carry it on, one finger left to right ends it. Plain views
     * rather than Compose because HudView and DotFieldView are the originals, and a
     * rewrite would have had to rediscover the gesture rules that were found on a real
     * phone.
     *
     * The rings sit above the words so a finger's instrument is never drawn behind
     * them, and HudView takes every touch, so the text can never swallow one.
     *
     * Unlocking still dismisses it. The gesture is an addition, not a replacement, so
     * knowing the PIN is never the slower way out.
     */
    private fun showJarvis(coldStart: Boolean) {
        val field = DotFieldView(this).apply {
            // The wide flat field: here the dots are the only thing on screen, which is
            // exactly the case this mode was written for.
            quiet = true
            if (coldStart) open()
        }

        val hud = HudView(this).apply {
            onFingers = { points, count -> field.lightUnder(points, count) }
            onLoad = {
                // Asked of the service, which owns the sound. Stopping it from here
                // would put the alarm back inside the window it was taken out of.
                WatchService.silence(this@AlertActivity)
                finish()
            }
        }

        // Between the field and the rings. Under the rings so a finger's instrument is
        // never drawn behind the words, and above the field so the words are readable
        // over it. No typeface is set: the phone's own is the one to use.
        val words = TextView(this).apply {
            text = Settings.message(this@AlertActivity)
            setTextColor(android.graphics.Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
            val pad = (32 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                addView(field, MATCH)
                addView(words, MATCH)
                addView(hud, MATCH)
            }
        )
    }

    /** Back does not dismiss this. Only unlocking does. */
    @Deprecated("Back is deliberately inert here")
    override fun onBackPressed() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(unlocked) }
        super.onDestroy()
    }
}
