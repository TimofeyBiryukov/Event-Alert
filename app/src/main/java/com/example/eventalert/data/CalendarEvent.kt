package com.example.eventalert.data

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long?,
    val calendarId: Long,
    val isAllDay: Boolean = false,
    val hasReminder: Boolean = false,
    /** Minutes before the event when the calendar reminder fires; null if no reminder or unknown. Used for all-day event alert time display. */
    val reminderMinutesBefore: Int? = null,
)
