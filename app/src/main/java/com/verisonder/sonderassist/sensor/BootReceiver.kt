package com.verisonder.sonderassist.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.verisonder.sonderassist.Settings
import com.verisonder.sonderassist.security.DeviceAdminLocker

/**
 * Starts the watch again after a reboot.
 *
 * RECEIVE_BOOT_COMPLETED was declared in the manifest from the first commit and nothing
 * ever listened for it, so the app was simply dead after every restart until someone
 * opened it and pressed the button. A declared permission with no receiver behind it is
 * worse than no permission, because it reads as handled.
 *
 * It checks the recorded intent rather than any live state: the person said they wanted
 * this on, and a reboot is not them changing their mind.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return
        if (!Settings.armed(context)) return
        // No point starting a watch that cannot lock anything.
        if (!DeviceAdminLocker.isReady(context)) return
        runCatching { WatchService.start(context) }
    }
}
