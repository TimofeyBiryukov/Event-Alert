package com.example.eventalert.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    currentDateFormatLabel: String,
    currentTimeFormatLabel: String,
    currentCalendarLabel: String,
    onSelectDateFormat: () -> Unit,
    onSelectTimeFormat: () -> Unit,
    onSelectCalendar: () -> Unit,
    onTestAlert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    @Composable
    fun spacer(margin: Int = 8) = Spacer(modifier = Modifier.height(margin.dp))

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Button(
            onClick = onTestAlert,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Test Alert")
        }
        spacer(margin = 24)
        ListItem(
            headlineContent = { Text(text = "Select calendar") },
            supportingContent = {
                Text(
                    text = currentCalendarLabel,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectCalendar() },
        )
        spacer(margin = 8)
        ListItem(
            headlineContent = { Text(text = "Date format") },
            supportingContent = {
                Text(
                    text = currentDateFormatLabel,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectDateFormat() },
        )
        spacer(margin = 8)
        ListItem(
            headlineContent = { Text(text = "Time format") },
            supportingContent = {
                Text(
                    text = currentTimeFormatLabel,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectTimeFormat() },
        )        
    }
}

