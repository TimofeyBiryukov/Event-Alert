package com.example.eventalert.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class SampleListItem(
    val title: String,
    val subtitle: String,
)

private val sampleListItems = listOf(
    SampleListItem("Team standup", "Mon 9:00 AM · Alert 8:50 AM"),
    SampleListItem("Design review", "Mon 2:00 PM · Alert 1:50 PM"),
    SampleListItem("Dentist", "Tue 10:30 AM · Alert 10:20 AM"),
    SampleListItem("Lunch with Sarah", "Tue 12:30 PM · Alert 12:20 PM"),
    SampleListItem("Sprint planning", "Wed 10:00 AM · Alert 9:50 AM"),
    SampleListItem("Gym", "Wed 6:00 PM · Alert 5:50 PM"),
    SampleListItem("Doctor appointment", "Thu 11:00 AM · Alert 10:50 AM"),
    SampleListItem("Movie night", "Thu 7:00 PM · Alert 6:50 PM"),
    SampleListItem("Weekend brunch", "Sat 11:00 AM · Alert 10:50 AM"),
    SampleListItem("Flight to NYC", "Sun 3:00 PM · Alert 2:50 PM"),
    SampleListItem("Conference call", "Mon 4:00 PM · Alert 3:50 PM"),
    SampleListItem("Birthday party", "Sat 5:00 PM · Alert 4:50 PM"),
)

@Composable
fun SampleListScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
    ) {
        items(
            items = sampleListItems,
            key = { it.title },
        ) { item ->
            ListItem(
                headlineContent = {
                    Text(text = item.title)
                },
                supportingContent = {
                    Text(text = item.subtitle)
                },
            )
        }
    }
}
