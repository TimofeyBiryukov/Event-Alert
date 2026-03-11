package com.example.eventalert.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.example.eventalert.alert.AlarmReceiver
import com.example.eventalert.alert.AlertScheduler
import com.example.eventalert.data.AlertStyleOption
import com.example.eventalert.data.DateFormatOption
import com.example.eventalert.data.TimeFormatOption
import com.example.eventalert.data.addDismissedAlertEventKey
import com.example.eventalert.data.clearSnoozedAlert
import com.example.eventalert.data.getDateFormatOption
import com.example.eventalert.data.getTimeFormatOption
import com.example.eventalert.data.getAlertStyleOption
import com.example.eventalert.ui.theme.EventAlertTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen reminder shown when an event alert fires. Launched via full-screen intent
 * from the notification so it can appear on the lock screen. Minimal UI: title, time, Dismiss.
 */
class ReminderActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        enableEdgeToEdge()
        setContent {
            EventAlertTheme {
                val ctx = LocalContext.current
                val appContext = ctx.applicationContext
                val scope = rememberCoroutineScope()
                val eventKey by remember {
                    mutableStateOf(intent?.getStringExtra(AlarmReceiver.EXTRA_EVENT_KEY).orEmpty())
                }
                val title = remember { intent?.getStringExtra(AlarmReceiver.EXTRA_TITLE).orEmpty() }
                val startTimeMillis: Long = remember { intent?.getLongExtra(AlarmReceiver.EXTRA_START_TIME_MILLIS, 0L) ?: 0L }
                val isAllDay: Boolean = remember { intent?.getBooleanExtra(AlarmReceiver.EXTRA_IS_ALL_DAY, false) ?: false }
                val notificationId: Int = remember { intent?.getIntExtra(EXTRA_NOTIFICATION_ID, 0) ?: 0 }

                var dateFormatOption by remember { mutableStateOf(DateFormatOption.SYSTEM_DEFAULT) }
                var timeFormatOption by remember { mutableStateOf(TimeFormatOption.SYSTEM_DEFAULT) }
                var alertStyleOption by remember { mutableStateOf(AlertStyleOption.SYSTEM_DEFAULT) }
                LaunchedEffect(Unit) {
                    // Best-effort, ignore failures; falls back to system default.
                    dateFormatOption = try {
                        getDateFormatOption(appContext)
                    } catch (_: Exception) {
                        DateFormatOption.SYSTEM_DEFAULT
                    }
                    timeFormatOption = try {
                        getTimeFormatOption(appContext)
                    } catch (_: Exception) {
                        TimeFormatOption.SYSTEM_DEFAULT
                    }
                    alertStyleOption = try {
                        getAlertStyleOption(appContext)
                    } catch (_: Exception) {
                        AlertStyleOption.SYSTEM_DEFAULT
                    }
                }
                val timeText = remember(startTimeMillis, isAllDay, dateFormatOption, timeFormatOption) {
                    if (startTimeMillis == 0L) ""
                    else if (isAllDay) {
                        val dateStr = when (dateFormatOption) {
                            DateFormatOption.SYSTEM_DEFAULT -> {
                                java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, Locale.getDefault())
                                    .format(Date(startTimeMillis))
                            }
                            DateFormatOption.DAY_MONTH_YEAR -> {
                                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(startTimeMillis))
                            }
                            DateFormatOption.MONTH_DAY_YEAR -> {
                                SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(Date(startTimeMillis))
                            }
                            DateFormatOption.YEAR_MONTH_DAY -> {
                                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(startTimeMillis))
                            }
                        }
                        dateStr
                    } else {
                        val dateStr = when (dateFormatOption) {
                            DateFormatOption.SYSTEM_DEFAULT -> {
                                java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, Locale.getDefault())
                                    .format(Date(startTimeMillis))
                            }
                            DateFormatOption.DAY_MONTH_YEAR -> {
                                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(startTimeMillis))
                            }
                            DateFormatOption.MONTH_DAY_YEAR -> {
                                SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(Date(startTimeMillis))
                            }
                            DateFormatOption.YEAR_MONTH_DAY -> {
                                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(startTimeMillis))
                            }
                        }
                        val is24HourSystem = DateFormat.is24HourFormat(ctx)
                        val timePattern = when (timeFormatOption) {
                            TimeFormatOption.HOUR_24 -> "HH:mm"
                            TimeFormatOption.HOUR_12 -> "h:mm a"
                            TimeFormatOption.SYSTEM_DEFAULT -> if (is24HourSystem) "HH:mm" else "h:mm a"
                        }
                        val timeStr = SimpleDateFormat(timePattern, Locale.getDefault()).format(Date(startTimeMillis))
                        "$dateStr · $timeStr"
                    }
                }

                data class SnoozeOption(val minutes: Int, val label: String)

                val snoozeOptions = remember {
                    listOf(
                        SnoozeOption(5, ctx.getString(com.example.eventalert.R.string.reminder_snooze_5m)),
                        SnoozeOption(30, ctx.getString(com.example.eventalert.R.string.reminder_snooze_30m)),
                        SnoozeOption(60, ctx.getString(com.example.eventalert.R.string.reminder_snooze_60m)),
                    )
                }
                var snoozeExpanded by remember { mutableStateOf(false) }
                var selectedSnoozeOption by remember { mutableStateOf(snoozeOptions.first()) }

                val (backgroundColor, contentColor) = when (alertStyleOption) {
                    AlertStyleOption.SYSTEM_DEFAULT -> MaterialTheme.colorScheme.background to MaterialTheme.colorScheme.onBackground
                    AlertStyleOption.LIGHT -> Color.White to Color.Black
                    AlertStyleOption.DARK -> Color.Black to Color.White
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = contentColor,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor,
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = ctx.getString(com.example.eventalert.R.string.reminder_snooze_for),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box {
                        Button(
                            onClick = { snoozeExpanded = true },
                        ) {
                            Text(text = selectedSnoozeOption.label)
                        }
                        DropdownMenu(
                            expanded = snoozeExpanded,
                            onDismissRequest = { snoozeExpanded = false },
                        ) {
                            snoozeOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        selectedSnoozeOption = option
                                        snoozeExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = {
                                if (eventKey.isNotEmpty()) {
                                    scope.launch {
                                        clearSnoozedAlert(appContext, eventKey)
                                        addDismissedAlertEventKey(appContext, eventKey)
                                    }
                                    ctx.sendBroadcast(
                                        Intent(AlarmReceiver.ACTION_ALERT_DISMISSED)
                                            .setPackage(ctx.packageName)
                                            .putExtra(AlarmReceiver.EXTRA_EVENT_KEY, eventKey),
                                    )
                                }
                                (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                                    .cancel(notificationId)
                                finish()
                            },
                        ) {
                            Text(ctx.getString(com.example.eventalert.R.string.reminder_dismiss))
                        }
                        Button(
                            onClick = {
                                val snoozeMinutes = selectedSnoozeOption.minutes
                                val snoozeUntil = System.currentTimeMillis() + snoozeMinutes * 60 * 1000L
                                if (eventKey.isNotEmpty()) {
                                    scope.launch {
                                        // Persist snooze state so the list can show \"Snoozed\" and keep the event.
                                        com.example.eventalert.data.putSnoozedAlert(appContext, eventKey, snoozeUntil)
                                    }
                                    ctx.sendBroadcast(
                                        Intent(AlarmReceiver.ACTION_ALERT_SNOOZED)
                                            .setPackage(ctx.packageName)
                                            .putExtra(AlarmReceiver.EXTRA_EVENT_KEY, eventKey),
                                    )
                                }
                                (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                                    .cancel(notificationId)
                                val extras = intent?.extras
                                if (extras != null) {
                                    AlertScheduler.scheduleSnooze(appContext, extras, snoozeMinutes * 60 * 1000L)
                                }
                                finish()
                            },
                        ) {
                            Text(ctx.getString(com.example.eventalert.R.string.reminder_snooze))
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_NOTIFICATION_ID = "notificationId"
    }
}
