package com.verisonder.sonderassist.security

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.KeyguardManager

/**
 * Turning on the lock screen, behind one interface.
 *
 * There is more than one way to do this and none of them is good everywhere, so the
 * detector talks to this and never to a specific mechanism.
 *
 * **Not an accessibility service.** That was the first implementation and it was wrong:
 * accessibility services are unreliable on several OEM skins — Xiaomi in particular kills
 * or silently disables them — and an anti-theft feature that stops working after a reboot
 * without saying so is worse than no feature, because the owner is relying on it.
 *
 * **Not Shizuku either, at least not as the primary.** Shizuku can inject a sleep key with
 * shell privileges and works well, but on an unrooted phone it has to be started again
 * after every reboot. For a vault app that is an annoyance; for something that only earns
 * its keep by always being on, the one time it matters is the one time you rebooted and
 * forgot. It belongs here as an extra implementation for rooted devices, not as the floor.
 */
interface Locker {
    /** Whether this mechanism is granted and usable right now. */
    fun isReady(context: Context): Boolean

    /** Lock. Returns false if it could not. */
    fun lockNow(context: Context): Boolean
}

/**
 * Device Admin with the force-lock policy.
 *
 * `lockNow` is the API the platform itself describes as securing a device in response to
 * an urgent situation such as a lost or stolen device, after which the device has to be
 * unlocked with strong authentication. Google's device admin deprecation removed a pile of
 * legacy policies — password rules, camera disabling, keyguard restrictions — but left
 * force-lock alone, because enterprise management depends on it. That dependency is also
 * why an OEM cannot quietly break it the way an accessibility service can be broken.
 *
 * The cost, and it is a real one: an active device admin cannot be uninstalled until it is
 * deactivated. The app has to offer that plainly rather than making the user go looking
 * through system settings for it.
 */
object DeviceAdminLocker : Locker {

    override fun isReady(context: Context): Boolean =
        policy(context).isAdminActive(component(context))

    override fun lockNow(context: Context): Boolean {
        val manager = policy(context)
        if (!manager.isAdminActive(component(context))) return false
        return runCatching { manager.lockNow() }.isSuccess
    }

    /**
     * Whether locking would actually lock anything.
     *
     * With no PIN, pattern or password set, lockNow puts the device to sleep but does not
     * secure it — the screen goes off and comes straight back on with everything visible.
     * An app that reported itself as protecting the phone in that state would be lying,
     * so the screen checks this and says so instead.
     */
    fun hasLockScreen(context: Context): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure

    /** The system screen that asks the user to activate the admin. */
    fun activationIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(com.verisonder.sonderassist.R.string.admin_description),
            )
        }

    /**
     * Give the admin up.
     *
     * Offered in the app because an active device admin blocks uninstall, and an anti-theft
     * app that is awkward to remove is one people warn each other about.
     */
    fun deactivate(context: Context) {
        runCatching { policy(context).removeActiveAdmin(component(context)) }
    }

    private fun policy(context: Context) =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun component(context: Context) =
        ComponentName(context.applicationContext, AdminReceiver::class.java)
}

/**
 * Exists so the system has something to bind the policy to. It holds no logic on purpose:
 * every callback here fires on a system event, and doing work in them would be doing work
 * the user did not ask for.
 */
class AdminReceiver : DeviceAdminReceiver()
