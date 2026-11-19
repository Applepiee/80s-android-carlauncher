# VFD Hi-Fi car launcher

A home-screen app for an Android head unit (ATOTO S8L, Android 15, 1280×720 landscape) that looks like the
vacuum-fluorescent display of an 80s hi-fi tuner. Built for a 2003 Toyota MR2 Roadster. Runs on stock firmware:
no root, no system privileges.

## What works (phase 1)

| Feature | How |
|---|---|
| Clock and date | Updates on the minute; follows the system 12/24 h setting. |
| Weather | [Open-Meteo](https://open-meteo.com) (no key). Uses the unit's last known location, else a fixed fallback (`WeatherRepository.FALLBACK_LAT/LON`, Brussels). Cached, so it shows instantly after wake and offline; marked `OFFLINE` when older than 3 h. |
| Now playing | `MediaSessionManager.getActiveSessions` via a notification listener. Mirrors ATOTO's radio app, Spotify, etc. A frequency in the metadata (`101.1`, `94,5`, `1215 kHz`) goes in the big digits; other sources show elapsed time. |
| Play/pause | Transport controls of the active session, or a media key if there is none. |
| Spectrum | 28-bar animation that runs while playing and freezes on pause (not real audio yet). |
| Volume | `AudioManager` music stream, 20-segment bar, follows hardware knobs. |
| App buttons | ANDROID AUTO, RADIO, MUSIC, CAMERA, SETTINGS. Auto-detected by package/label; **long-press** any button to pick an app. The picker can list hidden system activities, which is where head units keep the Android Auto receiver and camera. |
| Presets | Long-press P1–P6 to store the current frequency. Tap: if the radio session exposes a queue, jump to that entry; otherwise step next/previous until the frequency matches. |
| Session inspector | **Long-press the tuner panel** to dump every media session: actions, metadata keys, queue, custom actions. Use this on the S8L to see what ATOTO's radio app supports. |

First launch asks for location. Tap the tuner panel ("TAP TO ALLOW MEDIA ACCESS") to enable notification access
for VFD Launcher; that is what lets it read and control other apps' playback.

## Project layout

```
app/src/main/java/com/retro/vfdlauncher/
  MainActivity.kt              HOME activity, landscape, immersive, Back does nothing
  ui/theme/                    VFD colours, fonts, glow text, line icons
  ui/home/HomeScreen.kt        the 1280×720 layout, scaled to fit any screen
  ui/home/Spectrum.kt          the analyser
  ui/home/HomeViewModel.kt     state + actions
  ui/picker/                   app picker and session inspector overlays
  data/weather/                Open-Meteo client + cache
  data/media/                  media sessions, frequency parsing, presets
  data/audio/                  volume
  data/apps/                   app scan (incl. hidden), button assignments
app/src/main/res/font/         Share Tech Mono, Orbitron (SIL OFL, see licenses/)
```

No libraries beyond AndroidX/Compose, so cold start stays short after the unit's BGSleep wake.

## Build and run on your PC

1. Install [Android Studio](https://developer.android.com/studio) (on a Snapdragon/ARM64 Windows PC, use the ARM64 build). Use the standard setup.
2. **File › Open** this folder. Let Gradle sync (it downloads Gradle 8.11.1 and the SDK 35 platform the first time).
3. Create the emulator: **Device Manager › + › Create Virtual Device › New Hardware Profile**:
   - Name `Head unit 1280x720`, screen size 7", resolution **1280 × 720**, density **mdpi (160)**.
   - Next, pick system image **API 35 (Android 15)**, Google APIs: `arm64-v8a` on an ARM PC, `x86_64` on Intel/AMD.
   - Startup orientation **Landscape**. Finish.
4. Press **Run ▶** with that emulator selected.
5. To make it the home screen: press the emulator's Home button and choose **VFD Launcher › Always**
   (or Settings › Apps › Default apps › Home app).
6. To test now-playing in the emulator, start any media app (e.g. play a video in Chrome or a music app), then
   allow notification access when the tuner panel asks.

Command line: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

## Install on the S8L (USB stick)

Every push builds an APK in GitHub Actions (**Actions › Build APK › latest run › Artifacts › vfd-launcher-debug**).
Copy `app-debug.apk` to a USB stick, open it with the unit's file manager, allow installing from unknown sources,
then set VFD Launcher as the Home app.

## Next

- Night mode (dimmer VFD after sunset), real audio Visualizer for the spectrum (needs RECORD_AUDIO).
- Phase 2: optional CRT overlay (scanlines/vignette, alpha ≤ 0.8 so touches pass through) while maps or Android Auto
  are in front; OsmAnd teal-on-black map style.
