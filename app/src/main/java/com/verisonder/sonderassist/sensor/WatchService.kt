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
import com.verisonder.sonderassist.detect.Sample
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
                Intent.ACTION_USER_PRESENT -> startListening()
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

        // A device with no gyroscope still runs, on jerk alone. Worse, and the setting
        // says so rather than the app quietly pretending nothing changed.
        if (gyroscope == null) {
            detector = SnatchDetector(SnatchDetector.Tuning(requireRotation = false))
        }

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
    }

    override fun onDestroy() {
        stopListening()
        runCatching { unregisterReceiver(screenEvents) }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    // ------------------------------------------------------------------- listening

    private fun startListening() {
        if (listening) return
        val accel = accelerometer ?: return
        detector.reset()
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
                if (verdict is SnatchDetector.Verdict.Snatch) onSnatch()
            }
        }
    }

    private fun onSnatch() {
        // Stop listening first. Locking the screen fires ACTION_SCREEN_OFF, and a
        // detector still being fed during the lock would carry the tail of this event
        // into the next session and could fire again the moment the phone is unlocked.
        stopListening()
        DeviceAdminLocker.lockNow(this)
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

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WatchService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchService::class.java))
        }
    }
}
