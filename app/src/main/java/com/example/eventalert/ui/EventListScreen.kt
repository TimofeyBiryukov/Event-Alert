package com.example.eventalert.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eventalert.data.CalendarEvent
import com.example.eventalert.data.CalendarRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DEFAULT_ALERT_MINUTES = 10
private const val ONE_YEAR_MS = 365L * 24 * 60 * 60 * 1000

private val dateFormat = SimpleDateFormat("EEE h:mm a", Locale.getDefault())

private fun formatEventSubtitle(event: CalendarEvent): String {
    val startStr = dateFormat.format(Date(event.startTimeMillis))
    val alertMillis = event.startTimeMillis - DEFAULT_ALERT_MINUTES * 60 * 1000
    val alertStr = dateFormat.format(Date(alertMillis))
    return "$startStr · Alert $alertStr"
}

private fun eventToItemId(event: CalendarEvent): Long {
    return event.calendarId * 1_000_000_000_000L + (event.startTimeMillis / 1000)
}

@Composable
fun EventListScreen(
    calendarIds: Set<Long>,
    repository: CalendarRepository,
    modifier: Modifier = Modifier,
) {
    var loading by remember { mutableStateOf<Boolean>(true) }
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    val toggledOn = remember { mutableStateMapOf<Long, Boolean>() }

    LaunchedEffect(calendarIds) {
        loading = true
        events = try {
            val now = System.currentTimeMillis()
            repository.getEvents(
                calendarIds = calendarIds.toList(),
                fromMillis = now,
                toMillis = now + ONE_YEAR_MS,
            )
        } catch (e: Exception) {
            emptyList()
        }
        loading = false
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
                modifier = modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp),
            ) {
                items(
                    items = events,
                    key = { ev -> eventToItemId(ev) },
                ) { event ->
                    val itemId = eventToItemId(event)
                    ListItem(
                        headlineContent = { Text(text = event.title) },
                        supportingContent = {
                            Text(text = formatEventSubtitle(event))
                        },
                        trailingContent = {
                            Switch(
                                checked = toggledOn[itemId] ?: true,
                                onCheckedChange = { checked -> toggledOn[itemId] = checked },
                            )
                        },
                    )
                }
            }
        }
    }
}
