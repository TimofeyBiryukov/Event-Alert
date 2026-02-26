package com.example.eventalert.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class SampleListItem(
    val id: Long,
    val title: String,
    val subtitle: String,
)

private val seedTemplates = listOf(
    "Team standup" to "Mon 9:00 AM · Alert 8:50 AM",
    "Design review" to "Mon 2:00 PM · Alert 1:50 PM",
    "Dentist" to "Tue 10:30 AM · Alert 10:20 AM",
    "Lunch with Sarah" to "Tue 12:30 PM · Alert 12:20 PM",
    "Sprint planning" to "Wed 10:00 AM · Alert 9:50 AM",
    "Gym" to "Wed 6:00 PM · Alert 5:50 PM",
    "Doctor appointment" to "Thu 11:00 AM · Alert 10:50 AM",
    "Movie night" to "Thu 7:00 PM · Alert 6:50 PM",
    "Weekend brunch" to "Sat 11:00 AM · Alert 10:50 AM",
    "Flight to NYC" to "Sun 3:00 PM · Alert 2:50 PM",
    "Conference call" to "Mon 4:00 PM · Alert 3:50 PM",
    "Birthday party" to "Sat 5:00 PM · Alert 4:50 PM",
)

private const val initialPageSize = 20
private const val pageSize = 20
private const val loadMoreThreshold = 5

private fun generatePage(startId: Long, count: Int): List<SampleListItem> =
    (0 until count).map { i ->
        val template = seedTemplates[((startId + i) % seedTemplates.size).toInt()]
        SampleListItem(
            id = startId + i,
            title = template.first,
            subtitle = template.second,
        )
    }

@Composable
fun SampleListScreen(modifier: Modifier = Modifier) {
    val toggledOn = remember { mutableStateMapOf<Long, Boolean>() }
    val items = remember { mutableStateListOf<SampleListItem>().apply { addAll(generatePage(0, initialPageSize)) } }
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = layoutInfo.totalItemsCount
            lastIndex >= total - loadMoreThreshold && total > 0
        }.collect { shouldLoadMore ->
            if (shouldLoadMore) {
                val nextStart = items.size.toLong()
                items.addAll(generatePage(nextStart, pageSize))
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
    ) {
        items(
            items = items,
            key = { it.id },
        ) { item ->
            ListItem(
                headlineContent = {
                    Text(text = item.title)
                },
                supportingContent = {
                    Text(text = item.subtitle)
                },
                trailingContent = {
                    Switch(
                        checked = toggledOn[item.id] ?: true,
                        onCheckedChange = { toggledOn[item.id] = it },
                    )
                },
            )
        }
    }
}
