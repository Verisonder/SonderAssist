package com.verisonder.sonderassist.sensor

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Build
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

    /**
     * Android only delivers this while the panel is open, so the tile is refreshed here
     * rather than kept in step continuously. It also means the tile can never show a
     * state the person is not currently looking at.
     */
    override fun onStartListening() {
        super.onStartListening()
        refresh()
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
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        tile.state = when {
            !DeviceAdminLocker.isReady(this) -> Tile.STATE_UNAVAILABLE
            WatchService.isRunning -> Tile.STATE_ACTIVE
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
