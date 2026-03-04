package com.example.eventalert.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "selected_calendars")

private val SELECTED_CALENDAR_IDS_KEY = stringSetPreferencesKey("selected_calendar_ids")
private val SCHEDULED_ALERT_EVENT_KEYS_KEY = stringSetPreferencesKey("scheduled_alert_event_keys")
private val SNOOZED_ALERTS_KEY = stringSetPreferencesKey("snoozed_alerts")
private val DISMISSED_ALERT_EVENT_KEYS_KEY = stringSetPreferencesKey("dismissed_alert_event_keys")

fun selectedCalendarIdsFlow(context: Context): Flow<Set<Long>> =
    context.dataStore.data.map { prefs ->
        prefs[SELECTED_CALENDAR_IDS_KEY]
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
    }

suspend fun setSelectedCalendarIds(context: Context, ids: Set<Long>) {
    context.dataStore.edit { prefs ->
        prefs[SELECTED_CALENDAR_IDS_KEY] = ids.map { it.toString() }.toSet()
    }
}

suspend fun getSelectedCalendarIds(context: Context): Set<Long> =
    context.dataStore.data.map { prefs ->
        prefs[SELECTED_CALENDAR_IDS_KEY]
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
    }.first()

suspend fun getScheduledAlertEventKeys(context: Context): Set<String> =
    context.dataStore.data.map { prefs ->
        prefs[SCHEDULED_ALERT_EVENT_KEYS_KEY] ?: emptySet()
    }.first()

suspend fun setScheduledAlertEventKeys(context: Context, keys: Set<String>) {
    context.dataStore.edit { prefs ->
        prefs[SCHEDULED_ALERT_EVENT_KEYS_KEY] = keys
    }
}

/**
 * Returns a map of snoozed alerts: eventKey -> snoozeUntilMillis.
 */
suspend fun getSnoozedAlerts(context: Context): Map<String, Long> =
    context.dataStore.data.map { prefs ->
        val raw = prefs[SNOOZED_ALERTS_KEY] ?: emptySet()
        raw.mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 2) return@mapNotNull null
            val until = parts[1].toLongOrNull() ?: return@mapNotNull null
            parts[0] to until
        }.toMap()
    }.first()

/**
 * Store or update a snoozed alert for the given eventKey.
 */
suspend fun putSnoozedAlert(context: Context, eventKey: String, snoozeUntil: Long) {
    context.dataStore.edit { prefs ->
        val current = prefs[SNOOZED_ALERTS_KEY] ?: emptySet()
        val updated = current
            .filterNot { it.startsWith("$eventKey:") }
            .toMutableSet()
        updated.add("$eventKey:$snoozeUntil")
        prefs[SNOOZED_ALERTS_KEY] = updated
    }
}

/**
 * Clear any snoozed alert entry for the given eventKey.
 */
suspend fun clearSnoozedAlert(context: Context, eventKey: String) {
    context.dataStore.edit { prefs ->
        val current = prefs[SNOOZED_ALERTS_KEY] ?: emptySet()
        val updated = current.filterNot { it.startsWith("$eventKey:") }.toSet()
        prefs[SNOOZED_ALERTS_KEY] = updated
    }
}

/**
 * Returns the set of event keys that have been explicitly dismissed by the user.
 */
suspend fun getDismissedAlertEventKeys(context: Context): Set<String> =
    context.dataStore.data.map { prefs ->
        prefs[DISMISSED_ALERT_EVENT_KEYS_KEY] ?: emptySet()
    }.first()

/**
 * Mark an event key as dismissed by the user.
 */
suspend fun addDismissedAlertEventKey(context: Context, eventKey: String) {
    context.dataStore.edit { prefs ->
        val current = prefs[DISMISSED_ALERT_EVENT_KEYS_KEY] ?: emptySet()
        prefs[DISMISSED_ALERT_EVENT_KEYS_KEY] = current + eventKey
    }
}
