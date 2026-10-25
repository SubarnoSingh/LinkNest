# LinkNest

**Your personal library for links, notes, and connected ideas.**

LinkNest is an offline-first Android application that helps you save useful web resources, organize them with tags, attach personal notes, and explore connections through an interactive knowledge graph.

## Features

- **Bookmark management** — Save, edit, search, sort, and filter links.
- **Smart organization** — Group bookmarks with tags and mark favorites for quick access.
- **Personal notes** — Create notes and connect them to relevant bookmarks.
- **Knowledge graph** — Explore explicit connections and relationships formed by shared tags.
- **Quick capture** — Save links and shared text using Android's share functionality.
- **Metadata enrichment** — Retrieve webpage titles, descriptions, canonical URLs, and favicon metadata when available.
- **Backup and restore** — Export your library to JSON and restore it using Android's document picker.
- **Offline-first storage** — Keep your library locally available without requiring an account or cloud service.

## Tech Stack

- Kotlin
- Jetpack Compose and Material 3
- Room for local persistence
- Kotlin Coroutines and Flow
- Android Storage Access Framework for backup and restore
- Gradle with Kotlin DSL

## Getting Started

### Requirements

- Android Studio with a compatible Android SDK
- JDK compatible with the project's Gradle configuration
- Android device or emulator

### Build the app

Clone the repository:

```bash
git clone https://github.com/SubarnoSingh/LinkNest.git
cd LinkNest
```

Build the debug APK:

```bash
./gradlew assembleDebug
```

Run unit tests:

```bash
./gradlew testDebugUnitTest
```

Run Android lint checks:

```bash
./gradlew check
```

The debug APK is normally generated at:

`app/build/outputs/apk/debug/app-debug.apk`

## Data and Privacy

LinkNest is designed around local-first storage. Your bookmarks, notes, tags, and graph relationships are stored on your device. Webpage metadata enrichment requires network access, and opening saved links may connect to external websites.

Use the built-in backup feature to export your library before moving to another device or performing a restore.

## Project Status

LinkNest is under active development. Features and the user experience may evolve as testing and refinement continue.

## License

No open-source license has been specified yet. Unless a license is added, the source code remains subject to the default copyright rules.
