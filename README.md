# Cinematic Travel Map Generator - Android Application

A native Android application built with **Kotlin**, **Jetpack Compose**, and **Room Database** for parsing, visualizing, and animating Google Timeline location history datasets on interactive maps.

## Key Features

- **Google Timeline Import & Parser**: Parses Google Location History JSON / Takeout exports, GeoJSON, and custom route coordinate strings.
- **Interactive Route Map**:
  - Multi-colored path segments categorized by transport mode (Walking, Cycling, Driving, Transit).
  - Stop markers with pulsing halos and auto-detected place names.
  - Custom camera controls (Zoom in/out, pan, map style toggle for Satellite vs Terrain, reset view).
  - Active location traveler pin moving along the track in real-time.
- **Playback Animation Controls**:
  - Play, pause, rewind, fast forward, skip to start/end.
  - Interactive slider scrubber.
  - Playback speed multiplier (0.5x, 1x, 1.5x, 2x, 4x).
- **Journey Management**:
  - Card-based journey list displaying stats (GPS points, distance, duration, stops count).
  - Search and filter journeys by title or location.
  - Single and batch selection/deletion.
- **Cinematic Video Export Simulation**:
  - Export settings configuration (Resolution 1080p/4K, 60fps, Camera flyover/orbit/pan modes).
- **Dark & Light Themes**: Centralized Material 3 palette with instant theme switching.
- **Offline Persistence**: Room SQLite database with initial sample seed routes (European Exploration, Tokyo Metro, Alpine Pass).

## Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Database**: Room Database + KSP
- **Architecture**: MVVM with Kotlin Flow & StateFlow
- **Image Loading**: Coil Compose

## Mapbox 3D Setup

TMLN now uses the **Mapbox Maps SDK for Android** for real terrain, satellite imagery, 3D buildings, and cinematic map cameras. To keep credentials private, both tokens are supplied only through the developer machine and are ignored by Git.

Create or update `local.properties` in the repository root:

```properties
# Public token beginning with pk.; used by the Android app at runtime.
MAPBOX_ACCESS_TOKEN=pk.YOUR_PUBLIC_TOKEN

# Secret token beginning with sk. and the DOWNLOADS:READ scope; used only by Gradle.
MAPBOX_DOWNLOADS_TOKEN=sk.YOUR_DOWNLOADS_TOKEN
```

The map screen stays usable and shows a setup message until a public token is present. The visual engine includes **Cinematic** and **Satellite** styles, transport-coloured routes, weighted stop markers, and Overview, Follow, and Orbit camera controls.


## Selected-Journey Offline Maps

TMLN now keeps the personal journey archive locally in Room and can also download a **selected journey's Mapbox map pack**. From a journey detail screen, choose **Download for offline**. TMLN downloads the Mapbox Standard and Standard Satellite style packs together with a precise tile corridor following that journey; it does not download a whole city or country.

The download card reports progress and lets you remove an offline pack later. Offline maps are intentionally opt-in so that storage, battery use, and cellular data remain in your control. To validate a completed pack, open the journey map, download the pack on Wi-Fi, turn on airplane mode, reopen the same journey, and test both the Cinematic and Satellite map styles near the route's normal playback zoom.

For development, run the parser tests with `./gradlew testDebugUnitTest` after Android Studio has installed the Android SDK. Test device behaviour separately with a real Mapbox public token because style and tile downloads require an authenticated network connection.


## Real-device testing

The repository includes a practical first-install and offline-validation guide for the primary test phone: [`docs/XIAOMI_DEVICE_TESTING.md`](docs/XIAOMI_DEVICE_TESTING.md). It covers Android Studio setup, safe local Mapbox credentials, USB or Wi-Fi deployment, the initial UI/import/map pass, and the airplane-mode offline pack test.
