# Event Alert

## Description

We're building a native android app that will do one very simple function - create alerts for upcoming calendar (google calendar) events.
The App is going to pull all the calendar data, display it in a list and then create alerts for any upcoming events in the calendar.
The goal of the app is to help user not miss their events when the event is a simple push notification that gets lost in a sea of push notification.
On Samsung phones the calendar does this by default, whenever an event is coming up, 10 minutes in advance it will show a full screen alert (similar to a user created alert) about the event, it will let user snooze or dismiss (default alert behaviours).
Our app is going to be a simple layer between google calendar and alerts.
We're going to be using default android apis to achive the best functionality as close to native as possible.

## UI

The UI on the app is also going to reflect a simple native like setup:

* The wizard at on the first launch, let user select which calendar they want to import from.
* The main screen that shows an endless list of imported events and the data-times of their alerts.
* A settings page that let's user do simple option selecting - lead times, change which calendars are imported from, disable the app alerts, sync times
  
## Mindset

We're looking to create simple, light-weight, unobstructive, minimal friction app with a single function.
As close to native android functionality as possible both in features and UI.

## Tech Stack (proposed)

* Android Studio
* Kotlin
* Compose
* Material3
* Google Calendar API
* Android Jetpack
* AndroidX
* Android Studio
* Kotlin
* Compose
* Material3
