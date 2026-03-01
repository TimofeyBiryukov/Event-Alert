package com.example.eventalert.data

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CalendarRepository(private val contentResolver: ContentResolver) {

    suspend fun getCalendars(): List<CalendarItem> = withContext(Dispatchers.IO) {
        // Use null projection for maximum compatibility: some providers (e.g. on emulator
        // or AOSP builds) can return an empty cursor when requesting columns they don't support.
        val cursor = contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            null,
            null,
            null,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        ) ?: return@withContext emptyList()
        cursor.use {
            val idIdx = it.getColumnIndex(CalendarContract.Calendars._ID)
            val nameIdx = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val colorIdx = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)
            if (idIdx < 0 || nameIdx < 0) return@use emptyList<CalendarItem>()
            buildList {
                while (it.moveToNext()) {
                    add(
                        CalendarItem(
                            id = it.getLong(idIdx),
                            displayName = it.getString(nameIdx) ?: "",
                            color = if (colorIdx >= 0) it.getInt(colorIdx) else null,
                        )
                    )
                }
            }
        }
    }

    suspend fun getEvents(
        calendarIds: List<Long>,
        fromMillis: Long,
        toMillis: Long,
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (calendarIds.isEmpty()) return@withContext emptyList()
        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder, fromMillis)
        ContentUris.appendId(uriBuilder, toMillis)
        val uri = uriBuilder.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.ALL_DAY,
        )
        val placeholders = calendarIds.joinToString(",") { "?" }
        val selection = "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)"
        val selectionArgs = calendarIds.map { it.toString() }.toTypedArray()
        val cursor = contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            CalendarContract.Instances.BEGIN,
        ) ?: return@withContext emptyList()
        data class InstanceRow(
            val eventId: Long,
            val title: String,
            val startTimeMillis: Long,
            val endTimeMillis: Long?,
            val calendarId: Long,
            val isAllDay: Boolean,
        )
        val rows = cursor.use {
            val eventIdIdx = it.getColumnIndex(CalendarContract.Instances.EVENT_ID)
            val titleIdx = it.getColumnIndex(CalendarContract.Instances.TITLE)
            val beginIdx = it.getColumnIndex(CalendarContract.Instances.BEGIN)
            val endIdx = it.getColumnIndex(CalendarContract.Instances.END)
            val calIdIdx = it.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)
            val allDayIdx = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)
            if (eventIdIdx < 0 || beginIdx < 0 || calIdIdx < 0) return@use emptyList<InstanceRow>()
            buildList {
                while (it.moveToNext()) {
                    add(
                        InstanceRow(
                            eventId = it.getLong(eventIdIdx),
                            title = it.getString(titleIdx)?.takeIf { s -> s?.isNotBlank() == true } ?: "(No title)",
                            startTimeMillis = it.getLong(beginIdx),
                            endTimeMillis = if (endIdx >= 0) it.getLong(endIdx) else null,
                            calendarId = it.getLong(calIdIdx),
                            isAllDay = allDayIdx >= 0 && it.getInt(allDayIdx) == 1,
                        )
                    )
                }
            }
        }
        val reminderMap = if (rows.isEmpty()) emptyMap()
        else queryReminderMinutesByEventId(rows.map { it.eventId }.distinct())
        buildList {
            for (row in rows) {
                val minutesBefore = reminderMap[row.eventId]
                val hasReminder = minutesBefore != null
                if (row.isAllDay && !hasReminder) continue
                add(
                    CalendarEvent(
                        id = row.eventId,
                        title = row.title,
                        startTimeMillis = row.startTimeMillis,
                        endTimeMillis = row.endTimeMillis,
                        calendarId = row.calendarId,
                        isAllDay = row.isAllDay,
                        hasReminder = hasReminder,
                        reminderMinutesBefore = minutesBefore,
                    )
                )
            }
        }
    }

    /** Returns map of eventId -> minutes before event (uses earliest reminder per event for display). */
    private fun queryReminderMinutesByEventId(eventIds: List<Long>): Map<Long, Int> {
        if (eventIds.isEmpty()) return emptyMap()
        val batchSize = 100
        val result = mutableMapOf<Long, Int>()
        for (chunk in eventIds.chunked(batchSize)) {
            val placeholders = chunk.joinToString(",") { "?" }
            val selection = "${CalendarContract.Reminders.EVENT_ID} IN ($placeholders)"
            val selectionArgs = chunk.map { it.toString() }.toTypedArray()
            val cursor = contentResolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                arrayOf(CalendarContract.Reminders.EVENT_ID, CalendarContract.Reminders.MINUTES),
                selection,
                selectionArgs,
                null,
            ) ?: continue
            cursor.use {
                val eventIdIdx = it.getColumnIndex(CalendarContract.Reminders.EVENT_ID)
                val minutesIdx = it.getColumnIndex(CalendarContract.Reminders.MINUTES)
                if (eventIdIdx < 0 || minutesIdx < 0) return@use
                while (it.moveToNext()) {
                    val eid = it.getLong(eventIdIdx)
                    val minutes = it.getInt(minutesIdx)
                    if (minutes >= 0) {
                        result[eid] = minOf(result[eid] ?: Int.MAX_VALUE, minutes)
                    }
                }
            }
        }
        return result.filterValues { it != Int.MAX_VALUE }
    }
}
