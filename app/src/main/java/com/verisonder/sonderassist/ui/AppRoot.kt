package com.verisonder.sonderassist.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
// Aliased: the app has its own Settings, and two imports of one name do not compile.
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.clickable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.verisonder.sonderassist.CrashLog
import com.verisonder.sonderassist.Settings
import com.verisonder.sonderassist.security.DeviceAdminLocker
import com.verisonder.sonderassist.security.Keepalive
import com.verisonder.sonderassist.security.PowerMenu
import com.verisonder.sonderassist.command.ChatCommandListener
import com.verisonder.sonderassist.media.Alarm
import com.verisonder.sonderassist.report.Reporter
import com.verisonder.sonderassist.sensor.WatchService
import com.verisonder.sonderassist.learn.AlertLog
import com.verisonder.sonderassist.learn.AlertRecord

/**
 * One screen.
 *
 * The status is the hero and everything else is quiet underneath it, because the only
 * question anyone opens this app to answer is whether it is on. Settings are revealed
 * once it can actually run — offering a sensitivity slider to someone who has not granted
 * permission yet is asking them to tune something that is switched off.
 */
/**
 * The devices already paired with the phone, as address to name.
 *
 * Paired, not nearby: nothing is scanned. Scanning needs location permission and a user
 * who wonders why a theft app wants it, and the device this is for is one that is already
 * paired by definition. Empty when the permission has not been granted, which is also
 * what it looks like before the switch is first turned on.
 */
private fun pairedDevices(context: android.content.Context): List<Pair<String, String>> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.BLUETOOTH_CONNECT
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        return emptyList()
    }
    return runCatching {
        context.getSystemService(android.bluetooth.BluetoothManager::class.java)
            ?.adapter
            ?.bondedDevices
            ?.map { it.address to (it.name ?: it.address) }
            ?.sortedBy { it.second }
            .orEmpty()
    }.getOrDefault(emptyList())
}

@Composable
fun AppRoot(activity: ComponentActivity) {
    /**
     * Which page is showing.
     *
     * One screen carried everything, then two did: the status, then a gear button over a
     * long scroll of thirteen sections in no order anyone could guess. Now home holds the
     * status and one row per subject, each row saying what is on, and each page holds one
     * subject only.
     */
    var page by remember { mutableStateOf(Page.Home) }
    var granted by remember { mutableStateOf(DeviceAdminLocker.isReady(activity)) }
    // Refreshed on resume, so returning from a failed tile tap shows the reason.
    var crash by remember { mutableStateOf(CrashLog.read(activity)) }
    var friday by remember { mutableStateOf(Settings.friday(activity)) }
    var blockPower by remember { mutableStateOf(Settings.blockPowerMenu(activity)) }
    var alertNote by remember { mutableStateOf(Settings.alertNote(activity)) }
    var fullScreen by remember { mutableStateOf(canUseFullScreen(activity)) }
    var guardAlert by remember { mutableStateOf(Settings.guardAlert(activity)) }
    var uprightGuard by remember { mutableStateOf(Settings.uprightGuard(activity)) }
    var whichDetector by remember { mutableStateOf(Settings.detector(activity)) }
    var learn by remember { mutableStateOf(Settings.learn(activity)) }
    var learnCounts by remember { mutableStateOf(AlertLog.counts(activity)) }
    var learnPending by remember { mutableStateOf(AlertLog.pending(activity)) }
    var confirmClearLog by remember { mutableStateOf(false) }
    // Clearing asks twice: once to start, once more with what is about to go.
    var confirmClearAgain by remember { mutableStateOf(false) }
    var alertEntries by remember { mutableStateOf(AlertLog.entries(activity)) }
    var openAlert by remember { mutableStateOf<Long?>(null) }
    // Every answer goes through here, so the counts, the list and the prompt never disagree.
    val answerAlert = { id: Long, label: String ->
        AlertLog.label(activity, id, label)
        learnPending = AlertLog.pending(activity)
        learnCounts = AlertLog.counts(activity)
        alertEntries = AlertLog.entries(activity)
    }
    var tether by remember { mutableStateOf(Settings.tetherEnabled(activity)) }
    var tetherAddress by remember { mutableStateOf(Settings.tetherAddress(activity)) }
    var tetherGrace by remember { mutableIntStateOf(Settings.tetherGraceSeconds(activity)) }
    var strapVibrate by remember { mutableStateOf(Settings.strapVibrate(activity)) }
    var strapListen by remember { mutableStateOf(Settings.strapListen(activity)) }
    var strapMessage by remember { mutableStateOf(Settings.strapMessage(activity)) }
    var typeSpeed by remember { mutableIntStateOf(Settings.typeSpeedMs(activity)) }
    var strapDelay by remember { mutableIntStateOf(Settings.strapDelaySeconds(activity)) }
    var paired by remember { mutableStateOf(pairedDevices(activity)) }
    var connected by remember { mutableStateOf(Settings.connectedSet(activity)) }
    var strapNote by remember { mutableStateOf(Settings.strapNote(activity)) }
    val askBluetooth = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { paired = pairedDevices(activity) }
    var vibrateOnAlert by remember { mutableStateOf(Settings.vibrateOnAlert(activity)) }
    var report by remember { mutableStateOf(Settings.reportEnabled(activity)) }
    var smsNumber by remember { mutableStateOf(Settings.smsNumber(activity)) }
    var tgToken by remember { mutableStateOf(Settings.telegramToken(activity)) }
    var tgChat by remember { mutableStateOf(Settings.telegramChat(activity)) }
    var reportDelay by remember { mutableIntStateOf(Settings.reportDelaySeconds(activity)) }
    var reportCount by remember { mutableIntStateOf(Settings.reportCount(activity)) }
    var statusEvery by remember { mutableIntStateOf(Settings.statusEveryMinutes(activity)) }
    var reportNote by remember { mutableStateOf(Settings.reportNote(activity)) }
    var reportRunning by remember { mutableStateOf(Settings.reportRunning(activity)) }
    var pinLive by remember { mutableStateOf(Settings.livePin(activity) != null) }
    var stopping by remember { mutableStateOf(false) }
    // One stop, used by the home card and the Find my phone page alike. The result comes
    // back from the network, so the card only goes away once the pin is really down.
    val stopSending = {
        stopping = true
        reportRunning = false
        Reporter.stop(activity) { _ ->
            stopping = false
            reportRunning = Settings.reportRunning(activity)
            pinLive = Settings.livePin(activity) != null
            reportNote = Settings.reportNote(activity)
        }
    }
    val askReportPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    var smsCommands by remember { mutableStateOf(Settings.smsCommands(activity)) }
    var secretWord by remember { mutableStateOf(Settings.secretWord(activity)) }
    var commandNote by remember { mutableStateOf(Settings.commandNote(activity)) }
    val askReceiveSms = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    var smsFromAnyone by remember { mutableStateOf(Settings.smsFromAnyone(activity)) }
    var smsContacts by remember { mutableStateOf(Settings.smsContacts(activity)) }
    // The phone-number picker grants read access to the one row picked, so no contacts
    // permission is needed.
    val pickContact = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            activity.contentResolver.query(
                uri,
                arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ),
                null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val number = c.getString(0).orEmpty()
                    val name = c.getString(1).orEmpty()
                    if (number.isNotBlank()) {
                        Settings.setSmsContacts(
                            activity,
                            smsContacts + Settings.Contact(name, number),
                        )
                        smsContacts = Settings.smsContacts(activity)
                    }
                }
            }
        }
    }
    var fridayNote by remember { mutableStateOf(Settings.fridayNote(activity)) }
    var fridayWords by remember { mutableStateOf(Settings.fridayWords(activity)) }
    var newWord by remember { mutableStateOf("") }
    var countdownLabel by remember { mutableStateOf(Settings.countdownLabel(activity)) }
    var countdownEnd by remember { mutableStateOf(Settings.countdownEnd(activity)) }
    var cmdWords by remember { mutableStateOf(Settings.commandWords(activity)) }
    var chatCommands by remember { mutableStateOf(Settings.chatCommands(activity)) }
    var chatAccess by remember { mutableStateOf(ChatCommandListener.hasAccess(activity)) }
    var hasMic by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                activity,
                android.Manifest.permission.RECORD_AUDIO,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val askMic = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMic = granted
        // So the running service picks up the microphone type now, while the app is open.
        if (granted) runCatching { WatchService.sync(activity) }
    }
    var shizuku by remember { mutableStateOf(PowerMenu.available()) }
    var shizukuAsk by remember { mutableStateOf(PowerMenu.needsPermission()) }
    var suppressed by remember { mutableStateOf(Settings.powerMenuSuppressed(activity)) }
    var tileNote by remember { mutableStateOf(Settings.tileNote(activity)) }
    var hasLock by remember { mutableStateOf(DeviceAdminLocker.hasLockScreen(activity)) }
    // Read from the service, not from a local flag. The old screen kept its own boolean
    // that reset on every recomposition, so it could claim to be off while running.
    var watching by remember { mutableStateOf(WatchService.watching(activity)) }
    // Separate from watching. The strap only needs the service, not the watch.
    var serviceUp by remember { mutableStateOf(WatchService.isRunning) }

    var sensitivity by remember { mutableFloatStateOf(Settings.sensitivity(activity)) }
    var alarmOn by remember { mutableStateOf(Settings.alarmEnabled(activity)) }
    var grace by remember { mutableIntStateOf(Settings.graceSeconds(activity)) }
    var repeats by remember { mutableIntStateOf(Settings.alarmRepeats(activity)) }
    var message by remember { mutableStateOf(Settings.message(activity)) }
    var alarmName by remember { mutableStateOf(Settings.alarmUri(activity)?.lastPathSegment) }
    var confirmRemove by remember { mutableStateOf(false) }
    var backgroundName by remember { mutableStateOf(Settings.backgroundUri(activity)?.lastPathSegment) }
    var readout by remember { mutableStateOf(WatchService.lastVerdict) }
    var batteryExempt by remember { mutableStateOf(Keepalive.isBatteryExempt(activity)) }
    var canOverlay by remember { mutableStateOf(AndroidSettings.canDrawOverlays(activity)) }

    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Persisted, or the sound stops working the next time the phone restarts and
            // the alarm falls silently back to the default at the worst moment.
            runCatching {
                activity.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            Settings.setAlarmUri(activity, uri)
            alarmName = uri.lastPathSegment
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                activity.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            Settings.setBackgroundUri(activity, uri)
            backgroundName = uri.lastPathSegment
        }
    }

    // Polls while the screen is open, and only while it is open. The detector already
    // knows why it did or did not fire; there was simply no way for it to say so, and
    // guessing at that from a description costs a build each time.
    androidx.compose.runtime.LaunchedEffect(watching) {
        while (watching) {
            readout = WatchService.lastVerdict
            kotlinx.coroutines.delay(300)
        }
    }

    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = DeviceAdminLocker.isReady(activity)
                hasLock = DeviceAdminLocker.hasLockScreen(activity)
                watching = WatchService.watching(activity)
                batteryExempt = Keepalive.isBatteryExempt(activity)
                canOverlay = AndroidSettings.canDrawOverlays(activity)
                // The third and last guard. If the service was killed while the power
                // menu was closed, opening the app is the person's own way back.
                runCatching { PowerMenu.restore(activity) }
                crash = CrashLog.read(activity)
                shizuku = PowerMenu.available()
                shizukuAsk = PowerMenu.needsPermission()
                suppressed = Settings.powerMenuSuppressed(activity)
                alertNote = Settings.alertNote(activity)
                learnCounts = AlertLog.counts(activity)
                learnPending = AlertLog.pending(activity)
                alertEntries = AlertLog.entries(activity)
                reportNote = Settings.reportNote(activity)
                reportRunning = Settings.reportRunning(activity)
                pinLive = Settings.livePin(activity) != null
                commandNote = Settings.commandNote(activity)
                fridayNote = Settings.fridayNote(activity)
                chatAccess = ChatCommandListener.hasAccess(activity)
                hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
                    activity,
                    android.Manifest.permission.RECORD_AUDIO,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                fullScreen = canUseFullScreen(activity)
                // Re-read on every return, because the two things that change it both
                // happen outside this app: granting the permission, and pairing the watch
                // in the phone's own Bluetooth settings. Without this the list stays empty
                // after the person has gone and fixed exactly the thing it asked them to.
                paired = pairedDevices(activity)
                tetherAddress = Settings.tetherAddress(activity)
                // **Every time, not only when the switch is touched.** The switch was the
                // only thing that started the service, so anyone who already had the
                // strap on before this existed never got one — and the screen said
                // nothing, because there was nothing to say it with.
                runCatching { WatchService.sync(activity) }
                serviceUp = WatchService.isRunning
                connected = Settings.connectedSet(activity)
                strapNote = Settings.strapNote(activity)
                tileNote = Settings.tileNote(activity)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // The Surface is load-bearing, not decoration. It sets the background *and*
    // LocalContentColor to onSurface; without it every unstyled Text falls back to
    // Compose's default of black, which on a dark background reads as washed out and
    // half-legible. Dropping it during a rewrite is what made the screen look grey.
    // A fresh page starts at its top, and the system back gesture returns to the home
    // screen instead of closing the app from inside a page.
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }
    BackHandler(enabled = page != Page.Home) { page = page.up }

    Surface(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        if (page != Page.Home) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { page = page.up }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(page.title, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(8.dp))
        } else {
            // The home screen carries three things and was sitting hard against the status
            // bar with the rest of the screen empty below it. Pushed down so it reads as a
            // block on the screen rather than a list that ran out.
            Spacer(Modifier.height(64.dp))
            Text("SonderAssist", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Locks the screen when the phone is taken from your hand.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(28.dp))

        // Written since the first version and read by nothing, which is the same as not
        // recording it at all. It only appears when there is something to say.
        if (page == Page.Home) crash?.let { text ->
            Text("Something failed", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "The last error the app recorded. Show this when reporting a problem.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(text.take(4000), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                CrashLog.clear(activity)
                crash = null
            }) { Text("Clear") }
            Spacer(Modifier.height(28.dp))
        }

        // Said first and in red: the location going out is the one thing on this screen
        // that must never be missed.
        if (page == Page.Home && (reportRunning || pinLive)) {
            SendingCard(
                live = reportRunning,
                stopping = stopping,
                onStop = stopSending,
            )
            Spacer(Modifier.height(28.dp))
        }

        when {
            !hasLock -> Blocker(
                title = "Set a screen lock first",
                // lockNow only sleeps an insecure device: the screen goes off and comes
                // straight back on with everything visible. Claiming to protect the phone
                // in that state would be a lie.
                detail = "This phone has no PIN, pattern or password, so there is nothing " +
                    "to stop whoever takes it turning the screen back on.",
                action = "Open security settings",
            ) { activity.startActivity(Intent(AndroidSettings.ACTION_SECURITY_SETTINGS)) }

            !granted -> Blocker(
                title = "Not armed",
                detail = "SonderAssist needs permission to turn on the lock screen. The " +
                    "next screen lists what it can do: lock the screen, and nothing else.",
                action = "Give permission",
            ) { activity.startActivity(DeviceAdminLocker.activationIntent(activity)) }

            else -> {
                // Home carries the status and one row per page; each page holds one subject.
                if (page == Page.Home) {
                StatusCard(
                    watching = watching,
                    onToggle = {
                        watching = !watching
                        WatchService.setWatching(activity, watching)
                    },
                )

                if (watching) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Right now: $readout",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                // One row per page, each saying what is on, so the home screen answers "what is this
                // doing" without opening anything, and every setting is two taps away at most.
                Spacer(Modifier.height(28.dp))
                HorizontalDivider()
                PageRow(
                    "Detection",
                    when (whichDetector) {
                        Settings.Detector.V1 -> "v1"
                        Settings.Detector.V2 -> "v2"
                        Settings.Detector.BOTH -> "v1 + v2"
                    } + " · ${(sensitivity * 100).toInt()}%" +
                        (if (learn) " · learning" else "") +
                        (if (learn && learnCounts.unanswered > 0) " · ${learnCounts.unanswered} to answer" else ""),
                ) { page = Page.Detection }
                HorizontalDivider()
                PageRow(
                    "The strap",
                    if (tether) {
                        Settings.tetherName(activity).ifBlank { "On" } + " · ${tetherGrace} s"
                    } else {
                        "Off"
                    },
                ) { page = Page.Strap }
                HorizontalDivider()
                PageRow(
                    "When it fires",
                    if (friday) {
                        "F.R.I.D.A.Y" + if (hasMic) "" else " · mic off"
                    } else if (alarmOn) "Alarm after $grace s" else "Lock only",
                ) { page = Page.Fires }
                HorizontalDivider()
                PageRow(
                    "Find my phone",
                    listOfNotNull(
                        if (reportRunning || pinLive) "Sending now"
                        else if (report) "Location on" else null,
                        if (smsCommands) "Texts on" else null,
                    ).joinToString(" · ").ifEmpty { "Off" },
                ) { page = Page.Find }
                HorizontalDivider()
                PageRow("Power menu", if (blockPower) "Blocked" else "Off") { page = Page.Power }
                HorizontalDivider()
                PageRow(
                    "Keep it running",
                    listOf(!batteryExempt, !canOverlay).count { it }.let {
                        if (it == 0) "All allowed" else "$it to fix"
                    },
                ) { page = Page.Running }
                HorizontalDivider()
                Spacer(Modifier.height(32.dp))
                }

                if (page == Page.Detection) {
                // Which detector. v1 is the jerk detector everything so far was tuned on;
                // v2 is v1 with a shake gate that ignores knocks. Both runs them side by side and
                // either one firing locks, which is how one gets compared with the other
                // on a real phone rather than in a fixture.
                Spacer(Modifier.height(24.dp))
                SectionLabel("Detector")
                Spacer(Modifier.height(8.dp))
                val choices = listOf(
                    Settings.Detector.V1 to "v1",
                    Settings.Detector.V2 to "v2",
                    Settings.Detector.BOTH to "Both",
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    choices.forEachIndexed { index, (which, label) ->
                        SegmentedButton(
                            selected = whichDetector == which,
                            onClick = {
                                whichDetector = which
                                Settings.setDetector(activity, which)
                                WatchService.retune(activity)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                        ) { Text(label) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    when (whichDetector) {
                        Settings.Detector.V1 -> "The acceleration. What every release so far has used."
                        Settings.Detector.V2 -> "v1, but ignores knocks and pockets."
                        Settings.Detector.BOTH -> "Either one firing locks."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(Modifier.height(32.dp))
                SectionLabel("Sensitivity")
                Text(
                    when {
                        sensitivity < 0.3f -> "Only a hard pull. Fewer false locks, more misses."
                        sensitivity > 0.7f -> "Fires easily. Expect it to lock when you did not mean it to."
                        else -> "Balanced. Move it either way once you know how it behaves."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = sensitivity,
                    onValueChange = { sensitivity = it },
                    onValueChangeFinished = {
                        Settings.setSensitivity(activity, sensitivity)
                        WatchService.retune(activity)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                // The numbers, not just an adjective. They are what the slider actually
                // moves, and without them "balanced" means nothing that can be compared
                // between two phones or two attempts.
                val tuned = remember(sensitivity) {
                    com.verisonder.sonderassist.detect.SnatchDetector.Tuning
                        .forSensitivity(sensitivity)
                }
                Text(
                    "v1 needs an acceleration of %,.0f, or %,.0f with a twist."
                        .format(tuned.axialJerk, tuned.axialJerkWithRotation),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    // That figure alone is not enough: it is a rate of change, and
                    // flicking the edge of a still phone produces a large one while
                    // moving nothing.
                    "And at least %.1f m/s² toward the top edge.".format(tuned.minAxialAccel),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (whichDetector != Settings.Detector.V1) {
                    Text(
                        "v2 needs the same, then %.0f m/s² of shaking around it."
                            .format(com.verisonder.sonderassist.detect.SnatchDetector.Tuning.V2_MIN_SHAKE),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // The alert log. Every grab alert is written down with the few seconds of
                // samples that caused it, and the answer to "was it a theft?" goes with
                // it. Shared as one file, it is what lets the detector be tuned against
                // real false alarms instead of guessed ones.
                Spacer(Modifier.height(24.dp))
                SectionLabel("Learning")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ask after each alert", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Saves how it fired, to tune it later.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = learn,
                        onCheckedChange = {
                            learn = it
                            Settings.setLearn(activity, it)
                        },
                    )
                }
                if (learn) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${learnCounts.falseAlarm} false · ${learnCounts.theft} theft · " +
                            "${learnCounts.unanswered} unanswered",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val waiting = learnPending
                    if (waiting != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Last alert, ${alertTime(waiting)}: was it a theft?",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { answerAlert(waiting, AlertLog.THEFT) }) {
                                Text("Theft")
                            }
                            FilledTonalButton(onClick = { answerAlert(waiting, AlertLog.FALSE) }) {
                                Text("False alarm")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = {
                        alertEntries = AlertLog.entries(activity)
                        page = Page.Alerts
                    }) { Text("See every alert") }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { shareAlertLog(activity) }) { Text("Share") }
                        TextButton(onClick = { confirmClearLog = true }) { Text("Clear") }
                    }
                }
                }

                if (confirmClearLog) {
                    AlertDialog(
                        onDismissRequest = { confirmClearLog = false },
                        title = { Text("Clear the log?") },
                        text = { Text("Every saved alert is deleted.") },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmClearLog = false
                                confirmClearAgain = true
                            }) { Text("Clear") }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmClearLog = false }) { Text("Cancel") }
                        },
                    )
                }
                if (confirmClearAgain) {
                    AlertDialog(
                        onDismissRequest = { confirmClearAgain = false },
                        title = { Text("Sure?") },
                        text = {
                            Text(
                                "${learnCounts.theft + learnCounts.falseAlarm + learnCounts.unanswered} alerts, " +
                                    "${learnCounts.unanswered} unanswered. This cannot be undone.",
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                AlertLog.clear(activity)
                                learnCounts = AlertLog.counts(activity)
                                learnPending = null
                                alertEntries = emptyList()
                                confirmClearAgain = false
                            }) { Text("Delete all") }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmClearAgain = false }) { Text("Keep") }
                        },
                    )
                }

                if (page == Page.Alerts) {
                    Text(
                        "${learnCounts.falseAlarm} false · ${learnCounts.theft} theft · " +
                            "${learnCounts.unanswered} unanswered",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (alertEntries.isEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("No alerts saved.", style = MaterialTheme.typography.bodyMedium)
                    }
                    alertEntries.forEach { entry ->
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    openAlert = entry.id
                                    page = Page.Alert
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                alertTime(entry.id),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                labelText(entry.label),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (entry.label == null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        if (entry.label == null) {
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { answerAlert(entry.id, AlertLog.THEFT) }) {
                                    Text("Theft")
                                }
                                FilledTonalButton(onClick = { answerAlert(entry.id, AlertLog.FALSE) }) {
                                    Text("False alarm")
                                }
                            }
                        }
                    }
                }

                if (page == Page.Alert) {
                    val id = openAlert
                    val text = remember(id) { id?.let { AlertLog.read(activity, it) } }
                    val parsed = remember(text) {
                        text?.let { runCatching { AlertRecord.parse(it) }.getOrNull() }
                    }
                    val label = alertEntries.firstOrNull { it.id == id }?.label
                    if (id == null || text == null || parsed == null) {
                        Text("This alert is no longer saved.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        val (header, samples) = parsed
                        Text(alertTime(id), style = MaterialTheme.typography.titleMedium)
                        Text(labelText(label), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { answerAlert(id, AlertLog.THEFT) }) {
                                Text(if (label == AlertLog.THEFT) "✓ Theft" else "Theft")
                            }
                            FilledTonalButton(onClick = { answerAlert(id, AlertLog.FALSE) }) {
                                Text(if (label == AlertLog.FALSE) "✓ False alarm" else "False alarm")
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        Text("Movement", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Top-edge acceleration (bright), total (dim). Line: where it fired.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        AlertChart(samples, header["trigger_ns"]?.toLongOrNull())

                        Spacer(Modifier.height(20.dp))
                        Text("What it saw", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        listOf(
                            "Fired by" to header["by"],
                            "Acceleration" to header["value"],
                            "Twist (rad/s)" to header["rotation"],
                            "Detectors on" to header["detectors"],
                            "Sensitivity" to header["sensitivity"]?.toFloatOrNull()
                                ?.let { "${(it * 100).toInt()}%" },
                            "Upright guard" to header["upright_guard"],
                            "Sample rate (Hz)" to header["rate_hz"],
                            "Samples" to samples.size.toString(),
                            "Readout" to header["readout"],
                            "App" to header["app"],
                        ).forEach { (k, v) ->
                            if (v != null) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        k,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(0.4f),
                                    )
                                    Text(
                                        v,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(0.6f),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        var showRaw by remember(id) { mutableStateOf(false) }
                        TextButton(onClick = { showRaw = !showRaw }) {
                            Text(if (showRaw) "Hide raw data" else "Show raw data")
                        }
                        if (showRaw) {
                            Text(
                                text,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                ),
                            )
                        }
                    }
                }

                if (page == Page.Strap) {
                // Its own page, reached from a row on the home screen that says whether it is on.
                Spacer(Modifier.height(24.dp))
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Lock if the strap goes away", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Locks silently if it disconnects and stays gone. No message, no sound, screen stays off.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = tether,
                        onCheckedChange = {
                            tether = it
                            Settings.setTetherEnabled(activity, it)
                            // The strap lives in the service, so the switch has to be
                            // able to start it. It used to depend on the watch being on
                            // and said nothing about it.
                            WatchService.sync(activity)
                            // isRunning lags the request, so paint what was asked for.
                            serviceUp = watching || (it && tetherAddress.isNotEmpty())
                            if (it) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    askBluetooth.launch(
                                        arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT)
                                    )
                                }
                                paired = pairedDevices(activity)
                            }
                        },
                    )
                }

                if (tether) {
                    Spacer(Modifier.height(8.dp))
                    if (paired.isEmpty()) {
                        // Two reasons the list can be empty and they need different
                        // answers, so both ways out are offered rather than making the
                        // person work out which one they are in.
                        Text(
                            "Not allowed to see your devices, or nothing is paired yet.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    askBluetooth.launch(
                                        arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT)
                                    )
                                }
                                paired = pairedDevices(activity)
                            }) { Text("Allow") }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = {
                                runCatching {
                                    activity.startActivity(
                                        Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS)
                                    )
                                }
                            }) { Text("Pair a device") }
                        }
                    } else {
                        Text(
                            "Which device — nothing happens until one is chosen",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        paired.forEach { (address, name) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        Settings.setTether(activity, address, name)
                                        tetherAddress = address
                                        WatchService.sync(activity)
                                        serviceUp = true
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = address.equals(tetherAddress, ignoreCase = true),
                                    onClick = {
                                        Settings.setTether(activity, address, name)
                                        tetherAddress = address
                                        WatchService.sync(activity)
                                        serviceUp = true
                                    },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        if (connected.contains(address.uppercase())) {
                                            "connected — $address"
                                        } else {
                                            address
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Wait $tetherGrace seconds before acting",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "A reconnect inside this is ignored.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = tetherGrace.toFloat(),
                        onValueChange = { tetherGrace = it.toInt() },
                        onValueChangeFinished = {
                            Settings.setTetherGraceSeconds(activity, tetherGrace)
                        },
                        valueRange = 0f..300f,
                    )

                    Spacer(Modifier.height(8.dp))
                    Text("Message on a strap alert", style = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(
                        value = strapMessage,
                        onValueChange = {
                            strapMessage = it.take(200)
                            Settings.setStrapMessage(activity, strapMessage)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    Text(
                        "Leave it blank for nothing at all. Separate from the grab message.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Buzz when the strap fires", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "The only sign you get, since nothing else shows.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(
                            checked = strapVibrate,
                            onCheckedChange = {
                                strapVibrate = it
                                Settings.setStrapVibrate(activity, it)
                            },
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Listen for \"${fridayWords.first()}\"", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (friday) "For 60 s when the strap fires." else "Needs F.R.I.D.A.Y mode on.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(
                            checked = strapListen,
                            onCheckedChange = {
                                strapListen = it
                                Settings.setStrapListen(activity, it)
                                if (it && !hasMic) {
                                    askMic.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Send the location after $strapDelay seconds",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "Separate from the grab setting. Needs the location report on.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = strapDelay.toFloat(),
                        onValueChange = { strapDelay = it.toInt() },
                        onValueChangeFinished = {
                            Settings.setStrapDelaySeconds(activity, strapDelay)
                        },
                        valueRange = 0f..600f,
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (tetherAddress.isEmpty()) {
                            "Not running — no device chosen yet."
                        } else if (serviceUp) {
                            "Running. The strap is listening."
                        } else {
                            "Not running. Nothing can reach the strap in this state."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (serviceUp && tetherAddress.isNotEmpty()) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )

                    Spacer(Modifier.height(12.dp))
                    Text("What the strap has seen", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        strapNote ?: "Nothing yet.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        // Said outright, because an empty list is the finding and an
                        // absence reads as nothing having happened rather than as an
                        // answer.
                        "It should say listening whenever this screen is open.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                }

                if (page == Page.Fires) {
                Text(
                    if (typeSpeed == 0) "No typing effect" else "Type it out at $typeSpeed ms a letter",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Both messages. Drag to nought for all at once.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = typeSpeed.toFloat(),
                    onValueChange = { typeSpeed = it.toInt() },
                    onValueChangeFinished = { Settings.setTypeSpeedMs(activity, typeSpeed) },
                    valueRange = 0f..150f,
                )

                Spacer(Modifier.height(16.dp))
                SectionLabel("Message on the lock screen")
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(200) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    // Worth saying once, plainly: this is shown over the keyguard, which
                    // is the point and also the risk.
                    "Readable without unlocking. Nothing private.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { Settings.setMessage(activity, message) }) {
                    Text("Save message")
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    backgroundName?.let { "Background: $it" } ?: "Background: plain colour",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { pickImage.launch(arrayOf("image/*")) }) {
                        Text("Choose a picture")
                    }
                    if (backgroundName != null) {
                        TextButton(onClick = {
                            Settings.setBackgroundUri(activity, null)
                            backgroundName = null
                        }) { Text("Use plain") }
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(28.dp))

                SectionLabel("When the alert fires")
                Text(
                    // Named exactly as the phone names them, because a permission
                    // the person cannot find is a permission that stays off. Both are
                    // required and they are separate entries: one lets the window be
                    // created at all, the other lets it sit over the keyguard. Found the
                    // hard way, after a restart set one back to Deny - the alarm still
                    // sounded and the screen simply never appeared.
                    "Needs both “Open new windows while running in the background” and “Show on Lock screen”.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "A reboot can turn them off again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = { openPermissions(activity) }) {
                    Text("Open other permissions")
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    if (fullScreen) {
                        "Full-screen alerts are allowed."
                    } else {
                        "Not allowed, so the alert cannot open itself. The alarm still sounds."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (fullScreen) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                if (!fullScreen && Build.VERSION.SDK_INT >= 34) {
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = {
                        runCatching {
                            activity.startActivity(
                                Intent(
                                    AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                    android.net.Uri.parse("package:" + activity.packageName),
                                )
                            )
                        }
                    }) { Text("Allow full-screen alerts") }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Lock again if something covers it",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "The assistant can cover the alert. This locks again and brings it back, up to five times.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = guardAlert,
                        onCheckedChange = {
                            guardAlert = it
                            Settings.setGuardAlert(activity, it)
                        },
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Ignore it while the phone is upside down",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "A phone goes into a pocket top edge first, which looks like a grab. This ignores those.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = uprightGuard,
                        onCheckedChange = {
                            uprightGuard = it
                            Settings.setUprightGuard(activity, it)
                        },
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Buzz when it fires", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Goes out as an alarm, so it arrives on a silent phone.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = vibrateOnAlert,
                        onCheckedChange = {
                            vibrateOnAlert = it
                            Settings.setVibrateOnAlert(activity, it)
                        },
                    )
                }

                alertNote?.let { note ->
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "The last alert, step by step",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(note, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        // Said outright, because the absence of a line is the finding and
                        // an absence is easy to read straight past.
                        "If the last line is not the screen opening, it never appeared.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = { openPermissions(activity) }) {
                        Text("Open other permissions")
                    }
                }
                }

                if (page == Page.Find) {
                SectionLabel("Send the location")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("After a theft", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Position goes out by text and as a live Telegram pin.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = report,
                        onCheckedChange = {
                            report = it
                            Settings.setReportEnabled(activity, it)
                            if (it) {
                                askReportPermissions.launch(
                                    arrayOf(
                                        android.Manifest.permission.SEND_SMS,
                                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    )
                                )
                            }
                        },
                    )
                }

                AnimatedVisibility(visible = report) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            // The reason the delay is short, said where the slider is.
                            "Ten seconds on the power button ends everything, so keep this short.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Wait $reportDelay seconds, then send $reportCount texts",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Slider(
                            value = reportDelay.toFloat(),
                            onValueChange = { reportDelay = it.toInt() },
                            onValueChangeFinished = {
                                Settings.setReportDelaySeconds(activity, reportDelay)
                            },
                            valueRange = 15f..300f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Slider(
                            value = reportCount.toFloat(),
                            onValueChange = { reportCount = it.toInt().coerceAtLeast(1) },
                            onValueChangeFinished = {
                                Settings.setReportCount(activity, reportCount)
                            },
                            valueRange = 1f..15f,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(16.dp))
                        Text(
                            "A written update every $statusEvery minutes",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "The pin has no words, so the battery goes out separately.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = statusEvery.toFloat(),
                            onValueChange = { statusEvery = it.toInt() },
                            onValueChangeFinished = {
                                Settings.setStatusEveryMinutes(activity, statusEvery)
                            },
                            valueRange = 5f..240f,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Text(
                            "The pin runs until stopped. Only texts are counted.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = smsNumber,
                            onValueChange = { smsNumber = it.take(24) },
                            label = { Text("Phone number for the text") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(onClick = { Settings.setSmsNumber(activity, smsNumber) }) {
                            Text("Save number")
                        }

                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = tgToken,
                            onValueChange = { tgToken = it.take(80) },
                            label = { Text("Telegram bot token") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = tgChat,
                            onValueChange = { tgChat = it.take(40) },
                            label = { Text("Telegram chat id") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(onClick = {
                            Settings.setTelegramToken(activity, tgToken)
                            Settings.setTelegramChat(activity, tgChat)
                            // Read back, so the field shows the id that was kept rather
                            // than the link it was pulled out of.
                            tgChat = Settings.telegramChat(activity)
                        }) { Text("Save Telegram") }

                        Text(
                            "Id, web.telegram.org link, or @name.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = {
                            Reporter.test(activity)
                            // The checks are quick but not instant, so the answer lands in
                            // the trail below rather than in a dialog that would have to
                            // wait for the network.
                            reportNote = Settings.reportNote(activity)
                        }) { Text("Check all of this") }
                        Text(
                            "Sends a real message and checks every permission.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Location must be allowed all the time, not only while the app is open.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = { openPermissions(activity) }) {
                            Text("Open permissions")
                        }

                        if (reportRunning || pinLive) {
                            Spacer(Modifier.height(16.dp))
                            SendingCard(
                                live = reportRunning,
                                stopping = stopping,
                                onStop = stopSending,
                            )
                        }

                        reportNote?.let { note ->
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "The last report, step by step",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(note, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
                HorizontalDivider()
                Spacer(Modifier.height(28.dp))

                SectionLabel("Control by text")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Answer texts", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Text your word and a command from any phone.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = smsCommands,
                        onCheckedChange = {
                            smsCommands = it
                            Settings.setSmsCommands(activity, it)
                            if (it) {
                                askReceiveSms.launch(
                                    arrayOf(
                                        android.Manifest.permission.RECEIVE_SMS,
                                        android.Manifest.permission.SEND_SMS,
                                    )
                                )
                            }
                        },
                    )
                }

                AnimatedVisibility(visible = smsCommands) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = secretWord,
                            onValueChange = { secretWord = it.take(24) },
                            label = { Text("Secret word") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(onClick = { Settings.setSecretWord(activity, secretWord) }) {
                            Text("Save word")
                        }
                        Text(
                            // The word is the whole gate, so an empty one has to say so here
                            // rather than just quietly answering nothing.
                            if (secretWord.isBlank()) {
                                "Set a word, or nothing will be answered."
                            } else {
                                "Text \"$secretWord locate\" from any phone to try it."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(12.dp))
                        Text("Commands", style = MaterialTheme.typography.bodyLarge)
                        listOf<Triple<String, String, (String) -> Settings.CommandWords>>(
                            Triple("Send the location", cmdWords.locate, { v: String -> cmdWords.copy(locate = v) }),
                            Triple("Lock", cmdWords.lock, { v: String -> cmdWords.copy(lock = v) }),
                            Triple("Ring to find it", cmdWords.find, { v: String -> cmdWords.copy(find = v) }),
                            Triple("Countdown: after the number", cmdWords.countdown, { v: String -> cmdWords.copy(countdown = v) }),
                            Triple("Play the clip (empty = off)", cmdWords.play, { v: String -> cmdWords.copy(play = v) }),
                        ).forEach { (label, value, update) ->
                            OutlinedTextField(
                                value = value,
                                onValueChange = { cmdWords = update(it.take(16).replace(" ", "")) },
                                label = { Text(label) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        TextButton(onClick = {
                            Settings.setCommandWords(activity, cmdWords)
                            cmdWords = Settings.commandWords(activity)
                        }) { Text("Save commands") }
                        Text(
                            "e.g. \"$secretWord ${cmdWords.locate}\", \"$secretWord 10${cmdWords.countdown}\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(16.dp))
                        Text("Answer texts from", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf(true to "Anyone", false to "Chosen contacts")
                                .forEachIndexed { i, (anyone, label) ->
                                    SegmentedButton(
                                        selected = smsFromAnyone == anyone,
                                        onClick = {
                                            smsFromAnyone = anyone
                                            Settings.setSmsFromAnyone(activity, anyone)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                                    ) { Text(label) }
                                }
                        }
                        AnimatedVisibility(visible = !smsFromAnyone) {
                            Column {
                                Spacer(Modifier.height(8.dp))
                                if (smsContacts.isEmpty()) {
                                    Text(
                                        "No one yet - every text will be ignored.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                smsContacts.forEach { contact ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            contact.name.ifBlank { contact.number } +
                                                if (contact.name.isBlank()) "" else " · ${contact.number}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(onClick = {
                                            Settings.setSmsContacts(
                                                activity,
                                                smsContacts - contact,
                                            )
                                            smsContacts = Settings.smsContacts(activity)
                                        }) { Text("Remove") }
                                    }
                                }
                                FilledTonalButton(onClick = {
                                    pickContact.launch(
                                        Intent(
                                            Intent.ACTION_PICK,
                                            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                        )
                                    )
                                }) { Text("Add a contact") }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Also read RCS messages", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Reads them from the messaging app's notifications.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Switch(
                                checked = chatCommands,
                                onCheckedChange = {
                                    chatCommands = it
                                    Settings.setChatCommands(activity, it)
                                    if (it && !chatAccess) {
                                        activity.startActivity(ChatCommandListener.accessIntent())
                                    }
                                },
                            )
                        }
                        if (chatCommands && !chatAccess) {
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(onClick = {
                                activity.startActivity(ChatCommandListener.accessIntent())
                            }) { Text("Allow notification access") }
                        }

                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = { openPermissions(activity) }) {
                            Text("Open permissions")
                        }
                        Text(
                            "Needs permission to receive and send texts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        commandNote?.let { note ->
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "The last commands",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(note, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                }

                if (page == Page.Power) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Close it during a theft", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Power and volume up, and holding power, stop opening the menu once locked. Unlocking puts them back.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = blockPower,
                        onCheckedChange = {
                            blockPower = it
                            Settings.setBlockPowerMenu(activity, it)
                            if (!it) runCatching { PowerMenu.restore(activity) }
                        },
                    )
                }
                AnimatedVisibility(visible = blockPower) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            // Said plainly. This is the one feature here that depends on
                            // something outside the app, and a protection that has
                            // quietly stopped is worse than one that is honestly off.
                            if (shizuku) {
                                "Shizuku has to be started again after every reboot."
                            } else {
                                "Shizuku is not running. Start it and grant SonderAssist."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (shizukuAsk) {
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(onClick = { PowerMenu.requestPermission() }) {
                                Text("Grant Shizuku access")
                            }
                        }
                        if (suppressed) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Unlocking, a restart, or opening this app put it back.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Holding power ten seconds still restarts the phone. Nothing can stop that.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                }

                if (page == Page.Fires) {
                Spacer(Modifier.height(28.dp))
                HorizontalDivider()
                Spacer(Modifier.height(28.dp))

                SectionLabel("F.R.I.D.A.Y mode")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("F.R.I.D.A.Y mode", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Silent until you say or text \"Friday\".",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = friday,
                        onCheckedChange = {
                            friday = it
                            Settings.setFriday(activity, it)
                            if (it && !hasMic) {
                                askMic.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    )
                }
                AnimatedVisibility(visible = friday) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "\"${fridayWords.first()}\" plays the clip. \"${fridayWords.first()} 10\" (or 30, 60) sets off the siren after that many seconds.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Listens for 60 s after a theft. Unlocking stops everything.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Two-finger tap, two up, one across clears the screen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!hasMic) {
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(onClick = {
                                askMic.launch(android.Manifest.permission.RECORD_AUDIO)
                            }) { Text("Allow the microphone") }
                        }

                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = countdownLabel,
                                onValueChange = { countdownLabel = it.take(20) },
                                label = { Text("Text above the countdown") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                Settings.setCountdownLabel(activity, countdownLabel)
                                countdownLabel = Settings.countdownLabel(activity)
                            }) { Text("Save") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = countdownEnd,
                                onValueChange = { countdownEnd = it.take(12) },
                                label = { Text("Text at zero") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                Settings.setCountdownEnd(activity, countdownEnd)
                                countdownEnd = Settings.countdownEnd(activity)
                            }) { Text("Save") }
                        }
                        Text(
                            "Leave either empty to show nothing there.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(16.dp))
                        Text("Words it answers to", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Said out loud. Texts use the commands under Find my phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        fridayWords.forEach { w ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    w,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                // The last word stays: the mode needs one to be called by.
                                if (fridayWords.size > 1) {
                                    TextButton(onClick = {
                                        Settings.setFridayWords(activity, fridayWords - w)
                                        fridayWords = Settings.fridayWords(activity)
                                    }) { Text("Remove") }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newWord,
                                onValueChange = { newWord = it.take(24) },
                                label = { Text("New word") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                enabled = newWord.isNotBlank(),
                                onClick = {
                                    Settings.setFridayWords(activity, fridayWords + newWord)
                                    fridayWords = Settings.fridayWords(activity)
                                    newWord = ""
                                },
                            ) { Text("Add") }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { Alarm.playFriday(activity) }) {
                            Text("Play the clip")
                        }
                        fridayNote?.let { note ->
                            Spacer(Modifier.height(8.dp))
                            Text("What it heard", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(note, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
                HorizontalDivider()
                Spacer(Modifier.height(28.dp))

                SectionLabel(if (friday) "Siren" else "Sound")
                if (friday) {
                    Text(
                        alarmName?.let { "Siren: $it" } ?: "Siren: this phone's alarm",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { pickAudio.launch(arrayOf("audio/*")) }) {
                            Text("Choose a sound")
                        }
                        if (alarmName != null) {
                            TextButton(onClick = {
                                Settings.setAlarmUri(activity, null)
                                alarmName = null
                            }) { Text("Use default") }
                        }
                    }
                }
                if (!friday) Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Play a sound", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Plays on the alarm channel, so it is heard even on silent.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = alarmOn,
                        onCheckedChange = {
                            alarmOn = it
                            Settings.setAlarmEnabled(activity, it)
                        },
                    )
                }

                AnimatedVisibility(visible = alarmOn && !friday) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            alarmName?.let { "Sound: $it" } ?: "Sound: this phone's alarm",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { pickAudio.launch(arrayOf("audio/*")) }) {
                                Text("Choose a sound")
                            }
                            if (alarmName != null) {
                                TextButton(onClick = {
                                    Settings.setAlarmUri(activity, null)
                                    alarmName = null
                                }) { Text("Use default") }
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        Text(
                            if (repeats == 1) "Plays once" else "Plays $repeats times",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "Then it stops on its own. The message stays on screen.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = repeats.toFloat(),
                            onValueChange = { repeats = it.toInt().coerceAtLeast(1) },
                            onValueChangeFinished = { Settings.setAlarmRepeats(activity, repeats) },
                            valueRange = 1f..Settings.MAX_ALARM_REPEATS.toFloat(),
                            modifier = Modifier.fillMaxWidth(),
                        )

                    }
                }

                // F.R.I.D.A.Y mode has no timed sound, so no wait to set.
                AnimatedVisibility(visible = alarmOn && !friday) {
                    Column {
                        Spacer(Modifier.height(20.dp))
                        Text("Wait $grace seconds before the sound", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            // The reason this exists, in the person's terms rather than
                            // the detector's.
                            "The lock is instant. The sound waits, so a false alarm can be unlocked first.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = grace.toFloat(),
                            onValueChange = { grace = it.toInt() },
                            onValueChangeFinished = { Settings.setGraceSeconds(activity, grace) },
                            valueRange = 0f..30f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                }

                if (page == Page.Running) {
                Text(
                    if (batteryExempt) {
                        "Battery optimisation is off for SonderAssist."
                    } else {
                        "Android may stop SonderAssist to save battery."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!batteryExempt) {
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { activity.startActivity(Keepalive.batteryExemptionIntent(activity)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Stop Android from sleeping it") }
                }

                if (!canOverlay) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        // On several skins this is what actually decides whether the
                        // alert screen is allowed to open from the background.
                        "Without it the alert may not appear. The lock and the sound still work.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = {
                            runCatching {
                                activity.startActivity(
                                    Intent(
                                        AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${activity.packageName}"),
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Allow the alert to show") }
                }

                val autostart = remember { Keepalive.autostartIntent(activity) }
                if (autostart != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        // No API exists to read or request this, so the app cannot say
                        // whether it is already on. Pretending to know would be worse
                        // than admitting it does not.
                        "Check the phone’s own autostart list by hand, or it will not come back after a restart.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { runCatching { activity.startActivity(autostart) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Open autostart settings") }
                }

                Spacer(Modifier.height(40.dp))
                HorizontalDivider()
                Spacer(Modifier.height(20.dp))

                // Deliberately the last thing on the screen, worded for what it is for
                // rather than what it does internally, and behind a confirmation.
                //
                // It used to sit directly under Start watching, styled like a second
                // power switch and labelled "Turn off protection". Two controls that both
                // read as off switches, one of them quietly stripping a permission that
                // can only be granted back through a system dialog. Stopping the watch is
                // an everyday action; giving up the permission is a once-ever one, and
                // they should not look alike or live next to each other.
                // A diagnostic, not a status. It answers whether the tile did anything
                // at all, which is worth having and is not worth the top of the home
                // screen.
                tileNote?.let {
                    SectionLabel("The tile")
                    Text(it, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(28.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(20.dp))
                }

                Text(
                    "Uninstalling",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Remove the lock permission first, then uninstall normally.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { confirmRemove = true }) {
                    Text("Remove permission")
                }
                Spacer(Modifier.height(32.dp))
                }

            }
        }
    }

    }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove permission?") },
            text = {
                Text(
                    "SonderAssist will stop watching and cannot lock the screen."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    Settings.setArmed(activity, false)
                    // The strap too: without the permission it cannot lock anything.
                    WatchService.shutDown(activity)
                    watching = false
                    serviceUp = false
                    DeviceAdminLocker.deactivate(activity)
                    granted = false
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Keep it") }
            },
        )
    }
}

/**
 * No card, no tinted container.
 *
 * The filled card was an addition nobody asked for and it put a pale block across the top
 * of a dark screen. Status is carried by the words and the button, on the same background
 * as everything else.
 */
/** The location is going out right now, and the one button that ends it. */
@Composable
private fun SendingCard(live: Boolean, stopping: Boolean, onStop: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Your location is being sent", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                if (live) "Live, until you stop it." else "The Telegram pin is still live.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onStop,
                enabled = !stopping,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(if (stopping) "Stopping…" else "Stop sending") }
        }
    }
}

@Composable
private fun StatusCard(watching: Boolean, onToggle: () -> Unit) {
    // One tile, tappable anywhere, carrying the state and the way to change it. The whole
    // thing is the control: an eye that is open or shut, the word for what it is doing,
    // and a coloured edge that says which way pressing it goes.
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(120.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                Icon(
                    // Only the core icon set is in this build; the extended one is
                    // megabytes of glyphs for the sake of a padlock.
                    if (watching) Icons.Filled.Lock else Icons.Filled.Clear,
                    contentDescription = null,
                    modifier = Modifier.height(40.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (watching) "watching" else "not watching",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            // The edge. Green while it is on, and the arrow points the way a press goes -
            // forward into watching, back out of it.
            Surface(
                color = if (watching) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(6.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 22.dp),
                ) {
                    Icon(
                        if (watching) {
                            Icons.Filled.KeyboardArrowLeft
                        } else {
                            Icons.Filled.KeyboardArrowRight
                        },
                        contentDescription = if (watching) "Stop watching" else "Start watching",
                        tint = if (watching) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Blocker(title: String, detail: String, action: String, onAction: () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(detail, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(action) }
    }
}

/** Hand the alert log to the share sheet as one text file. */
private fun shareAlertLog(activity: ComponentActivity) {
    runCatching {
        val file = AlertLog.export(activity)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            activity, "${activity.packageName}.files", file,
        )
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(Intent.createChooser(send, "Share alert log"))
    }
}

/** "Sat 7 Nov, 14:12:05" from an alert id, which is its wall-clock time. */
private fun alertTime(id: Long): String =
    java.text.SimpleDateFormat("EEE d MMM, HH:mm:ss", java.util.Locale.US).format(java.util.Date(id))

private fun labelText(label: String?): String = when (label) {
    AlertLog.THEFT -> "Theft"
    AlertLog.FALSE -> "False alarm"
    else -> "Unanswered"
}

/**
 * The recording around one alert: acceleration toward the top edge (what the detector
 * watches) and the total, with a line at the trigger.
 */
@Composable
private fun AlertChart(samples: List<com.verisonder.sonderassist.detect.Sample>, triggerNs: Long?) {
    if (samples.size < 2) {
        Text("Too few samples to draw.", style = MaterialTheme.typography.bodySmall)
        return
    }
    val bright = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val mark = MaterialTheme.colorScheme.error
    val axis = MaterialTheme.colorScheme.outlineVariant
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
    ) {
        val t0 = samples.first().timestampNs
        val span = (samples.last().timestampNs - t0).coerceAtLeast(1L).toFloat()
        val ys = samples.map { it.ay } + samples.map { it.accelMagnitude }
        val lo = minOf(ys.min(), 0f)
        val hi = maxOf(ys.max(), 1f)
        val range = (hi - lo).coerceAtLeast(1f)
        fun x(ns: Long) = (ns - t0) / span * size.width
        fun y(v: Float) = size.height - (v - lo) / range * size.height

        drawLine(axis, androidx.compose.ui.geometry.Offset(0f, y(0f)),
            androidx.compose.ui.geometry.Offset(size.width, y(0f)), strokeWidth = 1f)

        fun path(value: (com.verisonder.sonderassist.detect.Sample) -> Float) =
            androidx.compose.ui.graphics.Path().apply {
                samples.forEachIndexed { i, s ->
                    if (i == 0) moveTo(x(s.timestampNs), y(value(s)))
                    else lineTo(x(s.timestampNs), y(value(s)))
                }
            }
        drawPath(path { it.accelMagnitude }, dim,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
        drawPath(path { it.ay }, bright,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))

        if (triggerNs != null && triggerNs in t0..samples.last().timestampNs) {
            val tx = x(triggerNs)
            drawLine(mark, androidx.compose.ui.geometry.Offset(tx, 0f),
                androidx.compose.ui.geometry.Offset(tx, size.height), strokeWidth = 3f)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(6.dp))
}

/**
 * Whether this app may put a full-screen alert up.
 *
 * A real permission with a real answer from 34 onward, and one a fresh install of an app
 * targeting 34 or above does not get by default. Below 34 the route is always open.
 */
private fun canUseFullScreen(activity: ComponentActivity): Boolean =
    if (Build.VERSION.SDK_INT >= 34) {
        runCatching {
            activity.getSystemService(android.app.NotificationManager::class.java)
                .canUseFullScreenIntent()
        }.getOrDefault(true)
    } else {
        true
    }

/**
 * Open the vendor's own permission editor, where the window permission lives.
 *
 * It is not a standard Android permission and has no standard screen, so this goes
 * straight at the vendor activity and falls back to the ordinary app page elsewhere.
 */
private fun openPermissions(activity: ComponentActivity) {
    val vendor = Intent("miui.intent.action.APP_PERM_EDITOR")
        .setClassName(
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.PermissionsEditorActivity",
        )
        .putExtra("extra_pkgname", activity.packageName)
    runCatching { activity.startActivity(vendor) }.onFailure {
        runCatching {
            activity.startActivity(
                Intent(
                    AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + activity.packageName),
                )
            )
        }
    }
}

/** One subject each. Home is the status and the rows that lead to the rest. */
private enum class Page(val title: String) {
    Home(""),
    Detection("Detection"),
    Strap("The strap"),
    Fires("When it fires"),
    Find("Find my phone"),
    Power("Power menu"),
    Running("Keep it running"),
    Alerts("Alerts"),
    Alert("Alert"),
    ;

    /** Where back goes: the alert list sits under Detection, one alert under the list. */
    val up: Page
        get() = when (this) {
            Alerts -> Detection
            Alert -> Alerts
            else -> Home
        }
}

/** A row on the home screen: the page's name, what is on there now, and the way in. */
@Composable
private fun PageRow(title: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null)
    }
}
