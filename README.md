# PeopleHub

**Your personal relationship hub — your data in the cloud, photos on the phone.**

PeopleHub helps you nurture the relationships that matter. It keeps track of the people in your
life, their birthdays, the last time you saw them ("check‑ins"), their interests, and the
significant events around them — with elapsed/remaining day counters that nudge you to stay in
touch.

The data lives **only in the cloud**: Cloud Firestore in your own Firebase project, under the Google
account you sign in with. The phone keeps no copy (Firestore runs with a memory-only cache), so a new
install signs in and finds everything already there; there is nothing to sync. The only things left
on the phone are the photos (profile pictures and event backgrounds, with their crop) and device
preferences. The other network use is the self‑updater, which checks GitHub Releases for a newer APK.
There are no analytics.

---

## Features

- **The Circle** — a searchable directory of the people you track (search over name, notes, tags and
  interests, tag filters,
  and sorting by name, last check‑in, or upcoming birthday). When sorted by upcoming birthday, each
  card shows the next birthday and a countdown.
- **Check‑ins** — record "I saw them today", keep a history, and get a "seen X days ago" status with
  per‑person or global warning/critical thresholds.
- **Milestones & birthdays** — year / month / list calendar views, with CSV and JSON import/export.
- **Events** — track significant events with categories, link them to people, and pin one to a home
  screen widget.
- **Per‑person notifications** — check‑in and birthday reminders are **opt‑in per person and off by
  default**, so you choose exactly who you want to be reminded about.
- **Birthday‑only entries** — keep someone purely for their birthday: shown in Milestones, hidden
  from The Circle.
- **Import a profile from JSON** — create a new person from a JSON file, or **update an existing
  person**: fields present in the file overwrite, anything missing is kept, the id never changes.
- **Home‑screen widgets** — upcoming birthdays, urgent check‑ins, and a pinned event (Glance).
- **Languages** — English and Italian, following the device language with an in‑app switcher
  (Vault → Language).
- **Self‑documenting UI** — every icon button shows a tooltip describing what it does.
- **Automatic backups** — export/import the full dataset (JSON) or just birthdays (CSV).
- **Cloud data** — sign in with Google (the app shows nothing else until you do) and the app reads and
  writes Firestore in your own Firebase project (`peoplehub-af26`) directly, so an assistant connected
  through the MCP server in `mcp/` sees and changes the same data. Export/import (JSON, CSV) work as
  before and act on the cloud. Photos and event backgrounds stay on the phone; one active phone per
  account is assumed for reminders. Details in [docs/firestore-schema.md](docs/firestore-schema.md).

## Design — "Velluto"

Velvet-dark and warm-paper themes with a single gold accent (`#FFC83D`), Instrument Serif titles
over Geist text (both bundled, SIL OFL), Lucide stroke icons, floating glass panels and navigation,
floating gold 3D objects in every tab, and calm motion that stops when the system removes
animations. Full rules in [DESIGN.md](DESIGN.md).

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.1, coroutines, Flow |
| UI | Jetpack Compose + Material 3 (optional Dynamic Color) |
| Architecture | Clean Architecture (Presentation / Domain / Data) |
| Data | Cloud Firestore (memory-only cache) + Firebase Auth; DataStore for device preferences |
| DI | Hilt |
| Navigation | Navigation Compose, type‑safe routes (Kotlin Serialization) |
| Background | WorkManager (CoroutineWorker) + AlarmManager (exact birthday alarm) |
| Notifications | NotificationCompat with separate channels |
| Widgets | Glance |
| I/O | kotlinx.serialization JSON + hand‑written CSV |
| Images | Coil 3 (local files in internal storage) |
| Sign-in | Google via Credential Manager; Firebase only in `:core:data` |
| Tests | JUnit 5 + MockK + Turbine |
| Quality | Ktlint + Detekt, `allWarningsAsErrors = true` |

`minSdk 26`, `targetSdk/compileSdk 35`.

## Module map

```
:app                  Hilt app, MainActivity, type-safe NavHost, bottom nav, Dashboard + Settings,
                      WorkManager workers, AlarmManager scheduler, BroadcastReceivers, per-app locale
:core:domain   (JVM)  Models, repository interfaces, use cases, pure date math, deep-link constants
:core:data            Firestore repositories, document mapping, in-memory search, Firebase Auth,
                      device DataStores (photo map, preferences), one-off cleanup of the old database
:core:ui              Compose theme (Velluto), design-system components, icons, scenes, UiState
:core:notifications   Notification channels + PeopleHubNotifier + action constants
:core:dataio   (JVM)  JSON (kotlinx.serialization) + CSV import/export, DTOs, mappers
:feature:people       People directory (search/filter/sort), tabbed detail, add/edit, JSON import
:feature:birthdays    Year/Month/List calendar views, CSV/JSON import + export
:feature:events       Events list with filters, detail, add/edit, pin-to-widget
:feature:widget       Three Glance widgets (birthdays, urgent check-ins, pinned event) + updater
```

Dependency direction: `feature:* → core:domain, core:ui (+ dataio where needed)`;
`core:data & core:notifications → core:domain`; `app → everything`. The domain module is pure
JVM with **no Android dependencies**.

## Architecture rules

- **Presentation** — each ViewModel exposes a `StateFlow<UiState<T>>` (`Loading / Success / Error /
  Empty`); every screen renders all four cases. ViewModels use only `viewModelScope` and import no
  `android.*` types.
- **Domain** — one use case per significant action (`operator fun invoke`), repository interfaces,
  models. "Now" is injected as `java.time.Clock` for testability.
- **Data** — repository impls map Firestore documents ↔ domain models over snapshot listeners
  (`Flow` for reads, `suspend fun` for writes). Firestore types never cross the repository boundary.
- Errors surface via `kotlin.Result` / `UiState.Error`, never uncaught exceptions.
- No hardcoded user‑facing strings — everything lives in a module `strings.xml` (with `values-it`
  Italian translations).

## Build & run

The project targets **JDK 17–21**. On a machine whose default JDK is too new for the bundled Gradle,
point `JAVA_HOME` at the Android Studio runtime:

```bash
export JAVA_HOME='/c/Program Files/Android/Android Studio/jbr'   # Git Bash on Windows
./gradlew :app:assembleDebug          # build the debug APK
./gradlew test                        # JVM unit tests (JUnit5 + MockK + Turbine)
./gradlew ktlintCheck detekt          # static analysis / formatting gates
./gradlew lint                        # Android lint
```

## Releases & auto‑update

The app self‑updates from **GitHub Releases** — the Release is the single source of truth (the cloud
data has nothing to do with updates).

- **Versioning** — `app/build.gradle.kts` defines `appVersionName` (`X.Y.Z`); `versionCode` is
  derived as `major*10000 + minor*100 + patch`.
- **In‑app updater** — on launch the app reads `releases/latest` from the GitHub API, compares the
  tag's version code to the installed one, and offers to download + install the `.apk` via a
  `FileProvider`. The Vault also has a manual "Check for updates". Apart from the cloud data,
  this is the app's only network use.
- **Publishing** (pick one):
  - Local: `pwsh ./publish-update.ps1 -Version X.Y.Z` — bumps the version, builds the signed APK,
    commits, pushes, and `gh release create`s with the APK attached.
  - CI: `.github/workflows/release.yml` (manual dispatch) builds + publishes from signing secrets.

The repository must remain **public** so the app can read releases and download the APK without
authentication.

## Privacy

PeopleHub is built to be private by design: no telemetry, no ads, no third‑party SDKs that phone
home. The relationship data lives in Firestore in your own Firebase project, readable only by your
Google account (security rules in `firestore.rules`); photos never leave the phone.

## License

See the repository for license details.
