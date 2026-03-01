package com.example.eventalert.alert

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * One-off worker that reschedules all event alerts (e.g. after device boot).
 * Runs [AlertScheduler.schedule] so alarms are re-registered with AlarmManager.
 */
class AlertRescheduleWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            AlertScheduler.schedule(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
