package com.doxaiz.netoptimizer.core.cellular

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Network settings must never be silently changed after boot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = Unit
}
