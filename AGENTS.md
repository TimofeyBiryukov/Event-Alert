# Event Alert – Agent Guide

This document guides AI coding agents and human contributors who use them when working on the Event Alert Android app.  
Read this file (and `README.md` plus `docs/EventAlertMain.md`) **before** making substantial changes.

---

## Project Vision & Mindset

- **Single-purpose app**: Event Alert exists to provide high-signal alerts for upcoming calendar events. It is *not* a full calendar client.
- **Native-first experience**: The app should feel like a natural extension of Android’s own calendar/alert capabilities, not a flashy replacement.
- **Simple, lightweight, unobtrusive**:
  - Minimal friction in setup and daily use.
  - Minimal settings, only what is necessary to control alerts.
  - No unnecessary screens, flows, or feature creep.
- **Thin layer over calendars**:
  - The app uses existing Google Calendar (or calendar provider) data.
  - It schedules alerts; it does not own or deeply manage calendar data.

When in doubt, prefer the simplest solution that preserves this vision.

---

## High-Level Architecture Guidelines

Strive for a modular structure, with clear separation of concerns:

- **Calendar access layer**
  - Encapsulate interactions with the Android calendar provider / Google Calendar API.
  - Provide a small, well-defined API for fetching upcoming events and available calendars.

- **Alert scheduling layer**
  - Responsible for mapping events to scheduled alerts.
  - Uses AlarmManager, WorkManager, or other appropriate Jetpack/system APIs.
  - Handles creating, updating, and canceling scheduled alerts.

- **UI layer (Jetpack Compose)**
  - Screens: onboarding wizard, main event list, settings.
  - Uses ViewModels (or equivalent state holders) to consume data from lower layers.

- **Settings / persistence**
  - Store user preferences (lead times, selected calendars, enabled/disabled state, sync interval).
  - Use appropriate Jetpack/AndroidX solutions (e.g., DataStore) where it makes sense.

Within this structure, keep components small, focused, and testable.

---

## Core Implementations (Reference)

Key implementation details are summarized in **`docs/CalendarImportAndAlerts.md`**. Read that file when touching calendar import, event list, wizard, or all-day/reminder behavior.

- **Calendar data and reminders**: `CalendarRepository`, `CalendarEvent` (all-day, `reminderMinutesBefore`), `CalendarItem`.
- **Wizard and persistence**: `CalendarWizardScreen`, `SelectedCalendarsPreferences`; navigation and first-run logic in `MainActivity`.
- **Event list**: `EventListScreen` (pagination, `EVENT_WINDOW_MS`, `MAX_IMPORT_WINDOW_MS`, `eventKey`, `formatEventSubtitle` for timed vs all-day).

Build verification: run `.\gradlew.bat assembleDebug` (Windows) or `./gradlew assembleDebug` (macOS/Linux) after code changes.

---

## Coding & Design Conventions

- **Language & style**
  - Use idiomatic **Kotlin**.
  - Favor immutability where reasonable.
  - Prefer clear, descriptive names over abbreviations.

- **UI**
  - Use **Jetpack Compose** and **Material 3** components.
  - Stick to standard Material patterns; avoid highly custom or non-native-feeling UI.
  - Keep screens visually simple and easy to scan.

- **Structure**
  - Keep functions and classes focused on a single responsibility.
  - Avoid over-engineering or premature abstraction.
  - Prefer explicitness over “clever” solutions.

- **Dependencies**
  - Prefer first-party Android, Jetpack, and Kotlin libraries.
  - Add new third-party dependencies only when there is a clear, documented benefit.
  - If adding a dependency, explain its purpose in the change description (PR, commit message, or code comments near integration points).

---

## Privacy, Permissions & UX Constraints

- **Data minimization**
  - Only request permissions that are strictly necessary (calendar read access, notifications, alarm scheduling where applicable).
  - Use calendar data solely to read events and schedule alerts on-device.

- **No data exfiltration**
  - Do not send calendar data or user-specific event details off-device.
  - If a future change proposes any network usage, it must be clearly justified and documented, and this document should be updated.

- **Respectful alerts**
  - Alerts should be high-signal but not abusive.
  - Respect system-level behaviors as much as possible (e.g., Do Not Disturb modes, platform restrictions).
  - Avoid creating excessive background work that might harm battery life.

Any change that touches permissions, background work, or user data handling should be carefully reasoned about and documented in code comments or commit messages.

---

## How Agents Should Operate in This Repo

- **Before making changes**
  - Read `README.md` to understand the app’s overall purpose and architecture.
  - Read `docs/EventAlertMain.md` for high-level product vision, UI, and mindset.

- **Scope & focus**
  - Prefer small, incremental changes over large refactors.
  - Avoid adding new features that significantly expand the app beyond its alert-focused scope.
  - When asked “should we add X?”, ensure X aligns with the single-purpose, low-friction vision before proceeding.

- **UI & UX**
  - Keep UI changes consistent with native Android and Material 3.
  - Avoid adding complex configuration for rare edge cases; favor sensible defaults.

- **Dependencies & architecture**
  - When introducing new patterns or dependencies, favor simplicity and established Android/Jetpack practices.
  - Document architectural decisions briefly (e.g., in PR descriptions or top-level comments where the decision is applied).

- **Build verification**
  - **Always run a build after making code changes** to confirm the app compiles and no regressions were introduced.
  - Use the project’s Gradle wrapper:
    - **Windows**: `.\gradlew.bat assembleDebug`
    - **macOS / Linux**: `./gradlew assembleDebug`
  - Fix any compilation or build errors before considering the change complete. Do not leave the project in a failing build state.

---

## Future Documentation Hooks

As the project evolves, additional documentation artifacts may be introduced:

- `ARCHITECTURE.md` for a more detailed description of modules, data flows, and key components.
- `CONTRIBUTING.md` for contribution guidelines, code review expectations, and branching/commit conventions.
- Additional docs for:
  - Calendar integration details.
  - Alert scheduling strategy and trade-offs (e.g., WorkManager vs. AlarmManager vs. foreground services).

When adding such documents, ensure they reference and remain consistent with the vision and constraints described here.
