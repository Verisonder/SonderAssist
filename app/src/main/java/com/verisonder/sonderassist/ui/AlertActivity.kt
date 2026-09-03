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

    /**
     * Unlocking is the only way out of this screen.
     *
     * Not a button, because a button is one a thief can press too. Only someone who knows
     * the PIN can dismiss it, which is the same test the alarm uses to fall silent.
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
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        androidx.core.content.ContextCompat.registerReceiver(
            this,
            unlocked,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )

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

    /** Back does not dismiss this. Only unlocking does. */
    @Deprecated("Back is deliberately inert here")
    override fun onBackPressed() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(unlocked) }
        super.onDestroy()
    }
}
