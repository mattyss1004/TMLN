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
