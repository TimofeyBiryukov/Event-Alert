package com.example.eventalert.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Receives [Intent.ACTION_BOOT_COMPLETED] and enqueues a one-off [AlertRescheduleWorker]
 * to reschedule all event alerts after device reboot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val workRequest = OneTimeWorkRequestBuilder<AlertRescheduleWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            WORK_NAME_RESCHEDULE,
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
    }

    companion object {
        private const val WORK_NAME_RESCHEDULE = "event_alert_reschedule"
    }
}
