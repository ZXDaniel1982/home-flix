# Design — Next Episode Playback (Auto-play + Next Episode Button)

**Date:** 2026-09-18
**Status:** Superseded — revised by `docs/superpowers/specs/2026-09-20-episode-navigation-controls-design.md`
**Scope:** Enhancement to TV episode playback in both clients (web frontend + Android app). Not tied to an existing project-plan story.

> **Superseded 2026-09-20.** The behavior below was implemented, shipped, and then revised. The 2026-09-20 spec is now authoritative. Key changes: (1) neighbor resolution and auto-play are **season-scoped** — no cross-season rollover; (2) the top-right text "Next Episode" button became **`|<` / `>|` icon buttons flanking play/pause**; (3) the web player uses a **custom control bar** instead of native `<video controls>`; (4) Android gained a previous-episode button. Read this document for history only.

## 1. Context

TV episodes play through a generic video player in both clients:

- **Web:** `web-frontend/src/lib/components/VideoPlayer.svelte` is used by both the movie route and the episode route `/tv/[seriesId]/play/[episodeId]`. It uses the browser's native `<video controls>` element and has no `ended` handling.
- **Android:** `PlayerScreen.kt` / `PlayerViewModel.kt` are used by the generic route `player/{movieId}`, which is also reused for episodes. The player's play button restarts from 0 when the ExoPlayer reaches `STATE_ENDED`.

In both clients, when an episode finishes the player stops with no way to continue to the next episode. This design adds the two missing pieces: a **Next Episode button** during playback and **auto-play of the next episode** after a short countdown.

Constraint from `AGENTS.md`: no server-side transcoding; clients direct-play H.264/AAC MP4. Episode ordering comes from Jellyfin metadata via its REST API / Kotlin SDK.

## 2. Requirements

- **R1 — Episodes only.** Behavior applies only when the playing item is an Episode. Movie playback is unchanged.
- **R2 — Next in series order.** The next episode is the next one in the current season by `IndexNumber`; if the current episode is the season finale, it is the first episode of the next season (seasons ordered by `IndexNumber`). If the current episode is the last episode of the last season, there is no next episode.
- **R3 — Next Episode button.** A button is visible for the whole duration of playback whenever a next episode exists, so the user can skip ahead immediately.
- **R4 — Countdown auto-play.** When the episode reaches its natural end, a centered overlay shows a countdown (8 seconds) with **Play Now** and **Cancel**. At zero, the next episode starts. **Cancel** stops the countdown and leaves the video ended. **Play Now** advances immediately.
- **R5 — No next episode.** No button and no countdown overlay; the video ends as it does today.
- **R6 — Parity.** The behavior contract is identical in web and Android.

## 3. Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Next-episode resolution | Series order, reusing existing `getSeasons` / `getEpisodes` helpers | Deterministic, handles season boundaries, no new API surface or endpoint semantics to verify |
| End-of-episode UX | 8-second countdown overlay with Play Now / Cancel | Familiar Jellyfin/Netflix pattern; avoids silently forcing the next episode on the user |
| Next Episode button | Always visible during playback (when a next exists) | Lets the user skip ahead at any time |
| Web navigation to next | SvelteKit `goto()` to the next episode URL with `?autoplay=1` | Reuses the existing `$effect` reload; the URL stays an accurate, shareable link |
| Android navigation to next | `navigate(player/{nextId})` popping the current player entry | New nav entry gets a fresh ViewModel that reloads; Back returns to the series, not the previous episode |
| Countdown length | 8 seconds, one constant per client | Parity between clients |
| Season 0 (specials) | No special handling; ordered by `IndexNumber` like any season | Keeps the algorithm simple; specials placement is a metadata concern |
| Autoplay blocked (web) | Leave video paused; keep the Next Episode button available | Browser autoplay policy can reject programmatic play after navigation; never dead-end the user |

## 4. Behavior contract (shared)

Both clients implement the same rules.

**Next-episode resolution** — given the current item and the series metadata:

1. If the current item is not an Episode, or has no `SeriesId`/`SeasonId`, return `null`.
2. Fetch the series' seasons (ordered by `IndexNumber`) and the current season's episodes (ordered by `IndexNumber`).
3. Find the current episode in the current season's episode list by `Id`.
   - If it is not found, return `null`.
   - If a later episode exists in the same season, return the first one after it.
4. Otherwise (current is the season finale): find the current season in the season list. If a later season exists and has episodes, return its first episode.
5. Otherwise return `null`.

**Countdown lifecycle:**

- Starts only on the natural end of playback (`ended` event / `STATE_ENDED`) and only when a next episode exists.
- Ticks down once per second; at zero, advance to the next episode.
- `Cancel` dismisses the overlay and does not advance.
- `Play Now` advances immediately.
- The timer is cleared when the overlay is dismissed and when the player component is disposed. A stale timer must never navigate.
- Manually stopping, pausing, or seeking does not start the countdown; only reaching the end does.

## 5. Web design

### 5.1 API layer

`web-frontend/src/lib/api/items.ts`

```ts
export async function getNextEpisode(episode: BaseItemDto): Promise<BaseItemDto | null>
```

- Implements the §4 resolution using the existing `getSeasons(seriesId)` and `getEpisodes(seasonId)` helpers.
- Returns `null` for non-episodes, missing `SeriesId`/`SeasonId`, an episode not present in its season, or the last episode.

`web-frontend/src/lib/api/types.ts`

- Add `SeasonId?: string` to `BaseItemDto` (needed to locate the current season).

### 5.2 Player component

`web-frontend/src/lib/components/VideoPlayer.svelte`

- New props: `autoplay?: boolean` (default `false`).
- New state: `nextEpisode: BaseItemDto | null`, `showCountdown: boolean`, `countdownRemaining: number`, and a timer handle.
- `load(itemId)` already fetches the item; extend it so that when `item.Type === 'Episode'`, it also calls `getNextEpisode(item)`. A failure in the next-episode lookup is swallowed and treated as "no next" — it must not break playback.
- `onended`: if `nextEpisode` exists, set `showCountdown = true` and start the 8-second interval; otherwise do nothing.
- `goToNext()`: clear the timer and `goto(resolve(`/tv/${seriesId}/play/${nextEpisode.Id}`) + '?autoplay=1')`.
- The `<video>` gets the `autoplay` attribute when the `autoplay` prop is true. Because autoplay may be rejected after navigation, the Next Episode button remains visible as the fallback path.
- Cleanup: clear the interval in the existing disposal `$effect` (and on route/`itemId` change) so no stale timer can navigate.

### 5.3 UI

- **Next Episode button:** a small overlay button in the top-right corner of the player, rendered only when `nextEpisode` exists. Keeps the native controls untouched.
- **Countdown overlay:** centered over the video, showing "Next episode in Ns" with `Play Now` and `Cancel` buttons.

### 5.4 Episode route

`web-frontend/src/routes/(app)/tv/[seriesId]/play/[episodeId]/+page.svelte`

- Read `autoplay=1` from the URL search params and pass it to `<VideoPlayer autoplay={...} />`.
- `backHref` is unchanged.

## 6. Android design

### 6.1 Repository

`MovieRepository` interface + `JellyfinMovieRepository`:

```kotlin
suspend fun getNextEpisode(itemId: String): BaseItemDto?
```

- Implemented in `JellyfinMovieRepository` using its existing `getMovie`, `getSeasons`, and `getEpisodes` methods and the §4 resolution.
- Returns `null` for non-episodes or when there is no next episode.

### 6.2 ViewModel

`PlayerViewModel.kt`

- Add a small value type, e.g. `data class NextEpisode(val id: String, val title: String)`.
- `UiState.Success` gains `nextEpisode: NextEpisode?`.
- In `load()`, after fetching the item, if it is an episode call `getNextEpisode(itemId)`; a failure is treated as `null` and does not fail the load.
- Existing report methods are unchanged.

### 6.3 Screen

`PlayerScreen.kt`

- `PlayerScreen` passes `nextEpisode` and an `onPlayNext: (String) -> Unit` callback into the private `VideoPlayer` composable.
- **Next Episode button:** added to the bottom controls row, visible whenever `nextEpisode != null`; clicking it calls `onPlayNext(nextId)`.
- **Countdown overlay:** when ExoPlayer reports `STATE_ENDED` and `nextEpisode != null`, show a centered Compose overlay with an 8-second countdown and `Play Now` / `Cancel`. A `LaunchedEffect` drives the countdown and is cancelled when the overlay is dismissed or the composable leaves composition.
- `Cancel` hides the overlay without advancing.

### 6.4 Navigation

`AppNavHost.kt`

- For the player route, provide `onPlayNext = { nextId -> navController.navigate(Routes.player(nextId)) { popUpTo(Routes.PLAYER) { inclusive = true } } }`, so the previous episode's entry is replaced and Back returns to the series detail screen.
- The route argument keeps its existing name (`movieId`); renaming it is out of scope.

## 7. Error handling

- **Next-episode lookup fails (either client):** behave as if no next episode exists — hide the button, never start the countdown. Playback itself is never affected.
- **Web autoplay rejected after navigation:** the video stays paused on the next episode; the Next Episode button is still available and the native controls work.
- **Timer after unmount:** timers are cleared on disposal, so no navigation occurs after the player is gone.
- **Progress reporting:** the web player reports playback stopped for the outgoing episode exactly once per playback session — from the `ended` handler and from `goToNext()` when advancing, or from the disposal cleanup if playback was abandoned — using a per-item guard to prevent duplicates; Android reports from `onDispose`. This ensures Jellyfin records the finished episode and marks it played even when advancing to the next episode.

## 8. Testing & verification

### Web (Vitest, existing setup)

- Unit tests for `getNextEpisode` in `web-frontend/src/lib/api/items.test.ts`:
  - mid-season episode → the following episode;
  - season finale → first episode of the next season;
  - last episode of the last season → `null`;
  - movie (non-episode) → `null`;
  - missing `SeriesId`/`SeasonId` → `null`.
- UI behavior (button, countdown, cancel, autoplay fallback) verified manually and/or via the existing Playwright setup; no Svelte component-test library is installed.

### Android (JUnit, existing setup)

- `PlayerViewModelTest`:
  - episode with a next episode → `Success.nextEpisode` is set;
  - movie → `nextEpisode == null`;
  - last episode → `nextEpisode == null`;
  - next-episode lookup throws → load still succeeds with `nextEpisode == null`.
- Update `FakeMovieRepository` in `app/src/test/kotlin/com/homeflix/app/TestDoubles.kt` for the new interface method. (The `androidTest` source set has no `MovieRepository` fake, so no change is needed there.)

### Commands

- Web: `npm run check`, `npm test`, `npm run lint`.
- Android: `./gradlew test` (module under `android-app/`).

## 9. Out of scope

- Auto-play / "up next" behavior for movies or next-up recommendations.
- A user setting to disable auto-play.
- Picture-in-picture, background playback, or cross-device resume changes.
- Special handling for season 0 (specials).
- Renaming the Android `player/{movieId}` route argument.
- Changes to Jellyfin's watched-state semantics.

## 10. Definition of done

- Web type check, unit tests, and lint pass; Android unit tests pass.
- Manual verification: mid-season advance, season-finale advance, last-episode (no button/countdown), Cancel, and Play Now all behave per §4.
- No new runtime dependencies.
