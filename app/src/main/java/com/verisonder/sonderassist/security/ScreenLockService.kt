package com.verisonder.sonderassist.security

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent

/**
 * Turns on the lock screen. That is the entire job.
 *
 * `GLOBAL_ACTION_LOCK_SCREEN` arrived in API 28, which is why the app's floor is 28 —
 * below it the only route is Device Admin, whose grant screen is milder but which can
 * also block uninstall, and an anti-theft app that is hard to remove is a support problem
 * waiting to happen.
 *
 * **This service reads nothing.** `onAccessibilityEvent` is empty and the config asks for
 * no window content. That is worth stating in the code as well as in the description
 * string, because the grant screen Android shows will claim the app can observe
 * everything you do — the platform cannot distinguish a service that reads everything
 * from one that reads nothing, so it warns as though every service were the former.
 */
class ScreenLockService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /** Deliberately empty. Nothing here looks at what is on screen. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: ScreenLockService? = null

        /**
         * Lock the screen now.
         *
         * @return false when the service is not connected, which is the ordinary state
         *   before the user has granted it. The caller shows that as "not armed" rather
         *   than treating it as a failure.
         */
        fun lockNow(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }

        /**
         * Whether the user has granted the service, read from Settings rather than from
         * [instance].
         *
         * The instance is only set once Android has bound the service, and a screen that
         * asks a moment too early would tell the user they had not granted something they
         * just had.
         */
        fun isGranted(context: Context): Boolean {
            val expected = "${context.packageName}/${ScreenLockService::class.java.name}"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            for (entry in splitter) {
                if (entry.equals(expected, ignoreCase = true)) return true
            }
            return false
        }
    }
}
