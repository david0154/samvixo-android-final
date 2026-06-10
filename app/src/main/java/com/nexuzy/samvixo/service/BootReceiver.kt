package com.nexuzy.samvixo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives BOOT_COMPLETED to reschedule any pending WorkManager tasks
 * (e.g. scheduled messages, auto-backup reminders).
 * Declared in AndroidManifest.xml — must exist or the app crashes on boot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d("BootReceiver", "Device booted — rescheduling WorkManager tasks")
        // WorkManager auto-reschedules its own workers on boot via SystemJobService.
        // If you add scheduled-message workers in the future, re-enqueue them here.
    }
}
