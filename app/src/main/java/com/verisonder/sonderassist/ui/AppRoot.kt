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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.verisonder.sonderassist.Settings
import com.verisonder.sonderassist.security.DeviceAdminLocker
import com.verisonder.sonderassist.security.Keepalive
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
                    "The pull has to start suddenly — a jerk of %,.0f, or %,.0f if the "
                        .format(tuned.axialJerk, tuned.axialJerkWithRotation) +
                        "phone twists as it goes.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    // Jerk alone is not enough: it is a rate of change, and flicking the
                    // edge of a still phone produces a large one while moving nothing.
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

                AnimatedVisibility(visible = alarmOn) {
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
