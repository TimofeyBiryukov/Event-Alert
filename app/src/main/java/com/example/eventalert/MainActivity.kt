package com.example.eventalert

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.eventalert.alert.AlertScheduler
import com.example.eventalert.data.CalendarRepository
import com.example.eventalert.data.getSelectedCalendarIds
import kotlinx.coroutines.launch
import com.example.eventalert.ui.CalendarWizardScreen
import com.example.eventalert.ui.EventListScreen
import com.example.eventalert.ui.theme.EventAlertTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EventAlertTheme {
                val context = LocalContext.current
                var selectedIds by remember { mutableStateOf<Set<Long>?>(null) }
                val scope = rememberCoroutineScope()
                LaunchedEffect(Unit) {
                    selectedIds = getSelectedCalendarIds(context)
                }
                val repository = remember {
                    CalendarRepository(context.applicationContext.contentResolver)
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                ) { _ -> /* result not needed for initial schedule */ }
                LaunchedEffect(selectedIds) {
                    if (!selectedIds.isNullOrEmpty()) {
                        scope.launch {
                            AlertScheduler.schedule(context.applicationContext)
                        }
                    }
                }
                LaunchedEffect(selectedIds) {
                    if (selectedIds.isNullOrEmpty()) return@LaunchedEffect
                    val toRequest = mutableListOf<String>()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            toRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.USE_FULL_SCREEN_INTENT) != PackageManager.PERMISSION_GRANTED) {
                        toRequest.add(Manifest.permission.USE_FULL_SCREEN_INTENT)
                    }
                    if (toRequest.isNotEmpty()) {
                        permissionLauncher.launch(toRequest.toTypedArray())
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (selectedIds == null || selectedIds!!.isEmpty()) {
                        CalendarWizardScreen(
                            repository = repository,
                            onComplete = {
                                scope.launch {
                                    selectedIds = getSelectedCalendarIds(context)
                                }
                            },
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else {
                        EventListScreen(
                            calendarIds = selectedIds!!,
                            repository = repository,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}