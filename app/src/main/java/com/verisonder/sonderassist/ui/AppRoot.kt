package com.verisonder.sonderassist.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.verisonder.sonderassist.report.Reporter
import com.verisonder.sonderassist.sensor.WatchService

/**
 * One screen.
 *
 * The status is the hero and everything else is quiet underneath it, because the only
 * question anyone opens this app to answer is whether it is on. Settings are revealed
 * once it can actually run — offering a sensitivity slider to someone who has not granted
 * permission yet is asking them to tune something that is switched off.
 */
@Composable
fun AppRoot(activity: ComponentActivity) {
    var granted by remember { mutableStateOf(DeviceAdminLocker.isReady(activity)) }
    // Refreshed on resume, so returning from a failed tile tap shows the reason.
    var crash by remember { mutableStateOf(CrashLog.read(activity)) }
    var jarvis by remember { mutableStateOf(Settings.jarvis(activity)) }
    var blockPower by remember { mutableStateOf(Settings.blockPowerMenu(activity)) }
    var alertNote by remember { mutableStateOf(Settings.alertNote(activity)) }
    var fullScreen by remember { mutableStateOf(canUseFullScreen(activity)) }
    var guardAlert by remember { mutableStateOf(Settings.guardAlert(activity)) }
    var report by remember { mutableStateOf(Settings.reportEnabled(activity)) }
    var smsNumber by remember { mutableStateOf(Settings.smsNumber(activity)) }
    var tgToken by remember { mutableStateOf(Settings.telegramToken(activity)) }
    var tgChat by remember { mutableStateOf(Settings.telegramChat(activity)) }
    var reportDelay by remember { mutableIntStateOf(Settings.reportDelaySeconds(activity)) }
    var reportCount by remember { mutableIntStateOf(Settings.reportCount(activity)) }
    var statusEvery by remember { mutableIntStateOf(Settings.statusEveryMinutes(activity)) }
    var reportNote by remember { mutableStateOf(Settings.reportNote(activity)) }
    var reportRunning by remember { mutableStateOf(Settings.reportRunning(activity)) }
    val askReportPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    var shizuku by remember { mutableStateOf(PowerMenu.available()) }
    var shizukuAsk by remember { mutableStateOf(PowerMenu.needsPermission()) }
    var suppressed by remember { mutableStateOf(Settings.powerMenuSuppressed(activity)) }
    var tileNote by remember { mutableStateOf(Settings.tileNote(activity)) }
    var hasLock by remember { mutableStateOf(DeviceAdminLocker.hasLockScreen(activity)) }
    // Read from the service, not from a local flag. The old screen kept its own boolean
    // that reset on every recomposition, so it could claim to be off while running.
    var watching by remember { mutableStateOf(WatchService.isRunning) }

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
                watching = WatchService.isRunning
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
                reportNote = Settings.reportNote(activity)
                reportRunning = Settings.reportRunning(activity)
                fullScreen = canUseFullScreen(activity)
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
    Surface(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Text("SonderAssist", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Locks the screen when the phone is taken from your hand.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))

        // Written since the first version and read by nothing, which is the same as not
        // recording it at all. It only appears when there is something to say.
        crash?.let { text ->
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
                StatusCard(
                    watching = watching,
                    onToggle = {
                        if (watching) WatchService.stop(activity) else WatchService.start(activity)
                        watching = !watching
                        // The recorded intent, which is what the boot receiver reads. The
                        // service being killed is not the person changing their mind.
                        Settings.setArmed(activity, watching)
                    },
                )

                if (watching) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Right now: $readout",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // Shown whether or not the watch is running: the question this answers
                // is whether the tile did anything at all, and a tile that did nothing
                // leaves the watch exactly as it was.
                tileNote?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tile: $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

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
                    onValueChangeFinished = { Settings.setSensitivity(activity, sensitivity) },
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
                    "The pull has to start suddenly — an acceleration of %,.0f, or %,.0f "
                        .format(tuned.axialJerk, tuned.axialJerkWithRotation) +
                        "if the phone twists as it goes.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    // That figure alone is not enough: it is a rate of change, and
                    // flicking the edge of a still phone produces a large one while
                    // moving nothing.
                    "And the phone has to actually move with it — at least %.1f m/s² "
                        .format(tuned.minAxialAccel) +
                        "toward the top edge, not just a knock.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Takes effect the next time you unlock the phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(28.dp))
                HorizontalDivider()
                Spacer(Modifier.height(28.dp))

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
                    "Anyone holding the phone can read this without unlocking it. Do not " +
                        "put anything private here.",
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
                    "This app needs two permissions under Other permissions, and it " +
                        "needs both: \u201cOpen new windows while running in the " +
                        "background\u201d and \u201cShow on Lock screen\u201d. Without " +
                        "either one the alert screen cannot open over the lock screen - " +
                        "the alarm still sounds, so it half looks like it is working.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Restarting the phone can turn them off again. Worth checking " +
                        "both after a reboot.",
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
                        "Full-screen alerts are not allowed, so the screen cannot open " +
                            "itself over the lock screen. The alarm still sounds."
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
                            "The assistant can open over the lock screen on a long press " +
                                "of the power button, and it covers the alert. This puts " +
                                "the screen out again and brings the alert back. Up to " +
                                "five times, then it stops.",
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
                        "If the last line is not the alert screen opening, the " +
                            "screen never appeared, and one of the two permissions " +
                            "above is almost always why.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = { openPermissions(activity) }) {
                        Text("Open other permissions")
                    }
                }

                Spacer(Modifier.height(28.dp))
                HorizontalDivider()
                Spacer(Modifier.height(28.dp))

                SectionLabel("Send the location")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("After a theft", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "If the phone is not unlocked in time, its position goes out " +
                                "by text message and as a live pin in a Telegram group.",
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
                            "Holding the power button for about ten seconds switches the " +
                                "phone off, and nothing in software can stop that. " +
                                "Whatever is sent has to be sent before then, so this " +
                                "wait is deliberately short.",
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
                            "The pin carries no words, so the battery goes out as a " +
                                "message of its own next to it.",
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
                            "The Telegram pin keeps moving until you stop it. Only the " +
                                "texts are counted, because each one is a real message.",
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
                            "Paste the id, the web.telegram.org link, or a markdown link " +
                                "- the number is taken out of it. A public channel's @name " +
                                "works too.",
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
                            "Sends a real message to the group and reads back every " +
                                "permission. The result appears below - reopen this screen " +
                                "if the Telegram lines have not arrived yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Location must be allowed all the time, not only while the " +
                                "app is open - a theft is never while the app is open. " +
                                "Grant it in the app's permissions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = { openPermissions(activity) }) {
                            Text("Open permissions")
                        }

                        if (reportRunning) {
                            Spacer(Modifier.height(16.dp))
                            Text("Sending now.", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                // Said plainly: on a phone that has been taken this button
                                // cannot be reached, and unlocking is what stops it.
                                "Unlocking the phone stops this on its own. This button " +
                                    "is for when it is back in your hands.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                Reporter.stop(activity)
                                reportRunning = false
                                reportNote = Settings.reportNote(activity)
                            }) { Text("Stop sending") }
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

                SectionLabel("The power menu")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Close it during a theft", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Power and volume up, and holding power, stop opening the " +
                                "menu once the phone has locked itself. Unlocking puts " +
                                "them back.",
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
                                "Shizuku is running. Note that it has to be started " +
                                    "again after every reboot, and this does nothing " +
                                    "while it is not."
                            } else {
                                "Shizuku is not running, so this will not happen. Start " +
                                    "it and grant SonderAssist, then come back."
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
                                "The power menu is closed right now. Unlocking, " +
                                    "restarting the phone, or opening this app all put " +
                                    "it back.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Holding power for about ten seconds still restarts the " +
                                "phone. That is below Android and nothing can stop it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))
                HorizontalDivider()
                Spacer(Modifier.height(28.dp))

                SectionLabel("J.A.R.V.I.S mode")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Replace the alert screen",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "Your message on a dark screen, and its own sound, " +
                                "played once. Two fingers tapped once, then two " +
                                "fingers up, then one finger left to right, clears " +
                                "the screen and stops the sound.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = jarvis,
                        onCheckedChange = {
                            jarvis = it
                            Settings.setJarvis(activity, it)
                            // The mode promises a sound, and the sound is gated on the
                            // switch below. Turning the mode on without this left it
                            // describing something that could not happen.
                            if (it && !alarmOn) {
                                alarmOn = true
                                Settings.setAlarmEnabled(activity, true)
                            }
                        },
                    )
                }
                AnimatedVisibility(visible = jarvis) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            // Said plainly rather than left to be discovered: the gesture
                            // is not a PIN and the phone cannot check who made it.
                            "Anyone who knows the gesture can clear it without unlocking " +
                                "the phone. Unlocking still works.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))
                HorizontalDivider()
                Spacer(Modifier.height(28.dp))

                SectionLabel("Sound")
                Row(
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

                AnimatedVisibility(visible = alarmOn && jarvis) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "J.A.R.V.I.S mode is using its own sound, played once. Turn " +
                                "the mode off to choose a sound or a repeat count.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                AnimatedVisibility(visible = alarmOn && !jarvis) {
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

                // Outside the mode check on purpose. J.A.R.V.I.S mode keeps the grace
                // period, so hiding the control would leave it applying and unreachable.
                AnimatedVisibility(visible = alarmOn) {
                    Column {
                        Spacer(Modifier.height(20.dp))
                        Text("Wait $grace seconds before the sound", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            // The reason this exists, in the person's terms rather than
                            // the detector's.
                            "The screen locks straight away. The sound waits, so you can " +
                                "unlock a false alarm before it makes a noise.",
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

                Spacer(Modifier.height(28.dp))
                HorizontalDivider()
                Spacer(Modifier.height(28.dp))

                SectionLabel("Keeping it running")
                Text(
                    if (batteryExempt) {
                        "Battery optimisation is off for SonderAssist."
                    } else {
                        "Android may stop SonderAssist to save battery. It only runs " +
                            "while the screen is on, so the cost is small."
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
                        "The alert screen may not appear unless SonderAssist can draw " +
                            "over other apps. The lock and the sound work either way.",
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
                        "This phone also has its own autostart list. SonderAssist cannot " +
                            "see whether it is on, so it is worth checking by hand — " +
                            "without it the app will not come back after a restart.",
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
                Text(
                    "Uninstalling",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Android will not let you uninstall SonderAssist while it can lock " +
                        "the screen. Remove that permission first, then uninstall normally.",
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

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove permission?") },
            text = {
                Text(
                    "SonderAssist will stop watching and will not be able to lock the " +
                        "screen. You can give the permission back at any time."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    WatchService.stop(activity)
                    watching = false
                    Settings.setArmed(activity, false)
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
@Composable
private fun StatusCard(watching: Boolean, onToggle: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            if (watching) "Watching" else "Not watching",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (watching) {
                "The screen locks if the phone is pulled out of your hand."
            } else {
                "Nothing is being watched for."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
            Text(if (watching) "Stop watching" else "Start watching")
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
