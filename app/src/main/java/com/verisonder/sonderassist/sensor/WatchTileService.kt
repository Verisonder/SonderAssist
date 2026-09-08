package com.verisonder.sonderassist.sensor

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.verisonder.sonderassist.Settings
import com.verisonder.sonderassist.security.DeviceAdminLocker
import com.verisonder.sonderassist.ui.MainActivity

/**
 * The tile in the quick settings panel.
 *
 * Tapping it starts or stops the watch. Long-pressing opens the app, which costs no code
 * here — Android routes a long press to whichever activity declares the
 * QS_TILE_PREFERENCES intent filter, and MainActivity does.
 *
 * A tile is the right place for this because arming and disarming is the one thing done
 * often, and it is done in exactly the moments when opening an app is inconvenient:
 * getting on a train, putting the phone away, handing it to someone.
 */
class WatchTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())

    /** A single re-read after a tap, in case the service never arrives. */
    private val verify = Runnable { refresh() }

    /**
     * Android only delivers this while the panel is open, so the tile is refreshed here
     * rather than kept in step continuously. It also means the tile can never show a
     * state the person is not currently looking at.
     */
    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onStopListening() {
        super.onStopListening()
        // Nobody is looking at the tile any more, and the panel closing is not a reason
        // to keep a callback alive against a service that may be gone.
        handler.removeCallbacks(verify)
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refresh()
    }

    override fun onClick() {
        super.onClick()

        // Nothing to toggle without the permission to lock, so the tap becomes an
        // invitation rather than doing nothing and looking broken.
        if (!DeviceAdminLocker.isReady(this)) {
            openApp()
            return
        }

        val running = WatchService.isRunning
        if (running) WatchService.stop(this) else WatchService.start(this)
        // The recorded intent, which is what the boot receiver reads. The service being
        // killed later is not the person changing their mind.
        Settings.setArmed(this, !running)

        // Neither the start nor the stop has happened yet. startForegroundService and
        // stopService both queue the work for the main looper, so WatchService.isRunning
        // still holds the old value on this line — reading it here painted the state the
        // tile was just tapped out of, and it stayed wrong until the service came up and
        // asked for a refresh. Paint what was asked for; the service corrects it from
        // onCreate or onDestroy if reality disagrees.
        refresh(pending = !running)

        // And if the service never arrives — refused, or killed on startup — nothing
        // else will put the tile right while the panel stays open. One check, then done.
        handler.removeCallbacks(verify)
        handler.postDelayed(verify, VERIFY_DELAY_MS)
    }

    /**
     * @param pending the state that has been asked for but has not taken effect yet.
     *   Null means read the service, which is the truth everywhere except the moment
     *   immediately after a tap.
     */
    private fun refresh(pending: Boolean? = null) {
        val tile = qsTile ?: return
        val watching = pending ?: WatchService.isRunning
        tile.state = when {
            !DeviceAdminLocker.isReady(this) -> Tile.STATE_UNAVAILABLE
            watching -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        // Subtitles arrived in 29 and this app runs from 28. On 28 the tile carries its
        // label and its on/off state, which is the part that matters.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (tile.state) {
                Tile.STATE_UNAVAILABLE -> "Needs permission"
                Tile.STATE_ACTIVE -> "Watching"
                else -> "Off"
            }
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            // The Intent overload throws UnsupportedOperationException from 34 onward,
            // and this app targets 35 — so the PendingIntent form is not a nicety.
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /**
         * Long enough for a foreground service to reach onCreate on a slow device, short
         * enough that the panel is probably still open.
         */
        private const val VERIFY_DELAY_MS = 1_200L

        /**
         * Ask Android to call onStartListening, so a change made inside the app is
         * reflected on the tile rather than the two disagreeing until the panel is
         * next opened.
         */
        fun refreshFrom(context: android.content.Context) {
            runCatching {
                requestListeningState(
                    context,
                    ComponentName(context, WatchTileService::class.java),
                )
            }
        }
    }
}
