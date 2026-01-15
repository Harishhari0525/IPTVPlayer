# IPTVPlayer

A lightweight IPTV player written in Kotlin. This project provides a simple Android client for playing IPTV streams (M3U playlists, HTTP/HLS streams) with an emphasis on ease of use, low latency, and a clean UI.

**Completely AD Free experience to stream the publicly available streaming content.**  
**NOTE - This app doesn't host any content and its just streams the HLS / m3u content available over internet.**

## Features
- Play IPTV streams (M3U / HLS)
- Add and manage playlists (local or remote)
- Channel grouping and favorites
- Basic EPG (Electronic Program Guide) support (if available)
- Picture-in-picture support (if implemented)
- Lightweight, Kotlin-first Android codebase

## Technology
- Language: Kotlin (100%)
- Platform: Android (Gradle / Android Studio)
- Media playback: ExoPlayer

## Prerequisites
- Java Development Kit (JDK) 11+ (or the version required by the project)
- Android Studio
- Android SDK and emulator or a physical Android device
- Gradle (wrapper included: use ./gradlew)

## Quick Start

1. Clone the repository
```bash
git clone https://github.com/Harishhari0525/IPTVPlayer.git
cd IPTVPlayer
```

2. Open the project in Android Studio
- File -> Open -> select the repository root
- Android Studio will sync Gradle and index the project

3. Build and run
- Select an emulator or connected device and run from Android Studio
- Or use the Gradle wrapper:
```bash
./gradlew assembleDebug
# then install on a device:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Usage
- Add an M3U playlist(consider raw files if imported from Github IPTV Org):
  - In the app UI: Menu -> Add Playlist -> enter a remote playlist URL (http/https) or pick a local file.
  - Example M3U entry:
    ```
    #EXTINF:-1 tvg-id="ChannelID" tvg-name="Channel Name",Channel Display Name
    https://example.com/stream/playlist.m3u8
    ```
- Play a channel by tapping it in the channel list.
- Use playback controls to pause, seek (if supported), and change quality (if supported).

## Configuration
- If the app requires runtime permissions (INTERNET, READ_EXTERNAL_STORAGE), Android Studio will prompt for them.
- EPG support usually requires a separate XMLTV or JSON EPG URL. Configure this in the app Settings if present.

## Project Structure (example)
- app/ - Android app module
- app/src/main/java/... - Kotlin source code
- app/src/main/res/ - Resources (layouts, drawables, strings)
- README.md - This file

Adjust these details to reflect the actual repository layout.

## Testing
- If unit or instrumentation tests are included, run:
```bash
./gradlew test           # local unit tests
./gradlew connectedAndroidTest   # instrumentation tests on device/emulator
```

## Contributing
Contributions are welcome. Suggested workflow:
1. Fork the repository
2. Create a new branch: `git checkout -b feat/your-feature`
3. Commit changes with clear messages
4. Open a pull request to the main repository

Please include:
- A clear description of the change
- Steps to reproduce (if a bug fix)
- Any relevant screenshots or logs

## Issues
If you find bugs or want new features, open an issue on the repository describing:
- Steps to reproduce
- Expected vs actual behavior
- Device / Android version
- Logs (if available)
