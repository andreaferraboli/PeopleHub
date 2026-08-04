# PeopleHub — Engineering Guide

PeopleHub is a **100% offline** Android app that acts as a personal relationship hub: it tracks the
people in your life, their birthdays, when you last saw them ("check-ins", grouped into "outings"),
their interests, and significant personal events with elapsed/remaining day counters.

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
:feature:people       People directory (FTS search/filter/sort), tabbed detail, add/edit, JSON import,
                      record/edit an outing, outings calendar
:feature:birthdays    Year/Month/List calendar views, CSV/JSON import + export
:feature:events       Events list with filters, detail, add/edit, pin-to-widget
:feature:reminders    Per-person relationship reminders: list (filter, pause, done, delete), add/edit,
                      the "why irregular cadence" science screen
:feature:widget       Three Glance widgets (birthdays, urgent check-ins, event) + config activity + updater
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

## Check-ins and outings

Storage stays **per person**: seeing four friends on one evening writes four `check_in` rows, so each
of them keeps the meeting in their own history and their own cadence tracker. `check_in.outing_id`
(v8, indexed, ids handed out monotonically by `MAX(outing_id) + 1` and never reused) groups the rows
written for one occasion, and the domain `Outing` is the *view* over them — the card the user sees on
the home screen and in the outings calendar, and the unit they edit. A quick one-person check-in is
just an outing with a single attendee; a multi-day meetup is one outing per day.

Two edit paths, deliberately distinct:

- `UpdateOutingUseCase` rewrites the whole outing — day, description and attendee list — applying the
  change to every attendee's row (removed people lose theirs, added people gain one) and re-deriving
  the denormalised last-seen of everyone touched on either side.
- `UpdateCheckInUseCase` edits **one person's** row from their own history and, if that row was part
  of a shared outing, **detaches** it into an outing of its own, so a personal edit never silently
  rewrites what everyone else sees.

Rows written before v8 (and imported backups carrying `outingId = 0`) are regrouped by
(local day, description) — the migration, `BackupRepositoryImpl.insertCheckIns` and
`ImportPersonUseCase` apply the same rule. **Every** import path allocates fresh outing ids: the ids in
a file were handed out by whichever install exported it, so reusing them would either fuse the import
into an unrelated local outing that happens to share a number, or — for `0` — collapse a whole history
into one bogus outing spanning every date in it.

The outing editor reuses the "record an outing" screen, seeded from the stored outing. Its people
picker excludes birthday-only entries (bare birthdays, not tracked relationships) but keeps any that
are *already attending the edited outing*: a quick check-in on such a person is reachable from their
own screen, and hiding them would leave that outing with nothing ticked — unremovable, and impossible
to save at all, since an outing needs at least one attendee.

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
`DayCountDisplay`, `CheckInStatusBadge`, `OutingCard`, `PeopleHubTopBar`, and the
`LoadingView/EmptyView/ErrorView` state views.

### Window insets (edge to edge)

The app calls `enableEdgeToEdge()`, so **nothing** is inset for you and every screen has to say where the
system bars go. The contract is:

- The root scaffold in `PeopleHubApp` applies no insets of its own; it pads the nav host by the bottom
  bar's height **and consumes that region** (`consumeWindowInsets`). Without the consume, every
  top-level screen would reserve the navigation-bar height a second time and float above the tab bar.
- Each screen's own `Scaffold` pads its content from the default `contentWindowInsets`, which covers the
  status bar and — on screens where no tab bar is drawn — the phone's navigation buttons. `TopAppBar`
  insets itself, so `PeopleHubTopBar` needs nothing.
- A screen that puts its own action bar in the `bottomBar` slot **must** add
  `Modifier.safeBottomBarPadding()` (`core:ui/modifier`). `Scaffold` does not inset that slot: it hands
  the bar the full width at the very bottom of the window, so without it a save button sits underneath
  the navigation buttons and cannot be tapped. The helper also lifts the bar above the soft keyboard.
- Full-width dialogs (`usePlatformDefaultWidth = false`) use `Modifier.safeDialogPadding()`, which is
  laid out against the raw window and would otherwise run under both bars.

## Background work

Every daily sweep runs at the single user-configurable `AppSettings.dailyReminderHour` (**default
05:00**, Settings → Reminder timing). `PeopleHubWorkScheduler` owns all of it and is `suspend`
because it reads that hour from DataStore; it records the hour it scheduled for, so changing it
re-enqueues with `CANCEL_AND_REENQUEUE` instead of being swallowed by `KEEP`.

- **Check-in reminders**: a daily `PeriodicWorkRequest` (`CheckInReminderWorker`,
  `setRequiresBatteryNotLow(false)`, initial delay computed to the target hour) notifies about
  people past their critical threshold. Gated on the person's `notificationsEnabled` opt-in.
- **Birthday reminders**: `BirthdayReminderWorker` notifies **every** person with a matching
  birthday — deliberately *not* gated on `notificationsEnabled` (that opt-in defaults to off, so
  gating birthdays on it silently muted every imported profile). Three redundant triggers enqueue
  it, because a lone alarm is fragile — since Android 14 `SCHEDULE_EXACT_ALARM` is denied by default
  at `targetSdk 35`, and each alarm firing re-arms only the next one, so one drop breaks the chain
  for good:
  1. the daily alarm (`BirthdayAlarmScheduler`, `setExactAndAllowWhileIdle`, inexact fallback when
     the permission is denied or the user turned exact alarms off) → `BirthdayAlarmReceiver`,
  2. a daily `PeriodicWorkRequest` backstop, which survives reboots and process death,
  3. an app-launch catch-up when the hour has passed and the day's sweep never ran.

  `ReminderStateRepository.lastBirthdaySweepDate` makes that idempotent: the first trigger each day
  claims the date and the rest no-op. The sweep is also enqueued as *unique* work so concurrent
  triggers collapse. Settings → Notification health forces a run (`INPUT_FORCE`) for verification.
- **Relationship reminders**: `RelationshipReminderWorker` fires the per-person reminders that have
  come due, then reschedules each from now with fresh jitter (so a device that was off for days fires
  each overdue reminder once, with no catch-up burst). It runs on the **same three redundant
  triggers** as the birthday sweep, with its own claim date
  (`ReminderStateRepository.lastRelationshipSweepDate`) and its own unique work name, because a lone
  periodic job is silently dropped by Doze and OEM battery managers. Also **not** gated on
  `notificationsEnabled`: the user created the reminder explicitly, and that off-by-default opt-in
  meant it never notified at all. Settings → Notification health shows both sweeps' last run and its
  "check now" forces both.

  "Due" is resolved to **the end of the current day**, not the current instant (`GetDueRemindersUseCase`).
  The cadence is whole days but a fire time carries the time of day it was drawn at, so comparing
  against "now" made the early-morning sweep skip every reminder due later that day and deliver it a
  full day late. A double fire is impossible: the next occurrence is at least a day away, so it cannot
  also land inside today's window.
- **Doing a reminder**: the reminder cards carry a "done" button (`MarkReminderDoneUseCase`, shared with
  the notification's "Done" action) that logs the day in `reminder_completion` and restarts the cadence
  from now with a **freshly drawn** interval. The log is what makes the gesture durable — a reminder row
  only carries its *next* occurrence, so rescheduling alone would erase that anything happened — and it
  is what keeps "done" distinct from "notified": `lastFiredAt` moves when the sweep posts a
  notification, the log only when the user says they did the thing. One row per (reminder, day), so
  ticking twice in a day counts once in the history while still redrawing the next occurrence.
- **Diagnostics**: notifications, exact alarms and battery optimisation all fail silently, so
  Settings surfaces each with a one-tap route to the system screen that fixes it
  (`NotificationDiagnostics`).
- `BootReceiver` re-schedules everything after a reboot; it and `BirthdayAlarmReceiver` use
  `goAsync()` since scheduling now suspends.
- **Widgets**: `WidgetUpdateWorker` refreshes all Glance widgets every 6 hours; `updateWidgetsNow()`
  triggers an immediate refresh after a check-in. Feature modules request that refresh through the
  `WidgetRefresher` fun-interface in `core:domain` (bound in the app's `CoreModule`), because
  `:feature:events` cannot depend on `:feature:widget`.
- **Event widget**: each placed instance stores its own `event_id` in Glance state, chosen in
  `EventWidgetConfigActivity` (declared `android:configure` + `reconfigurable`), so several event
  widgets can coexist. Instances placed before per-instance selection existed have no stored id and
  fall back to the app's pinned event. The widget renders the event's background photo — the scrim
  gradient is baked into the bitmap because Glance has no gradient brush.

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

- **Version scheme**: `versionCode = major*10000 + minor*100 + patch`, derived from the version name.
  The release CI owns the version: it auto-increments the patch from the latest GitHub release and
  injects it via the `APP_VERSION_NAME` env var, so you never edit a number to ship. The literal
  `appVersionName` in `app/build.gradle.kts` is only the fallback for local/offline builds.
  `UPDATE_OWNER`/`UPDATE_REPO` are exposed as `BuildConfig` fields.
- **In-app updater** (`app/.../update/`): on launch `UpdatePrompt` silently GETs
  `api.github.com/repos/<owner>/<repo>/releases/latest`, parses the tag + the `.apk` asset, and if
  its derived versionCode exceeds the installed one shows a dialog. `ApkInstaller` downloads to the
  private cache and launches the system installer via a `FileProvider` (`REQUEST_INSTALL_PACKAGES`).
  The Vault also has a manual "Check for updates". **This is the only network use in the app**;
  personal data never leaves the device.
- **Publishing** — two options (use one):
  - Local (Windows): `pwsh ./publish-update.ps1 -Version 1.1.1` — bumps the version, builds the
    signed APK, tags, pushes, and `gh release create`s with the APK attached.
  - Local (Linux/macOS, e.g. a Debian deploy agent): `./publish-update.sh --version 1.1.1`
    (add `--auto-confirm` for headless runs) — the POSIX/Bash counterpart with identical behaviour.
    Needs `gh` authenticated (or `GH_TOKEN`) and a JDK 17–21 (`JAVA_HOME` or `java` on `PATH`).
    **Signing on a Debian agent**: the signing key is a secret and is **never** committed (the repo is
    public). The agent stores the keystore as a base64 secret and exports
    `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`; the script decodes it to a
    git-ignored `.jks` at build time and deletes it afterwards. Generate the base64 once with
    `base64 -w0 keystore/peoplehub-release.jks`. Without a key the script refuses to publish an
    unsigned APK (override with `--allow-unsigned` only for throwaway test builds).
  - CI (recommended, push-only): `.github/workflows/release.yml` runs **automatically on every push
    to `master`** and does everything — no version edit, no local build. It auto-computes the next
    version (latest published release's patch + 1), injects it via `APP_VERSION_NAME`, then builds +
    signs + publishes `v<version>` with the APK. Because the version drives the build, the APK genuinely
    *is* that version, and a post-build step asserts the APK's `versionName` equals the tag — so a
    release can never claim a version the APK isn't (the bug that made the in-app updater prompt
    forever). Put `[skip ci]` in a commit message to skip releasing that push. For a minor/major jump
    (e.g. 1.6.x → 1.7.0), run the workflow manually from the Actions tab with the `version` input (it
    is injected too, so it stays drift-free). Needs repo secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
    `KEY_ALIAS`, `KEY_PASSWORD` (Settings → Secrets and variables → Actions; safe even on a public
    repo). Pinned actions: `checkout@v7`, `setup-java@v5`, `action-gh-release@v3`. The local
    publish-update scripts above still work for offline builds.
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
  camera capture (which would need a `FileProvider`) is not wired up. Person photos can't be
  re-cropped after the fact (event backgrounds can — they keep their original alongside the crop).
- Birthday reminder offsets, and the hour every sweep fires at, are configured globally (Settings);
  per-person overrides are not persisted (the schema models a single global set). Birthday
  notifications themselves are always on for everyone; the per-person `notificationsEnabled` toggle
  only governs check-in reminders.
