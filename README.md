# Digital Clock Pro: World & Alarm

A modern, battery-efficient and highly customizable Android clock app — home-screen widget studio,
world-clock hub and an "unstoppable" alarm engine.

**Stack:** 100% Kotlin · Jetpack Compose + Glance/RemoteViews · MVVM + Clean Architecture ·
Dagger-Hilt · Room + DataStore · AlarmManager (exact) + ForegroundService + WorkManager ·
Material 3 / Material You · **minSdk 26 · targetSdk 35**

---

## 1. Project structure

```
DigitalClockPro/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml           # version catalog (AGP 8.6, Kotlin 2.0, Compose BOM)
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── assets/cities_extra.tsv          # supplementary offline city catalogue
        │   ├── java/com/digitalclockpro/
        │   │   ├── DigitalClockApp.kt           # @HiltAndroidApp + WorkManager config
        │   │   │
        │   │   ├── core/                        # cross-cutting helpers
        │   │   │   ├── ui/theme/                # Color.kt, Theme.kt (Material You), Type.kt
        │   │   │   └── util/                    # AppIntents.kt, TimeFormatters.kt
        │   │   │
        │   │   ├── domain/                      # ← pure Kotlin, no Android deps
        │   │   │   ├── model/                   # Alarm, SavedCity, WidgetConfig, UserPreferences…
        │   │   │   ├── repository/              # Repositories.kt (interfaces)
        │   │   │   ├── scheduler/AlarmPlanner.kt
        │   │   │   └── usecase/                 # AlarmUseCases.kt, WorldClockUseCases.kt
        │   │   │
        │   │   ├── data/                        # ← implementations
        │   │   │   ├── local/                   # Room: AppDatabase, dao/, entity/ (+mappers)
        │   │   │   ├── prefs/                   # DataStore: user prefs + per-widget configs
        │   │   │   ├── repository/              # *RepositoryImpl
        │   │   │   ├── timezone/CityCatalog.kt  # offline IANA search index
        │   │   │   └── weather/WeatherRepositoryImpl.kt   # Open-Meteo, key-less, 30-min cache
        │   │   │
        │   │   ├── alarm/                       # MODULE C – alarm engine
        │   │   │   ├── AlarmScheduler.kt        # setAlarmClock / setExactAndAllowWhileIdle
        │   │   │   ├── AlarmReceiver.kt         # fire / snooze / dismiss
        │   │   │   ├── AlarmService.kt          # foreground service: audio, vibration, wakelock
        │   │   │   ├── AlarmNotifications.kt    # channels + full-screen intent
        │   │   │   ├── AlarmSoundPlayer.kt      # ExoPlayer + volume ramp
        │   │   │   ├── VibrationController.kt   # Continuous / Pulse / Heartbeat / SOS
        │   │   │   ├── BootReceiver.kt          # BOOT_COMPLETED restore
        │   │   │   └── challenge/               # MathChallengeGenerator, ShakeDetector
        │   │   │
        │   │   ├── widget/                      # MODULE A – home-screen widgets
        │   │   │   ├── ClockWidgetProvider.kt   # main resizable clock widget
        │   │   │   ├── WorldClockWidgetProvider.kt
        │   │   │   ├── ClockWidgetRenderer.kt   # Canvas → Bitmap (fonts, glow, gradients)
        │   │   │   ├── FontCatalog.kt           # 30+ bundled fonts + .ttf/.otf import
        │   │   │   ├── WidgetTapActions.kt      # per-region PendingIntents
        │   │   │   ├── WidgetUpdater.kt / WidgetTickController.kt / WidgetTickReceiver.kt
        │   │   │   └── glance/GlanceClockWidget.kt
        │   │   │
        │   │   ├── presentation/
        │   │   │   ├── MainActivity.kt, DigitalClockProApp.kt   # nav + bottom bar
        │   │   │   ├── dashboard/  world/  alarms/  settings/
        │   │   │   ├── studio/                  # Widget Customizer Studio (WYSIWYG)
        │   │   │   ├── ringing/                 # full-screen alarm UI + challenges
        │   │   │   └── common/                  # ClockTicker, PermissionGate
        │   │   │
        │   │   └── di/                          # AppModule, DatabaseModule, RepositoryModule
        │   │
        │   └── res/
        │       ├── layout/widget_clock.xml, widget_world_clock.xml
        │       ├── xml/widget_clock_info.xml, widget_world_info.xml, widget_glance_info.xml
        │       ├── drawable/   values/   values-night/   mipmap-anydpi-v26/
        │       └── font/                        # ← drop your .ttf files here (see §6)
        └── test/java/com/digitalclockpro/domain/   # JUnit: next-trigger, timezone, challenges
```

---

## 2. Module A — widget engine

| Concern | Implementation |
|---|---|
| Sizes 2x1 … 5x2, resizable | `widget_clock_info.xml` (`targetCellWidth/Height`, `resizeMode`, min/max resize) |
| No text clipping | `ClockWidgetRenderer.fittingTextSize()` shrinks until the string fits the real `OPTION_APPWIDGET_MIN_WIDTH/HEIGHT`; re-rendered on `onAppWidgetOptionsChanged` |
| 7 themes | `ClockStyle` + `WidgetStudioViewModel.applyPreset()` + `widget_bg_*.xml` |
| Custom fonts / glow / gradient / shadow | Canvas-rendered bitmap (RemoteViews `TextView` cannot do these) |
| Battery, next alarm, weather | `ClockWidgetProvider.batteryPercent/nextAlarmLabel/weatherLabel` |
| Tap actions | 4 transparent overlay views → `WidgetTapActions.pendingIntent()` |
| **Battery policy** | `updatePeriodMillis="0"` (never wakes the device) + dynamic `ACTION_TIME_TICK` while the screen is **on only**; the receiver returns immediately on `ACTION_SCREEN_OFF`. No wake-locks, no polling, no repeating alarms. |

## 3. Module B — world clock

* `CityCatalog` merges every `ZoneId.getAvailableZoneIds()` entry with `assets/cities_extra.tsv`
  → fully offline search over 10k+ names, prefix matches ranked first, debounced 180 ms.
* `SavedCity` computes `utcOffsetLabel()`, `relativeLabel()` ("6 hr ahead of you"),
  `dayLabel()` ("Yesterday/Today/Tomorrow") and `isDaytime()` for the sun/moon + gradient.
* **Time-travel slider**: `WorldClockViewModel.travelOffsetMinutes` (±24 h, 15-min steps) is added
  to "now" and every card re-renders from that single reference instant.
* `WorldClockWidgetProvider` = dual-zone / multi-city widget (up to 4 rows).

## 4. Module C — alarm engine

```
AlarmScheduler.schedule(alarm)
   └─ canScheduleExactAlarms() ? setAlarmClock(…)                 // never deferred, shows in status bar
                               : setExactAndAllowWhileIdle(…)     // Doze-proof fallback
   └─ SecurityException        → setWindow(RTC_WAKEUP, …, 5 min)  // last-resort, still rings

AlarmReceiver (ACTION_ALARM_FIRE)
   └─ startForegroundService(AlarmService)        // allowed from an exact-alarm broadcast
         ├─ ServiceCompat.startForeground(mediaPlayback|specialUse)
         ├─ AlarmSoundPlayer  → ExoPlayer, USAGE_ALARM, quadratic volume ramp 0→target (10-60 s)
         ├─ VibrationController → Continuous / Pulse / Heartbeat / SOS
         ├─ AlarmNotifications.buildRingingNotification() → setFullScreenIntent(…, true)
         ├─ PARTIAL_WAKE_LOCK (released on stop)
         └─ auto-silence after 15 min → snooze
BootReceiver: BOOT_COMPLETED | LOCKED_BOOT_COMPLETED | MY_PACKAGE_REPLACED | TIME_SET |
              TIMEZONE_CHANGED | SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED → reschedule all
```

**Edge cases handled**

* Doze / App Standby → `setAlarmClock` + `setExactAndAllowWhileIdle`.
* Android 12+ exact-alarm permission revoked → graceful fallback + in-app banner
  (`AlarmPermissionBanner`) deep-linking to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.
* Android 13+ `POST_NOTIFICATIONS` → runtime request before an alarm can be shown.
* Reboot / app update → pending intents re-armed by `BootReceiver`.
* Task swipe / activity kill → ringing lives in the foreground service, not the Activity.
* Back button on the ringing screen is a no-op; dismissal requires the configured challenge.
* DST & timezone changes → next trigger always recomputed from `LocalDateTime` + `ZoneId`.
* Snooze budget (`maxSnoozeCount`) enforced in `SnoozeAlarmUseCase`.

## 5. Permissions

`SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`,
`RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_SPECIAL_USE`, `INTERNET` (weather, optional).

## 6. Build & run

```bash
# Android Studio Ladybug+ : File ▸ Open ▸ this folder, then Run ▸ app
./gradlew :app:assembleDebug      # requires JDK 17 + Android SDK 35
./gradlew :app:testDebugUnitTest  # domain unit tests
```

**Fonts.** `FontCatalog` resolves each face with `getIdentifier()`, so the project builds with an
empty `res/font/`. Drop a `.ttf` named exactly like `FontSpec.resName` (e.g. `dseg7_classic.ttf`,
`orbitron.ttf`) into `app/src/main/res/font/` and it is picked up automatically; anything missing
falls back to its system family. Users can also import their own `.ttf`/`.otf` at runtime
(`FontCatalog.importFont`).

**Launcher icon.** Adaptive icon XML is included; add raster `ic_launcher.png` variants under
`mipmap-*dpi/` if you want legacy-density assets.
