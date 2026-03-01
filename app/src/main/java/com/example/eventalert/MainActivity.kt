package com.example.eventalert

import android.Manifest
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.eventalert.alert.AlertRescheduleWorker
import com.example.eventalert.alert.AlertScheduler
import com.example.eventalert.data.CalendarRepository
import com.example.eventalert.data.getSelectedCalendarIds
import com.example.eventalert.data.requestCalendarSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import com.example.eventalert.ui.CalendarWizardScreen
import com.example.eventalert.ui.EventListScreen
import androidx.lifecycle.lifecycleScope
import com.example.eventalert.ui.theme.EventAlertTheme

class MainActivity : ComponentActivity() {

    /** Bumping this triggers EventListScreen to do a full reload; updated on resume and calendar change. */
    private val refreshTriggerState = mutableStateOf(0L)

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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (selectedIds == null || selectedIds!!.isEmpty() || !hasCalendarPermission) {
                        CalendarWizardScreen(
                            repository = repository,
                            onComplete = { ids ->
                                completedWizardThisSession = true
                                selectedIds = ids
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
                        EventListScreen(
                            calendarIds = selectedIds!!,
                            repository = repository,
                            refreshTrigger = refreshTriggerState.value,
                            onRefreshRequested = { requestSync() },
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}