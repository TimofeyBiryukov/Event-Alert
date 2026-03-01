package com.example.eventalert.alert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.eventalert.R
import com.example.eventalert.ui.ReminderActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Receives scheduled alarms, starts [ReminderActivity] immediately so the full-screen alert
 * shows first (Samsung-style), then posts a notification for the shade/fallback.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras ?: return
        val eventKey = extras.getString(EXTRA_EVENT_KEY) ?: return
        val title = extras.getString(EXTRA_TITLE) ?: "(No title)"
        val startTimeMillis = extras.getLong(EXTRA_START_TIME_MILLIS, 0L)
        val isAllDay = extras.getBoolean(EXTRA_IS_ALL_DAY, false)

        val channelId = REMINDER_CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.notification_channel_reminders),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setBypassDnd(true)
                setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }

        val reminderIntent = Intent(context, ReminderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                @Suppress("DEPRECATION")
                addFlags(0x00080000 or 0x00040000) // FLAG_ACTIVITY_SHOW_WHEN_LOCKED | FLAG_ACTIVITY_TURN_SCREEN_ON
            }
            putExtras(extras)
            putExtra(ReminderActivity.EXTRA_NOTIFICATION_ID, notificationIdForEventKey(eventKey))
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "eventalert:reminder",
        ).apply { acquire(5000L) }
        try {
            context.startActivity(reminderIntent)
        } finally {
            Handler(Looper.getMainLooper()).postDelayed({ wakeLock.release() }, 2000L)
        }

        val contentText = if (isAllDay) {
            SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(startTimeMillis))
        } else {
            SimpleDateFormat("EEE, MMM d, h:mm a", Locale.getDefault()).format(Date(startTimeMillis))
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            notificationIdForEventKey(eventKey),
            reminderIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(contentText)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationIdForEventKey(eventKey), notification)
        } catch (_: SecurityException) { /* permission not granted */ }

        // Notify any visible event list so it can remove this event immediately.
        context.sendBroadcast(Intent(ACTION_ALERT_FIRED).setPackage(context.packageName))
    }

    companion object {
        /** Broadcast action when an alert has fired; MainActivity refreshes the event list on receive. */
        const val ACTION_ALERT_FIRED = "com.example.eventalert.ALERT_FIRED"

        const val EXTRA_EVENT_KEY = "eventKey"
        const val EXTRA_TITLE = "title"
        const val EXTRA_START_TIME_MILLIS = "startTimeMillis"
        const val EXTRA_END_TIME_MILLIS = "endTimeMillis"
        const val EXTRA_IS_ALL_DAY = "isAllDay"
        const val EXTRA_REMINDER_MINUTES_BEFORE = "reminderMinutesBefore"

        const val REMINDER_CHANNEL_ID = "event_reminders"

        /** Stable notification ID per event so we can cancel on dismiss. */
        fun notificationIdForEventKey(eventKey: String): Int = eventKey.hashCode()
    }
}
