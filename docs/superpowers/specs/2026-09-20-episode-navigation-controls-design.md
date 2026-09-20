# Design — Season-Scoped Episode Navigation Controls

**Date:** 2026-09-20
**Status:** Draft (awaiting review)
**Scope:** Revision of the episode navigation UI added on 2026-09-18. Applies to both clients (web frontend + Android app).
**Supersedes:** `docs/superpowers/specs/2026-09-18-next-episode-playback-design.md`

## 1. Context

The 2026-09-18 design added a text **Next Episode** button and series-order auto-play (rolling into the next season at a finale). After using the deployed web build, the human partner wants a different interaction:

- No top-right text button.
- `|<` (previous episode) and `>|` (next episode) icon buttons flanking the existing play/pause control, bottom-left.
- Navigation is **season-scoped**: the buttons never cross a season boundary, and are greyed out / non-clickable at the first and last episode of a season.
- Auto-play at a season finale is removed (no cross-season rollover).

Because the web player currently uses the browser's native `<video controls>` bar (whose play/pause cannot be flanked by custom buttons), satisfying the layout requires replacing the native bar with a custom control bar. The Android player already has a custom control row.

Constraint from `AGENTS.md`: no server-side transcoding; clients direct-play H.264/AAC MP4. Episode ordering comes from Jellyfin metadata via its REST API / Kotlin SDK.

## 2. Requirements

- **R1 — Episodes only.** Previous/next controls exist only while playing an Episode. Movie playback is unchanged and shows no prev/next buttons.
- **R2 — Season-scoped neighbors.** For the current episode: `previous` is the preceding episode in the same season by `IndexNumber`; `next` is the following episode in the same season. At the first episode of a season `previous = null`; at the last episode of a season `next = null`. Seasons are never crossed.
- **R3 — Control layout.** In the bottom control bar: `|<` immediately left of play/pause, `>|` immediately right of play/pause. Icon-only, no text labels.
- **R4 — Disabled state.** When a neighbor is `null`, its button is disabled: greyed out and not clickable.
- **R5 — Countdown auto-play.** The end-of-episode countdown (8s, Play Now / Cancel) appears only when an in-season `next` exists. At a season finale there is no countdown and no auto-play.
- **R6 — Navigation.** Activating prev/next plays that episode immediately (web: navigate with `?autoplay=1`; Android: navigate to the player route). The current episode's playback-stopped state is reported first.
- **R7 — Web custom control bar.** Replaces native controls with: prev, play/pause, next, seek bar, current time / duration, fullscreen. No volume control.
- **R8 — Parity.** The behavior contract is identical in web and Android.

## 3. Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Neighbor resolution | Within the current season only, by `IndexNumber` | Matches the requested UX; removes the cross-season rollover and the extra season query |
| Neighbor query | Fetch the current season's episodes once and return the adjacent entries | One request; no `getSeasons` call needed |
| Prev/next icons | `\|<` style previous, `>\|` style next (bar + triangle), icon-only | Matches the requested glyphs and keeps the bar compact |
| Disabled rendering | `disabled` attribute + reduced opacity / default cursor | Greyed and non-clickable, standard semantics |
| Countdown scope | Only when an in-season next exists | Consistent with season-scoped buttons; no surprising jump to another season |
| Web control bar | Custom bar replacing native controls; always visible; essential controls only | Native play/pause cannot be flanked; volume stays with system/keyboard |
| Web play/pause at end | Restart from the beginning | Matches the Android player's existing behavior |
| Movies | Prev/next controls omitted entirely | A disabled prev/next on a movie is noise |
| Missing ids | Episode without `SeriesId` or `SeasonId` → both neighbors `null` | Cannot build a navigation URL or locate the season; fail safe |

## 4. Behavior contract (shared)

Both clients implement the same rules.

**Neighbor resolution** — given the current item:

1. If the current item is **not an Episode**, return `null` (no neighbor controls at all).
2. If the Episode has no `SeriesId` or no `SeasonId`, return `{ previous: null, next: null }`.
3. Fetch the current season's episodes (`SeasonId`), ordered by `IndexNumber` ascending.
4. Find the current episode by `Id`.
   - If it is not found, return `{ previous: null, next: null }`.
5. `previous` = the episode immediately before the current one, or `null`.
6. `next` = the episode immediately after the current one, or `null`.

The `null` (non-episode) result means "hide the controls"; a `{ previous, next }` object means "this is an episode — show both controls, disabling whichever neighbor is `null`". This distinction matters for a single-episode season, where both neighbors are `null` but the controls must still appear (greyed).

**Countdown lifecycle:**

- Starts only on the natural end of playback (`ended` event / `STATE_ENDED`) and only when `next` is non-null.
- Ticks down once per second; at zero, play `next`.
- `Cancel` dismisses the overlay and does not advance.
- `Play Now` advances immediately.
- The timer is cleared when the overlay is dismissed and when the player is disposed; a stale timer must never navigate.
- Pausing, seeking, or stopping manually does not start the countdown.

**Button behavior:**

- Prev/next render only for Episodes and are disabled (greyed, not clickable) when their neighbor is `null`.
- Activating either reports playback stopped for the current episode, then navigates to the neighbor and plays it.

## 5. Web design

### 5.1 API layer

`web-frontend/src/lib/api/items.ts` — replace `getNextEpisode` with:

```ts
export interface EpisodeNeighbors {
	previous: BaseItemDto | null;
	next: BaseItemDto | null;
}

export async function getEpisodeNeighbors(episode: BaseItemDto): Promise<EpisodeNeighbors | null>
```

- Implements §4 using `getEpisodes(seasonId)` only. No `getSeasons` call.
- Returns `null` for any non-Episode item; returns `{ previous: null, next: null }` for an Episode with missing ids or one absent from its season.

`web-frontend/src/lib/api/types.ts` already has `SeasonId?: string`; no change.

### 5.2 Player component

`web-frontend/src/lib/components/VideoPlayer.svelte`

- State: `neighbors: EpisodeNeighbors | null` (null = non-episode, controls hidden), `isPlaying: boolean`, plus the existing countdown state and timer.
- `load()` sets `neighbors` via `getEpisodeNeighbors(item)`. A lookup failure is handled without breaking playback: for an Episode it falls back to `{ previous: null, next: null }` (controls visible but disabled); for a non-episode it stays `null`.
- Replace the native `controls` attribute with a custom control bar:
  - `|<` previous button, play/pause button, `>|` next button, current time, seek `<input type="range">`, duration, fullscreen button.
  - Inline SVG icons with `aria-label`s; no text labels.
  - Always visible; semi-transparent background.
  - Seek uses a local scrub value while dragging, then sets `video.currentTime`.
  - Fullscreen uses `requestFullscreen()` / `exitFullscreen()` on the player container and tracks `fullscreenchange`.
  - Play/pause toggles playback; when playback has ended it restarts from 0.
  - Prev/next controls are hidden when `neighbors === null` (movies), and each is `disabled` when its own neighbor is `null` (first/last of season, single-episode season).
- `goToPrevious()` mirrors `goToNext()`: clear the countdown, `reportStopped()`, then `goto(resolve(`/tv/${seriesId}/play/${id}?autoplay=1`))`.
- Remove the top-right `.next` button and its CSS.
- The countdown overlay is unchanged in appearance and behavior, except it now only triggers for an in-season next.
- Keep the existing stop-report guarding and autoplay fallback.

### 5.3 Episode route

`web-frontend/src/routes/(app)/tv/[seriesId]/play/[episodeId]/+page.svelte` is unchanged (still reads `?autoplay=1`).

## 6. Android design

### 6.1 Repository

`data/NextEpisode.kt`:

- Keep `nextEpisodeInSeason(episodes, currentId)`.
- Add `previousEpisodeInSeason(episodes, currentId): BaseItemDto?`.
- Remove `nextSeason` and `resolveNextEpisode` (no rollover).

`data/MovieRepository.kt`:

- Replace `getNextEpisode(itemId)` with `getAdjacentEpisodes(itemId): AdjacentEpisodes?`, where
  `data class AdjacentEpisodes(val previous: BaseItemDto?, val next: BaseItemDto?)`.
- Returns `null` for a non-Episode. For an Episode it always returns an `AdjacentEpisodes` (each field is `null` when `seasonId` is missing or there is no neighbor), so the UI can show disabled controls rather than hiding them.
- Implementation: `getMovie(itemId)`, guard Episode, `getEpisodes(seasonId)`, then the two helpers. No `getSeasons`.

### 6.2 ViewModel

`ui/screens/PlayerViewModel.kt`:

- Replace `NextEpisode(id, title)` with `data class EpisodeRef(val id: String)` (the title was never displayed).
- `UiState.Success` gains `isEpisode: Boolean`, `previousEpisode: EpisodeRef?`, and `nextEpisode: EpisodeRef?`.
- `load()` calls `getAdjacentEpisodes(movieId)`; `isEpisode = adjacent != null`; each neighbor maps to `EpisodeRef?.id`. A lookup failure yields `isEpisode = false` for movies and `true` with both neighbors `null` for episodes.

### 6.3 Screen

`ui/screens/PlayerScreen.kt`:

- Remove the `Next Episode` `TextButton`.
- In the bottom control row, render, in order: `SkipPrevious` `IconButton`, play/pause `IconButton`, `SkipNext` `IconButton`, elapsed time, seek slider, duration.
- Prev/next use `Icons.Filled.SkipPrevious` / `Icons.Filled.SkipNext` with `contentDescription` and `enabled = neighbor != null`; disabled buttons use a dimmed tint.
- Prev/next render only when `isEpisode`; each is `enabled = neighbor != null`. They are omitted for movies.
- Countdown overlay and `STATE_ENDED` trigger require an in-season `next`.

### 6.4 Navigation

`AppNavHost.kt`:

- Replace the `onPlayNext` callback with a single `onPlayEpisode: (String) -> Unit`, used by both prev and next:
  `navController.navigate(Routes.player(id)) { popUpTo(Routes.PLAYER) { inclusive = true } }`.

## 7. Error handling

- **Neighbor lookup fails (either client):** behave as if both neighbors are absent — buttons disabled/omitted, no countdown. Playback is never affected.
- **Web autoplay rejected after navigation:** the target episode stays paused; the controls remain usable.
- **Timer after unmount:** timers are cleared on disposal, so no navigation occurs after the player is gone.
- **Fullscreen API unavailable:** the fullscreen button is hidden or does nothing without error.
- **Progress reporting:** unchanged from the prior design — the web player reports stopped once per playback session with a per-item guard; Android reports from `onDispose`.

## 8. Testing & verification

### Web (Vitest)

`web-frontend/src/lib/api/items.test.ts`, replace the `getNextEpisode` cases with `getEpisodeNeighbors`:

- middle episode → `previous` and `next` both set;
- first episode of a season → `previous = null`, `next` set;
- last episode of a season → `next = null` (even when a later season exists);
- single-episode season → object with both `null`;
- movie → `null` result, no fetch;
- missing `SeasonId`/`SeriesId` on an Episode → `{ previous: null, next: null }`, no fetch.

UI behavior (custom bar, disabled buttons, countdown absent at a finale, fullscreen, seek) is verified manually; no Svelte component-test library is installed.

### Android (JUnit)

- `NextEpisodeTest`: `nextEpisodeInSeason` and `previousEpisodeInSeason` boundaries (first/last/absent/middle); remove `nextSeason` tests.
- `PlayerViewModelTest`: episode with both neighbors sets them; first episode → `previous == null`; last episode → `next == null`; movie → `isEpisode == false` and both `null`; lookup failure → load succeeds with `isEpisode == true` and both `null`.
- Update `FakeMovieRepository` in `app/src/test/kotlin/com/homeflix/app/TestDoubles.kt` for `getAdjacentEpisodes`.

### Commands

- Web: `npm run check`, `npm test`, `npm run lint`, `npm run build`.
- Android: `./gradlew :app:testDebugUnitTest`.

### Manual verification

- Web and Android: first episode of a season (prev greyed), middle episode (both active), last episode of a season (next greyed, no countdown), single-episode season (both greyed), a movie (no prev/next), prev/next navigation, and custom-bar play/pause, seek, and fullscreen.
- Android install on Windows (MuMu Player) per `docs/development.md` §6.4; web redeployed to the Orange Pi.

## 9. Out of scope

- Cross-season navigation or auto-play (explicitly removed).
- Volume control in the web custom bar.
- Auto-hiding the web control bar.
- Captions/subtitles, Picture-in-Picture, keyboard shortcuts.
- `up next` recommendations, user settings, background playback.
- Renaming the Android `player/{movieId}` route argument.

## 10. Definition of done

- Web type check, unit tests, lint, and build pass; Android unit tests pass.
- Manual verification per §8 passes in both clients.
- No new runtime dependencies.
- The 2026-09-18 spec is marked superseded and `docs/user-guide.md` reflects the new controls.
