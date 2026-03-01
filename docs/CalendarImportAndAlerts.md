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
  - DataStore Preferences for selected calendar IDs.
  - Exposes `selectedCalendarIdsFlow` and `setSelectedCalendarIds`.
  - Used for wizard completion and event list calendar filter.

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
  - Time-window pagination: initial load one month (`EVENT_WINDOW_MS`), load more when near bottom (`snapshotFlow` + `LazyListState`).
  - Cap at `MAX_IMPORT_WINDOW_MS` (e.g. one year) so the list does not grow indefinitely with recurring events.
  - Events merged and deduplicated by `eventKey(calendarId, id, startTimeMillis)` to avoid duplicate LazyColumn keys.
  - Bottom loading indicator when `loadingMore`.

---

## Display and all-day handling

- **Timed events**
  - Subtitle = date + start time + "Alert &lt;time&gt;" using `DEFAULT_ALERT_MINUTES` (10).

- **All-day events**
  - Subtitle = "All day · &lt;date&gt; · Alert &lt;time&gt;".
  - Alert time is computed from **start of event day in local timezone** minus `reminderMinutesBefore` (not raw UTC), to avoid wrong local time (e.g. 4:50 AM in UTC+x).
  - Multiple reminders per event: only the **closest** (smallest minutes) is stored and shown.

---

## Permissions and manifest

- **AndroidManifest.xml** (`app/src/main/AndroidManifest.xml`)
  - READ_CALENDAR declared; requested on wizard before loading calendars.
