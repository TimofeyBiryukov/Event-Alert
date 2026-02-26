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
