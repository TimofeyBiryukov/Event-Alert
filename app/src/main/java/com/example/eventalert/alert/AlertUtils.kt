package com.example.eventalert.alert

import com.example.eventalert.data.CalendarEvent
import java.util.Calendar

/** Default minutes before a timed event when the alert fires. */
const val DEFAULT_ALERT_MINUTES = 10

/** Time window for each page of events (initial load and "load more"). */
const val EVENT_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

/** Don't load events beyond this from now (stops endless scroll with recurring events). */
const val MAX_IMPORT_WINDOW_MS = 365L * 24 * 60 * 60 * 1000

/**
 * Unique key per event instance; avoids duplicate keys (e.g. recurring events).
 * Used for scheduling, cancellation, and list keys.
 */
fun eventKey(event: CalendarEvent): String =
    "${event.calendarId}_${event.id}_${event.startTimeMillis}"

/**
 * Returns the wall-clock time (millis) when the alert for this event should fire.
 * - Timed events: startTimeMillis minus [DEFAULT_ALERT_MINUTES].
 * - All-day events: start of event day in local timezone minus [CalendarEvent.reminderMinutesBefore].
 *   All-day events without reminderMinutesBefore are excluded by the repository.
 */
fun alertTimeMillis(event: CalendarEvent): Long {
    return if (event.isAllDay) {
        val min = event.reminderMinutesBefore ?: DEFAULT_ALERT_MINUTES
        val cal = Calendar.getInstance()
        cal.timeInMillis = event.startTimeMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStartLocalMillis = cal.timeInMillis
        dayStartLocalMillis - min * 60L * 1000
    } else {
        event.startTimeMillis - DEFAULT_ALERT_MINUTES * 60L * 1000
    }
}
