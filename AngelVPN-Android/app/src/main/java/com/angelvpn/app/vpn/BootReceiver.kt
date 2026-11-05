package com.angelvpn.app.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Placeholder for "connect on boot" feature (Phase 4).
 * Currently does nothing; kept so the manifest entry is valid.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("AngelBoot", "Boot completed – auto-connect not enabled yet")
        }
    }
}
