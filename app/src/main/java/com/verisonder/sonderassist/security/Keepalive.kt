package com.verisonder.sonderassist.security

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Keeping the watch alive on phones that would rather it were not.
 *
 * A foreground service with an ongoing notification is supposed to be enough. On several
 * OEM skins it is not: the app gets killed anyway, silently, and an anti-theft feature
 * that has quietly stopped is worse than one that is honestly off, because the owner is
 * relying on it either way.
 *
 * Two separate things have to be true, and only one of them has an API:
 *
 * 1. **Battery optimisation exemption.** Standard Android, askable, and checkable.
 * 2. **Autostart.** A vendor invention with no public API at all — it cannot be read, and
 *    it cannot be requested. The best anything can do is open the screen and ask the
 *    person to look. Without it the app does not come back after a reboot on MIUI no
 *    matter what the boot receiver says.
 */
object Keepalive {

    fun isBatteryExempt(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    fun batteryExemptionIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))

    /**
     * The vendor's autostart screen, if this phone has one.
     *
     * Every one of these is an undocumented internal activity that can be renamed or
     * removed in any update, so each is checked against the package manager before being
     * offered rather than thrown at the system and hoped for.
     */
    fun autostartIntent(context: Context): Intent? {
        val candidates = listOf(
            // Xiaomi / MIUI — the one that matters here
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            // Huawei
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
            // Oppo / Realme
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            // Vivo
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            // Letv
            "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
        )
        for ((pkg, cls) in candidates) {
            val intent = Intent().setComponent(ComponentName(pkg, cls))
            val resolved = context.packageManager.resolveActivity(intent, 0)
            if (resolved != null) return intent
        }
        return null
    }

    /** The app's own settings page, which every phone has. The honest fallback. */
    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
}
