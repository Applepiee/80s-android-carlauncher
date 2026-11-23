# Roadster Launcher

An Android home-screen app for car head units that looks like the glowing vacuum-fluorescent display (VFD) of an
80s hi-fi tuner: teal and amber segments, scanlines, a spectrum analyser and chunky hardware-style keys.

It's built for an **ATOTO S8L** head unit (Android 15, 1280×720 landscape) in a **2003 Toyota MR2 Roadster**, and
runs on stock firmware, with no root and no system privileges. It's written in Kotlin with Jetpack Compose and uses
nothing beyond AndroidX, so it is ready right after the unit wakes from sleep.

## Features

### Home screen

- **Source switch.** The **RADIO** and **YT MUSIC** tabs start that app's playback and pause the other one. If the
  app isn't running yet, the tab opens it. The screen also follows the audio, so if music starts from the steering
  wheel it switches to the music view. Long-press a tab to choose which app it controls.
- **Radio (internet radio, the default).** The launcher plays internet radio itself, over the unit's SIM or Wi-Fi.
  - The station name fills the big segment display, with country, genre and bitrate top right, and the song now
    playing (when the stream sends it) plus the spectrum underneath. The NET chip in the top bar shows the stream
    bitrate, BUFFERING, or NO NET.
  - **STATIONS** opens the station search: type a name, tap COUNTRY to cycle (Belgium first) and pick a genre. Tap a
    result to play it. Stations come from the free [Radio Browser](https://www.radio-browser.info) directory.
  - Six preset keys: hold one to save the station that's playing, tap to play it. PREV and NEXT step through them,
    and so do the floating rail and the steering-wheel keys.
  - STOP stops the stream. Without data it shows NO SIGNAL, retries every 5 seconds, and the key becomes RETRY.
- **Radio (the unit's own FM radio).** Long-press RADIO and choose the unit's radio app to use it instead; the
  INTERNET RADIO key in that list switches back. It shows the frequency in big segment digits, the station name,
  six preset keys, SEEK, TUNE and MUTE.
  - Long-press a preset key to store the current station. Tap it to tune back to that station.
  - The launcher controls the unit's own radio app through Android's media session. What works therefore depends
    on what that app shares. See [Head-unit radio](#head-unit-radio) below.
- **YT Music.**
  - Display: the playlist name, title, artist and elapsed/total time, plus a 40-segment progress bar. Tap or drag
    the bar to jump to any point in the song.
  - Big keys: PREV / PLAY / NEXT.
  - Small keys: SHUFFLE, REPEAT, LIKE, PLAYLISTS and OPEN APP.
- **Playlists.** A panel with six pinned YouTube Music playlists. To set a key:
  1. Long-press the key and give it a name.
  2. In YouTube Music, open the playlist and choose **Share › Copy link**.
  3. Press **PASTE** in the launcher.
  
  Tapping the key then starts that playlist in YouTube Music.
- **Spectrum analyser.** It shows the real spectrum of whatever is playing, using Android's audio `Visualizer`.
  Nothing is recorded or stored. Without permission or without a signal, it falls back to an animation. The bars
  fall back down slowly, so on pause they sink to empty.
- **Next turn.** While Google Maps is navigating, the top-right card shows the next turn from Maps' navigation
  notification: the arrow, distance, street and arrival time. Tap the card to open Maps.
- **TOP-DOWN SCORE.** When you're not navigating, the same card rates from 0 to 10 whether the roof can go down. It
  draws the MR2 with the roof up or down, with animated rain, sun and wind. Tap the card to see the plain weather
  instead.
- **App keys.**
  - MAPS, ANDROID AUTO and CAMERA are detected automatically. Long-press a key to choose a different app.
  - SETTINGS opens the launcher's own settings (permissions, CRT effect, night mode). Android's settings are in the
    app drawer.
    The picker can also show hidden system activities, which is where head units often keep the Android Auto app.
  - **APPS** opens the app drawer. Long-press an app there to pin it to the favourites row at the top.
- **Volume and clock.** Volume is a 20-segment media-volume bar that follows the unit's knob. The clock follows
  the 12/24-hour setting.

### TOP-DOWN SCORE rules

The score starts at 10, uses the [Open-Meteo](https://open-meteo.com) hourly and 15-minute forecast, and subtracts
points:

| What | Penalty |
|---|---|
| Raining now | score capped at 1 |
| Rain chance in the next 2 h | 60%+ −6 · 40–60% −3 · 20–40% −1 |
| "Feels like" temperature, now and next hour | 18–26 °C ideal · 14–18 −2 · 10–14 −4 · below 10 −6 · above 30 −2 |
| Wind gusts | over 35 km/h −1 · over 50 km/h −2 |

- **Roof drawing:** the roof is drawn down from a score of 5.
- **Colours:** teal from 7, amber for 4–6, red below 4.
- **Verdicts:** the verdict looks up to 3 hours ahead, for example `ROOF UP · RAIN 17:40`, `TOP DOWN TILL 17:40`,
  `TOP DOWN, HEATER ON` or `GO TOPLESS!`.
- **Middle case:** if it's warm (feels like 18 °C or more) with only light rain and no storm, the verdict is
  `SPEED KEEPS YOU DRY`. In light rain the airflow over the windscreen carries the drops over an open cabin.

The logic lives in `data/weather/RoofMeter.kt`.

### Floating controls over other apps

Apps opened from the launcher get a slim VFD control rail on the left edge, with home, previous / play / next and
volume. It stays clear of Google Maps' own buttons and folds away to a single HOME key.

Maps and Android Auto also get a CRT overlay: scanlines, RGB phosphor stripes, a curved-glass bezel with colour
fringing and a slow rolling refresh band. The overlay is slightly see-through and ignores touches, so taps still
reach the app underneath.

### Session inspector

Long-press the big display to list every media session on the unit: its actions, metadata, queue and custom
actions. This is the tool for finding out what a head unit's radio app exposes.

## Permissions

| Permission | Why |
|---|---|
| Notification access | Read and control other apps' playback, and read Maps' next turn |
| Location (coarse) | Local weather. Falls back to a fixed location (Brussels) without it |
| Display over other apps | The floating control rail and the CRT overlay |
| Microphone (`RECORD_AUDIO`) | Android requires it for the audio `Visualizer`; no audio is recorded |
| Query all packages | Find the radio, Android Auto and camera apps, including hidden ones |

On first launch, tap the big display ("TAP TO ALLOW MEDIA ACCESS") to grant notification access.

## Install on a head unit

Every push builds a debug APK in GitHub Actions (**Actions › Build APK › latest run › Artifacts**).

- **USB stick:** copy the APK to a stick and open it with the unit's file manager. Allow installing from unknown
  sources, then choose Roadster Launcher as the Home app (**Settings › Apps › Default apps › Home app**).
- **Over the air (no USB):** every push publishes a release APK, signed with a fixed release key, on this
  repo's [Releases](https://github.com/Applepiee/80s-android-carlauncher/releases) page as version
  0.1.*run number*. In the launcher, **APPS › UPDATE** downloads the newest version over Wi-Fi or mobile data and
  opens Android's installer; confirm with **Update**. The launcher also checks quietly every 6 hours. The first
  time, Android asks you to allow "install unknown apps" for Roadster Launcher.
  - First install: open the Releases page in the unit's browser and install `vfd-launcher.apk` from the latest
    release.
  - Updates only install over a build signed with the same key. A debug build from Android Studio has to be
    uninstalled once before the release builds can replace it.
  - Publishing needs three repository secrets (**Settings › Secrets and variables › Actions**):
    `SIGNING_KEYSTORE_B64`, `SIGNING_PASSWORD` (used for both the keystore and the key) and `SIGNING_KEY_ALIAS`. The keystore
    itself is never committed. Without these secrets the workflow skips publishing.

## Build it yourself

1. Install [Android Studio](https://developer.android.com/studio) and open this folder. Gradle downloads Gradle
   8.11.1 and the Android 15 (API 35) SDK on the first sync.
2. Press **Run ▶**, using either an emulator or a device:
   - **Emulator:** create one with a 1280×720 screen at density 160 (mdpi), API 35, in landscape.
   - **Phone or tablet:** the Android Emulator doesn't run on ARM Windows PCs. On those, use a phone over USB and
     make it behave like the head unit with `adb shell wm size 1280x720` and `adb shell wm density 160`. Undo this
     with `wm size reset` and `wm density reset`.
3. Press Home and choose **Roadster Launcher › Always**.

From the command line, `./gradlew assembleDebug testDebugUnitTest` builds
`app/build/outputs/apk/debug/app-debug.apk` and runs the unit tests.

## Head-unit radio

Android gives apps no direct access to the FM tuner. The launcher mirrors the unit's own radio app through its media
session, the same way it mirrors YouTube Music. Here's what that means for each part:

- **SEEK and play/pause** fall back to standard media keys if the radio app shares nothing.
- **The frequency display and station name** only appear if the radio app publishes them in its "now playing" text.
- **Presets** step through stations until the frequency matches. They jump straight there if the radio app shares
  its own preset list.
- **TUNE** sends fast-forward and rewind, which many head-unit radio apps treat as a manual tuning step.
- **Volume, MUTE and the spectrum** may not affect the radio if the unit routes FM audio outside Android's mixer.

If something doesn't work on your unit, the session inspector shows what its radio app exposes.

## Project layout

```
app/src/main/java/com/retro/vfdlauncher/
  MainActivity.kt          HOME activity (landscape, immersive, Back does nothing)
  ui/theme/                VFD colours, fonts, glow text, line icons
  ui/home/                 the 1280×720 layout (scaled to any screen), view model, analysers
  ui/picker/               app picker, app drawer, session inspector
  data/media/              media sessions, frequency parsing, presets, playlists
  data/weather/            Open-Meteo client, cache, TOP-DOWN score
  data/audio/              volume and the audio Visualizer
  data/apps/               app scan (including hidden activities) and key assignments
  data/nav/                next turn from the Maps notification
  overlay/                 floating control rail and CRT overlay service
app/src/main/res/font/     Share Tech Mono and Orbitron
```

## Credits

- Fonts: [Share Tech Mono](https://fonts.google.com/specimen/Share+Tech+Mono) and
  [Orbitron](https://fonts.google.com/specimen/Orbitron), both under the SIL Open Font License 1.1 (see
  [`licenses/`](licenses)).
- Weather data: [Open-Meteo](https://open-meteo.com), free for non-commercial use, no API key.
- Not affiliated with ATOTO, Toyota, Google or YouTube. Product names are used only to describe compatibility.
