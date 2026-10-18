package com.verisonder.sonderassist.learn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The two buttons on the "Was it a theft?" notification.
 *
 * Not exported: only this app's own PendingIntent, which names the class, reaches it.
 */
class LabelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlertLog.ACTION_LABEL) return
        val id = intent.getLongExtra(AlertLog.EXTRA_ID, 0L)
        val label = intent.getStringExtra(AlertLog.EXTRA_LABEL) ?: return
        if (id == 0L || (label != AlertLog.THEFT && label != AlertLog.FALSE)) return
        AlertLog.label(context, id, label)
    }
}
