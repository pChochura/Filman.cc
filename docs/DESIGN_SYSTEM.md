# Filman — Design System & UI Guidelines

This document defines Filman's visual language, component library, layout conventions, and
TV/D-pad interaction model. Everything here reflects the current implementation in `:core:ui`
and `:feature:*`.

Companion document: [ARCHITECTURE.md](ARCHITECTURE.md) (modules, MVI, data layer, DI, testing).

---

## 1. Visual philosophy

- **Cinematic dark-only aesthetic.** Pure black (`#000000`) canvas so full-bleed artwork and
  poster photography dominate, paired with a bold crimson accent (`#E50914`) and high-contrast
  white text. There is no light theme.
- **TV-first, 10-foot legibility.** Built on **`androidx.tv.material3`** (`MaterialTheme`,
  `Surface`, `Button`, `IconButton`, `ListItem`, `Text`, `Icon`). Standard
  `androidx.compose.material3` is used *only* for primitives TV Material doesn't ship
  (`CircularProgressIndicator`, `TextField`, `TooltipBox`/`PlainTooltip`).
- **Unmistakable focus state.** Because TV users navigate with a D-pad, the focused element
  must always be obvious from across the room — via the pulsing focus ring (`selectablePulse`),
  color inversion, or subtle scale.
- **Full-bleed immersion with safe gutters.** Hero banners and horizontal carousels bleed
  edge-to-edge while grid content sits inside generous `32.dp` horizontal margins.

---

## 2. Design tokens

All tokens live in [`core/ui/.../ui/theme/`](../core/ui/src/main/java/com/pointlessapps/filman/ui/theme/).
Always read them through `androidx.tv.material3.MaterialTheme` — **never** hardcode hex colors
or arbitrary spacing values in screens or components.

### 2.1 Color palette

Defined in [`Color.kt`](../core/ui/src/main/java/com/pointlessapps/filman/ui/theme/Color.kt) and
wired into `darkColorScheme` in [`Theme.kt`](../core/ui/src/main/java/com/pointlessapps/filman/ui/theme/Theme.kt):

| Token | Role (`MaterialTheme.colorScheme.*`) | Hex | Usage |
|---|---|---|---|
| `PremiumBackground` | `background` | `#000000` | Root scaffold, screen canvas, gradient scrims, watched overlay (`0.7f` alpha) |
| `PremiumSurface` | `surface` | `#141414` | Elevated containers, floating nav bar pill (`0.6f`/`0.9f` alpha), button content |
| `PremiumSurfaceVariant` | `surfaceVariant` | `#2F2F2F` | Selected pill fill (`0.7f`), progress bar track, toast background (`0.95f`) |
| `PremiumPrimary` | `primary` | `#E50914` | Focused nav items, progress bar fill, loading indicators, error retry focus |
| `PremiumOnPrimary` | `onPrimary` | `#FFFFFF` | Content on `primary` |
| `PremiumTextPrimary` | `onBackground`, `onSurface` | `#FFFFFF` | Primary text, focused icon buttons, primary button container (`0.7f` unfocused, `1.0f` focused) |
| `PremiumTextSecondary` | `onSurfaceVariant` | `#B3B3B3` | Secondary text, menu headers/footers, focus pulse border, unfocused icon button container |
| `ImdbColor` | *(standalone)* | `#DBA506` | IMDb badge and star tint in `PosterSectionMetaInfo` |
| *(Material default)* | `error`, `errorContainer`, `onErrorContainer` | M3 dark defaults | Error text (`ErrorSection`) and `StaleBannerSection` (`0.8f` alpha) |
| *(Material default)* | `inverseSurface`, `inverseOnSurface` | M3 dark defaults | Rating pill badge on cards (`0.8f` alpha) |

**Alpha conventions:**
- Scrim / overlay drawer background: `Color.Black.copy(alpha = 0.7f)`
- Watched card dim overlay: `background.copy(alpha = 0.7f)`
- Floating nav bar pill: `surface.copy(alpha = 0.6f)` unfocused → `0.9f` focused
- Unfocused primary button: `onBackground.copy(alpha = 0.7f)` → `1.0f` focused
- Secondary / subtitle text inside list items & badges: `0.7f` alpha
- Muted footer text: `onSurfaceVariant.copy(alpha = 0.5f)`
- Floating badges / stale banner: `0.8f` alpha
- Toast container: `surfaceVariant.copy(alpha = 0.95f)`

### 2.2 Typography

Defined in [`Type.kt`](../core/ui/src/main/java/com/pointlessapps/filman/ui/theme/Type.kt),
using `FontFamily.SansSerif`:

| Style | Weight | Size / Line height | Tracking | Typical usage |
|---|---|---|---|---|
| `displayLarge` | Bold | `57sp / 64sp` | `-0.25sp` | Large hero / screensaver |
| `displayMedium` | Bold | `45sp / 52sp` | `0sp` | Featured hero & details poster title |
| `displaySmall` | Bold | `36sp / 44sp` | `0sp` | Secondary hero display |
| `headlineLarge` | SemiBold | `32sp / 40sp` | `0sp` | Screen-level titles |
| `headlineMedium` | SemiBold | `28sp / 36sp` | `0sp` | Major section display |
| `headlineSmall` | SemiBold | `24sp / 32sp` | `0sp` | Sub-screen headers |
| `titleLarge` | Medium | `22sp / 28sp` | `0sp` | `SectionHeader`, `FilmanOverlayTitleBar` |
| `titleMedium` | Medium | `16sp / 24sp` | `0.15sp` | Tabs (`TabRowSection`), "Show more" card, error message |
| `titleSmall` | Medium | `14sp / 20sp` | `0.1sp` | Overlay menu section headers |
| `bodyLarge` | Normal | `16sp / 24sp` | `0.5sp` | Card titles, hero synopsis, overlay item labels, loading footer |
| `bodyMedium` | Normal | `14sp / 20sp` | `0.25sp` | Button labels (`FontWeight.Bold`), toast, metadata pills, stale banner |
| `bodySmall` | Normal | `12sp / 16sp` | `0.4sp` | Overlay item sub-values, menu footers, secondary metadata |
| `labelLarge` | Medium | `14sp / 20sp` | `0.1sp` | Prominent labels |
| `labelMedium` | Medium | `12sp / 16sp` | `0.5sp` | Tooltips, seek bar time popup |
| `labelSmall` | Medium | `11sp / 16sp` | `0.5sp` | Card rating badge, card uppercase eyebrow badge, IMDb tag |

### 2.3 Spacing

Accessed via `MaterialTheme.spacing.*` ([`Spacing.kt`](../core/ui/src/main/java/com/pointlessapps/filman/ui/theme/Spacing.kt)):

| Token | Value | Standard usage |
|---|---|---|
| `default` | `0.dp` | Reset / no spacing |
| `extraSmall` | `4.dp` | Tight gaps (button icon–text, badge padding, nav pill inner padding, card badge–title) |
| `small` | `8.dp` | Compact gaps (card overlay badge inset, toast vertical padding, chip gaps, metadata rows) |
| `medium` | `16.dp` | Standard internal padding (card text inset, toast horizontal, overlay item spacing, button group top) |
| `large` | `24.dp` | Grid column spacing (`horizontalArrangement`), `SectionHeader` vertical padding, CTA row spacing |
| `extraLarge` | `32.dp` | **Screen horizontal margin**, bottom grid padding, section bottom gap, nav bar outer margin, overlay drawer padding |
| `extraExtraLarge` | `40.dp` | Extra separation where needed |
| `huge` | `64.dp` | Large empty-state / hero spacing |

> **Rule:** Use `MaterialTheme.spacing.*` for all layout padding and `Arrangement.spacedBy(...)`.
> Raw `.dp` literals are only acceptable for fixed intrinsic dimensions: icon sizes (`16.dp`,
> `24.dp`), border strokes (`1.dp`, `2.dp`), component widths/heights (e.g., overlay drawer `400.dp`,
> featured thumbnail `100.dp`), and the `1.dp` focus-transition anchor.

### 2.4 Shapes

- **`CircleShape`** — Pill buttons (`FilmanButton`), icon buttons (`FilmanIconButton`),
  navigation bar pills (`FilmanNavigationBar`), metadata chips, seek/progress bars.
- **`MaterialTheme.shapes.medium`** — `MediaCard`, `FilmanToast`, default `selectablePulse` shape.
- **`MaterialTheme.shapes.small`** — Overlay menu items (`FilmanOverlay*Item`), rating badges,
  seek-bar time popup.
- **`MaterialTheme.shapes.extraSmall`** — Tab underline/highlight shape (`TabRowSection`).
- **`RoundedCornerShape(15)`** — IMDb rectangular badge only.

### 2.5 Icons & assets

- All vector icons live in `core/ui/src/main/res/drawable/` with `ic_` prefix (`ic_play`,
  `ic_search`, `ic_settings`, `ic_back`, `ic_star`, `ic_favorite`, `ic_favorite_empty`, …).
- Standard icon sizes:
  - `24.dp` — Buttons (`FilmanButton`, `FilmanIconButton`), loading spinners.
  - `16.dp` — Navigation bar icons, card rating stars, inline metadata icons.
- Load with `painterResource(R.drawable.ic_*)` (avoid pulling large Material icon packs unless
  an icon does not exist in `core/ui`).

---

## 3. Layout & screen anatomy

### 3.1 App shell (`FilmanScaffold`)

`FilmanScaffold` uses a `SubcomposeLayout` that overlays `navigationTopBar` **on top of**
`content` and passes `PaddingValues(top = topBarHeight)` to the screen:

- Screens with a hero (`FeaturedSection` or `PosterSection`) render the hero at `y = 0` so
  artwork flows behind the floating translucent nav bar, and pass `paddingValues` into the hero
  for safe text/control insets.
- Screens without a hero (or when `featuredItems` is empty) insert a top `Spacer` of
  `paddingValues.calculateTopPadding()`.

### 3.2 Standard browsable screen layout

All catalog/browse screens (`Home`, `Movies`, `TvShows`, `ForKids`, `Details`, `Actor`,
`WatchHistory`, `Search`) are built on a single **`LazyVerticalGrid(columns = GridCells.Fixed(5))`**:

```kotlin
LazyVerticalGrid(
    columns = GridCells.Fixed(5),
    state = listState,
    contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.extraLarge)
        .plus(PaddingValues(bottom = MaterialTheme.spacing.extraLarge)),
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
    modifier = Modifier
        .fillMaxSize()
        .focusRequester(contentFocusRequester),
) {
    errorSection(...)
    if (state.errorMessage != null) return@LazyVerticalGrid
    featuredSection(...)
    staleBannerSection(...)
    continueWatchingSection(...)
    moviesRowSection(...)
    moviesGridSection(...)
}
```

- **Full-width sections** inside the 5-column grid use `span = { GridItemSpan(maxLineSpan) }`.
- **Full-bleed sections** (`FeaturedSection`, `PosterSection`, `MoviesRowSection`,
  `ContinueWatchingSection`, `EpisodesRowSection`) apply
  `Modifier.horizontalBleed(MaterialTheme.spacing.extraLarge)` to break out of the grid's
  `32.dp` horizontal padding without clipping focus rings or scale animations.
- **Hero height:** `LocalWindowInfo.current.containerDpSize.height * 0.9f` (90% of viewport)
  with `Modifier.gradientForeground()` fading to pure black at the bottom.
- **Card aspect ratios:**
  - Portrait poster cards (`MoviesGridSection`, `MoviesRowSection`): **`0.75f`** (3:4).
  - Landscape cards (`ContinueWatchingSection`, `EpisodesRowSection`): **`1.5f`** (3:2).

### 3.3 Loading, error, stale & empty states

| State | Pattern |
|---|---|
| Initial screen load | `Crossfade(targetState = state.isLoading)` → `FilmanFullscreenLoader()` |
| Full-screen error | `errorSection(errorMessage, paddingValues, onRefresh)` — centered `error` text + Retry `FilmanButton` (`primary` focus) |
| Stale cached data | `staleBannerSection(isShowingStaleData)` — `errorContainer` banner below hero |
| Pagination footer | `LoadingMoreFooter()` (uses `BringIntoViewRequester` so TV scrolls it into view) or "Show more" card in `MoviesGridSection` |
| Transient feedback | `FilmanToast(message)` — auto-dismisses after `3000ms` with `fadeIn`/`fadeOut` |

---

## 4. Core component catalog

All shared components live in `core/ui/.../ui/components/` and `components/sections/`. Reuse
them before creating new ones.

| Component | Purpose & key conventions |
|---|---|
| `FilmanScaffold` | Root layout overlaying floating `navigationTopBar` over full-bleed `content` |
| `FilmanNavigationBar` | Floating pill bar at top (`32.dp` inset). Switches tabs after a `300ms` focus debounce; handles `BackHandler` to jump back to top bar / Home tab |
| `FilmanButton` | Pill (`CircleShape`) primary/secondary action button. Supports optional leading icon, `isLoading` spinner, `fullWidth`, `onLongClick` + Menu key, `selectablePulse`, `ButtonScale.None` |
| `FilmanIconButton` | Circular icon button with `selectablePulse` and auto-shown `PlainTooltip` on focus (`Above` / `Below`) |
| `MediaCard` | Standard poster/episode card: `AsyncImage` (200px target, `crossfade(false)`) + `gradientForeground()`, optional top-start `sourceLabelsContent`, top-end rating badge, bottom-start uppercase `badgeText` + 3-line `title`, bottom `FilmanProgressBar`, and full-card "Watched" scrim when `isFinished` or `progress >= MARK_AS_WATCHED_PROGRESS_THRESHOLD`. Focused scale: `1.1f` |
| `SectionHeader` | `titleLarge` text with `spacing.large` vertical padding |
| `FilmanOverlayMenu` | Right-aligned `400.dp` slide-over drawer (`Dialog`, `Color.Black.copy(0.7f)`). Supports nested navigation stack (`BackHandler`), `Header`, `Footer`, `Button`, `Option` (with `RadioButton`), `NestedMenu`, and `ReorderableOption` (move up/down + trailing toggle/submenu). Resets screensaver inactivity timer on every key/pointer event |
| `FilmanProgressBar` | Thin pill progress bar (`surfaceVariant` track, `primary` fill); takes `progressProvider: () -> Float` to defer state reads to draw phase |
| `FilmanSeekBar` | Player scrub bar with D-pad left/right scrubbing and floating `labelMedium` time popup |
| `FilmanFullscreenLoader` | Centered `CircularProgressIndicator(color = primary)` on `background` |
| `FilmanToast` | Floating `surfaceVariant` pill notification, `3000ms` default |
| `LoadingMoreFooter` | Inline pagination indicator with `BringIntoViewRequester` |

### 4.1 Reusable `LazyGridScope` sections (`components/sections/`)

| Section | Purpose |
|---|---|
| `featuredSection` | 90% viewport hero carousel with thumbnail selector strip |
| `posterSection` | Details-screen 90% viewport hero + `PosterSectionMetaInfo` (IMDb rating, year, tags) + CTA buttons |
| `continueWatchingSection` | Horizontal 3:2 landscape row with progress bars |
| `moviesRowSection` | Horizontal 3:4 portrait row (`horizontalBleed` + `focusGroup` + focus restoration) |
| `moviesGridSection` | 5-column portrait grid with optional "Show more" tile or infinite pagination |
| `episodesRowSection` | Horizontal 3:2 episode row for TV shows |
| `tabRowSection` | Focusable tab strip (Episodes / Recommended / Similar / Details) |
| `searchBarSection` | Search input + voice dictation (`VoiceDictationInterceptor`) |
| `categoriesGridSection` | Genre/category filter chips/grid |
| `actorInfoSection` | Actor biography & portrait header |
| `movieDetailsSection` | Extended metadata (cast, crew, synopsis) |
| `errorSection` / `staleBannerSection` | Standard error & stale-data banners |

---

## 5. Motion, focus & TV interaction guidelines

TV ergonomics are the most bug-prone part of a Compose TV app. Follow these patterns strictly.

### 5.1 Focus feedback (`selectablePulse`)

Apply `Modifier.selectablePulse(...)` to every interactive card, button, tab, and overlay list
item, and pass `scale = ButtonScale.None` / `ClickableSurfaceScale.None` to the underlying TV
Material component so animations don't double up:

- Draws a pulsing stroke (`1× → 2×` `borderWidth` over `800ms`, reversed infinitely, respecting
  system `MotionDurationScale` via `getComposeAnimationScale()`).
- Animates scale via `drawWithCache` (no relayout):
  - Buttons & list items: `focusedScale = 1f`, `pressedScale = 0.9f`.
  - `MediaCard`: `focusedScale = 1.1f`, `pressedScale = 1f`.
- Listens to `DirectionCenter` / `Enter` / `NumPadEnter` key down/up for press feedback.

### 5.2 Remote key guards (`InteractionGuards.kt`)

| Modifier | When to apply | Why |
|---|---|---|
| `Modifier.handleMenuAsLongClick(onLongClick)` | Every card or button that has a context menu (`onLongClick`) | Maps TV remote `KEYCODE_MENU` (KeyUp) to the same context menu as long-pressing OK |
| `Modifier.suppressKeyRepeat()` | One-shot buttons/surfaces (navigation, toggles) | Holding OK/Enter emits repeated `KeyDown` events (`repeatCount > 0`); prevents firing `onClick` repeatedly |
| `Modifier.suppressInitialKeyUp()` | Newly-focused items inside dialogs/menus opened via long-click | Prevents the stray `KeyUp` from the opening long-press from immediately activating the first menu item |

### 5.3 Focus restoration (`FocusRestorationState.kt`)

When a user opens Details or a Context Menu from a card and comes back, focus **must** return to
that exact card (or its nearest neighbour if it was removed):

1. Screen owns `var lastFocusedItemIds by rememberSaveable { mutableStateOf(emptyList<String>()) }`
   and a `returnFocusRequester = remember { FocusRequester() }`.
2. Before navigating or opening a context menu, push `"${sectionPrefix}${itemKey}"` onto
   `lastFocusedItemIds` (prefixes come from `SectionFocusRestorationId`: `FEATURED`, `EPISODES`,
   `CREW`, `CONTINUE_WATCHING`, `RECOMMENDED`, `moviesRowPrefix(title)`).
3. Provide `FocusRestorationState(returnFocusRequester, lastFocusedItemIds)` via
   `LocalFocusRestorationState`.
4. Items attach `Modifier.withFocusRestoration(itemKey)` or the list-aware overload
   `Modifier.withFocusRestoration(index, items, sectionPrefix, keyMapper)` (which automatically
   falls back to the nearest surviving index if the item was deleted).
5. Horizontal rows attach `Modifier.sectionFocusRestorer(sectionPrefix, defaultFallback)` on
   their `focusGroup()`.
6. On `LifecycleResumeEffect` and when `overlayMenuData` transitions from non-null to null,
   wait `100.milliseconds`, call `runCatching { returnFocusRequester.requestFocus() }`, and pop
   the last key.
7. If removing the **last** item in a section removes the whole section (e.g. last favorite),
   the ViewModel computes a fallback key (`HomeFocusFallbackExt.kt`) and emits `OverrideFocus`.

### 5.4 Navigation bar & Back button behaviour

- `FilmanNavigationBar` uses `focusProperties { onEnter = { ... }; down = contentFocusRequester }`
  so pressing Up from content always lands on the currently selected tab, and pressing Down
  returns to content.
- Pressing `Back` while focused on screen content (on a top-level tab) moves focus to the top
  navigation bar and dispatches `ScrollToTopEvent`.
- Pressing `Back` while focused on a non-Home tab (or Settings) moves focus to the Home tab.
- Pressing `Back` on Home exits.

### 5.5 Screensaver & inactivity reset

`MainActivity` tracks `lastInteractionTime` across key, touch, and generic motion events.
**Dialogs and Popups live in separate windows** and bypass `MainActivity.dispatchKeyEvent`.
Therefore, every `Dialog` (like `FilmanOverlayMenuDialog`) **must** read
`LocalInteractionReset.current` and invoke it in both `onPreviewKeyEvent` and `pointerInput` so
the screensaver doesn't trigger while the user is interacting with a menu.

---

## 6. Performance & Compose hygiene

- **Defer state reads:** Pass `progressProvider: () -> Map<String, Float>` /
  `progressProvider: () -> Float` and `currentRouteProvider: () -> Route` lambdas to leaf
  composables instead of raw values when state updates frequently.
- **Stable keys & `contentType`:** Every `LazyVerticalGrid` `item`/`items` call must specify
  both `key` and `contentType` for smooth TV scrolling.
- **Coil image sizing:** `MediaCard` requests `.size(200)` and `.crossfade(false)` to keep
  low-end Android TV boxes jank-free during fast D-pad scrolling. Use full resolution only for
  hero backdrops (`FeaturedSection`, `PosterSection`, `ScreensaverScreen`).
- **Immutability:** Annotate UI state and models with `@Immutable` / `@Stable`; rely on
  `compose_stability.txt` for `kotlin.collections.List`.

---

## 7. Quick review checklist for UI PRs

- [ ] Uses `androidx.tv.material3` (`MaterialTheme`, `Text`, `Surface`, `Button`, `Icon`) — no
  accidental `androidx.compose.material3.MaterialTheme` imports.
- [ ] Zero hardcoded hex colors; all spacing uses `MaterialTheme.spacing.*` (except fixed icon,
  stroke, or intrinsic component sizes).
- [ ] All interactive elements have `selectablePulse` and `scale = *.None` where applicable.
- [ ] Long-clickable items include `handleMenuAsLongClick`; one-shot actions use `suppressKeyRepeat`.
- [ ] Focus returns cleanly after navigating back or closing an overlay menu.
- [ ] Any new `Dialog` / `Popup` calls `LocalInteractionReset.current` on key and pointer events.
- [ ] Zero hardcoded user-facing strings: all text, labels, button titles, toasts, and descriptions
  use `stringResource()`, `pluralStringResource()`, or `TextValue.StringResource` — no literal `"..."`
  strings or `TextValue.DynamicString("...")` for static messages.
- [ ] All strings live in `core/ui` `values/strings.xml` **and** `values-pl/strings.xml` (or `core/player`
  for player-local strings). String concatenation with localized text is prohibited; parameterized
  resources (e.g. `"%1$s %2$s"`) are used instead.
- [ ] `LazyVerticalGrid` items specify `key` and `contentType`.
