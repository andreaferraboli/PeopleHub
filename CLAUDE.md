# PeopleHub — Engineering Guide

PeopleHub is a **100% offline** Android app that acts as a personal relationship hub: it tracks the
people in your life, their birthdays, when you last saw them ("check-ins"), their interests, and
significant personal events with elapsed/remaining day counters.

There is **no networking** anywhere in the app — no HTTP client, no analytics, nothing leaves the
device. All data lives in a local Room database and DataStore.

## Stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.1, coroutines, Flow |
| UI | Jetpack Compose + Material 3 (+ optional Dynamic Color) |
| Architecture | Clean Architecture (Presentation / Domain / Data) |
| Database | Room (FTS4) with a migration strategy |
| DI | Hilt |
| Navigation | Navigation Compose, type-safe (Kotlin Serialization routes) |
| Background | WorkManager (CoroutineWorker) + AlarmManager (exact birthday alarm) |
| Notifications | NotificationCompat with separate channels |
| Widgets | Glance |
| I/O | kotlinx.serialization JSON + hand-written CSV parsing |
| Images | Coil 3 (local files in internal storage) |
| Tests | JUnit5 + MockK + Turbine |
| Quality | Ktlint + Detekt, `allWarningsAsErrors = true` |

`minSdk 26`, `targetSdk/compileSdk 35`.

## Module map

```
:app                  Hilt app, MainActivity, type-safe NavHost, bottom nav, Dashboard + Settings,
                      WorkManager workers, AlarmManager scheduler, BroadcastReceivers, app DI (Clock)
:core:domain   (JVM)  Models, repository interfaces, use cases, pure date math, deep-link constants
:core:database        Room entities/DAOs/migrations, repository impls, mappers, DataStore settings, DI
:core:ui              Compose theme (Midnight Gold), design-system components, UiState, RelativeTime
:core:notifications   Notification channels + PeopleHubNotifier + action constants
:core:dataio   (JVM)  JSON (kotlinx.serialization) + CSV import/export, DTOs, mappers
:feature:people       People directory (FTS search/filter/sort), tabbed detail, add/edit, JSON import
:feature:birthdays    Year/Month/List calendar views, CSV/JSON import + export
:feature:events       Events list with filters, detail, add/edit, pin-to-widget
:feature:widget       Three Glance widgets (birthdays, urgent check-ins, pinned event) + updater
```

Dependency direction: `feature:* -> core:domain, core:ui (+ dataio where needed)`;
`core:database & core:notifications -> core:domain`; `app -> everything`. Domain is pure JVM and
has **no Android dependencies**.

## Architecture rules

- **Presentation**: each ViewModel exposes a `StateFlow<UiState<T>>`. `UiState` is the sealed
  interface in `:core:ui` with `Loading / Success(data) / Error(message) / Empty`; every screen
  renders all four cases (via `UiStateContent`). ViewModels use only `viewModelScope`. ViewModels
  import no `android.*` types (only `androidx.lifecycle`, `java.time.Clock`, and the domain).
- **Domain**: one use case per significant action (`operator fun invoke`), repository interfaces,
  models. No framework dependencies. "Now" is injected as `java.time.Clock` for testability.
- **Data**: repository implementations map Room entities <-> domain models; DAOs return `Flow<T>`
  for reads and `suspend fun` for writes. Room types never cross the repository boundary.
- Errors are surfaced via `kotlin.Result` / sealed `UiState.Error`, never uncaught exceptions.
- No hardcoded user-facing strings — everything lives in a module `strings.xml`.

## Design system — "Midnight Gold"

A Neo-Luxury dark-first identity (transcribed from `stitch_peoplehub_personal_relationship_manager/`):
onyx background `#131313`, champagne-gold primary `#f2ca50`, editorial serif (Bodoni Moda) for
display/headline and a clean sans (Manrope) for body, glassmorphic panels, and a gold "fade"
divider. Because the app ships no fonts (offline), the families resolve to the platform serif /
sans-serif; drop real `bodoni_moda` / `manrope` into `core:ui/res/font` and repoint
`PeopleHubFonts` to adopt the exact faces. Dynamic Color is supported on Android 12+ but **off by
default** so the gold-on-onyx brand is preserved (`PeopleHubTheme(dynamicColor = true)` to opt in).

Reusable components live in `core:ui/components`: `GlassPanel`, `GoldDivider`, `SectionHeader`,
`CapsLabel`, `PrimaryGoldButton`, `GhostButton`, `TagChip`, `CategoryChip`, `PersonAvatar`,
`DayCountDisplay`, `CheckInStatusBadge`, `PeopleHubTopBar`, and the `LoadingView/EmptyView/ErrorView`
state views.

## Background work

- **Check-in reminders**: a daily `PeriodicWorkRequest` (`CheckInReminderWorker`) around 09:00
  (`setRequiresBatteryNotLow(false)`, initial delay computed to the target hour) notifies about
  people past their critical threshold.
- **Birthday reminders**: a daily exact alarm (`BirthdayAlarmScheduler`, `setExactAndAllowWhileIdle`
  with an inexact fallback when the Android 12+ exact-alarm permission is denied) fires
  `BirthdayAlarmReceiver`, which enqueues a one-off `BirthdayReminderWorker` and re-arms tomorrow's
  alarm. `BootReceiver` re-schedules everything after a reboot.
- **Widgets**: `WidgetUpdateWorker` refreshes all Glance widgets every 6 hours; `updateWidgetsNow()`
  triggers an immediate refresh after a check-in.

WorkManager uses the Hilt worker factory (the default initializer is disabled in the manifest and
`PeopleHubApplication` implements `Configuration.Provider`).

## Build & run

The project targets **JDK 17–21**. The machine default JDK is too new for the bundled Gradle, so set
`JAVA_HOME` to the Android Studio runtime for command-line builds:

```bash
export JAVA_HOME='/c/Program Files/Android/Android Studio/jbr'   # Git Bash on Windows
./gradlew :app:assembleDebug          # build the debug APK
./gradlew test                        # JVM unit tests (JUnit5 + MockK + Turbine)
./gradlew :core:domain:test           # domain tests only
./gradlew ktlintCheck detekt          # static analysis / formatting gates
./gradlew lint                        # Android lint
```

## Auto-update (GitHub Releases)

The app self-updates from **GitHub Releases** — no Firestore/Firebase. The Release is the single
source of truth.

- **Version scheme**: `app/build.gradle.kts` defines `appVersionName` ("X.Y.Z") and derives
  `versionCode = major*10000 + minor*100 + patch`. `UPDATE_OWNER`/`UPDATE_REPO` are exposed as
  `BuildConfig` fields.
- **In-app updater** (`app/.../update/`): on launch `UpdatePrompt` silently GETs
  `api.github.com/repos/<owner>/<repo>/releases/latest`, parses the tag + the `.apk` asset, and if
  its derived versionCode exceeds the installed one shows a dialog. `ApkInstaller` downloads to the
  private cache and launches the system installer via a `FileProvider` (`REQUEST_INSTALL_PACKAGES`).
  The Vault also has a manual "Check for updates". **This is the only network use in the app**;
  personal data never leaves the device.
- **Publishing** — two options (use one):
  - Local: `pwsh ./publish-update.ps1 -Version 1.1.1` — bumps the version, builds the signed APK,
    tags, pushes, and `gh release create`s with the APK attached.
  - CI: `.github/workflows/release.yml` (manual `workflow_dispatch`) builds + publishes from secrets
    `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- **One-time setup**: create the repo and push, e.g.
  `gh repo create andreaferraboli/PeopleHub --public --source . --push`. If the repo name/owner
  differs, update `updateOwner`/`updateRepo` in `app/build.gradle.kts`. The repo must be **public**
  so the app can read releases and download the APK without auth.

## Conventions

- Naming: PascalCase types, `camelCase` members, use-case classes end in `UseCase` and expose
  `operator fun invoke`; repository implementations end in `Impl` and are `internal`.
- `LazyColumn`/`LazyRow` always pass a `key`. No `lateinit` in composables — use `remember`/`by`.
- KDoc on public classes and non-trivial public functions. No `TODO`/`FIXME` in committed code.
- Routes are `@Serializable` types; each feature exposes a `NavGraphBuilder.<name>Section(...)`
  extension that takes navigation callbacks, keeping features decoupled. The app wires them in
  `PeopleHubNavHost`.

## Known simplifications

- Profile photos are picked with the permission-less Photo Picker and copied into internal storage;
  camera capture (which would need a `FileProvider`) is not wired up.
- Birthday reminder offsets are configured globally (Settings); per-person reminder overrides are
  not persisted (the schema models a single global set).
