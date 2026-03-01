package com.example.eventalert.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.eventalert.alert.AlarmReceiver
import com.example.eventalert.alert.EVENT_WINDOW_MS
import com.example.eventalert.alert.MAX_IMPORT_WINDOW_MS
import com.example.eventalert.alert.alertTimeMillis
import com.example.eventalert.alert.eventKey
import com.example.eventalert.data.CalendarEvent
import com.example.eventalert.data.CalendarRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val LOAD_MORE_THRESHOLD = 5

private val dateFormat = SimpleDateFormat("EEE h:mm a", Locale.getDefault())

private fun formatEventSubtitle(event: CalendarEvent): String {
    return try {
        val dateStr = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(Date(event.startTimeMillis))
        if (event.isAllDay) {
            val alertStr = event.reminderMinutesBefore?.let {
                SimpleDateFormat("EEE, MMM d, h:mm a", Locale.getDefault()).format(Date(alertTimeMillis(event)))
            } ?: "Uses calendar reminder"
            "All day · $dateStr · Alert $alertStr"
        } else {
            val shortTimeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val startTimeStr = shortTimeFormat.format(Date(event.startTimeMillis))
            val alertTimeStr = shortTimeFormat.format(Date(alertTimeMillis(event)))
            "$dateStr · $startTimeStr · Alert $alertTimeStr"
        }
    } catch (e: Exception) {
        "Event · Alert"
    }
}

@Composable
fun EventListScreen(
    calendarIds: Set<Long>,
    repository: CalendarRepository,
    modifier: Modifier = Modifier,
) {
    var loading by remember { mutableStateOf<Boolean>(true) }
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var loadedEndMillis by remember { mutableStateOf(0L) }
    var loadingMore by remember { mutableStateOf<Boolean>(false) }
    var hasMore by remember { mutableStateOf(true) }
    val toggledOn = remember { mutableStateMapOf<String, Boolean>() }
    val listState = rememberLazyListState()
    var testAlertScheduledAt by remember { mutableStateOf<Long?>(null) }
    var countdownTick by remember { mutableStateOf(0) }

    LaunchedEffect(calendarIds) {
        loading = true
        val now = System.currentTimeMillis()
        val maxEnd = now + MAX_IMPORT_WINDOW_MS
        events = try {
            repository.getEvents(
                calendarIds = calendarIds.toList(),
                fromMillis = now,
                toMillis = minOf(now + EVENT_WINDOW_MS, maxEnd),
            ).distinctBy { eventKey(it) }
        } catch (e: Exception) {
            emptyList()
        }
        loadedEndMillis = minOf(now + EVENT_WINDOW_MS, maxEnd)
        hasMore = loadedEndMillis < maxEnd
        loading = false
    }

    LaunchedEffect(listState, calendarIds) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastIdx = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = layoutInfo.totalItemsCount
            val nearEnd = lastIdx >= total - LOAD_MORE_THRESHOLD && total > 0
            val end = loadedEndMillis
            val loadingMoreNow = loadingMore
            val hasMoreNow = hasMore
            nearEnd && !loading && !loadingMoreNow && hasMoreNow
        }.collect { shouldLoad ->
            if (!shouldLoad) return@collect
            val now = System.currentTimeMillis()
            val maxEnd = now + MAX_IMPORT_WINDOW_MS
            val start = loadedEndMillis
            if (start >= maxEnd) {
                hasMore = false
                return@collect
            }
            loadingMore = true
            val endForQuery = minOf(start + EVENT_WINDOW_MS, maxEnd)
            val batch = try {
                repository.getEvents(
                    calendarIds = calendarIds.toList(),
                    fromMillis = start,
                    toMillis = endForQuery,
                )
            } catch (e: Exception) {
                emptyList()
            }
            val existingKeys = events.mapTo(mutableSetOf()) { eventKey(it) }
            val newEvents = batch.filter { eventKey(it) !in existingKeys }
            events = events + newEvents
            loadedEndMillis = endForQuery
            if (batch.isEmpty() || loadedEndMillis >= maxEnd) hasMore = false
            loadingMore = false
        }
    }

    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("h:mm:ss a", Locale.getDefault()) }

    LaunchedEffect(testAlertScheduledAt) {
        testAlertScheduledAt ?: return@LaunchedEffect
        while (true) {
            delay(1000L)
            countdownTick++
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    val eventKey = "test_now"
                    val startTime = System.currentTimeMillis()
                    context.startActivity(
                        Intent(context, ReminderActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra(AlarmReceiver.EXTRA_EVENT_KEY, eventKey)
                            putExtra(AlarmReceiver.EXTRA_TITLE, "Test event")
                            putExtra(AlarmReceiver.EXTRA_START_TIME_MILLIS, startTime)
                            putExtra(AlarmReceiver.EXTRA_IS_ALL_DAY, false)
                            putExtra(ReminderActivity.EXTRA_NOTIFICATION_ID, AlarmReceiver.notificationIdForEventKey(eventKey))
                        },
                    )
                },
            ) {
                Text("Test Alert")
            }
            Button(
                onClick = {
                    val app = context.applicationContext
                    val triggerAt = System.currentTimeMillis() + 60_000L
                    val eventKey = "test_1min"
                    val intent = Intent(app, AlarmReceiver::class.java).apply {
                        putExtra(AlarmReceiver.EXTRA_EVENT_KEY, eventKey)
                        putExtra(AlarmReceiver.EXTRA_TITLE, "Test event (1 min)")
                        putExtra(AlarmReceiver.EXTRA_START_TIME_MILLIS, triggerAt)
                        putExtra(AlarmReceiver.EXTRA_IS_ALL_DAY, false)
                    }
                    val pending = PendingIntent.getBroadcast(
                        app,
                        eventKey.hashCode(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val useExact = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
                        alarmManager.canScheduleExactAlarms()
                    if (useExact) {
                        try {
                            val showIntent = PendingIntent.getActivity(
                                app, 0,
                                Intent(app, com.example.eventalert.MainActivity::class.java),
                                PendingIntent.FLAG_IMMUTABLE,
                            )
                            alarmManager.setAlarmClock(
                                AlarmManager.AlarmClockInfo(triggerAt, showIntent),
                                pending,
                            )
                        } catch (_: SecurityException) {
                            alarmManager.setWindow(
                                AlarmManager.RTC_WAKEUP,
                                triggerAt,
                                15_000L,
                                pending,
                            )
                        }
                    } else {
                        alarmManager.setWindow(
                            AlarmManager.RTC_WAKEUP,
                            triggerAt,
                            15_000L,
                            pending,
                        )
                    }
                    testAlertScheduledAt = triggerAt
                },
            ) {
                Text("Test Alert in 1 min")
            }
        }

        testAlertScheduledAt?.let { scheduledAt ->
            val remainingSec = ((scheduledAt - System.currentTimeMillis()) / 1000).toInt()
            val tick = countdownTick
            val line1 = "Scheduled for ${timeFormat.format(Date(scheduledAt))} (1 min from when you tapped)"
            val line2 = if (remainingSec > 0) {
                "Countdown: ${remainingSec}s — full-screen alert may still be delayed by battery/Doze."
            } else {
                "Expected time passed. If no alert appeared, the system likely delayed it (battery saver, Doze)."
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(
                    text = line1,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = line2,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                events.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No upcoming events",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 8.dp),
                    ) {
                        items(
                            items = events,
                            key = { ev -> eventKey(ev) },
                        ) { event ->
                            val key = eventKey(event)
                            ListItem(
                                headlineContent = { Text(text = event.title) },
                                supportingContent = {
                                    Text(text = formatEventSubtitle(event))
                                },
                                trailingContent = {
                                    Switch(
                                        checked = toggledOn[key] ?: true,
                                        onCheckedChange = { checked -> toggledOn[key] = checked },
                                    )
                                },
                            )
                        }
                        if (loadingMore) {
                            item(key = "loading_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
