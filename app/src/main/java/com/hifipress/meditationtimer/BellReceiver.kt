package com.hifipress.meditationtimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Woken by AlarmManager at each phase boundary; hands off to the service. */
class BellReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val i = Intent(context, TimerService::class.java)
            .setAction(TimerService.ACTION_RING)
        ContextCompat.startForegroundService(context, i)
    }
}
