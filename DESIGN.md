# PeopleHub design system: "Velluto"

Instructions for anyone (human or Claude Code) touching PeopleHub UI. Velluto replaces "Midnight
Gold". Source of truth: the approved 390x844 mockups (Riflessione, Cerchia, Eventi, Promemoria,
Cassaforte) and the code in `core/ui`. When this file and the code disagree, the code wins and this
file gets fixed.

## Principles

1. **Velvet and one gold.** A warm near-black (dark) or warm paper (light) background, one accent
   (`#FFC83D`), everything else neutral. Gold is for the one thing that matters on a card: the main
   action, "today", the selected tab, the accent half of a title. Never two solid gold things
   competing in the same card.
2. **Editorial type.** Instrument Serif for titles (one weight, contrast comes from size and the
   italic accent word), Geist for everything else, big tabular Geist numerals for counts.
3. **Floating glass.** Translucent panels with a 1dp hairline and a top light edge, soft warm
   shadows, a breathing accent glow behind every screen, a floating glass navigation pill.
4. **Objects, not icons, in heroes.** Each tab has a floating gold 3D object (rings, gift, pin,
   hourglass, lock) bleeding out of the top-right corner.
5. **Calm motion.** One easing, cascading entrances, slow ambient loops. Everything stops when the
   system says "remove animations".
6. **Accessible by construction.** Text tokens are AA (>= 4.5:1) on the surfaces they are meant for;
   every touch target is >= 48dp; decorative things have no semantics.

## What you may and may not change

- **Visual only.** A redesign never changes logic, data, navigation, ViewModels, use cases, string
  resources or translations. If a mockup shows data the app does not have, adapt the layout to the
  real data; never invent features. Features the mockup does not show stay, dressed in the same tokens.
- **Never hardcode colours, fonts, radii or durations in feature code.** Use `PeopleHubTheme.colors`,
  `MaterialTheme.typography`, `PeopleHubTheme.type`, `VellutoShapes`, `PeopleHubTheme.spacing`,
  `PeopleHubTheme.motion`. The only literal colours allowed live in `core/ui/theme` and in the
  scene/cover components (illustration colours).
- **Do not add a second accent.** To re-colour the app change `VellutoAccent` in
  `core/ui/.../theme/Color.kt` and regenerate the scenes (see Assets). Nothing else.
- **Do not use Material Icons.** Use `VellutoIcons` (Lucide). Need a glyph that is not there? Add it
  to `VellutoIcons` from Lucide, same stroke (1.6), never a different family.
- **Do not create one-off components in a feature** when a `core/ui` one exists. If you need a
  variant, extend the shared component with a parameter.

## Tokens

All in `core/ui/src/main/kotlin/com/peoplehub/core/ui/theme/`. Read colours with
`PeopleHubTheme.colors` (`VellutoColors`). They are computed by `vellutoColors(dark, accent)`, a
line-by-line port of the mockups' `theme()` function (`mix`, `readable`, `onAccent`).

### Colours

| Token | Dark | Light | Use |
|---|---|---|---|
| `bg` | #0B0A09 | #F4F1EC | screen background |
| `surface` | #161412 | #FFFFFF | opaque cards |
| `surfaceHi` | #221E1A | #FFFFFF | lit top of hero gradients |
| `surface2` | #1F1C19 | #F7F4EF | date tiles, inner rows, neutral buttons |
| `text` | #F5F1EA | #141210 | primary text (17.6 / 16.6:1 on bg) |
| `text2` | #A8A097 | #5E574F | secondary text, inactive icons (7.7 / 6.3:1) |
| `glass` | white 5.5 % | white 72 % | glass panels, chips, search |
| `navGlass` | rgba(30,27,24,.62) | white 74 % | floating nav bar |
| `hi` | white 12 % | white 95 % | top light edge |
| `line` | white 8 % | rgba(20,18,16,.09) | hairlines, dividers |
| `shadow` / `shadowSoft` | black 50 / 30 % | warm 12 / 6 % | drop shadows |
| `track` | white 14 % | ink 14 % | neutral ring/bar, "not tracked" |
| `accent` | #FFC83D | #FFC83D | accent as a FILL |
| `onAccent` | #0B0A09 | #0B0A09 | content on the accent (12.8:1) |
| `accentText` | #FFC83D | #80641F | accent as TEXT on bg (12.8 / 5.0:1) |
| `accentTextSurface` | #FFC83D | #8C6E22 | accent as TEXT on surface (4.8:1 light) |
| `accentSoft` / `accentEdge` | accent 16 % / 45 % | accent 20 % / 45 % | soft buttons, selected soft chips |
| `accentOnSoft` | #FFC83D | #80641F | accent TEXT on accentSoft (>= 4.6:1) |
| `glowStrong` / `glowSoft` | accent 45 / 13 % | accent 34 / 12 % | FAB glow, ambient glow |
| `ok` / `warn` / `err` | #2FD07A / #FFC23D / #FF5A5F | #0C7040 / #8F5A00 / #B8252B | status |
| `okSoft` `warnSoft` `errSoft` | status 14 % | status 10 % | status pill fills |
| `heroBg` | #1C1814 | #14110E | the always-dark hero card (Promemoria) |
| `accentOnHero` | #FFC83D | #FFC83D | accent text on heroBg |
| `g1..g5` | accent ramp | same | 3D objects, brand mark (g1 lightest, g5 deepest) |
| `onMedia` / `onMediaMuted` | #FFF8EE / 86 % | same | text over photos and covers (always on a dark scrim) |

Light `ok` and `err` are the mockup values deepened a touch (#0E7A45 -> #0C7040, #C8282E -> #B8252B):
the originals drop to 4.2:1 on their own soft fill over the page background.

**Accent as fill vs accent as text.** In light theme `#FFC83D` is unreadable as text (1.4:1). Fill
with `accent`; write with `accentText` (on bg), `accentTextSurface` (on cards), `accentOnSoft` (on
`accentSoft`), `accentOnHero` (on `heroBg`). Material's `colorScheme.primary` is mapped to
`accentText`, so stock Material components (TextButton, focused text fields, switches, progress)
stay AA. `primaryContainer`/`secondaryContainer` are the opaque `accentSoft`.

Material roles also mapped: `background`/`surface` = `bg` (top bars blend in), `surfaceContainer*` =
the card surfaces (dialogs and sheets are `surface`/white in light), `onSurfaceVariant` = `text2`,
`outline` = a 3.3:1 neutral (text-field borders), `outlineVariant` = `line` flattened, `error` = `err`.
`surfaceTint` = `bg`, so tonal elevation never tints surfaces gold.

Check-in status: `statusColorsFor(status)` -> FRESH ok, DUE warn, OVERDUE err, NEVER neutral
(`surface2` + `text2`, ring `track`).

### Typography

Fonts are bundled in `core/ui/src/main/res/font` (no downloadable fonts; the app is offline):
`instrument_serif_regular/italic.ttf`, `geist_light/regular/medium/semibold/bold.ttf`. Licences (SIL
OFL 1.1) in `core/ui/src/main/assets/licenses/`.

| Role | Face | Size / line | Use |
|---|---|---|---|
| displayLarge | Serif | 62 / 58, -0.022em | hero title |
| displayMedium | Serif | 46 / 44 | hero card title ("Porta i fiori") |
| displaySmall | Serif | 34 / 36 | big section title ("Ricorrenze") |
| headlineLarge | Serif | 32 / 34 | cover title |
| headlineMedium | Serif | 30 / 34 | section header |
| headlineSmall | Serif | 26 / 30 | card heading, name on hero rows |
| titleLarge | Serif | 26 / 30 | top app bar, dialog titles |
| titleMedium | Geist SemiBold | 17 / 22 | row primary text (names) |
| titleSmall | Geist SemiBold | 15 / 20 | buttons |
| bodyLarge / bodyMedium / bodySmall | Geist | 16 / 15 / 13 | reading, paragraphs (15/1.45), captions |
| labelLarge / labelMedium / labelSmall | Geist SemiBold | 14 / 12 / 11 | chips, badges, tiny caps |

`PeopleHubTheme.type` (`VellutoType`): `eyebrow` (12sp, 0.2em tracking, uppercase at call site; same
as `LabelCaps`), `tileCaps` (11sp caps in tiles), `kpiHero` 92, `kpiLarge` 60, `kpiCover` 52,
`kpiMedium` 40, `kpiSmall` 30, `kpiTile` 22: Geist Medium, negative tracking, tabular figures.

Titles are two-tone: `DisplayTitle` / `accentTitle()` set the accent part in italic `accentText`
(last word by default, the second half for a one-word title, or an explicit substring).

### Spacing, radii, elevation

- `PeopleHubTheme.spacing`: 4, 6, 8, 10, 12, 14, 16, 20, 28, 32; `gutter` 16 (cards and lists),
  `heroInset` 20 (titles), `touchTarget` 48; nav `navHeight` 68, `navWidth` 316, `navBottom` 22.
- `VellutoShapes`: `Pill` (all interactive things), `Mark` 11, `Tile` 14, `DateTile` 16, `Inner` 20,
  `CardSmall` 24, `Card` 26, `CardLarge` 28, `Hero` 30. Material shapes: text fields 12, menus 16-20,
  dialogs and sheets 30, buttons full pill.
- `VellutoElevation` (CSS-like coloured shadows via `Modifier.softShadow`): `Card` 0/14/30,
  `Raised` 0/20/40, `Hero` 0/24/46, `Nav` 0/20/40, `Glow` 0/14/34 (accent), `GlowSmall` 0/6/18.
  API < 28 renders them flat (framework limitation), which is acceptable.

### Motion

`PeopleHubTheme.motion` (`VellutoMotion`): easing `VellutoEasing` = cubic-bezier(.2,.8,.2,1);
`riseMillis` 800 / `riseOffset` 16dp / `staggerMillis` 60 (capped at 480); `popMillis` 600;
`fillMillis` 900; `breathMillis` 4000; `floatMillis` 6500; `glowMillis` 7000; `quickMillis` 200.
`motion.reduced` is true when `Settings.Global.ANIMATOR_DURATION_SCALE == 0` (Accessibility >
Remove animations): every component then renders its final state and ambient loops stand still.
Feature code must not start its own infinite animations; use the components below, which already
honour `reduced`.

- Entrance: `Modifier.riseIn(index)` on the hero and the first cards (cascade by index).
- Ambient: `AmbientGlow`, `FloatingScene`, breathing `NotchButton` / `VellutoButton(breathing = true)`,
  `EventCover(drift = true)` on the first cover only.

## Components (`core/ui/src/main/kotlin/com/peoplehub/core/ui/components`)

| Component | File | What it is |
|---|---|---|
| `VellutoBackground` | Ambient.kt | screen backdrop: `bg` + breathing top-right glow (+ optional second glow) |
| `AmbientGlow` | Ambient.kt | a breathing radial accent glow of a given size |
| `BottomFade` | Ambient.kt | fade above the nav bar (drawn by the app shell) |
| `ScreenHero` | Hero.kt | eyebrow row + actions, two-tone display title, subtitle, extra content, floating 3D object |
| `DisplayTitle`, `accentTitle()` | Hero.kt | two-tone serif title |
| `FloatingScene`, `SceneArt` | Scene.kt | floating 3D object with glow and ground shadow |
| `CoverScene`, `coverSceneFor()` | Scene.kt | illustrated cover for events without a photo (keyword, then hash) |
| `GlassPanel` | Surfaces.kt | glass card (fill + hairline + top light + soft shadow) |
| `SurfaceCard` | Surfaces.kt | opaque card on `surface` |
| `NotchedCard`, `NotchedShape` | Notched.kt | hero card with a circular notch and its round button |
| `GoldDivider` | Surfaces.kt | accent fade line, optional glowing diamond |
| `SectionHeader` | Surfaces.kt | serif 30 title + count + fade line + text action |
| `TextAction` | Surfaces.kt | inline accent link with 48dp target |
| `CapsLabel` | Surfaces.kt | eyebrow label (default `text2`) |
| `VellutoButton` (+ `VellutoButtonStyle`) | Buttons.kt | pill button: Primary (accent + glow), Soft, Neutral, Ghost, Outline, Danger |
| `PrimaryGoldButton`, `GhostButton` | Buttons.kt | legacy names, now Primary / Ghost `VellutoButton` |
| `GlassIconButton` | Buttons.kt | 48dp round glass icon button with tooltip (header actions) |
| `AccentFab` | Buttons.kt | 60dp accent FAB, or extended pill with `text`; pops in |
| `NotchButton` | Buttons.kt | 64-68dp breathing accent button for a `NotchedCard` |
| `TooltipIconButton`, `WithTooltip` | TooltipIconButton.kt | plain icon button with tooltip (unchanged) |
| `TagChip` (+ `ChipSelectedStyle`) | Chips.kt | 48dp filter pill, glass idle, Solid or Soft accent selected, count/icon/dot |
| `ToggleChip` | Chips.kt | check pill (offsets) |
| `CategoryChip` | Chips.kt | read-only pill; `onMedia` = dark glass caps chip over covers |
| `StatusPill`, `CheckInStatusBadge` | Chips.kt, CheckInStatusBadge.kt | soft status pill with dot/icon |
| `SegmentedPill` | Chips.kt | segmented control (selected = inverted text fill) |
| `LegendDot` | Chips.kt | dot + label legend |
| `SearchField` | SearchField.kt | 54dp glass search pill with trailing slot |
| `PersonAvatar` | PersonAvatar.kt | photo or monogram on a warm gradient; `ringColor` = status ring with gap |
| `AvatarStack`, `StackedFace` | PersonAvatar.kt | overlapping faces + "+N" |
| `DateTile` | DateTile.kt | 50x54 month/day tile |
| `KpiBlock`, `KpiRow` | Kpi.kt | big tabular number + caption; row with hairline separators |
| `SegmentBar`, `BarSegment` | Kpi.kt | stacked proportion bar with animated fill |
| `DayCountDisplay` | DayCount.kt | TRA / 14 / GIORNI countdown, `onMedia` variant |
| `EventCover` (+ `CoverCountdown`, `CoverPin`) | EventCover.kt | event as cover card: photo or scene, scrim, chip, pin toggle, countdown |
| `OutingCard` | OutingCard.kt | outings history entry (date column, note, faces) |
| `SettingsCard`, `IconTile` | Settings.kt | Vault section card with icon tile and serif title |
| `SettingsRow`, `SwitchRow`, `VellutoSwitch` | Settings.kt | navigation row, switch row, accent switch |
| `StepperRow` | Settings.kt | − value + row |
| `GroupedRows`, `Hairline` | Settings.kt | grouped rows panel, 1dp divider |
| `PeopleHubTopBar`, `BrandMark` | TopBar.kt | transparent top bar with serif title; gold brand tile |
| `VellutoNavBar`, `NavBarItem` | NavBar.kt | floating glass nav pill (used only by the app shell) |
| `LoadingView`, `EmptyView`, `ErrorView`, `UiStateContent` | StateViews.kt | state views with glow and glass icon tile |
| `PhotoCropDialog` | PhotoCropDialog.kt | unchanged behaviour |

Modifiers (`core/ui/.../modifier`): `glassSurface`, `cardSurface`, `lightEdge`, `softShadow`,
`radialGlow` (Glass.kt), `riseIn` (Entrance.kt), `safeBottomBarPadding`, `safeDialogPadding`
(SafeArea.kt). They take `VellutoColors` explicitly: pass `PeopleHubTheme.colors`.

## Glass

Real backdrop blur is used only where content scrolls under glass: the floating navigation bar,
through Haze (`dev.chrisbanes.haze`, 1.1.1, offline library) — `RenderEffect` blur on Android 12+,
a denser translucent fill below API 31. Everywhere else glass sits on the flat background, where
blur would be invisible, so `glassSurface` (translucent fill + hairline + top light edge) is the
effect. Screens never need Haze themselves.

## The app shell and insets (top-level screens)

The five tabs draw **under** the floating nav bar. `PeopleHubApp` consumes the system navigation
inset for the nav host and provides `LocalNavBarClearance` (system inset + 22 + 68dp) on top-level
screens, `0.dp` elsewhere. A top-level screen must:

- put its content in `VellutoBackground` (or draw `bg` itself) and keep its Scaffold transparent;
- add `LocalNavBarClearance.current + 60.dp` of bottom content padding to its scrolling list, so the
  last card clears the FAB, the fade and the bar;
- lift its FAB with `Modifier.padding(bottom = LocalNavBarClearance.current)` (Scaffold already adds
  its 16dp margin).

Detail/edit screens keep the old contract (see CLAUDE.md, "Window insets"): their own Scaffold insets
itself; bottom action bars use `Modifier.safeBottomBarPadding()`.

## Assets

- **Icons**: `VellutoIcons` (`core/ui/.../icon/VellutoIcons.kt`), Lucide 1.49.0 (ISC; licence in
  `assets/licenses/LICENSE-lucide.txt`), stroke 1.6, round caps/joins. Map: ArrowBack -> ArrowLeft,
  Add -> Plus, Delete -> Trash2, Remove -> Minus, Groups -> Users, Edit -> Pencil, Close -> X,
  CardGiftcard -> Gift, Science -> Flask, PushPin -> Pin, MoreVert -> MoreVertical, FileDownload ->
  Download, FileUpload -> Upload, CheckCircle -> CircleCheck, AutoAwesome -> Sparkles, ExpandMore /
  ExpandLess -> ChevronDown / ChevronUp, SelectAll -> ListChecks, Schedule -> Clock,
  NotificationsOff -> BellOff, NotificationsActive -> BellRing, Favorite -> Heart, EventBusy ->
  CalendarX, ErrorOutline -> CircleAlert, Celebration -> PartyPopper, CalendarViewMonth ->
  CalendarDays, CalendarMonth -> Calendar, AddPhotoAlternate -> ImagePlus, ViewList -> List, Sort ->
  ArrowUpDown, EventNote -> CalendarRange. Lucide has no filled variants: show "active" with colour
  or a soft accent container, not with a filled glyph.
- **3D objects and covers**: VectorDrawables `core/ui/src/main/res/drawable/scene_*.xml` (+
  `drawable-night/scene_hourglass.xml`), generated by `python3 tools/scenes/build_scenes.py --accent
  FFC83D` from the SVG sources in `tools/scenes/src/`. Never edit the XML by hand. They are
  placeholders for real renders: replace the drawable, keep `SceneArt` / `CoverScene`.
  Not replicated in VectorDrawable (and how it is covered): SVG `drop-shadow` filters (ground shadow
  ellipse in `FloatingScene`), CSS animations of inner parts (twinkles, sand, sheen, flame: the whole
  object floats instead), the archive desaturation (a ColorMatrix in `EventCover(past = true)`).

## Checklist before saying "done"

- Both themes, AA contrast: use the text tokens on the surfaces listed above; never `accent` as text.
- Every tappable thing >= 48dp (`GlassIconButton`, `TagChip`, `TextAction` already are).
- Animations: none of your own; check with "Remove animations" on.
- No Material Icons imports, no hardcoded colours, no new strings unless the feature already had them.
- `./gradlew -Dorg.gradle.jvmargs=-Xmx2g --max-workers=1 :app:assembleDebug ktlintCheck detekt lint test`.
