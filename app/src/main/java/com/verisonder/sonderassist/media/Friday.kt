package com.verisonder.sonderassist.media

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.verisonder.sonderassist.Settings

/**
 * F.R.I.D.A.Y mode: silent after a theft until the owner asks for sound.
 *
 *  - "Friday" (or any word the owner adds in [Settings.fridayWords]) plays the clip.
 *  - "Friday 30" (any number of seconds, or "a minute") starts a countdown; when it runs
 *    out the siren starts at full volume and loops until the phone is unlocked.
 *
 * Two ways in: said out loud near the phone, which listens for [WINDOW_MS] after a grab,
 * or texted through the text commands ([command]). Every command said out loud starts the
 * minute again; a countdown ends the listening.
 *
 * Listening uses the phone's speech recognizer, restarted after each phrase. Its start beep
 * would give the game away on a phone that is meant to be silent, so the media, notification
 * and system streams are muted while it listens - the clip and siren play on the alarm
 * stream, which is left alone.
 *
 * Everything writes to [Settings.noteFriday], so "it never listened", "it listened and
 * heard nothing" and "it heard something else" are three different lines on screen.
 *
 * Main thread only: SpeechRecognizer must be created and used there.
 */
object Friday {

    const val WINDOW_MS = 60_000L

    sealed interface Command {
        data object Play : Command
        data class Countdown(val seconds: Int) : Command
    }

    private val handler = Handler(Looper.getMainLooper())

    private var app: Context? = null
    private var recognizer: SpeechRecognizer? = null
    private var listenUntil = 0L
    private var listening = false
    private var countdown: Runnable? = null
    private var wake: PowerManager.WakeLock? = null
    private val muted = mutableListOf<Int>()

    /**
     * The trigger word has been heard during this alert. From then on a bare "10 seconds"
     * is a command too, and the word alone does not play the clip again.
     */
    private var awake = false
    private var clipPlayed = false

    /** For the countdown wheel on the alert screen: when the siren is due, and from how long. */
    @Volatile var countdownEndsAt = 0L
        private set
    @Volatile var countdownTotalMs = 0L
        private set
    @Volatile var sirenOn = false
        private set

    // ------------------------------------------------------------------ the voice

    /** After a grab, in F.R.I.D.A.Y mode. */
    fun listen(context: Context) {
        val app = context.applicationContext
        this.app = app
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Settings.noteFriday(app, "cannot listen - microphone not allowed", fresh = true)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(app)) {
            Settings.noteFriday(app, "cannot listen - no speech recognition on this phone", fresh = true)
            return
        }
        Settings.noteFriday(app, "listening for 60 s", fresh = true)
        // A new alert: the word has to be said again, and the clip gets its one play.
        awake = false
        clipPlayed = false
        listening = true
        extend(app)
        startOnce()
    }

    private fun extend(app: Context) {
        listenUntil = SystemClock.elapsedRealtime() + WINDOW_MS
        // Handler timing does not run on a sleeping phone, and this phone is dark in a
        // thief's hand. Bounded to the window.
        holdAwake(app, WINDOW_MS + 5_000)
    }

    private fun startOnce() {
        val app = app ?: return
        if (!listening) return
        if (SystemClock.elapsedRealtime() >= listenUntil) {
            endListening("nothing heard - microphone off")
            return
        }
        val r = recognizer ?: runCatching { SpeechRecognizer.createSpeechRecognizer(app) }
            .getOrNull()
            ?.also {
                it.setRecognitionListener(listener)
                recognizer = it
            }
        if (r == null) {
            endListening("cannot listen - recognizer would not start")
            return
        }
        muteBeeps(app)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName)
        // Tells the recognizer which words to expect, so a made-up name is less likely to
        // come out as something else. Android 13 and up.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.putStringArrayListExtra(
                RecognizerIntent.EXTRA_BIASING_STRINGS,
                ArrayList(Settings.fridayWords(app)),
            )
        }
        runCatching { r.startListening(intent) }
            .onFailure { endListening("cannot listen - ${it.javaClass.simpleName}") }
    }

    private fun restart(afterMs: Long = 150) {
        handler.postDelayed({ startOnce() }, afterMs)
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val app = app ?: return
            val words = Settings.fridayWords(app)
            val command = heard.firstNotNullOfOrNull { parse(it, words) }
                ?: if (awake) heard.firstNotNullOfOrNull { countdownIn(it) } else null
            if (command == null) {
                heard.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
                    Settings.noteFriday(app, "heard \"${it.take(40)}\"")
                }
                restart()
                return
            }
            Settings.noteFriday(app, "heard \"${heard.first().take(40)}\"")
            act(app, command, byVoice = true)
        }

        override fun onError(error: Int) {
            val app = app ?: return
            if (!listening) return
            when (error) {
                // Silence, or speech that was not words. The normal case; go again.
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restart()
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    endListening("cannot listen - microphone not allowed")
                SpeechRecognizer.ERROR_AUDIO ->
                    endListening("cannot listen - microphone blocked (open the app once after a restart)")
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> {
                    recognizer?.runCatching { destroy() }
                    recognizer = null
                    restart(500)
                }
                else -> {
                    Settings.noteFriday(app, "recognizer error $error, retrying")
                    restart(1_000)
                }
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun endListening(why: String?) {
        val app = app
        listening = false
        recognizer?.runCatching { cancel(); destroy() }
        recognizer = null
        app?.let { unmuteBeeps(it) }
        if (countdown == null) releaseAwake()
        if (app != null && why != null) Settings.noteFriday(app, why)
    }

    // ------------------------------------------------------------------ the commands

    /**
     * "friday", "Friday 30", "friday thirty seconds", "friday 1 minute". Null when the word
     * is not there at all.
     */
    fun parse(text: String, triggers: List<String>): Command? {
        val t = text.lowercase().replace('-', ' ')
        val end = triggers
            .mapNotNull { word ->
                Regex("""\b${Regex.escape(word.lowercase())}\b""").find(t)?.range?.last
            }
            .minOrNull() ?: return null
        return countdownIn(t.substring(end + 1)) ?: Command.Play
    }

    /** "10 seconds", "thirty", "a minute" - the number part alone, or null if there is none. */
    fun countdownIn(text: String): Command.Countdown? {
        val tokens = Regex("""[a-z]+|\d+""").findAll(text.lowercase().replace('-', ' '))
            .map { it.value }
            .toList()
        val words = mapOf(
            "a" to 1, "one" to 1, "two" to 2, "five" to 5, "ten" to 10, "fifteen" to 15,
            "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60,
            "ninety" to 90,
        )
        for ((i, token) in tokens.withIndex()) {
            val n = token.toIntOrNull() ?: words[token] ?: continue
            val next = tokens.getOrNull(i + 1).orEmpty()
            val minutes = next == "m" || next.startsWith("min")
            // "a" is only a number in "a minute".
            if (token == "a" && !minutes) continue
            val seconds = if (minutes) n * 60 else n
            if (seconds > 0) return Command.Countdown(seconds.coerceAtMost(3_600))
        }
        return null
    }

    /** For texts: the command has to start with one of the words, not merely contain it. */
    fun startsWithTrigger(text: String, triggers: List<String>): Boolean {
        val t = text.lowercase().trimStart()
        return triggers.any { word ->
            val w = word.lowercase()
            t.startsWith(w) && (t.length == w.length || !t[w.length].isLetterOrDigit())
        }
    }

    /** From a text command. Works whether or not anything is listening. */
    fun command(context: Context, command: Command): Boolean {
        val app = context.applicationContext
        this.app = app
        return act(app, command, byVoice = false)
    }

    /** True if something was played or started; false for a repeated wake word. */
    private fun act(app: Context, command: Command, byVoice: Boolean): Boolean {
        when (command) {
            Command.Play -> {
                awake = true
                if (clipPlayed) {
                    // Once per alert. Said again it only keeps it listening.
                    Settings.noteFriday(app, "awake - waiting for a command")
                    if (byVoice) {
                        extend(app)
                        restart()
                    }
                    return false
                }
                clipPlayed = true
                Settings.noteFriday(app, "playing the clip")
                if (byVoice) {
                    // Not listening to its own voice; the minute starts again after.
                    recognizer?.runCatching { cancel() }
                    Alarm.playFriday(app) {
                        if (listening) {
                            extend(app)
                            startOnce()
                        }
                    }
                } else {
                    Alarm.playFriday(app)
                }
            }
            is Command.Countdown -> {
                endListening(null)
                cancelCountdown()
                Settings.noteFriday(app, "siren in ${command.seconds} s")
                // One of the two replies, at random.
                Alarm.playAck(app)
                holdAwake(app, command.seconds * 1000L + 10_000)
                val ms = command.seconds * 1000L
                countdownTotalMs = ms
                countdownEndsAt = SystemClock.elapsedRealtime() + ms
                sirenOn = false
                val task = Runnable {
                    countdown = null
                    sirenOn = true
                    Settings.noteFriday(app, "siren on")
                    Alarm.siren(app)
                    releaseAwake()
                }
                countdown = task
                handler.postDelayed(task, ms)
            }
        }
        return true
    }

    private fun cancelCountdown() {
        countdown?.let { handler.removeCallbacks(it) }
        countdown = null
        countdownEndsAt = 0L
        countdownTotalMs = 0L
    }

    /** Unlocking, or the owner's gesture: everything stops. The sound is [Alarm.stop]'s. */
    fun stopAll() {
        val wasActive = listening || countdown != null
        cancelCountdown()
        sirenOn = false
        awake = false
        clipPlayed = false
        endListening(null)
        releaseAwake()
        app?.let { if (wasActive) Settings.noteFriday(it, "stopped - phone unlocked") }
    }

    // ------------------------------------------------------------------ plumbing

    private fun muteBeeps(app: Context) {
        if (muted.isNotEmpty()) return
        val audio = app.getSystemService(AudioManager::class.java) ?: return
        for (stream in listOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM,
        )) {
            if (runCatching { audio.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0) }.isSuccess) {
                muted += stream
            }
        }
    }

    private fun unmuteBeeps(app: Context) {
        val audio = app.getSystemService(AudioManager::class.java) ?: return
        for (stream in muted) {
            runCatching { audio.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0) }
        }
        muted.clear()
    }

    private fun holdAwake(app: Context, ms: Long) {
        releaseAwake()
        wake = app.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SonderAssist:friday")
            ?.apply {
                setReferenceCounted(false)
                acquire(ms)
            }
    }

    private fun releaseAwake() {
        wake?.runCatching { if (isHeld) release() }
        wake = null
    }
}
