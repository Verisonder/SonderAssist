package com.verisonder.sonderassist.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
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
import com.verisonder.sonderassist.security.DeviceAdminLocker
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

    /**
     * Whether this screen was ever actually in front.
     *
     * onPause runs during the launch itself when the display is already off, so without
     * this the guard fires before the alert has been seen: it locks, the screen never
     * comes up, and the phone sits dark until the power button is pressed. Nothing is
     * guarded until there is something to guard.
     */
    private var seen = false

    /** When it last came up, so the guard can ignore its own arrival. */
    private var seenAt = 0L

    /** Whether the guard put the display out and this screen is waiting to come back. */
    private var wentDark = false

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Going dark, a moment after the cover was noticed.
     *
     * Locking the instant the assistant appears races its own launch: the display goes
     * out and the window coming up turns it straight back on, inside a tenth of a second,
     * leaving the lock screen with the assistant over it. Letting it settle first means
     * the lock is the last thing to happen rather than the first.
     */
    private val goDark = Runnable {
        wentDark = true
        Settings.noteAlert(this, "something covered the screen, going dark")

        // Silent as well as dark. A phone making a noise is not a phone that looks
        // switched off, and the point of going dark is that it should.
        WatchService.silence(this)

        // Stop this screen waking the display on its own. It is wanted when the alert
        // first arrives and is exactly wrong now: it would undo the lock the moment this
        // window came back. Turning the screen on by hand still brings the alert up,
        // because showWhenLocked is untouched.
        runCatching { setTurnScreenOn(false) }

        DeviceAdminLocker.lockNow(this)
        // Not finished. This screen stays alive behind the dark display so that turning
        // it back on brings the alert back rather than nothing at all.
    }

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
                // Dismissed on purpose, which is different from being covered: the guard
                // only hides the alert and the service puts it back when the screen
                // returns. This says it should not.
                WatchService.dismiss(this@AlertActivity)
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

    override fun onResume() {
        super.onResume()
        // The first appearance only. The quiet second exists for the churn of arriving -
        // the lock, the keyguard and the display settling against each other - and none
        // of that happens again on a return. Re-arming it on every resume made the guard
        // deaf for a second each time, which is exactly long enough for something to
        // cover the screen the moment it comes back and not be noticed.
        if (!seen) seenAt = SystemClock.elapsedRealtime()
        seen = true

        // Back in front, so whatever covered it has gone and there is nothing to darken
        // for. Cancelled rather than left to fire into an alert that is already showing.
        handler.removeCallbacks(goDark)
        runCatching { setTurnScreenOn(true) }

        if (wentDark && !isFinishing) {
            wentDark = false
            // The screen is back, so the alarm comes back with it. Asked of the service,
            // which owns the sound.
            Settings.noteAlert(this, "the screen came back, sounding again")
            WatchService.resound(this)
        }
    }

    /**
     * Something covered the alert screen without the phone being unlocked.
     *
     * On this phone the assistant launches over the keyguard on a long press of the power
     * button, and it lands on top of this screen. Anything that can clear the alert
     * without the PIN is a way out for whoever took the phone.
     *
     * So the screen going away is treated as the signal, and the answer is to look like a
     * phone that has been switched off: display out, sound stopped. Whoever is holding it
     * sees nothing to work with.
     *
     * This screen is not finished, though. It waits behind the dark display, so turning
     * the screen back on brings the alert and the sound straight back - see [onResume].
     *
     * Off by default, and checked first: this fights the phone for the front of the
     * display, which is not something to start doing unasked.
     *
     * Then three guards, because going dark when it should not is worse than the hole it
     * closes:
     *  - `seen` means this screen was actually in front, so the launch itself is not it
     *  - a second must have passed, which is longer than the churn of arriving and much
     *    shorter than anyone reaching for the phone
     *  - `isFinishing` means it is closing on purpose, including on unlock
     *  - the display must still be on, or this is the screen going off rather than
     *    something covering it
     *  - locking is skipped if Device Admin is not active, where it would fail anyway
     */
    override fun onPause() {
        super.onPause()

        if (!Settings.guardAlert(this)) return
        // Never in front, so nothing covered it. This is the launch itself.
        if (!seen) return
        if (isFinishing) return

        // Everything that goes wrong here goes wrong in the first moment: the lock, the
        // keyguard, the display coming on and this window being placed all pause and
        // resume against each other while the alert is arriving. A second is longer than
        // any of that and far shorter than picking a phone up and holding a button.
        if (SystemClock.elapsedRealtime() - seenAt < ARM_DELAY_MS) return

        // The one that matters. onPause does not mean "something covered me" - it also
        // fires when the display goes off, which happens moments after this screen
        // appears. Treating that as a cover locked and finished the alert immediately,
        // so turning the screen back on showed nothing and only the alarm was left.
        //
        // Being covered by another window leaves the display on. That is the difference,
        // and it is the only signal available without a permission that watches every app
        // the phone runs.
        val display = getSystemService(PowerManager::class.java)
        if (display?.isInteractive != true) return

        if (!DeviceAdminLocker.isReady(this)) return

        handler.removeCallbacks(goDark)
        handler.postDelayed(goDark, GO_DARK_DELAY_MS)
    }

    /** Back does not dismiss this. Only unlocking does. */
    @Deprecated("Back is deliberately inert here")
    override fun onBackPressed() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(goDark)
        runCatching { unregisterReceiver(unlocked) }
        super.onDestroy()
    }

    private companion object {
        /** How long the alert ignores being paused after it arrives. */
        const val ARM_DELAY_MS = 1_000L

        /** How long to let whatever covered the screen settle before locking. */
        const val GO_DARK_DELAY_MS = 1_000L
    }
}
