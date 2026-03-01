package com.example.eventalert.data

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Authority for the platform calendar provider; sync adapter uses this. */
private const val CALENDAR_AUTHORITY = "com.android.calendar"

/**
 * Asks the system to sync calendar data for the given accounts so that new or updated
 * events from Google (or other backends) appear in the local CalendarContract sooner.
 * Best-effort: the system may throttle or ignore the request (e.g. some devices only allow
 * the sync adapter owner to trigger sync). We call setIsSyncable(account, authority, 1)
 * before requestSync so the account is eligible for sync. Uses reflection because
 * requestSync/setIsSyncable are not in the public API on all SDK levels.
 */
suspend fun requestCalendarSync(context: Context, accounts: List<CalendarAccount>) =
    withContext(Dispatchers.IO) {
        if (accounts.isEmpty()) return@withContext
        val cr = context.contentResolver
        val extras = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        }
        val requestSyncMethod = try {
            ContentResolver::class.java.getMethod(
                "requestSync",
                Account::class.java,
                String::class.java,
                Bundle::class.java,
            )
        } catch (_: NoSuchMethodException) {
            null
        }
        val setIsSyncableMethod = try {
            ContentResolver::class.java.getMethod(
                "setIsSyncable",
                Account::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
            )
        } catch (_: NoSuchMethodException) {
            null
        }
        for (acc in accounts) {
            try {
                val account = Account(acc.name, acc.type)
                // Ensure account is syncable so requestSync is not ignored (best-effort).
                setIsSyncableMethod?.invoke(cr, account, CALENDAR_AUTHORITY, 1)
                requestSyncMethod?.invoke(cr, account, CALENDAR_AUTHORITY, extras)
            } catch (_: Exception) {
                // Ignore: account may be invalid or method may not be supported
            }
        }
    }
