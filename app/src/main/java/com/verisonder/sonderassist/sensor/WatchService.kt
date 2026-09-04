package com.verisonder.sonderassist.sensor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.verisonder.sonderassist.R
import com.verisonder.sonderassist.Settings
import com.verisonder.sonderassist.detect.Sample
import com.verisonder.sonderassist.media.Alarm
import com.verisonder.sonderassist.detect.SnatchDetector
import com.verisonder.sonderassist.security.DeviceAdminLocker

/**
 * Watches the motion sensors while the phone is unlocked and in use.
 *
 * **It only listens between unlock and screen-off, and that is not an optimisation.** The
 * feature is meaningless when the screen is already off: there is nothing to protect,
 * because the phone is already locked. Narrowing to the window where a grab actually
 * costs something is what keeps this off the battery blame list, and it falls out of the
 * problem rather than being a compromise.
 */
class WatchService : Service(), SensorEventListener {

    private lateinit var sensors: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    // Rebuilt from the sensitivity setting each time the watch starts, so a change on
    // the slider takes effect the next time the phone is unlocked rather than needing
    // the service restarted.
    private var detector = SnatchDetector()
    private var listening = false

    // The gyroscope arrives on its own schedule, so the latest reading is held and
    // attached to the next accelerometer sample rather than the two being interpolated.
    // At the rates used here they are never more than a few milliseconds apart.
    private var gx = 0f
    private var gy = 0f
    private var gz = 0f

    private val screenEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT -> {
                    // Unlocking is the one thing that proves the owner is holding it, so
                    // it is what silences the alarm — whether or not the alert screen
                    // ever managed to open.
                    Alarm.stop()
                    getSystemService(NotificationManager::class.java)
                        .cancel(ALERT_NOTIFICATION_ID)
                    startListening()
                }

                Intent.ACTION_SCREEN_OFF -> stopListening()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // A device with no gyroscope needs no special tuning. Rotation only ever lowers
        // the bar, so a device that reports none simply holds every grab to the higher
        // axial threshold — which is the correct behaviour and falls out on its own.
        // Detecting the absence and passing a different Tuning was left over from the
        // version where rotation was a hard requirement, and did not survive it.

        // RECEIVER_NOT_EXPORTED is not optional. An app targeting 34 or above that
        // registers a receiver without a flag throws SecurityException at registration,
        // so this would be a crash on the first launch rather than a subtle problem.
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            screenEvents,
            IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        startForeground(
            NOTIFICATION_ID,
            notification(),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        startListening()
        isRunning = true
        WatchTileService.refreshFrom(this)
    }

    override fun onDestroy() {
        isRunning = false
        WatchTileService.refreshFrom(this)
        Alarm.stop()
        stopListening()
        runCatching { unregisterReceiver(screenEvents) }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    // ------------------------------------------------------------------- listening

    private fun startListening() {
        if (listening) return
        val accel = accelerometer ?: return
        detector = SnatchDetector(
            SnatchDetector.Tuning.forSensitivity(Settings.sensitivity(this))
        )
        // GAME rather than NORMAL. A grab transient lasts tens of milliseconds and NORMAL
        // (about 5 Hz) would step straight over it. FASTEST is not used because the extra
        // rate buys nothing at this scale and costs battery for the whole session.
        sensors.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        gyroscope?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        listening = true
    }

    private fun stopListening() {
        if (!listening) return
        sensors.unregisterListener(this)
        listening = false
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                gx = event.values[0]
                gy = event.values[1]
                gz = event.values[2]
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val verdict = detector.accept(
                    Sample(
                        timestampNs = event.timestamp,
                        ax = event.values[0],
                        ay = event.values[1],
                        az = event.values[2],
                        gx = gx, gy = gy, gz = gz,
                    )
                )
                lastVerdict = when (verdict) {
                    is SnatchDetector.Verdict.Idle -> "not in a hand"
                    is SnatchDetector.Verdict.Watching -> "watching"
                    is SnatchDetector.Verdict.Candidate ->
                        "possible grab (jerk %.0f)".format(verdict.axialJerk)
                    is SnatchDetector.Verdict.Rejected -> "rejected: ${verdict.reason}"
                    is SnatchDetector.Verdict.Snatch -> "locked"
                }
                if (verdict is SnatchDetector.Verdict.Snatch) {
                    lastFiredAt = System.currentTimeMillis()
                    onSnatch()
                }
            }
        }
    }

    private fun onSnatch() {
        // Stop listening first. Locking the screen fires ACTION_SCREEN_OFF, and a
        // detector still being fed during the lock would carry the tail of this event
        // into the next session and could fire again the moment the phone is unlocked.
        stopListening()
        DeviceAdminLocker.lockNow(this)

        // The sound does not depend on the screen appearing. It used to, and on a real
        // theft — where the app is not in the foreground — the screen is exactly what
        // Android refuses to open.
        Alarm.scheduleAfterGrace(this)

        showAlert()
    }

    /**
     * Get the alert screen up from the background.
     *
     * A plain startActivity is silently dropped: since Android 10 an app in the
     * background may not launch an activity, and a foreground service does not change
     * that. It appeared to work only while SonderAssist happened to be in the foreground,
     * which is precisely the case during testing and never the case during a theft.
     *
     * A full-screen intent is the sanctioned route — the one alarm clocks and incoming
     * calls use. The notification is posted and the system decides; if it declines, the
     * notification is still there on the lock screen and the alarm is still sounding.
     * The direct start is kept as well, because when it is allowed it is instant.
     */
    private fun showAlert() {
        val intent = Intent(this, com.verisonder.sonderassist.ui.AlertActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pending = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                getString(R.string.alert_channel),
                // A full-screen intent is ignored on anything below HIGH.
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
        manager.notify(
            ALERT_NOTIFICATION_ID,
            NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setContentTitle(getString(R.string.alert_notification))
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pending, true)
                .setAutoCancel(true)
                .build(),
        )

        runCatching { startActivity(intent) }
    }

    // ---------------------------------------------------------------- notification

    private fun notification(): android.app.Notification {
        // The channel has to exist before the notification is posted, so it is created
        // here in order rather than as a side effect hanging off the builder.
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.watch_channel),
                NotificationManager.IMPORTANCE_MIN,
            )
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.watch_notification))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "watch"
        private const val NOTIFICATION_ID = 1
        private const val ALERT_CHANNEL_ID = "alert"
        private const val ALERT_NOTIFICATION_ID = 2

        /**
         * Whether the service is actually alive, as opposed to whether the person asked
         * for it. The screen used to keep a local flag that reset on every recomposition,
         * so the button could say "Start watching" while it was running and the reverse
         * after the system killed it — which is indistinguishable from the feature
         * failing at random.
         */
        @Volatile
        var isRunning: Boolean = false
            private set

        /**
         * What the detector last thought, for the readout on the main screen.
         *
         * Guessing at why a grab did not fire costs a build and a round trip each time.
         * The phone already knows; it just had no way to say so.
         */
        @Volatile
        var lastVerdict: String = "—"
            private set

        @Volatile
        var lastFiredAt: Long = 0L
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WatchService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchService::class.java))
        }
    }
}
