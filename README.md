# Timestamp

Lyrics and subtitle sync studio for Android. Kotlin, Jetpack Compose (Material 3), AndroidX Media3 (ExoPlayer), Room.
Formats: LRC, TTML, ELARC (Enhanced LRC with word-level tags, saved as `.elrc`).

## Build without a computer (GitHub Actions)
1. Create a GitHub repository and upload the contents of this folder (keep `.github/workflows/build.yml`).
2. Open the Actions tab, run **Build APK** (it also runs on every push).
3. Download the `Timestamp-debug-apk` artifact, unzip it and install the APK.

## Build in Android Studio
Open this folder, let Gradle sync (Gradle 8.9, JDK 17), then Run. minSdk 26, targetSdk 35.
If the IDE does not pick up the wrapper, choose Gradle 8.9 under Settings > Build Tools > Gradle.

## Structure
- `data/` Room entity, DAO, database, repositories, JSON for lyrics
- `format/` LRC / Enhanced LRC parser and the LRC, ELARC, TTML exporters
- `audio/` MediaCodec waveform extractor
- `viewmodel/` Home, Import and Editor (player, tap sync, split / merge / offset, undo, autosave)
- `ui/` theme, shared components, Home / Import / Studio / Export / Settings screens
