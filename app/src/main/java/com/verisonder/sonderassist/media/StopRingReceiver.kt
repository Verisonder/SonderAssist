package com.verisonder.sonderassist.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The Stop button on the "ringing to find it" notification. Not exported. */
class StopRingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Alarm.stopFindRing()
    }
}
