package com.example.eventalert.data

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long?,
    val calendarId: Long,
)
