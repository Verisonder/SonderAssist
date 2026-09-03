package com.verisonder.sonderassist.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.verisonder.sonderassist.security.ScreenLockService
import com.verisonder.sonderassist.sensor.WatchService

/**
 * One screen, because there is one thing to say: whether it is armed, and why not.
 *
 * The permission state can only be changed outside the app, so it is re-read every time
 * the screen comes back to the foreground. Reading it once at composition would leave the
 * app telling the user to grant something they had granted a moment earlier, which is the
 * kind of thing that gets an app deleted.
 */
@Composable
fun AppRoot(activity: ComponentActivity) {
    var granted by remember { mutableStateOf(ScreenLockService.isGranted(activity)) }
    var armed by remember { mutableStateOf(false) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = ScreenLockService.isGranted(activity)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text("SonderAssist", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Locks the screen when the phone is taken from your hand.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            if (!granted) {
                Text("Not armed", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "SonderAssist needs permission to turn on the lock screen. Android " +
                        "grants that through accessibility settings, and the screen it " +
                        "shows warns that the app can observe everything you do. It " +
                        "cannot: it reads no screen content and performs one action. " +
                        "Android shows the same warning for every app of this kind.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open accessibility settings") }
            } else {
                Text(if (armed) "Watching" else "Ready", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (armed) WatchService.stop(activity) else WatchService.start(activity)
                        armed = !armed
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (armed) "Stop watching" else "Start watching") }
            }

            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            Text("Recording", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "The detector's thresholds are guesses from the physics until there are " +
                    "real recordings to check them against. Record a grab, a put-down, a " +
                    "pocket and a hand-over, then replay them in the tests.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { /* TODO: the capture screen — the next thing to build */ },
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Record a trace") }
        }
    }
}
