<p align="center">
  <img src="graphics/docs/icon-512.png" alt="Event Alert logo" width="160" />
</p>

### Event Alert

Event Alert is a small, native Android app that adds high-signal alerts on top of your existing calendars. It is a thin, on-device layer over the Android calendar provider and notification system, focused on one job: surfacing clear, full-screen alerts so you do not miss important events.

This repository is mainly for our future selves. The goal of this README is to help a maintainer get back up to speed quickly, not to pitch the app as a big open-source project.

---

### What it does (short version)

- **Calendar-based alerts**: Reads upcoming events from the device calendars you select.
- **High-signal notifications**: Schedules native-feeling, full-screen alerts with dismiss/snooze actions.
- **Simple settings**: Lets you pick calendars and basic lead times before events.
- **Strictly on-device**: No backend, no data exfiltration; everything runs locally.

Non-goals:

- Not a full calendar client or replacement UI.
- No complex account management.
- No server-side components.

For deeper product and architecture details, see `docs/EventAlertMain.md`, `docs/CalendarImportAndAlerts.md`, and `AGENTS.md`.

---

### Build & run

- **Prerequisites**
  - Android Studio (latest stable).
  - Recent Android SDK installed.

- **CLI build (Gradle wrapper)**
  - Windows:
    - `.\gradlew.bat assembleDebug`
    - `.\gradlew.bat assembleRelease`
  - macOS / Linux:
    - `./gradlew assembleDebug`
    - `./gradlew assembleRelease`

- **From Android Studio**
  - Open the `EventAlert` project.
  - Let Gradle sync.
  - Use the default app run configuration and deploy to a phone/emulator.

The app is phone-first; tablet behavior is currently a nice-to-have.

---

### Releases & CI

On merges to the `release` branch, GitHub Actions (see `.github/workflows/release-android.yml`) will build the app and attach an APK to a GitHub Release.

- **Latest release (APK download)**: `https://github.com/timofeybiryukov/EventAlert/releases/latest`

Once the first release is created, update the placeholder above with the real repository path if needed.

**Lightweight release checklist (for humans):**

1. Bump version code/name in the Android module (if needed) and commit.
2. Make sure `release` has the changes you want (merge from your main/dev branch as appropriate).
3. Push to `release` on GitHub.
4. Wait for the \"Build and release APK on release branch\" workflow to complete.
5. Go to the GitHub Releases page and confirm that the latest release has an APK attached and is installable on a device.

---

### Maintainer notes

- **When changing core behavior**
  - Keep the app within its single-purpose scope: a lightweight alerts layer over existing calendars.
  - Respect the privacy constraints in `AGENTS.md` (on-device only, no unnecessary permissions).

If the structure of the app or release workflow changes, keep this README brief but up to date.
