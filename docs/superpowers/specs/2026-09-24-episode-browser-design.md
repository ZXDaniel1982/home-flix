# Design — Episode Browser During Playback

**Date:** 2026-09-24
**Status:** Accepted
**Scope:** New player affordance in both clients (web frontend + Android app). Builds on the player work from 2026-09-18/2026-09-20.

## 1. Context

Both players now support season-scoped `|<` / `>|` navigation, but to jump to an arbitrary episode the user must go back to the series page, find the season, and tap the episode. This design adds an **episodes button** in the player that opens a browsable list of the **current season's** episodes, with the current episode highlighted; selecting any episode plays it.

Existing building blocks:

- **Web:** `VideoPlayer.svelte` has a custom control bar and a `goTo(episode)` helper that navigates to `/tv/{seriesId}/play/{id}?autoplay=1`. `items.ts` has `getSeasons(seriesId)` and `getEpisodes(seasonId)`.
- **Android:** `PlayerScreen.kt` has a custom control row; `PlayerViewModel` already resolves `isEpisode` and exposes `onPlayEpisode(id)`. `MovieRepository` has `getSeasons(seriesId)` and `getEpisodes(seasonId)`; `SeriesDetailViewModel` already builds a seasons-with-episodes structure.

Constraint from `AGENTS.md`: no server-side transcoding; clients direct-play H.264/AAC MP4.

## 2. Requirements

- **R1 — Episodes only.** The episodes button is shown only while playing an Episode. Movies keep the current controls with no such button.
- **R2 — Button placement.** An icon button in the player's control bar (web and Android), distinct from the existing prev/play-pause/next controls.
- **R3 — Season list.** Activating the button opens a panel listing the **current season's** episodes in episode order, labelled with the season (e.g., "Season 1").
- **R4 — Current highlighted.** The currently playing episode is visibly marked and is not a dead action.
- **R5 — Select to play.** Selecting an episode plays it immediately using the existing navigation path (same as prev/next: report the current episode stopped, navigate to the new episode, autoplay).
- **R6 — Lazy load.** Episode data is fetched when the panel is first opened, not during player startup. The panel shows a loading state and an error state; a failed fetch never affects playback.
- **R7 — Playback continues.** Opening the panel does not pause playback; the panel can be dismissed without changing the episode.
- **R8 — Dismiss.** Web: close button, click outside, or `Escape`. Android: swipe down / back / tap outside.
- **R9 — Parity.** Behavior is identical in web and Android; only the presentation differs.

## 3. Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Data source | The existing `getEpisodes(seasonId)` for the played item's `SeasonId` | Simplest; one request; no new API surface |
| Fetch timing | On first open of the panel; result cached in the ViewModel/component state | Keeps player startup fast; avoids repeated calls |
| Episode ordering | `getEpisodes` order (IndexNumber) | Consistent with the existing TV detail page |
| Android presentation | `ModalBottomSheet` with a `LazyColumn` (season header + episode rows) | Native pattern; back/swipe dismiss for free |
| Web presentation | Absolutely-positioned overlay inside the player, scrollable list, close button | Matches the existing countdown overlay approach; no new dependency |
| Current episode | Highlighted row with a "now playing" indicator; selecting it is a no-op/disabled | Clear affordance |
| Playback while browsing | Continue playing | Browsing should not interrupt; can be revisited later |
| Season label | Derived from the played item's `ParentIndexNumber` (season 0 = specials) | No extra request; matches the current season |
| Images | None in the panel (text only) | Keeps it fast and avoids extra requests |

## 4. Behavior contract (shared)

1. If the playing item is not an Episode, no episodes button is shown.
2. On first activation, fetch the current season's episodes via `getEpisodes(seasonId)`:
   - show a loading state while in flight;
   - on success, show the season's episodes;
   - on failure, show a short error message and a Retry action; playback is unaffected.
3. The currently playing episode is highlighted.
4. Selecting a different episode:
   - reports playback stopped for the current episode (existing behavior),
   - navigates to the selected episode with autoplay,
   - closes the panel.
5. Selecting the current episode does nothing.
6. Dismissing the panel keeps the current episode playing.

## 5. Web design

### 5.1 API layer — `web-frontend/src/lib/api/items.ts`

No new function: the panel uses the existing `getEpisodes(seasonId)` with the played item's `SeasonId`.

- `BaseItemDto` gains `ParentIndexNumber?: number` (the season number) for the season label.

### 5.2 Player — `web-frontend/src/lib/components/VideoPlayer.svelte`

- New state: `showEpisodes: boolean`, `episodesLoading: boolean`, `episodesError: string`, `episodes: BaseItemDto[]`, `seasonId: string`, `seasonNumber: number | null`.
- New control-bar button (icon, `aria-label="Episodes"`), rendered only when `neighbors !== null` (i.e., the item is an episode).
- On first activation (`showEpisodes` goes true and `episodes` is empty), call `getEpisodes(seasonId)`; cache the result. Show a loading line, then the list.
- Overlay panel: absolutely positioned over the player (like the countdown overlay), scrollable, with a header ("Episodes"), a close button, and a "Season {seasonNumber}" heading. Each episode row shows `S{seasonNumber}E{episode.IndexNumber}` and the name; the current row is highlighted and disabled.
- Selecting a row calls `goTo(episode)` (existing helper), which reports stopped and navigates with `?autoplay=1`.
- `Escape` and clicking outside close the panel (via a `<svelte:window onkeydown>` and a backdrop).
- Opening the panel cancels any active end-of-episode countdown.

## 6. Android design

### 6.1 Data layer

No new repository method: the ViewModel uses the existing `getEpisodes(seasonId)`. `FakeMovieRepository.getEpisodes` exposes `episodesError` / `episodesCalls` for the ViewModel failure/idempotency tests.

### 6.2 ViewModel — `ui/screens/PlayerViewModel.kt`

- `sealed interface EpisodesState { object Idle; object Loading; data class Error(message); data class Loaded(episodes: List<BaseItemDto>) }`
- Tracks `playingItemId`, `playingSeasonNumber` (from `ParentIndexNumber`), and the played item's `seasonId`.
- `val episodesState: StateFlow<EpisodesState>`; `fun loadEpisodes()` — no-op if already `Loading`/`Loaded`; failures become `Error` (never affect the player `UiState`).
- Reuse the existing `isEpisode` from `UiState.Success`.

### 6.3 Screen — `ui/screens/PlayerScreen.kt`

- Add an episodes `IconButton` (e.g., `Icons.AutoMirrored.Filled.List` or `Icons.Filled.VideoLibrary`) to the bottom control row, only when `isEpisode`, calling `onShowEpisodes` -> `viewModel.loadEpisodes()` and setting local `showEpisodes = true`.
- When open, show a `ModalBottomSheet` containing:
  - loading / error (with Retry) / list states from `episodesState`;
  - a `LazyColumn` with a "Season {n}" header and the season's episode rows, current episode highlighted and disabled;
  - tapping a row calls `onPlayEpisode(id)` and dismisses the sheet.
- Playback continues while the sheet is open (ExoPlayer is untouched).

### 6.4 Navigation

No change — the existing `onPlayEpisode` callback in `AppNavHost` (pop current player entry, navigate to `Routes.player(id)`) is reused.

## 7. Error handling

- **Episode fetch fails:** the panel shows an error with Retry; the player keeps playing.
- **Not authenticated (401):** behave like the rest of the app (best-effort; no change to the player). If a fetch throws a 401, surface the generic error in the panel.
- **Rapid open/close:** `loadEpisodes()` is idempotent; in-flight results still populate the cached state.
- **Web overlay vs countdown:** opening the episodes panel cancels any active countdown.

## 8. Testing & verification

### Web (Vitest + Playwright)

- The panel reuses `getEpisodes`, already covered by unit tests; there is no new API function. UI is covered by the existing Playwright suite, which opens the panel, asserts the season heading and episode rows, the disabled current row, and navigation to another episode.

### Android (JUnit)

- `PlayerViewModelTest`: `loadEpisodes()` success sets `Loaded` with the fake's episodes; failure sets `Error`; calling twice does not reload; a 401 emits the unauthorized event; `EpisodesState` is independent of `UiState.Success`.
- `FakeMovieRepository.getEpisodes` exposes `episodesError` / `episodesCalls`.

### Manual verification

- Web and Android: from a mid-season episode, open the list, confirm the season heading and episodes and the highlighted current episode; select another episode → it plays and the panel closes; retry after a simulated failure; dismiss without changing the episode; movies show no episodes button.

## 9. Out of scope

- Episode thumbnails/images in the panel.
- Watched/unwatched badges, next-up ordering, or filtering.
- Browsing episodes from other seasons (the panel is limited to the current season).
- Cross-season prev/next (still season-scoped per the 2026-09-20 spec).
- Downloading or offline behavior.
- Search within the episode list.
- Theming/animation polish beyond a basic panel.

## 10. Definition of done

- Web type check, unit tests, lint, and build pass; Android unit tests pass.
- Manual verification per §8 passes in both clients.
- No new runtime dependencies.
- A plan document accompanies this spec and the work is executed test-first.
