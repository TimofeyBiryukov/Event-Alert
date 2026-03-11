package com.example.eventalert.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val DATE_FORMAT_KEY = stringPreferencesKey("date_format")
private val TIME_FORMAT_KEY = stringPreferencesKey("time_format")
private val ALERT_STYLE_KEY = stringPreferencesKey("alert_style")

enum class DateFormatOption {
    SYSTEM_DEFAULT,
    DAY_MONTH_YEAR,
    MONTH_DAY_YEAR,
    YEAR_MONTH_DAY,
}

enum class TimeFormatOption {
    SYSTEM_DEFAULT,
    HOUR_12,
    HOUR_24,
}

enum class AlertStyleOption {
    SYSTEM_DEFAULT,
    LIGHT,
    DARK,
}

suspend fun getDateFormatOption(context: Context): DateFormatOption {
    val raw = context.settingsDataStore.data
        .map { prefs -> prefs[DATE_FORMAT_KEY] ?: DateFormatOption.SYSTEM_DEFAULT.name }
        .first()
    return runCatching { DateFormatOption.valueOf(raw) }.getOrDefault(DateFormatOption.SYSTEM_DEFAULT)
}

suspend fun setDateFormatOption(context: Context, option: DateFormatOption) {
    context.settingsDataStore.edit { prefs ->
        prefs[DATE_FORMAT_KEY] = option.name
    }
}

suspend fun getTimeFormatOption(context: Context): TimeFormatOption {
    val raw = context.settingsDataStore.data
        .map { prefs -> prefs[TIME_FORMAT_KEY] ?: TimeFormatOption.SYSTEM_DEFAULT.name }
        .first()
    return runCatching { TimeFormatOption.valueOf(raw) }.getOrDefault(TimeFormatOption.SYSTEM_DEFAULT)
}

suspend fun setTimeFormatOption(context: Context, option: TimeFormatOption) {
    context.settingsDataStore.edit { prefs ->
        prefs[TIME_FORMAT_KEY] = option.name
    }
}

suspend fun getAlertStyleOption(context: Context): AlertStyleOption {
    val raw = context.settingsDataStore.data
        .map { prefs -> prefs[ALERT_STYLE_KEY] ?: AlertStyleOption.SYSTEM_DEFAULT.name }
        .first()
    return runCatching { AlertStyleOption.valueOf(raw) }.getOrDefault(AlertStyleOption.SYSTEM_DEFAULT)
}

suspend fun setAlertStyleOption(context: Context, option: AlertStyleOption) {
    context.settingsDataStore.edit { prefs ->
        prefs[ALERT_STYLE_KEY] = option.name
    }
}

