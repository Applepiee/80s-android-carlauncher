# VFD Hi-Fi car launcher

A home-screen app for an Android head unit (ATOTO S8L, Android 15, 1280×720 landscape) that looks like the
vacuum-fluorescent display of an 80s hi-fi tuner. Built for a 2003 Toyota MR2 Roadster. Runs on stock firmware:
no root, no system privileges.

## What it does

| Feature | How |
|---|---|
| Source switch | **RADIO / YT MUSIC** tabs at the top. Tapping one starts that app's playback and pauses the other (or opens the app if it isn't running yet). The screen also follows the audio: start Spotify from the steering wheel and it flips to the music view. **Long-press** a tab to pick which app it controls. |
| Radio | Big frequency, preset/manual label, station name and a small analyser. Six station keys: **long-press** to store the current station (frequency + name), tap to recall (uses the radio app's queue if it publishes one, otherwise steps next/previous until the frequency matches). The analysers show the real spectrum of what is playing (Visualizer on the output mix; asks for microphone access, nothing is recorded) and fall back to an animation without permission or signal. SEEK = next/previous, TUNE = fast-forward/rewind (most head-unit radio apps map these to a manual step), MUTE mutes the music stream. |
| YT Music | Title, artist, elapsed/length and a 40-segment progress bar. PREV / PLAY / NEXT, SHUFFLE and REPEAT (sent the way AndroidX media controllers do), LIKE (the app's thumbs-up custom action, if it has one). |
| Next turn | While Google Maps navigates, its turn-by-turn notification feeds the top-right panel (arrow, distance, street, ETA). Tap it to open Maps. Otherwise the panel shows the weather. |
| Weather | [Open-Meteo](https://open-meteo.com) (no key), unit location or a fixed fallback (`WeatherRepository.FALLBACK_LAT/LON`, Brussels). Cached; `OFFLINE` when older than 3 h. |
| YT Music | Header shows the playlist (the app's queue title, else the playlist last started here) and TRACK x/y; a small analyser sits beside the artist. Row: SHUFFLE, REPEAT, LIKE, **PLAYLISTS** (six pinned playlists; tap to play in YouTube Music, long-press a key to set its name and paste a share link: Share › Copy link › PASTE) and **OPEN APP** (opens the MUSIC app). |
| Apps | MAPS, ANDROID AUTO, CAMERA, SETTINGS are auto-detected; **long-press** to reassign (the picker can show hidden system activities, where head units keep the Android Auto receiver). APPS opens an app drawer; **long-press** an app there to pin it to the FAVORITES row at the top (long-press again to unpin). |
| Floating controls | Apps opened from the launcher get the floating VFD bar (home, now playing, prev/play/next, volume, collapsible). Maps (however it is opened from the launcher) and Android Auto also get the CRT overlay: heavy scanlines, RGB phosphor stripes, a curved-glass bezel with colour fringing, deep vignette and a slow rolling refresh band. It is drawn at window alpha 0.8 and not touchable so touches reach the app; it cannot bend the app's own pixels (that would need screen capture), so curvature is suggested by the bezel. Needs "display over other apps", asked the first time you open an app. |
| Volume | Music stream, 20 segments, follows hardware knobs. |
| Clock | Updates on the minute; follows 12/24 h. |
| Session inspector | **Long-press the big display** to dump every media session (actions, metadata, queue, custom actions). Use this on the S8L to see what ATOTO's radio app supports. |

First launch asks for location. Tap the big display ("TAP TO ALLOW MEDIA ACCESS") to give the launcher notification
access: that is what lets it read and control other apps' playback and read Maps' next turn.

## Project layout

```
app/src/main/java/com/retro/vfdlauncher/
  MainActivity.kt              HOME activity, landscape, immersive, Back does nothing
  ui/theme/                    VFD colours, fonts, glow text, line icons
  ui/home/HomeScreen.kt        the 1280×720 layout, scaled to fit any screen
  ui/home/Spectrum.kt          the analysers (real spectrum, animation as fallback)
  data/audio/AudioSpectrum.kt  Visualizer on the output mix -> 14 log bands
  ui/home/HomeViewModel.kt     state + actions
  ui/picker/                   app picker and session inspector overlays
  data/weather/                Open-Meteo client + cache
  data/media/                  media sessions, frequency parsing, presets
  data/audio/                  volume
  data/apps/                   app scan (incl. hidden), button assignments
  data/nav/                    next turn from the Maps notification
  overlay/                     floating control bar + CRT overlay service
app/src/main/res/font/         Share Tech Mono, Orbitron (SIL OFL, see licenses/)
```

No libraries beyond AndroidX/Compose, so cold start stays short after the unit's BGSleep wake.

## Build and run on your PC

> The Android Emulator only runs on Intel/AMD PCs. On a Snapdragon (ARM) Windows PC, test on a phone or tablet over USB
> instead: enable USB debugging, then `adb shell wm size 1280x720` and `adb shell wm density 160` to mimic the head unit
> (`adb shell wm size reset` / `wm density reset` to undo).


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
- Detect the app in front (so the CRT follows Maps even when opened another way); OsmAnd teal-on-black map style.
