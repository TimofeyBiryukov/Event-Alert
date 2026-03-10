package com.example.eventalert.ui

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.eventalert.data.CalendarItem
import com.example.eventalert.data.CalendarRepository

@Composable
fun CalendarWizardScreen(
    repository: CalendarRepository,
    onComplete: (Set<Long>) -> Unit,
    modifier: Modifier = Modifier,
    initialSelectedIds: Set<Long> = emptySet(),
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val hasPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    var permissionGranted by remember { mutableStateOf(hasPermission) }
    var calendars by remember { mutableStateOf<List<CalendarItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedIds by remember { mutableStateOf(initialSelectedIds) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (granted) {
            loading = true
            error = null
        }
    }

    LaunchedEffect(permissionGranted) {
        if (!permissionGranted) return@LaunchedEffect
        loading = true
        error = null
        try {
            calendars = repository.getCalendars()
        } catch (e: Exception) {
            error = e.message ?: "Failed to load calendars"
        }
        loading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Choose calendars to import events from",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            when {
                !permissionGranted -> {
                    Text(
                        text = "Calendar access is required to show your calendars.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Button(
                        onClick = {
                            activity?.let { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) }
                        },
                    ) {
                        Text("Allow calendar access")
                    }
                }
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Text(
                        text = "Calendar access is required",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Button(
                        onClick = {
                            activity?.let { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) }
                        },
                    ) {
                        Text("Retry")
                    }
                }
                calendars.isEmpty() -> {
                    Text(
                        text = "No calendars found.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(calendars, key = { it.id }) { calendar ->
                            ListItem(
                                headlineContent = { Text(calendar.displayName.ifBlank { "Calendar ${calendar.id}" }) },
                                leadingContent = {
                                    Checkbox(
                                        checked = calendar.id in selectedIds,
                                        onCheckedChange = { checked ->
                                            selectedIds = if (checked) selectedIds + calendar.id
                                            else selectedIds - calendar.id
                                        },
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
        if (permissionGranted && !loading && error == null && calendars.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val ids = selectedIds.toSet()
                    onComplete(ids)
                },
                enabled = selectedIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue")
            }
        }
    }
}
