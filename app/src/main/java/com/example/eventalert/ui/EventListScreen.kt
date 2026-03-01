package com.example.eventalert.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eventalert.data.CalendarEvent
import com.example.eventalert.data.CalendarRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DEFAULT_ALERT_MINUTES = 10
/** Time window for each page of events (initial load and "load more"). */
private const val EVENT_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
/** Don't load events beyond this from now (stops endless scroll with recurring events). */
private const val MAX_IMPORT_WINDOW_MS = 365L * 24 * 60 * 60 * 1000
private const val LOAD_MORE_THRESHOLD = 5

private val dateFormat = SimpleDateFormat("EEE h:mm a", Locale.getDefault())

private fun formatEventSubtitle(event: CalendarEvent): String {
    return try {
        val fullDateFormat = SimpleDateFormat("EEE, MMM d, yyyy h:mm a", Locale.getDefault())
        val shortTimeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        val startDate = Date(event.startTimeMillis)
        val alertMillis = event.startTimeMillis - DEFAULT_ALERT_MINUTES * 60 * 1000
        val alertDate = Date(alertMillis)

        val dateStr = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(startDate)
        val startTimeStr = shortTimeFormat.format(startDate)
        val alertTimeStr = shortTimeFormat.format(alertDate)

        "$dateStr · $startTimeStr · Alert $alertTimeStr"
    } catch (e: Exception) {
        "Event · Alert"
    }
}

/** Unique key per instance; avoids Long overflow and duplicate keys (e.g. recurring events). */
private fun eventKey(event: CalendarEvent): String =
    "${event.calendarId}_${event.id}_${event.startTimeMillis}"

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

    when {
        loading -> {
            Box(
                modifier = modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        events.isEmpty() -> {
            Box(
                modifier = modifier.fillMaxSize().padding(32.dp),
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
                modifier = modifier
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
