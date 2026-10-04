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
        │   │   ├── ads/                        # MODULE D – AdMob hosts + UMP consent
        │   │   │   ├── AdsController.kt        # UMP → MobileAds.init, app-open ad, surface tracking
        │   │   │   ├── AdPolicyBanner.kt       # the single banner host (gated by AdPolicy)
        │   │   │   └── AdLaunchOrigins.kt      # launch Intent → AdLaunchOrigin
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
        │       └── font/                        # ← drop your .ttf files here (see §10)
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

## 5. Module D — ads (AdMob + UMP consent)

**Every eligibility decision lives in one pure file**: `clock-engine/…/AdPolicy.kt` (JVM-tested in
`AdPolicyTest.kt`); the app layer only translates routes/tabs/intents into its plain inputs.

* **Test IDs only** (Google's official sample account): App ID `…~3347511713` in the manifest
  `com.google.android.gms.ads.APPLICATION_ID` meta-data, anchored-adaptive banner `…/9214589741`,
  app-open `…/9257395921` — all pinned as constants in `AdPolicy` and asserted verbatim by tests.
* **Ad-free forever** on: alarm ringing screen, desk clock, chess clock, alarm editor, OEM guide,
  widgets (+ widget studio). On those surfaces no `AdView` is even constructed.
* **App-open ad never shows when the open came from an alarm** (full-screen intent, alarm
  notification, status-bar alarm icon) — `EXTRA_FROM_ALARM` → `AdLaunchOrigin.ALARM` → veto in
  `AdPolicy.allowsAppOpenAd()`.
* **UMP consent before initialization**: `requestConsentInfoUpdate` →
  `loadAndShowConsentFormIfRequired` → only then `MobileAds.initialize()` (driven from
  MainActivity so a consent form can never appear over the ringing screen).
* Full details & the production-swap checklist: **[docs/ADS.md](docs/ADS.md)**.

## 6. Reliability on OEM ROMs & custom alarm sounds

**OEM autostart / power guide** (`core/util/OemPowerSettings.kt`, `presentation/settings/oem/`)
Detects the vendor from `Build.MANUFACTURER`/`BRAND` (Xiaomi-MIUI/HyperOS, Huawei-EMUI, Samsung
One UI, OPPO-ColorOS, OnePlus, vivo, ASUS, LeEco) and renders a checklist with direct deep links
into each vendor screen (Autostart, Battery saver "No restrictions", Protected apps, Sleeping
apps…). Component names change between ROM versions, so each step probes several historical
candidates with `queryIntentActivities` and only shows a button when one resolves — otherwise the
written instructions remain. Progress is ticked off and stored in DataStore. The matching
`<package>` entries are declared in `<queries>`, without which Android 11+ visibility rules would
hide every vendor Activity.

**Direct Boot & custom ringtones** (`data/ringtone/`)
Imports are copied into **device-protected** `files/ringtones/` (migrated automatically from the
old credential-protected folder), because an alarm can fire before the first unlock after a
reboot. `resolvePlayableAlarmUri(alarm)` is the single entry point used by the player:
user sound (if readable *and* reachable in the current boot state) -> system default (only after
unlock, it is a MediaStore Uri) -> `res/raw/fallback_alarm.wav` bundled in the APK, which is the
only asset guaranteed readable during Direct Boot. Silence is an explicit sentinel
(`AlarmSound.SILENT_URI`) so it is never confused with "no choice yet". The decision table lives
in the Android-free `AlarmSoundPolicy` and is fully unit-tested.

**Custom ringtones** (`data/ringtone/RingtoneRepository.kt`)
`ActivityResultContracts.OpenDocument()` picks an audio file; the repository **copies it into
`filesDir/ringtones/`** (same approach as imported fonts) and the alarm stores a stable `file://`
Uri. This survives revoked Uri grants, moved/deleted originals, unmounted SD cards and Direct
Boot, where a credential-protected provider cannot be resolved at all. Imports are validated by
MIME/extension (mp3, wav, ogg, m4a, aac, flac, opus) **and magic-number content sniffing**, and
capped at 25 MB; file names keep Unicode letters but lose path segments, control characters and
reserved symbols, and are de-duplicated. Reads/deletes are confined to the ringtones folder via
canonical-path checks. The SAF read grant is taken persistably only while copying, then released.
`isPlayable()` warns in the editor when a previously chosen sound has disappeared.
All of these rules are pure functions in `RingtoneFileRules` with JVM unit tests.

## 7. Localization & RTL

All user-facing copy lives in `res/values/strings.xml`, with a hand-written Arabic translation in
`res/values-ar/strings.xml` (key parity is enforced; only genuinely untranslatable values — format
patterns, separators and brand names — carry `translatable="false"`).

Two patterns keep the logic locale-agnostic:
* `TopLevelDestination` and `OemPowerSettings.Vendor` hold `@StringRes` ids instead of text;
* `AlarmReliabilityStatus.issues` returns a list of string **ids**, so the data class stays a pure
  JVM type and remains unit-testable while the UI renders it in the current locale.

RTL: `supportsRtl="true"`, no `Left`/`Right`/absolute modifiers anywhere, and the slide-to-dismiss
gesture on the ringing screen mirrors its direction from `LocalLayoutDirection`, so the swipe
follows the arrow in both LTR and RTL.

## 8. Ringtone preview

`RingtonePreviewPlayer` is a separate `MediaPlayer`-based singleton — never `AlarmSoundPlayer`,
which is owned by `AlarmService` and may be mid-ramp on a live alarm. It plays at most 8 seconds,
stops itself on completion or timeout, stops when another sound is selected, when the alarm is
saved, and when the editor leaves the composition (`DisposableEffect`), and refuses to start at all
while `AlarmSessionManager.isRinging` is true.

## 9. Permissions

`SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`,
`RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_SPECIAL_USE`, `INTERNET` (weather, optional).

## 10. Build & run

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
