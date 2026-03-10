package com.example.eventalert

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.registerReceiver
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.eventalert.alert.AlarmReceiver
import com.example.eventalert.alert.AlertRescheduleWorker
import com.example.eventalert.alert.AlertScheduler
import com.example.eventalert.data.CalendarRepository
import com.example.eventalert.data.DateFormatOption
import com.example.eventalert.data.getDateFormatOption
import com.example.eventalert.data.getSelectedCalendarIds
import com.example.eventalert.data.requestCalendarSync
import com.example.eventalert.data.setDateFormatOption
import com.example.eventalert.data.setSelectedCalendarIds
import com.example.eventalert.ui.ReminderActivity
import com.example.eventalert.ui.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import com.example.eventalert.ui.CalendarWizardScreen
import com.example.eventalert.ui.EventListScreen
import androidx.lifecycle.lifecycleScope
import com.example.eventalert.ui.theme.EventAlertTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    /** Bumping this triggers EventListScreen to do a full reload; updated on resume and calendar change. */
    private val refreshTriggerState = mutableStateOf(0L)

    /** Bumps the refresh trigger so EventListScreen reloads (e.g. when an alert has fired). */
    fun refreshEventList() {
        refreshTriggerState.value = System.currentTimeMillis()
    }

    /** Called when calendar data may have changed or when app is resumed; refreshes list and reschedules alerts. */
    fun requestSync() {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        // Immediate read so the list shows current local state right away.
        refreshTriggerState.value = System.currentTimeMillis()
        lifecycleScope.launch {
            AlertScheduler.schedule(applicationContext)
        }
        lifecycleScope.launch {
            val app = applicationContext
            val calendarIds = getSelectedCalendarIds(app)
            if (calendarIds.isNotEmpty()) {
                val repo = CalendarRepository(app.contentResolver)
                val accounts = repo.getAccountsForCalendars(calendarIds.toList())
                // Request sync first so the system pulls from Google; then refresh after a delay
                // so we read after the sync has had time to update the local provider.
                requestCalendarSync(app, accounts)
            }
            delay(4_000L)
            withContext(Dispatchers.Main.immediate) {
                refreshTriggerState.value = System.currentTimeMillis()
            }
            AlertScheduler.schedule(app)
            delay(4_000L)
            withContext(Dispatchers.Main.immediate) {
                refreshTriggerState.value = System.currentTimeMillis()
            }
            AlertScheduler.schedule(app)
        }
    }

    override fun onResume() {
        super.onResume()
        requestSync()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EventAlertTheme {
                val context = LocalContext.current
                val activity = this@MainActivity
                var selectedIds by remember { mutableStateOf<Set<Long>?>(null) }
                var completedWizardThisSession by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                LaunchedEffect(Unit) {
                    val loaded = getSelectedCalendarIds(context)
                    if (!completedWizardThisSession) {
                        selectedIds = loaded
                    }
                }
                val repository = remember {
                    CalendarRepository(context.applicationContext.contentResolver)
                }

                var dateFormatOption by remember { mutableStateOf(DateFormatOption.SYSTEM_DEFAULT) }
                var showDateFormatDialog by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    dateFormatOption = getDateFormatOption(context)
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                ) { _ -> /* result not needed for initial schedule */ }
                LaunchedEffect(selectedIds) {
                    if (!selectedIds.isNullOrEmpty() &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                        scope.launch {
                            AlertScheduler.schedule(context.applicationContext)
                        }
                        val app = context.applicationContext
                        val periodicRequest = PeriodicWorkRequestBuilder<AlertRescheduleWorker>(30, TimeUnit.MINUTES)
                            .setInitialDelay(5, TimeUnit.MINUTES)
                            .build()
                        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
                            "event_alert_periodic_sync",
                            ExistingPeriodicWorkPolicy.KEEP,
                            periodicRequest,
                        )
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

                val hasCalendarPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                var showingSettings by remember { mutableStateOf(false) }
                var appBarMenuExpanded by remember { mutableStateOf(false) }
                var forceShowWizard by remember { mutableStateOf(false) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        if (selectedIds != null && selectedIds!!.isNotEmpty() && hasCalendarPermission) {
                            if (showingSettings) {
                                TopAppBar(
                                    title = { Text(text = "Settings") },
                                    navigationIcon = {
                                        IconButton(onClick = { showingSettings = false }) {
                                            Icon(
                                                imageVector = Icons.Filled.ArrowBack,
                                                contentDescription = "Back",
                                            )
                                        }
                                    },
                                )
                            } else {
                                TopAppBar(
                                    title = { Text(text = "Event Alert") },
                                    actions = {
                                        IconButton(onClick = { appBarMenuExpanded = true }) {
                                            Icon(
                                                imageVector = Icons.Filled.MoreVert,
                                                contentDescription = "More options",
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = appBarMenuExpanded,
                                            onDismissRequest = { appBarMenuExpanded = false },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(text = "Settings") },
                                                onClick = {
                                                    appBarMenuExpanded = false
                                                    showingSettings = true
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    if (forceShowWizard || selectedIds == null || selectedIds!!.isEmpty() || !hasCalendarPermission) {
                        CalendarWizardScreen(
                            repository = repository,
                            onComplete = { ids ->
                                completedWizardThisSession = true
                                selectedIds = ids
                                forceShowWizard = false
                                activity.lifecycleScope.launch {
                                    setSelectedCalendarIds(activity.applicationContext, ids)
                                }
                            },
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else {
                        val activity = context as? MainActivity
                        DisposableEffect(activity) {
                            if (activity == null) return@DisposableEffect onDispose { }
                            val onCalendarChange = { activity.requestSync() }
                            val handler = Handler(Looper.getMainLooper())
                            val observer = object : ContentObserver(handler) {
                                override fun onChange(selfChange: Boolean, uri: Uri?) {
                                    onCalendarChange()
                                }
                            }
                            val cr = activity.contentResolver
                            cr.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, observer)
                            cr.registerContentObserver(CalendarContract.Instances.CONTENT_URI, true, observer)
                            onDispose {
                                cr.unregisterContentObserver(observer)
                            }
                        }
                        DisposableEffect(activity) {
                            if (activity == null) return@DisposableEffect onDispose { }
                            val receiver = object : BroadcastReceiver() {
                                override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                                    activity.refreshEventList()
                                }
                            }
                            val filter = IntentFilter().apply {
                                addAction(AlarmReceiver.ACTION_ALERT_SNOOZED)
                                addAction(AlarmReceiver.ACTION_ALERT_DISMISSED)
                                addAction(AlarmReceiver.ACTION_ALERT_FIRED)
                            }
                            registerReceiver(activity, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                            onDispose {
                                activity.unregisterReceiver(receiver)
                            }
                        }
                        if (showingSettings) {
                            SettingsScreen(
                                currentDateFormatLabel = when (dateFormatOption) {
                                    DateFormatOption.SYSTEM_DEFAULT -> "System default"
                                    DateFormatOption.DAY_MONTH_YEAR -> "DD/MM/YYYY"
                                    DateFormatOption.MONTH_DAY_YEAR -> "MM/DD/YYYY"
                                    DateFormatOption.YEAR_MONTH_DAY -> "YYYY-MM-DD"
                                },
                                currentCalendarLabel = "Tap to change calendars",
                                onSelectDateFormat = { showDateFormatDialog = true },
                                onSelectCalendar = {
                                    showingSettings = false
                                    // Show the calendar wizard without clearing the existing selection.
                                    // The wizard will only persist a new selection once the user taps Continue.
                                    forceShowWizard = true
                                },
                                onTestAlert = {
                                    val intent = Intent(context, ReminderActivity::class.java).apply {
                                        putExtra(AlarmReceiver.EXTRA_EVENT_KEY, "test_event")
                                        putExtra(AlarmReceiver.EXTRA_TITLE, "Sample Event Reminder")
                                        putExtra(AlarmReceiver.EXTRA_START_TIME_MILLIS, System.currentTimeMillis() + 10 * 60 * 1000L)
                                        putExtra(AlarmReceiver.EXTRA_IS_ALL_DAY, false)
                                        putExtra(ReminderActivity.EXTRA_NOTIFICATION_ID, 0)
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.padding(innerPadding),
                            )
                        } else {
                            EventListScreen(
                                calendarIds = selectedIds!!,
                                repository = repository,
                                refreshTrigger = refreshTriggerState.value,
                                onRefreshRequested = { requestSync() },
                                modifier = Modifier.padding(innerPadding),
                            )
                        }
                        if (showDateFormatDialog) {
                            AlertDialog(
                                onDismissRequest = { showDateFormatDialog = false },
                                title = { Text(text = "Date format") },
                                text = {
                                    Column {
                                        DateFormatOption.values().forEach { option ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                                    .clickable {
                                                        dateFormatOption = option
                                                    },
                                            ) {
                                                RadioButton(
                                                    selected = option == dateFormatOption,
                                                    onClick = { dateFormatOption = option },
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = when (option) {
                                                        DateFormatOption.SYSTEM_DEFAULT -> "System default"
                                                        DateFormatOption.DAY_MONTH_YEAR -> "DD/MM/YYYY"
                                                        DateFormatOption.MONTH_DAY_YEAR -> "MM/DD/YYYY"
                                                        DateFormatOption.YEAR_MONTH_DAY -> "YYYY-MM-DD"
                                                    },
                                                )
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showDateFormatDialog = false
                                            scope.launch {
                                                setDateFormatOption(context, dateFormatOption)
                                            }
                                        },
                                    ) {
                                        Text("OK")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDateFormatDialog = false }) {
                                        Text("Cancel")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}