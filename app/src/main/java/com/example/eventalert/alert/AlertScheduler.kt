package com.example.eventalert.alert

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.eventalert.data.CalendarRepository
import com.example.eventalert.data.getScheduledAlertEventKeys
import com.example.eventalert.data.getSelectedCalendarIds
import com.example.eventalert.data.setScheduledAlertEventKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Schedules exact alarms for upcoming calendar event alerts. Fetches events in the same
 * window as the event list, computes alert time per event, cancels previous alarms, and
 * sets new ones via [AlarmManager.setAlarmClock] so they fire when the app is closed.
 */
object AlertScheduler {

    suspend fun schedule(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return@withContext
        }
        val calendarIds = getSelectedCalendarIds(app)
        if (calendarIds.isEmpty()) return@withContext

        val repository = CalendarRepository(app.contentResolver)
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        val maxEnd = now + MAX_IMPORT_WINDOW_MS

        val allEvents = mutableListOf<com.example.eventalert.data.CalendarEvent>()
        var from = now
        while (from < maxEnd) {
            coroutineContext.ensureActive()
            val to = minOf(from + EVENT_WINDOW_MS, maxEnd)
            val batch = repository.getEvents(
                calendarIds = calendarIds.toList(),
                fromMillis = from,
                toMillis = to,
            )
            allEvents.addAll(batch)
            if (batch.isEmpty() || to >= maxEnd) break
            from = to
        }

        val eventsByKey = allEvents.associateBy { eventKey(it) }

        val previousKeys = getScheduledAlertEventKeys(app)
        for (key in previousKeys) {
            val cancelIntent = Intent(app, AlarmReceiver::class.java).apply {
                putExtra(AlarmReceiver.EXTRA_EVENT_KEY, key)
            }
            val pending = PendingIntent.getBroadcast(
                app,
                key.hashCode(),
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.cancel(pending)
        }

        val toSchedule = eventsByKey.values
            .mapNotNull { event ->
                val key = eventKey(event)
                val triggerAt = alertTimeMillis(event)
                if (triggerAt <= now) null else Triple(key, event, triggerAt)
            }

        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        val newKeys = mutableSetOf<String>()
        for ((key, event, triggerAt) in toSchedule) {
            newKeys.add(key)
            val intent = Intent(app, AlarmReceiver::class.java).apply {
                putExtra(AlarmReceiver.EXTRA_EVENT_KEY, key)
                putExtra(AlarmReceiver.EXTRA_TITLE, event.title)
                putExtra(AlarmReceiver.EXTRA_START_TIME_MILLIS, event.startTimeMillis)
                event.endTimeMillis?.let { putExtra(AlarmReceiver.EXTRA_END_TIME_MILLIS, it) }
                putExtra(AlarmReceiver.EXTRA_IS_ALL_DAY, event.isAllDay)
                event.reminderMinutesBefore?.let { putExtra(AlarmReceiver.EXTRA_REMINDER_MINUTES_BEFORE, it) }
            }
            val pending = PendingIntent.getBroadcast(
                app,
                key.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            if (canScheduleExact) {
                val showIntent = PendingIntent.getActivity(
                    app,
                    0,
                    Intent(app, com.example.eventalert.MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerAt, showIntent),
                        pending,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerAt, showIntent),
                        pending,
                    )
                }
            } else {
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    60_000L,
                    pending,
                )
            }
        }

        setScheduledAlertEventKeys(app, newKeys)
    }
}
