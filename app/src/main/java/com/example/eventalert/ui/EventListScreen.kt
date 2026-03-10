package com.example.eventalert.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.eventalert.alert.EVENT_WINDOW_MS
import com.example.eventalert.alert.MAX_IMPORT_WINDOW_MS
import com.example.eventalert.alert.alertTimeMillis
import com.example.eventalert.alert.eventKey
import com.example.eventalert.data.CalendarEvent
import com.example.eventalert.data.CalendarRepository
import com.example.eventalert.data.DateFormatOption
import com.example.eventalert.data.TimeFormatOption
import com.example.eventalert.data.getDateFormatOption
import com.example.eventalert.data.getTimeFormatOption
import com.example.eventalert.data.getDismissedAlertEventKeys
import com.example.eventalert.data.getSnoozedAlerts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val LOAD_MORE_THRESHOLD = 5

private fun formatEventSubtitle(
    event: CalendarEvent,
    snoozedUntil: Long?,
    dateFormatOption: DateFormatOption,
    timeFormatOption: TimeFormatOption,
    is24HourSystem: Boolean,
): String {
    return try {
        val dateStr = when (dateFormatOption) {
            DateFormatOption.SYSTEM_DEFAULT -> {
                java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, Locale.getDefault())
                    .format(Date(event.startTimeMillis))
            }
            DateFormatOption.DAY_MONTH_YEAR -> {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(event.startTimeMillis))
            }
            DateFormatOption.MONTH_DAY_YEAR -> {
                SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(Date(event.startTimeMillis))
            }
            DateFormatOption.YEAR_MONTH_DAY -> {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(event.startTimeMillis))
            }
        }
        val base = if (event.isAllDay) {
            val alertStr = event.reminderMinutesBefore?.let {
                val timePattern = when (timeFormatOption) {
                    TimeFormatOption.HOUR_24 -> "HH:mm"
                    TimeFormatOption.HOUR_12 -> "h:mm a"
                    TimeFormatOption.SYSTEM_DEFAULT -> if (is24HourSystem) "HH:mm" else "h:mm a"
                }
                val alertDate = Date(alertTimeMillis(event))
                val timeStr = SimpleDateFormat(timePattern, Locale.getDefault()).format(alertDate)
                // Reuse the already formatted dateStr plus formatted time
                "$dateStr, $timeStr"
            } ?: "Uses calendar reminder"
            "All day · $dateStr · Alert $alertStr"
        } else {
            val timePattern = when (timeFormatOption) {
                TimeFormatOption.HOUR_24 -> "HH:mm"
                TimeFormatOption.HOUR_12 -> "h:mm a"
                TimeFormatOption.SYSTEM_DEFAULT -> if (is24HourSystem) "HH:mm" else "h:mm a"
            }
            val shortTimeFormat = SimpleDateFormat(timePattern, Locale.getDefault())
            val startTimeStr = shortTimeFormat.format(Date(event.startTimeMillis))
            val alertTimeStr = shortTimeFormat.format(Date(alertTimeMillis(event)))
            "$dateStr · $startTimeStr · Alert $alertTimeStr"
        }
        return if (snoozedUntil != null && snoozedUntil > System.currentTimeMillis()) {
            val timePattern = when (timeFormatOption) {
                TimeFormatOption.HOUR_24 -> "HH:mm"
                TimeFormatOption.HOUR_12 -> "h:mm a"
                TimeFormatOption.SYSTEM_DEFAULT -> if (is24HourSystem) "HH:mm" else "h:mm a"
            }
            val shortTimeFormat = SimpleDateFormat(timePattern, Locale.getDefault())
            val snoozeStr = shortTimeFormat.format(Date(snoozedUntil))
            "$base · Snoozed until $snoozeStr"
        }
 else {
            base
        }
    } catch (e: Exception) {
        "Event · Alert"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(
    calendarIds: Set<Long>,
    repository: CalendarRepository,
    refreshTrigger: Long = 0L,
    onRefreshRequested: (() -> Unit)? = null,
    isBatteryOptimized: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var dateFormatOption by remember { mutableStateOf(DateFormatOption.SYSTEM_DEFAULT) }
    var timeFormatOption by remember { mutableStateOf(TimeFormatOption.SYSTEM_DEFAULT) }
    var loading by remember { mutableStateOf<Boolean>(true) }
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var loadedEndMillis by remember { mutableStateOf(0L) }
    var loadingMore by remember { mutableStateOf<Boolean>(false) }
    var hasMore by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var snoozedAlerts by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var dismissedEventKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    val toggledOn = remember { mutableStateMapOf<String, Boolean>() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        dateFormatOption = getDateFormatOption(context)
        timeFormatOption = getTimeFormatOption(context)
    }

    LaunchedEffect(calendarIds, refreshTrigger) {
        loading = true
        if (events.isEmpty()) {
            events = emptyList()
            loadedEndMillis = 0L
            hasMore = true
        }
        val now = System.currentTimeMillis()
        val maxEnd = now + MAX_IMPORT_WINDOW_MS
        val snoozed = try {
            getSnoozedAlerts(context.applicationContext)
        } catch (e: Exception) {
            emptyMap()
        }
        val dismissed = try {
            getDismissedAlertEventKeys(context.applicationContext)
        } catch (e: Exception) {
            emptySet()
        }
        snoozedAlerts = snoozed
        dismissedEventKeys = dismissed

        val newEvents = try {
            repository.getEvents(
                calendarIds = calendarIds.toList(),
                fromMillis = now,
                toMillis = minOf(now + EVENT_WINDOW_MS, maxEnd),
            ).distinctBy { eventKey(it) }
        } catch (e: Exception) {
            emptyList()
        }
        events = newEvents.filter { event ->
            val key = eventKey(event)
            if (dismissed.contains(key)) {
                false
            } else {
                val snoozedUntil = snoozed[key]
                when {
                    snoozedUntil != null && snoozedUntil > now -> true
                    else -> alertTimeMillis(event) > now
                }
            }
        }
        loadedEndMillis = minOf(now + EVENT_WINDOW_MS, maxEnd)
        hasMore = loadedEndMillis < maxEnd
        loading = false
    }

    LaunchedEffect(loading) {
        if (!loading) isRefreshing = false
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
            val snoozedNow = snoozedAlerts
            val dismissedNow = dismissedEventKeys
            val filteredNew = newEvents.filter { event ->
                val key = eventKey(event)
                if (dismissedNow.contains(key)) {
                    false
                } else {
                    val snoozedUntil = snoozedNow[key]
                    when {
                        snoozedUntil != null && snoozedUntil > now -> true
                        else -> alertTimeMillis(event) > now
                    }
                }
            }
            events = (events + filteredNew).filter { event ->
                val key = eventKey(event)
                if (dismissedNow.contains(key)) {
                    false
                } else {
                    val snoozedUntil = snoozedNow[key]
                    when {
                        snoozedUntil != null && snoozedUntil > now -> true
                        else -> alertTimeMillis(event) > now
                    }
                }
            }
            loadedEndMillis = endForQuery
            if (batch.isEmpty() || loadedEndMillis >= maxEnd) hasMore = false
            loadingMore = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            loading && events.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        onRefreshRequested?.invoke()
                    },
                ) {
                    when {
                        events.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
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
                                if (isBatteryOptimized) {
                                    item(key = "battery_optimization_warning") {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            ),
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                            ) {
                                                Text(
                                                    text = "Battery optimization is limiting Event Alert. Alerts may not work reliably until you disable optimization for this app.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                            }
                                        }
                                    }
                                }
                                items(
                                    items = events,
                                    key = { ev -> eventKey(ev) },
                                ) { event ->
                                    val key = eventKey(event)
                                    val snoozedUntil = snoozedAlerts[key]
                                    ListItem(
                                        headlineContent = { Text(text = event.title) },
                                        supportingContent = {
                                            Text(
                                                text = formatEventSubtitle(
                                                    event = event,
                                                    snoozedUntil = snoozedUntil,
                                                    dateFormatOption = dateFormatOption,
                                                    timeFormatOption = timeFormatOption,
                                                    is24HourSystem = DateFormat.is24HourFormat(context),
                                                ),
                                            )
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
    }
}
