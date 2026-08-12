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
