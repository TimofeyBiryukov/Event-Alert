package com.example.eventalert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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