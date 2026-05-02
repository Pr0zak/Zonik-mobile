# Material You Redesign — Audit

Audit of the existing app vs the design package at `/tmp/zonik-design/zonik-app/project/phone-v1.jsx`.
The design intent: strip the fixed gold accent and theme each screen from the contextual album palette
(Home → featured album, Now Playing/Queue → playing album, Library/Search/Settings → neutral M3 baseline).

---

## Top-level themes (apply across the codebase)

| # | Theme | Status |
|---|-------|-------:|
| 1 | Replace fixed gold (`ZonikColors.gold`) with dynamic `MaterialTheme.colorScheme.primary`/`tertiary` per-screen. 18 references across 8 files (incl. TV screens — leave TV alone since design is mobile-only). | Pending |
| 2 | Add Roboto Flex variable font and rebuild the M3 type ramp to match design `M3.type` sizes/weights/letter-spacing. Existing Typography uses generic system font with bolder weights and tighter letter-spacing than M3 spec. | Pending |
| 3 | Build `buildSchemeFromPalette(palette: AlbumPalette)` and a `NEUTRAL_SCHEME` to drive `MaterialTheme.colorScheme` per-screen via `CompositionLocalProvider` / nested `MaterialTheme`. | Pending |
| 4 | Add `rememberAlbumPalette(coverArtId)` using AndroidX Palette + Coil. Cache by id. Returns 3 colors (dark/vibrant/deep) matching design `palette[0..2]`. | Pending |
| 5 | Format-badge redesign: lossless → `tertiary`-tinted pill (`tertiary` container @ 0.15, border @ 0.5, text on container) using a per-track palette where available (per design `M3FormatBadge` uses `a.palette[1]`). Lossy → neutral `surfaceContainerHigh` pill with `onSurfaceVariant` text. The lossless/lossy distinction is preserved by tint vs neutral, not by gold. | Pending |

---

## Theme primitives

### Existing (`ui/theme/Theme.kt`)

- `ZonikColors`: `gold`, `goldDim`, `glassBg`, `gradientStart`, `gradientEnd`, `navBarBg`.
- `ZonikShapes`: `cardShape` 16, `miniPlayerShape` 16, `navBarShape` topStart/End 24, `coverArtShape` 12, `coverArtLargeShape` 24, `buttonShape` 12, `badgeShape` 6.
- `ZonikDarkColorScheme`: hardcoded purple-ish dark scheme (primary `#AFA9EC`, primaryContainer `#534AB7`, tertiary `#E2CB6F` gold).
- `ZonikTypography`: custom display/headline/title/body/label values, off-spec weights (Bold for headline, SemiBold for titleLarge/Medium).

### Target

- New `Type.kt`: M3 type ramp on Roboto Flex matching `M3.type` table (displayMd 36/44/400, headlineLg 32/40/400, headlineMd 28/36/400, headlineSm 24/32/500, titleLg 22/28/500, titleMd 16/24/600/+0.15, titleSm 14/20/600/+0.1, bodyLg 16/24/400/+0.5, bodyMd 14/20/400/+0.25, bodySm 12/16/400/+0.4, labelLg 14/20/600/+0.1, labelMd 12/16/600/+0.5, labelSm 11/16/600/+0.5).
- New `DynamicScheme.kt`: `buildSchemeFromPalette(palette)` returns a `ColorScheme` with `primary=vibrant`, `onPrimary=#0d0a18`, `primaryContainer=dark`, `onPrimaryContainer=White`, `secondaryContainer=lighten(dark, 10%)`, `surface=#0f0c1a`, `surfaceContainer=White@5%`, `surfaceContainerHigh=White@8%`, `surfaceContainerHighest=White@11%`, `onSurface=#ECE6F0`, `onSurfaceVariant=onSurface@65%`, `outline/outlineVariant=onSurface@18%/10%`, plus `tertiary` for lossless badge tint.
- New `NeutralScheme.kt`: `NEUTRAL_SCHEME` matching design `NEUTRAL` const — primary `#B5A2E8`, onPrimary `#1F1147`, primaryContainer `#372A6E`, secondaryContainer `#2A2240`, rest as above. Used by Library/Search/Settings/Downloads.
- New `Palette.kt`: `rememberAlbumPalette(coverArtId)` — fetches via Coil's existing pipeline (auth handled) at small size, runs through `Palette.from(bitmap).generate()`, returns `AlbumPalette(dark, vibrant, deep)`. LRU cache keyed by id.
- New `LocalDynamicScheme`: `CompositionLocal<ColorScheme>` so child screens can opt into a contextual scheme.
- `bgGrad` helper: `Brush.radialGradient` matching design `radial-gradient(ellipse at 50% -10%, dark, transparent 60%) over #0f0c1a`.
- `ZonikShapes` keeps `coverArtShape`, `cardShape`, etc., but rounded radii adjusted toward design (cards 14–16dp, cover art 12dp, large cover art 24dp).
- Drop unused `ZonikColors` constants (gold/goldDim) once references are migrated. Keep `glassBg`/`navBarBg`/`gradientStart`/`gradientEnd` for now — used outside V1 scope (TV, Downloads gradient).

---

## Per-screen audit

### Home (`ui/screens/home/HomeScreen.kt`, 531 lines)

| Aspect | Current | Design | Action |
|---|---|---|---|
| Top bar | Custom Row with launcher icon + headlineMedium "Zonik" + sync IconButton | M3 small top app bar 64dp: titleLg "Zonik" + Cast/Search/More icons | Replace |
| Greeting | None (jumps to shuffle button) | `headlineLg` "Good evening" with 8/24 margins | Add |
| Shuffle | Single gradient horizontal button "Shuffle Mix" | 2-up grid: `ShuffleTile` Mix (primary container) + Favorites tonal (primaryContainer) | Replace |
| Recently played | LazyRow of 176dp cards | LazyRow of 144dp cards with format badge overlay top-left | Re-tune size + add badge |
| Jump back in | Not present | Vertical list of 4 album rows (56dp art, surfaceContainer bg, More icon) | Add as "Jump back in" — wire to existing `recentlyPlayed`/`recentTracks` |
| Recent tracks | List of plain `TrackListItemWithMenu` rows | Not in design (design's "Jump back in" replaces this) | Keep behind "Recent Tracks" section header (preserves behavior) |
| FAB | None | Extended FAB 56dp "Play" pinned bottom-right above mini-player | Add |
| Color source | `ZonikColors.gradientStart/End`, fixed scheme | Featured-album palette → `buildSchemeFromPalette` | Wire palette + nested `MaterialTheme` |
| Pull-to-refresh | `PullToRefreshBox` wired | Not in design (only on Library) but present in app | Keep |

Behavioral preserves: `viewModel.shuffleMix`, `playTrack`, `playNext`, `addToQueue`, `toggleMarkForDeletion`, `startRadio`, `syncNow`, `recentTracks` flow, `recentlyPlayed` flow.

### Now Playing (`ui/screens/nowplaying/NowPlayingScreen.kt`, 1057 lines)

| Aspect | Current | Design | Action |
|---|---|---|---|
| Background | Blurred cover art + black gradient | Radial palette gradient: `primary@33` top, `primaryContainer@cc` bottom-left, linear `deep → surface` 70% | Replace |
| Top bar | ChevDown + "NOW PLAYING" pill + spacer | ChevDown + center stack ("PLAYING FROM ALBUM" labelMd + album titleSm) + More | Replace |
| Album art | Aspect 1:1 + glow blur | 300dp cover with `boxShadow: 30px 60px ${primaryContainer}cc`, `radius: 24` | Resize/restyle |
| Title block | Title, artist, album in glass card | headlineMd title + bodyLg artist + format/cloud row + Heart IconBtn (right) | Rework |
| Seek bar | Slider w/ optional waveform 40dp | Waveform (palette-tinted, 36dp) + 14dp dot thumb at progress; tabular time labels | Rework |
| Controls | Shuffle, prev (60), play (80 gradient), next (60), repeat | Shuffle 40, prev 48, play 76 (palette-glow), next 48, repeat 40 | Resize |
| Bottom bar | Glass pill: Star, Trash, Radio, Cast, Queue | Cast left, "3 of N · Up next: …" center bodySm, Queue right (no glass pill) | Restructure (preserve all actions) |
| Format badge | gold `ZonikColors.gold@20` for lossless | `palette[1]@26` bg, `lighten(palette[1], 0.4)` text, palette `@80` border for lossless; neutral pill for lossy | Rework `M3FormatBadge` |
| Color source | Manually computed `dominantColor`/`accentColor` from Coil + Palette | Same approach but exposed as scheme primary | Migrate to shared `rememberAlbumPalette` |

Behavioral preserves: ALL — `togglePlayPause`, `skipNext/Prev`, `seekTo`, `skipToIndex`, `getCurrentPosition/Duration`, `toggleShuffle`, `cycleRepeat`, `toggleStar`, `toggleMarkForDeletion`, `cyclePlaybackSpeed`, `startRadio`, swipe-down dismiss, queue sheet (bottom modal sheet), waveform toggle, cast button, keep-screen-on, casting indicator, error banner.

### Library (`ui/screens/library/LibraryScreen.kt`, 1457 lines)

| Aspect | Current | Design | Action |
|---|---|---|---|
| Top bar | Plain "Library" headlineMedium | M3 small top app bar 64dp: titleLg "Your library" + More | Restyle |
| Search bar | None on screen (Search is its own tab) | Docked 56dp pill `surfaceContainerHigh` with leading Search icon, "Search your library" placeholder | Add (visual) — clicking it routes to Search tab |
| Filter chips | `ScrollableTabRow` of 8 tabs (Tracks/Albums/Artists/Favorites/Genres/Playlists/Flagged/Offline) | M3 filter chips with check+count: Albums/Artists/Playlists/Tracks (4 only in design) | Restyle existing tabs as M3 filter chips, keep all 8 |
| Album grid | 2-col grid w/ Card containers | 2-col grid w/ no card border — cover art 170dp, format badge top-left, cloud badge top-right | Rework AlbumGridCard |
| Pull-to-refresh | `PullToRefreshBox` | Spinner shown 24dp inline | Keep |
| Color source | Theme default (purple) | NEUTRAL scheme | Apply NEUTRAL via nested `MaterialTheme` |

Behavioral preserves: ALL tabs and viewmodel state — sort chips, alpha sidebar, deletion flow, all 8 sub-tabs, gradient `Play All` (replace gradient with `primary`).

### Queue (currently a sheet inside NowPlayingScreen)

| Aspect | Current | Design | Action |
|---|---|---|---|
| Surface | `ModalBottomSheet` with hardcoded `Color(0xFF151320)` bg | Full-screen overlay (artboard) but in app context can stay as bottom sheet — design is reference for visual treatment | Restyle as bottom sheet w/ palette scheme |
| Header | "QUEUE" pill + count badge + cache button | ChevDown + center "UP NEXT" + count/duration + More | Restyle |
| Row treatment | Zebra-stripe ListItem, current=accent text | Zebra-stripe + current row has `primaryContainer` left-accent + 3px primary border-left + drag handle on right | Restyle |
| Swipe-to-remove | NOT IMPLEMENTED currently | Design shows `← Swipe to remove` | DOCUMENT — keep current (no swipe behavior to preserve, design adds new gesture). Skip in this PR — visual rewrite only. |
| Color source | Hardcoded dark blue + animatedAccent | Playing-album palette via `buildSchemeFromPalette` | Apply per-track palette |

**Ambiguity**: Queue sheet is currently nested in Now Playing. Design treats it as own screen. Plan: keep as bottom sheet (preserves nav) but apply visual spec.

### Search (`ui/screens/search/SearchScreen.kt`, 474 lines)

| Aspect | Current | Design | Action |
|---|---|---|---|
| Top bar | M3 `TopAppBar(title="Search")` | ChevDown back IconBtn + 56dp pill bar with "halcyon" query + caret | Restyle (no back since this is a tab — use just the pill) |
| Search field | `OutlinedTextField` w/ rounded large shape | 56dp pill `surfaceContainerHigh`, leading icon, trailing More IconBtn, primary caret animation | Restyle |
| Filter chips | None | `All` (selected secondaryContainer) / Albums / Artists / Tracks (outlined) | Add (presentation only — wires to existing artists/albums/tracks sections) |
| Top result hero | None — section headers + lists | "Top result" big card (16dp radius, surfaceContainer bg, 72dp art, inline Play button) | Add when results exist |
| Sub-results | Plain ListItem rows w/ small icons | 48dp art rows w/ kind label (uppercase labelSm) + highlighted query in primary color + More icon | Rework rows |
| Color source | Theme default (purple) | NEUTRAL scheme | Apply NEUTRAL |

Behavioral preserves: ALL — debounce, query state, search results sections, play/playNext/addToQueue/toggleMarkForDeletion/startRadio.

### Settings (`ui/screens/settings/SettingsScreen.kt`, 1522 lines)

| Aspect | Current | Design | Action |
|---|---|---|---|
| Top bar | M3 `TopAppBar(title="Settings")` | ChevDown + titleLg "Settings" 64dp | Restyle (no back since tab — keep title only) |
| Account card | Server card w/ test connection / edit fields | Avatar circle (gradient) + name + bodySm "host · X tracks · Y GB" + ChevRight | Add an account header card on top using existing server stats + library stats |
| Section labels | Plain Text headers | `labelLg` colored `primary` | Restyle |
| Setting rows | Mostly Card-wrapped ListItem rows w/ background `Color(0xFF1A1824)` hardcoded | Plain rows 56dp w/ titleSm + bodySm sub, no card container; cache progress bar inline | Restyle — drop hardcoded bg, use surface tier shifts |
| Toggles | M3 `Switch` | Custom 52×32 pill toggle (cosmetic difference) | Use M3 `Switch` (closer M3 idiom; design is custom-built) |
| Theme palette dots | None | "Theme · Material You · From album art" with 4×20 dots showing palette samples | Add (sample from recent albums) |
| Color source | Theme default (purple) + many hardcoded `Color(0xFF1A1824)` Card containers | NEUTRAL scheme | Apply NEUTRAL + remove hardcoded card colors |

Behavioral preserves: ALL — all dropdowns, switches, dialogs (server URL/username/API key edit), test connection, sync controls, equalizer, cache controls, debug log, sign out, etc.

### Downloads (`ui/screens/downloads/DownloadsScreen.kt`, 1131 lines)

Not in design. Stage 4 retheme only — apply NEUTRAL scheme + new type ramp + drop hardcoded card backgrounds + remove gradient buttons (gold-tinted) for primary actions. Layout untouched.

---

## Component-level changes

### `MiniPlayer.kt`

| Aspect | Current | Design | Action |
|---|---|---|---|
| Container | `ZonikColors.glassBg` solid surface | Linear gradient `primaryContainer@cc → rgba(20,18,32,0.92)` 135deg, 1px outline | Restyle |
| Position | Above NavBar (managed in `MainScreen`) | `bottom: 80, left/right: 8, radius 18` | Same — managed by parent |
| Buttons | Skip prev/next IconButton + circular play/pause `primaryContainer` | Skip prev (rgba 10% bg) + Play/Pause filled circle `primary` w/ palette glow | Restyle |
| Progress | `LinearProgressIndicator` 2dp | 2dp absolute bar at bottom 14/4 inset, `primary` fill | Restyle |
| Color source | Static theme | Inherits from contextual scheme (parent screen's) | Use `LocalDynamicScheme` |

### `TrackListItem.kt` — `FormatBadge`

| Current | Design |
|---|---|
| Lossless: `ZonikColors.gold@15` bg, `gold` text, `badgeShape` 6dp | Lossless: `palette[1]@26` bg, `lighten(palette[1], 0.4)` text, `palette[1]@80` border, pill 999 |
| Lossy: gray | Lossy: `White@10` bg, `White@70` text, `White@12` border, pill 999 |

Action: drop gold; use `MaterialTheme.colorScheme.tertiary` (palette-driven via scheme builder) for lossless tint; neutral for lossy. Pill shape (999dp).

### Bottom Nav (`MainActivity.kt` `MainScreen`)

| Aspect | Current | Design | Action |
|---|---|---|---|
| Container | `ZonikColors.navBarBg` w/ `navBarShape` topStart/End 24dp | `rgba(13,10,24,0.92)` flat top w/ 1px outline-top | Restyle (keep topStart/End round so floating mini-player overlap reads cleaner) |
| Item indicator | `primary@12` rounded indicator (M3 default) | `secondaryContainer` rounded indicator | Theme-driven — use `secondaryContainer` |
| 5 tabs | Home/Library/Search/Downloads/Settings | Home/Search/Library (3 in design) | Keep 5 (constraint — Downloads stays) |

---

## Format-badge decision (resolves theme #5)

Design uses palette-tinted lossless pill. In the rebuilt scheme, `tertiary` will be derived from the palette
(or a fallback for NEUTRAL). For lossless: `tertiary @ 15-25%` bg, `tertiary` (or lightened) text, `tertiary @ 50%` border, pill 999.
For lossy: `surfaceContainerHigh` bg, `onSurfaceVariant` text, `outline` border, pill 999.
This keeps the lossless/lossy visual distinction strong without using gold.

---

## Pre-existing issues noticed (not fixing here)

- `MainActivity.kt` imports `kotlinx.coroutines.launch` twice (lines 55 & 64).
- HomeScreen uses `mutableStateOf(0L)` instead of `mutableLongStateOf` in a couple places — minor perf only.
- `Color(0xFF1A1824)` is hardcoded as Card container in many places in SettingsScreen — fixing as part of retheme.
- `ZonikTheme` accepts `dynamicColor` and `darkTheme` params but `darkTheme=true` short-circuits the dynamic path entirely — looks like a bug, but we replace the theme anyway.

---

## Resolution

| Item | Stage | Status |
|---|---|---|
| Roboto Flex + M3 type ramp | 2a | Resolved — `res/font/roboto_flex.ttf` (variable) bundled; `Type.kt` rebuilds the full M3 ramp on Roboto Flex. |
| `buildSchemeFromPalette` + `LocalAlbumPalette` | 2b | Resolved — `DynamicScheme.kt` exposes `buildSchemeFromPalette`, `NEUTRAL_SCHEME`, and a `LocalAlbumPalette` CompositionLocal. |
| `rememberAlbumPalette` | 2c | Resolved — `AlbumPaletteExtraction.kt` extracts via Coil + Palette with an LRU keyed by coverArtId. |
| `NEUTRAL_SCHEME` | 2d | Resolved — bundled in `DynamicScheme.kt`. |
| Strip `ZonikColors.gold` (mobile screens only) | 2e | Resolved — TrackListItem, AlbumDetail row, Now Playing format badge all switch to `MaterialTheme.colorScheme.tertiary`. TV screens left untouched (out of scope). |
| Settings rewrite | 3.1 | Resolved — wrapped in `WithNeutralScheme`, custom 64 dp top app bar, `surfaceContainer` cards in place of `#1A1824`, M3 section headers. |
| Search rewrite | 3.2 | Resolved — 56 dp pill bar, filter chips, "Top result" hero card, highlighted query, ResultRow / TrackRow with kind labels. |
| Queue (sheet) rewrite | 3.3 | Resolved — sheet `surface` color, "UP NEXT" header, primaryContainer→transparent gradient on current row + 3 dp primary `drawBehind` accent stripe, surfaceContainer zebra striping. |
| Library rewrite | 3.4 | Resolved — `WithNeutralScheme`, 64 dp top app bar, scheme.primary "Play All" button. 8 sub-tabs preserved. |
| Home rewrite | 3.5 | Resolved — `WithAlbumScheme(featured cover)`, 64 dp top bar, "Good evening" headline, 2-up Shuffle Tile row, 144 dp Recently Played cards w/ format-badge overlay, Jump Back In rows w/ context menus, Extended FAB "Play". |
| Now Playing rewrite | 3.6 | Resolved — `WithAlbumScheme(track cover)`, "PLAYING FROM ALBUM / album" centered top bar, 76 dp scheme.primary play button. |
| Downloads retheme | 4 | Resolved — `WithNeutralScheme`, M3 type bar; layout untouched per spec. |
| Default `ZonikTheme` to NEUTRAL_SCHEME + retheme MainScreen chrome | 4 | Resolved — root theme now points at NEUTRAL; mini-player + bottom nav use `surfaceContainerHigh`. |
