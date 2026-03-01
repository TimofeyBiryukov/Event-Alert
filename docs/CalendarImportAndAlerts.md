# Calendar import and alert display (implementation reference)

This document summarizes the calendar import and alert-related implementations in Event Alert and where the logic lives. Use it when touching calendar import, event list, wizard, or all-day/reminder behavior.

---

## Navigation and entry

- **MainActivity.kt** (`app/src/main/java/com/example/eventalert/MainActivity.kt`)
  - Reads selected calendar IDs from DataStore.
  - Shows `CalendarWizardScreen` when the set is empty, else `EventListScreen`.
  - Single-Activity, conditional composition (no nav graph yet).

---

## Calendar wizard

- **CalendarWizardScreen.kt** (`app/src/main/java/com/example/eventalert/ui/CalendarWizardScreen.kt`)
  - Requests READ_CALENDAR before loading calendars.
  - Loads calendar list via repository; multi-select list with checkboxes.
  - "Continue" persists selected IDs via DataStore and triggers switch to event list.

---

## Persistence

- **SelectedCalendarsPreferences.kt** (`app/src/main/java/com/example/eventalert/data/SelectedCalendarsPreferences.kt`)
  - DataStore Preferences for selected calendar IDs and scheduled alert event keys.
  - Exposes `selectedCalendarIdsFlow`, `setSelectedCalendarIds`, `getSelectedCalendarIds`; `getScheduledAlertEventKeys`, `setScheduledAlertEventKeys` (for AlertScheduler).
  - Used for wizard completion, event list calendar filter, and alarm reschedule bookkeeping.

---

## Data layer

- **CalendarRepository.kt** (`app/src/main/java/com/example/eventalert/data/CalendarRepository.kt`)
  - ContentResolver-based.
  - `getCalendars()`: lists calendars (id, displayName, color).
  - `getEvents(calendarIds, fromMillis, toMillis)`: queries Instances with ALL_DAY; then queries Reminders (batched by EVENT_ID) to get per-event `reminderMinutesBefore` (minimum minutes per event). All-day events without reminders are excluded in the repository.

- **CalendarEvent.kt** (`app/src/main/java/com/example/eventalert/data/CalendarEvent.kt`)
  - id, title, startTimeMillis, endTimeMillis, calendarId, isAllDay, hasReminder, reminderMinutesBefore.

- **CalendarItem.kt** (`app/src/main/java/com/example/eventalert/data/CalendarItem.kt`)
  - id, displayName, color (for wizard list).

---

## Event list and pagination

- **EventListScreen.kt** (`app/src/main/java/com/example/eventalert/ui/EventListScreen.kt`)
  - Time-window pagination: initial load one month (`EVENT_WINDOW_MS`), load more when near bottom (`snapshotFlow` + `LazyListState`). Constants and `eventKey` / `alertTimeMillis` come from **AlertUtils.kt**.
  - Cap at `MAX_IMPORT_WINDOW_MS` (e.g. one year) so the list does not grow indefinitely with recurring events.
  - Events merged and deduplicated by `eventKey(calendarId, id, startTimeMillis)` to avoid duplicate LazyColumn keys.
  - Bottom loading indicator when `loadingMore`.

---

## Display and all-day handling

- **Timed events**
  - Subtitle = date + start time + "Alert &lt;time&gt;" using `DEFAULT_ALERT_MINUTES` (10) from **AlertUtils.kt**.

- **All-day events**
  - Subtitle = "All day · &lt;date&gt; · Alert &lt;time&gt;".
  - Alert time is computed from **start of event day in local timezone** minus `reminderMinutesBefore` (not raw UTC), to avoid wrong local time (e.g. 4:50 AM in UTC+x). Same logic in **AlertUtils.alertTimeMillis()** for scheduling.
  - Multiple reminders per event: only the **closest** (smallest minutes) is stored and shown.

---

## Full-screen reminders

- **AlertUtils.kt** (`app/src/main/java/com/example/eventalert/alert/AlertUtils.kt`)
  - Shared constants: `DEFAULT_ALERT_MINUTES` (10), `EVENT_WINDOW_MS`, `MAX_IMPORT_WINDOW_MS`.
  - `eventKey(event)`: unique key per instance. `alertTimeMillis(event)`: when the alert should fire (timed: start − 10 min; all-day: start of day local − reminderMinutesBefore).

- **AlertScheduler** (`alert/AlertScheduler.kt`)
  - Fetches events in the same window as the event list (via CalendarRepository), computes alert time per event, cancels previously scheduled alarms, and sets new ones with `AlarmManager.setAlarmClock()` so they fire when the app is closed. Stores scheduled event keys in DataStore (`getScheduledAlertEventKeys` / `setScheduledAlertEventKeys` in SelectedCalendarsPreferences).

- **AlarmReceiver** (`alert/AlarmReceiver.kt`)
  - Receives the alarm; **starts ReminderActivity directly first** (with WakeLock and show-when-locked/turn-screen-on flags) so the full-screen alert appears immediately (Samsung-style). Then posts a high-priority notification with full-screen intent as shade entry/fallback. Notification channel "Event reminders" (IMPORTANCE_HIGH).

- **ReminderActivity** (`ui/ReminderActivity.kt`)
  - Full-screen UI: event title, date/time, Dismiss button. Launched by the full-screen intent. On dismiss: cancels the notification and finishes. Uses `setShowWhenLocked(true)` / `setTurnScreenOn(true)` in code for lock-screen display.

- **Reschedule**
  - **BootReceiver** (`alert/BootReceiver.kt`): on `BOOT_COMPLETED`, enqueues a one-off **AlertRescheduleWorker** (WorkManager) that runs `AlertScheduler.schedule()`.
  - **MainActivity**: when the event list is shown (selected calendar IDs non-empty), calls `AlertScheduler.schedule(applicationContext)` so alerts are scheduled after wizard complete or when opening the app.

---

## Sync and refresh

The app does not sync with Google directly; it reads from the system calendar provider (which syncs with Google). “Sync” here means when we re-read from the provider and reschedule alarms.

- **ContentObserver** (MainActivity, when event list is visible): Registers a `ContentObserver` on `CalendarContract.Events.CONTENT_URI`. When the calendar provider notifies a change (e.g. after Google sync or edits in another app), the app triggers a full event-list reload and runs `AlertScheduler.schedule()` so the list and alarms stay in sync while the app is open.
- **Refresh on app open/resume**: On `MainActivity.onResume()`, `requestSync()` is called: it bumps a refresh trigger (so `EventListScreen` does a full reload) and runs `AlertScheduler.schedule()`. This guarantees fresh data and alarms whenever the user opens or returns to the app.
- **Requesting calendar sync**: Before each refresh, the app asks the system to sync calendar data for the selected calendars’ accounts (`requestCalendarSync` in **CalendarSync.kt**, using `ContentResolver.requestSync` with authority `com.android.calendar`). This can make new events from Google appear sooner. A **second refresh** is triggered 2 seconds later so events that arrive shortly after the sync request are picked up.
- **Periodic background reschedule**: When the user has selected calendars, MainActivity enqueues a **periodic** WorkManager job (unique name `event_alert_periodic_sync`, 30-minute interval, 5-minute initial delay) that runs `AlertRescheduleWorker` (same as boot). This keeps alarms up to date when the app is closed; WorkManager is Doze-aware and does not require new permissions.

---

## Permissions and manifest

- **AndroidManifest.xml** (`app/src/main/AndroidManifest.xml`)
  - READ_CALENDAR; POST_NOTIFICATIONS; USE_FULL_SCREEN_INTENT; SCHEDULE_EXACT_ALARM; RECEIVE_BOOT_COMPLETED; WAKE_LOCK.
  - READ_CALENDAR requested on wizard. POST_NOTIFICATIONS and USE_FULL_SCREEN_INTENT requested when event list is shown (MainActivity).
