# Next Episode Playback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Superseded 2026-09-20:** the behavior implemented by this plan was revised to season-scoped prev/next icon controls. See `docs/superpowers/specs/2026-09-20-episode-navigation-controls-design.md` and its plan `docs/superpowers/plans/2026-09-20-episode-navigation-controls.md`. This file is a historical record.

**Goal:** Add auto-play of the next episode (after an 8-second countdown) and an always-available Next Episode button to episode playback in both the web frontend and the Android app.

**Architecture:** Each client resolves the next episode from Jellyfin metadata using existing season/episode queries, in series order (current season by `IndexNumber`, rolling into the next season at a finale). The web player computes it in `getNextEpisode()` and navigates to the next episode URL with `?autoplay=1`; the Android player exposes it on `PlayerViewModel.UiState.Success` and navigates to a new player route. The resolution primitives are pure functions unit-tested in isolation; the UI wiring is verified by type-check/lint/compile and manual playback.

**Tech Stack:** SvelteKit 2 / Svelte 5 runes / TypeScript / Vitest (web); Kotlin / Jetpack Compose / Media3 ExoPlayer / jellyfin-sdk-kotlin / JUnit4 (Android).

**Spec:** `docs/superpowers/specs/2026-09-18-next-episode-playback-design.md`

## Global Constraints

- **No transcoding.** Clients direct-play H.264/AAC MP4 only; no server-side changes.
- **Web:** SvelteKit + TypeScript, plain scoped CSS, **no Tailwind**, **no new runtime dependencies**.
- **Android:** Kotlin + Jetpack Compose, MVVM, jellyfin-sdk-kotlin, Media3 ExoPlayer.
- **Shared behavior (both clients):** 8-second countdown, series-order next episode, Next Episode button visible during the whole episode when a next exists, no button/countdown on the last episode.
- **Never hardcode IPs/hosts**; URLs come from existing config/session.
- **No failed next-episode lookup may break playback** — treat it as "no next episode".
- **Commit after each task** with the message shown in the task.

---

## File Structure

**Web (`web-frontend/`)**
- `src/lib/api/types.ts` — add `SeasonId` to `BaseItemDto`.
- `src/lib/api/items.ts` — add `getNextEpisode()`; reuses `getSeasons`/`getEpisodes`.
- `src/lib/api/items.test.ts` — unit tests for `getNextEpisode`.
- `src/lib/components/VideoPlayer.svelte` — countdown, button, autoplay, navigation.
- `src/routes/(app)/tv/[seriesId]/play/[episodeId]/+page.svelte` — read `?autoplay=1`.

**Android (`android-app/`)**
- `app/src/main/kotlin/com/homeflix/app/data/NextEpisode.kt` — new pure resolution helpers.
- `app/src/main/kotlin/com/homeflix/app/data/MovieRepository.kt` — interface + impl `getNextEpisode()`.
- `app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerViewModel.kt` — expose `nextEpisode`.
- `app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerScreen.kt` — button, countdown overlay, `onPlayNext`.
- `app/src/main/kotlin/com/homeflix/app/navigation/AppNavHost.kt` — wire `onPlayNext`.
- `app/src/test/kotlin/com/homeflix/app/TestDoubles.kt` — fake updates.
- `app/src/test/kotlin/com/homeflix/app/NextEpisodeTest.kt` — new helper tests.
- `app/src/test/kotlin/com/homeflix/app/PlayerViewModelTest.kt` — new ViewModel tests.

---

## Task 1: Web next-episode resolution API

**Files:**
- Modify: `web-frontend/src/lib/api/types.ts`
- Modify: `web-frontend/src/lib/api/items.ts`
- Test: `web-frontend/src/lib/api/items.test.ts`

**Interfaces:**
- Consumes: existing `getSeasons(seriesId): Promise<BaseItemDto[]>` and `getEpisodes(seasonId): Promise<BaseItemDto[]>` in `items.ts`.
- Produces: `getNextEpisode(episode: BaseItemDto): Promise<BaseItemDto | null>`; `BaseItemDto.SeasonId?: string`.

- [x] **Step 1: Add the failing tests**

Append a new `describe` block to `web-frontend/src/lib/api/items.test.ts`. First extend the imports and add an `episode` fixture helper near the top (after `jsonResponse`):

```ts
import { getItem, getMovies, getNextEpisode, search } from './items';
import { setSession } from './session';
import type { BaseItemDto } from './types';

function episode(overrides: Partial<BaseItemDto>): BaseItemDto {
	return {
		Id: 'e1',
		Name: 'Episode',
		Type: 'Episode',
		SeriesId: 'series1',
		SeasonId: 's1',
		IndexNumber: 1,
		...overrides
	};
}
```

Then append:

```ts
describe('getNextEpisode', () => {
	it('returns the next episode in the same season', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		vi.stubGlobal(
			'fetch',
			vi.fn<typeof fetch>().mockImplementation(async (input) => {
				const url = input as string;
				if (url.includes('IncludeItemTypes=Season')) {
					return jsonResponse({ Items: [{ Id: 's1', Name: 'Season 1', IndexNumber: 1 }] });
				}
				return jsonResponse({
					Items: [
						{ Id: 'e1', Name: 'One', Type: 'Episode', SeriesId: 'series1', SeasonId: 's1' },
						{ Id: 'e2', Name: 'Two', Type: 'Episode', SeriesId: 'series1', SeasonId: 's1' }
					]
				});
			})
		);

		const next = await getNextEpisode(episode({ Id: 'e1' }));

		expect(next?.Id).toBe('e2');
	});

	it('returns the first episode of the next season after a season finale', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		vi.stubGlobal(
			'fetch',
			vi.fn<typeof fetch>().mockImplementation(async (input) => {
				const url = input as string;
				if (url.includes('IncludeItemTypes=Season')) {
					return jsonResponse({
						Items: [
							{ Id: 's1', Name: 'Season 1', IndexNumber: 1 },
							{ Id: 's2', Name: 'Season 2', IndexNumber: 2 }
						]
					});
				}
				if (url.includes('ParentId=s2')) {
					return jsonResponse({
						Items: [
							{ Id: 'e2s1', Name: 'S2E1', Type: 'Episode', SeriesId: 'series1', SeasonId: 's2' }
						]
					});
				}
				return jsonResponse({
					Items: [{ Id: 'e1', Name: 'One', Type: 'Episode', SeriesId: 'series1', SeasonId: 's1' }]
				});
			})
		);

		const next = await getNextEpisode(episode({ Id: 'e1' }));

		expect(next?.Id).toBe('e2s1');
	});

	it('returns null for the last episode of the last season', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		vi.stubGlobal(
			'fetch',
			vi.fn<typeof fetch>().mockImplementation(async (input) => {
				const url = input as string;
				if (url.includes('IncludeItemTypes=Season')) {
					return jsonResponse({ Items: [{ Id: 's1', Name: 'Season 1', IndexNumber: 1 }] });
				}
				return jsonResponse({
					Items: [{ Id: 'e1', Name: 'One', Type: 'Episode', SeriesId: 'series1', SeasonId: 's1' }]
				});
			})
		);

		expect(await getNextEpisode(episode({ Id: 'e1' }))).toBeNull();
	});

	it('returns null for a non-episode item without fetching', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		const fn = vi.fn<typeof fetch>();
		vi.stubGlobal('fetch', fn);

		expect(await getNextEpisode({ Id: 'm1', Name: 'Movie', Type: 'Movie' })).toBeNull();
		expect(fn).not.toHaveBeenCalled();
	});

	it('returns null when the episode has no season id', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		const fn = vi.fn<typeof fetch>();
		vi.stubGlobal('fetch', fn);

		expect(
			await getNextEpisode({ Id: 'e1', Name: 'One', Type: 'Episode', SeriesId: 'series1' })
		).toBeNull();
		expect(fn).not.toHaveBeenCalled();
	});
});
```

- [x] **Step 2: Run tests to verify they fail**

Run (from `web-frontend/`): `npm test -- src/lib/api/items.test.ts`
Expected: FAIL — `getNextEpisode` is not exported / not a function, and the `SeasonId` property is a type error.

- [x] **Step 3: Add `SeasonId` to the type**

In `web-frontend/src/lib/api/types.ts`, add the field to `BaseItemDto` after `SeriesId`:

```ts
	SeriesId?: string;
	SeasonId?: string;
```

- [x] **Step 4: Implement `getNextEpisode`**

In `web-frontend/src/lib/api/items.ts`, add after `getEpisodes`:

```ts
export async function getNextEpisode(episode: BaseItemDto): Promise<BaseItemDto | null> {
	if (episode.Type !== 'Episode' || !episode.SeriesId || !episode.SeasonId) {
		return null;
	}
	const seasons = await getSeasons(episode.SeriesId);
	const episodes = await getEpisodes(episode.SeasonId);
	const index = episodes.findIndex((candidate) => candidate.Id === episode.Id);
	if (index === -1) {
		return null;
	}
	const nextInSeason = episodes[index + 1];
	if (nextInSeason) {
		return nextInSeason;
	}
	const seasonIndex = seasons.findIndex((season) => season.Id === episode.SeasonId);
	const nextSeason = seasonIndex === -1 ? undefined : seasons[seasonIndex + 1];
	if (!nextSeason) {
		return null;
	}
	const nextSeasonEpisodes = await getEpisodes(nextSeason.Id);
	return nextSeasonEpisodes[0] ?? null;
}
```

- [x] **Step 5: Run tests to verify they pass**

Run (from `web-frontend/`): `npm test -- src/lib/api/items.test.ts`
Expected: PASS (all `getNextEpisode` cases).

- [x] **Step 6: Run type check and lint**

Run (from `web-frontend/`): `npm run check && npm run lint`
Expected: no errors.

- [x] **Step 7: Commit**

```bash
git add web-frontend/src/lib/api/types.ts web-frontend/src/lib/api/items.ts web-frontend/src/lib/api/items.test.ts
git commit -m "feat(web): resolve next episode in series order"
```

---

## Task 2: Web player countdown, Next Episode button, and autoplay

**Files:**
- Modify: `web-frontend/src/lib/components/VideoPlayer.svelte`
- Modify: `web-frontend/src/routes/(app)/tv/[seriesId]/play/[episodeId]/+page.svelte`

**Interfaces:**
- Consumes: `getNextEpisode()` from Task 1; `resolve` from `$app/paths`; `goto` from `$app/navigation`.
- Produces: `VideoPlayer` accepts a new optional `autoplay?: boolean` prop (default `false`).

- [x] **Step 1: Replace the `VideoPlayer.svelte` script block**

Replace the entire `<script lang="ts"> … </script>` block in `web-frontend/src/lib/components/VideoPlayer.svelte` with:

```svelte
<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import {
		getPlaybackInfo,
		streamUrl,
		reportPlaybackStarted,
		reportPlaybackProgress,
		reportPlaybackStopped
	} from '$lib/api/playback';
	import { getItem, getNextEpisode } from '$lib/api/items';
	import type { MediaSourceInfo, BaseItemDto } from '$lib/api/types';

	let {
		itemId,
		backHref,
		autoplay = false
	}: {
		itemId: string;
		backHref?: `/movies/${string}` | `/tv/${string}`;
		autoplay?: boolean;
	} = $props();

	const PROGRESS_INTERVAL_MS = 10_000;
	const COUNTDOWN_SECONDS = 8;

	let streamSrc = $state('');
	let loading = $state(true);
	let error = $state('');
	let playbackError = $state(false);
	let video = $state<HTMLVideoElement | null>(null);
	let nextEpisode = $state<BaseItemDto | null>(null);
	let seriesId = $state('');
	let showCountdown = $state(false);
	let countdownRemaining = $state(COUNTDOWN_SECONDS);

	let mediaSourceId = '';
	let resumeTicks = 0;
	let lastReportAt = 0;
	let countdownTimer: ReturnType<typeof setInterval> | null = null;

	$effect(() => {
		streamSrc = '';
		error = '';
		playbackError = false;
		loading = true;
		mediaSourceId = '';
		resumeTicks = 0;
		lastReportAt = 0;
		nextEpisode = null;
		seriesId = '';
		cancelCountdown();
		if (!itemId) {
			error = 'Invalid item.';
			loading = false;
			return;
		}
		load(itemId)
			.then(({ src, msId, ticks, next, series }) => {
				streamSrc = src;
				mediaSourceId = msId;
				resumeTicks = ticks;
				nextEpisode = next;
				seriesId = series;
			})
			.catch(() => {
				error = 'Could not load the video.';
			})
			.finally(() => {
				loading = false;
			});
	});

	async function load(id: string) {
		const [info, item] = await Promise.all([getPlaybackInfo(id), getItem(id)]);
		const sources = info.MediaSources ?? [];
		const source: MediaSourceInfo | undefined =
			sources.find((s) => s.SupportsDirectPlay) ?? sources[0];
		if (!source) {
			throw new Error('No playable media source');
		}
		const next = item.Type === 'Episode' ? await getNextEpisode(item).catch(() => null) : null;
		return {
			src: streamUrl(id, source.Id),
			msId: source.Id,
			ticks: item.UserData?.PlaybackPositionTicks ?? 0,
			next,
			series: item.SeriesId ?? ''
		};
	}

	function currentTicks(): number {
		return Math.floor((video?.currentTime ?? 0) * 10_000_000);
	}

	function onLoadedMetadata() {
		if (video && resumeTicks > 0) {
			video.currentTime = resumeTicks / 10_000_000;
		}
	}

	function onPlay() {
		reportPlaybackStarted(itemId, mediaSourceId, currentTicks()).catch(() => {});
	}

	function onTimeUpdate() {
		const now = Date.now();
		if (now - lastReportAt < PROGRESS_INTERVAL_MS) return;
		lastReportAt = now;
		sendProgress();
	}

	function onPause() {
		sendProgress();
	}

	function onEnded() {
		if (!nextEpisode) return;
		startCountdown();
	}

	function startCountdown() {
		clearCountdown();
		countdownRemaining = COUNTDOWN_SECONDS;
		showCountdown = true;
		countdownTimer = setInterval(() => {
			countdownRemaining -= 1;
			if (countdownRemaining <= 0) {
				goToNext();
			}
		}, 1000);
	}

	function cancelCountdown() {
		clearCountdown();
		showCountdown = false;
	}

	function clearCountdown() {
		if (countdownTimer !== null) {
			clearInterval(countdownTimer);
			countdownTimer = null;
		}
	}

	function goToNext() {
		if (!nextEpisode) return;
		clearCountdown();
		showCountdown = false;
		goto(resolve(`/tv/${seriesId}/play/${nextEpisode.Id}?autoplay=1`));
	}

	function sendProgress() {
		if (!mediaSourceId) return;
		reportPlaybackProgress(itemId, mediaSourceId, currentTicks(), video?.paused ?? true).catch(
			() => {}
		);
	}

	$effect(() => {
		const el = video;
		if (!el) return;
		return () => {
			clearCountdown();
			const ticks = Math.floor(el.currentTime * 10_000_000);
			if (mediaSourceId) {
				reportPlaybackStopped(itemId, mediaSourceId, ticks).catch(() => {});
			}
		};
	});
</script>
```

- [x] **Step 2: Update the `VideoPlayer.svelte` markup**

In the same file, inside the `{:else}` branch that contains `<div class="player">`, add the `autoplay` attribute, the `onended` handler, and the two overlays. The block becomes:

```svelte
		<div class="player">
			<!-- svelte-ignore a11y_media_has_caption -->
			<video
				controls
				autoplay={autoplay}
				src={streamSrc}
				bind:this={video}
				onloadedmetadata={onLoadedMetadata}
				onplay={onPlay}
				ontimeupdate={onTimeUpdate}
				onpause={onPause}
				onended={onEnded}
				onerror={() => (playbackError = true)}
			></video>
			{#if nextEpisode}
				<button class="next" type="button" onclick={goToNext}>Next Episode</button>
			{/if}
			{#if showCountdown && nextEpisode}
				<div class="countdown" role="dialog" aria-label="Next episode">
					<p>Next episode in {countdownRemaining}s</p>
					<div class="countdown-actions">
						<button type="button" onclick={goToNext}>Play Now</button>
						<button type="button" onclick={cancelCountdown}>Cancel</button>
					</div>
				</div>
			{/if}
		</div>
```

- [x] **Step 3: Update the `VideoPlayer.svelte` styles**

Replace the `.player` rule and add the new rules in the `<style>` block:

```css
	.player {
		position: relative;
		width: 100%;
		max-width: 60rem;
	}

	.next {
		position: absolute;
		top: 1rem;
		right: 1rem;
		padding: 0.5rem 0.875rem;
		border: none;
		border-radius: 0.375rem;
		background-color: rgba(0, 0, 0, 0.7);
		color: #fff;
		cursor: pointer;
	}

	.countdown {
		position: absolute;
		inset: 0;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 1rem;
		background-color: rgba(0, 0, 0, 0.75);
		color: #fff;
		text-align: center;
	}

	.countdown-actions {
		display: flex;
		gap: 0.75rem;
	}

	.countdown-actions button {
		padding: 0.5rem 1rem;
		border: none;
		border-radius: 0.375rem;
		cursor: pointer;
	}
```

- [x] **Step 4: Pass autoplay from the episode route**

Replace the contents of `web-frontend/src/routes/(app)/tv/[seriesId]/play/[episodeId]/+page.svelte` with:

```svelte
<script lang="ts">
	import { page } from '$app/state';
	import VideoPlayer from '$lib/components/VideoPlayer.svelte';

	const seriesId = $derived(page.params.seriesId ?? '');
	const episodeId = $derived(page.params.episodeId ?? '');
	const autoplay = $derived(page.url.searchParams.get('autoplay') === '1');
</script>

<VideoPlayer itemId={episodeId} backHref={`/tv/${seriesId}`} {autoplay} />
```

Leave the movie play route (`web-frontend/src/routes/(app)/movies/[id]/play/+page.svelte`) unchanged; it relies on the default `autoplay = false`.

- [x] **Step 5: Type check, lint, and build**

Run (from `web-frontend/`): `npm run check && npm run lint && npm run build`
Expected: all pass.

- [ ] **Step 6: Manual verification (dev server)**

Run (from `web-frontend/`): `npm run dev`, then in a browser:
1. Play a mid-season episode to the end → countdown overlay appears, then the next episode auto-plays.
2. During playback, click **Next Episode** → advances immediately.
3. On the countdown overlay, click **Cancel** → overlay disappears and the video stays ended.
4. Play the last episode of the last season → no Next Episode button and no countdown.
5. Play a movie → no Next Episode button and no countdown.

If autoplay is blocked by the browser after advancing, confirm the next episode is loaded, paused, with the Next Episode button still visible.

- [x] **Step 7: Commit**

```bash
git add web-frontend/src/lib/components/VideoPlayer.svelte "web-frontend/src/routes/(app)/tv/[seriesId]/play/[episodeId]/+page.svelte"
git commit -m "feat(web): auto-play next episode with countdown and next button"
```

---

## Task 3: Android next-episode resolution helpers and repository method

**Files:**
- Create: `android-app/app/src/main/kotlin/com/homeflix/app/data/NextEpisode.kt`
- Modify: `android-app/app/src/main/kotlin/com/homeflix/app/data/MovieRepository.kt`
- Modify: `android-app/app/src/test/kotlin/com/homeflix/app/TestDoubles.kt`
- Test: `android-app/app/src/test/kotlin/com/homeflix/app/NextEpisodeTest.kt`

**Interfaces:**
- Consumes: existing `JellyfinMovieRepository.getMovie`, `getSeasons`, `getEpisodes`; `FakeMovieRepository` in the test source set.
- Produces:
  - `internal fun nextEpisodeInSeason(episodes: List<BaseItemDto>, currentId: String): BaseItemDto?`
  - `internal fun nextSeason(seasons: List<BaseItemDto>, currentSeasonId: String): BaseItemDto?`
  - `MovieRepository.getNextEpisode(itemId: String): BaseItemDto?`
  - `FakeMovieRepository.nextEpisode: BaseItemDto?` and `FakeMovieRepository.nextEpisodeError: Exception?`

- [x] **Step 1: Write the failing helper tests**

Create `android-app/app/src/test/kotlin/com/homeflix/app/NextEpisodeTest.kt`:

```kotlin
package com.homeflix.app

import com.homeflix.app.data.nextEpisodeInSeason
import com.homeflix.app.data.nextSeason
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextEpisodeTest {

    private val season1 = "00000000-0000-0000-0000-000000000101"
    private val season2 = "00000000-0000-0000-0000-000000000102"
    private val episode1 = "00000000-0000-0000-0000-000000000201"
    private val episode2 = "00000000-0000-0000-0000-000000000202"
    private val episode3 = "00000000-0000-0000-0000-000000000203"

    @Test
    fun nextEpisodeInSeason_returnsFollowingEpisode() {
        val episodes = listOf(
            baseItem(episode1, "One", type = BaseItemKind.EPISODE, indexNumber = 1),
            baseItem(episode2, "Two", type = BaseItemKind.EPISODE, indexNumber = 2),
            baseItem(episode3, "Three", type = BaseItemKind.EPISODE, indexNumber = 3)
        )

        assertEquals(episode2, nextEpisodeInSeason(episodes, episode1)?.id?.toString())
    }

    @Test
    fun nextEpisodeInSeason_returnsNullForLastEpisode() {
        val episodes = listOf(baseItem(episode1, "One", type = BaseItemKind.EPISODE, indexNumber = 1))

        assertNull(nextEpisodeInSeason(episodes, episode1))
    }

    @Test
    fun nextEpisodeInSeason_returnsNullWhenCurrentIsMissing() {
        val episodes = listOf(baseItem(episode1, "One", type = BaseItemKind.EPISODE, indexNumber = 1))

        assertNull(nextEpisodeInSeason(episodes, episode2))
    }

    @Test
    fun nextSeason_returnsFollowingSeason() {
        val seasons = listOf(
            baseItem(season1, "Season 1", type = BaseItemKind.SEASON, indexNumber = 1),
            baseItem(season2, "Season 2", type = BaseItemKind.SEASON, indexNumber = 2)
        )

        assertEquals(season2, nextSeason(seasons, season1)?.id?.toString())
    }

    @Test
    fun nextSeason_returnsNullForLastSeason() {
        val seasons = listOf(baseItem(season1, "Season 1", type = BaseItemKind.SEASON, indexNumber = 1))

        assertNull(nextSeason(seasons, season1))
    }
}
```

- [x] **Step 2: Run tests to verify they fail**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest --tests "com.homeflix.app.NextEpisodeTest"`
Expected: FAIL to compile — `nextEpisodeInSeason` and `nextSeason` are unresolved.

- [x] **Step 3: Create the resolution helpers**

Create `android-app/app/src/main/kotlin/com/homeflix/app/data/NextEpisode.kt`:

```kotlin
package com.homeflix.app.data

import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Returns the episode immediately after [currentId] in [episodes] (which must be
 * sorted by IndexNumber), or null if [currentId] is absent or is the last episode.
 */
internal fun nextEpisodeInSeason(episodes: List<BaseItemDto>, currentId: String): BaseItemDto? {
    val index = episodes.indexOfFirst { it.id.toString() == currentId }
    if (index == -1) return null
    return episodes.getOrNull(index + 1)
}

/**
 * Returns the season immediately after [currentSeasonId] in [seasons] (which must be
 * sorted by IndexNumber), or null if [currentSeasonId] is absent or is the last season.
 */
internal fun nextSeason(seasons: List<BaseItemDto>, currentSeasonId: String): BaseItemDto? {
    val index = seasons.indexOfFirst { it.id.toString() == currentSeasonId }
    if (index == -1) return null
    return seasons.getOrNull(index + 1)
}
```

- [x] **Step 4: Run helper tests to verify they pass**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest --tests "com.homeflix.app.NextEpisodeTest"`
Expected: PASS.

- [x] **Step 5: Add `getNextEpisode` to the repository interface**

In `android-app/app/src/main/kotlin/com/homeflix/app/data/MovieRepository.kt`, add to the `MovieRepository` interface after `getEpisodes`:

```kotlin
    suspend fun getNextEpisode(itemId: String): BaseItemDto?
```

- [x] **Step 6: Implement it in `JellyfinMovieRepository`**

In the same file, add after the `getEpisodes` override:

```kotlin
    override suspend fun getNextEpisode(itemId: String): BaseItemDto? {
        val item = getMovie(itemId)
        if (item.type != BaseItemKind.EPISODE) return null
        val seriesId = item.seriesId?.toString() ?: return null
        val seasonId = item.seasonId?.toString() ?: return null
        val seasons = getSeasons(seriesId)
        val currentSeasonEpisodes = getEpisodes(seasonId)
        nextEpisodeInSeason(currentSeasonEpisodes, itemId)?.let { return it }
        val season = nextSeason(seasons, seasonId) ?: return null
        return getEpisodes(season.id.toString()).firstOrNull()
    }
```

`BaseItemKind` is already imported in this file.

- [x] **Step 7: Update `FakeMovieRepository`**

In `android-app/app/src/test/kotlin/com/homeflix/app/TestDoubles.kt`, add fields next to the other `var`s in `FakeMovieRepository`:

```kotlin
    var nextEpisode: BaseItemDto? = null
    var nextEpisodeError: Exception? = null
```

and add the override after `getEpisodes`:

```kotlin
    override suspend fun getNextEpisode(itemId: String): BaseItemDto? {
        nextEpisodeError?.let { throw it }
        error?.let { throw it }
        return nextEpisode
    }
```

- [x] **Step 8: Compile and run the unit test suite**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all existing tests plus `NextEpisodeTest` pass.

- [x] **Step 9: Commit**

```bash
git add android-app/app/src/main/kotlin/com/homeflix/app/data/NextEpisode.kt android-app/app/src/main/kotlin/com/homeflix/app/data/MovieRepository.kt android-app/app/src/test/kotlin/com/homeflix/app/TestDoubles.kt android-app/app/src/test/kotlin/com/homeflix/app/NextEpisodeTest.kt
git commit -m "feat(android): resolve next episode in series order"
```

---

## Task 4: Android ViewModel exposes the next episode

**Files:**
- Modify: `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerViewModel.kt`
- Test: `android-app/app/src/test/kotlin/com/homeflix/app/PlayerViewModelTest.kt`

**Interfaces:**
- Consumes: `MovieRepository.getNextEpisode()` and `FakeMovieRepository.nextEpisode` / `nextEpisodeError` from Task 3.
- Produces: `PlayerViewModel.NextEpisode(id: String, title: String)`; `PlayerViewModel.UiState.Success(stream, resumeTicks, nextEpisode: NextEpisode?)`.

- [x] **Step 1: Write the failing tests**

Append these tests to `android-app/app/src/test/kotlin/com/homeflix/app/PlayerViewModelTest.kt` (add `import org.junit.Assert.assertNull` and `import org.jellyfin.sdk.model.api.BaseItemKind`):

```kotlin
    @Test
    fun load_episodeWithNext_setsNextEpisode() = runTest(mainDispatcherRule.testDispatcher) {
        val nextId = "00000000-0000-0000-0000-000000000004"
        val repo = FakeMovieRepository().apply {
            stream = PlaybackStream("http://host/stream", "ms1")
            movie = baseItem(EPISODE_ID, "Episode 1", type = BaseItemKind.EPISODE, seriesId = SERIES_ID)
            nextEpisode = baseItem(nextId, "Episode 2", type = BaseItemKind.EPISODE, seriesId = SERIES_ID)
        }
        val viewModel = createViewModel(repo, movieId = EPISODE_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlayerViewModel.UiState.Success
        assertEquals(nextId, state.nextEpisode?.id)
        assertEquals("Episode 2", state.nextEpisode?.title)
    }

    @Test
    fun load_movie_hasNoNextEpisode() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply {
            stream = PlaybackStream("http://host/stream", "ms1")
            movie = baseItem(MOVIE_ID, "Movie")
            nextEpisode = baseItem(
                "00000000-0000-0000-0000-000000000004",
                "Should be ignored",
                type = BaseItemKind.EPISODE
            )
        }
        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlayerViewModel.UiState.Success
        assertNull(state.nextEpisode)
    }

    @Test
    fun load_nextEpisodeLookupFailure_stillSucceedsWithNull() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply {
            stream = PlaybackStream("http://host/stream", "ms1")
            movie = baseItem(EPISODE_ID, "Episode 1", type = BaseItemKind.EPISODE, seriesId = SERIES_ID)
            nextEpisodeError = RuntimeException("boom")
        }
        val viewModel = createViewModel(repo, movieId = EPISODE_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlayerViewModel.UiState.Success
        assertNull(state.nextEpisode)
    }
```

- [x] **Step 2: Run tests to verify they fail**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest --tests "com.homeflix.app.PlayerViewModelTest"`
Expected: FAIL to compile — `Success` has no `nextEpisode` and `NextEpisode` type does not exist.

- [x] **Step 3: Update `PlayerViewModel`**

In `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerViewModel.kt`:

Add the import:

```kotlin
import org.jellyfin.sdk.model.api.BaseItemKind
```

Add the value type inside the class, above `sealed interface UiState`:

```kotlin
    data class NextEpisode(val id: String, val title: String)
```

Change `UiState.Success` to:

```kotlin
        data class Success(
            val stream: PlaybackStream,
            val resumeTicks: Long,
            val nextEpisode: NextEpisode?
        ) : UiState
```

In `load()`, replace the block that builds `_uiState.value` with:

```kotlin
                val stream = movieRepository.getStream(movieId)
                val movie = movieRepository.getMovie(movieId)
                mediaSourceId = stream.mediaSourceId
                val resumeTicks = movie.userData?.playbackPositionTicks ?: 0L
                val nextEpisode = if (movie.type == BaseItemKind.EPISODE) {
                    try {
                        movieRepository.getNextEpisode(movieId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }
                _uiState.value = UiState.Success(
                    stream = stream,
                    resumeTicks = resumeTicks,
                    nextEpisode = nextEpisode?.let { NextEpisode(it.id.toString(), it.name.orEmpty()) }
                )
```

- [x] **Step 4: Run tests to verify they pass**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all tests pass.

- [x] **Step 5: Commit**

```bash
git add android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerViewModel.kt android-app/app/src/test/kotlin/com/homeflix/app/PlayerViewModelTest.kt
git commit -m "feat(android): expose next episode on player state"
```

---

## Task 5: Android player UI and navigation for the next episode

**Files:**
- Modify: `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerScreen.kt`
- Modify: `android-app/app/src/main/kotlin/com/homeflix/app/navigation/AppNavHost.kt`

**Interfaces:**
- Consumes: `PlayerViewModel.NextEpisode` and `UiState.Success.nextEpisode` from Task 4; `Routes.player(id)`.
- Produces: `PlayerScreen(onBack, onPlayNext: (String) -> Unit, onLogout, …)`; private `VideoPlayer` gains `nextEpisode` and `onPlayNext` parameters.

- [x] **Step 1: Update `PlayerScreen` signature and success branch**

In `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerScreen.kt`, change the `PlayerScreen` signature to add `onPlayNext`:

```kotlin
fun PlayerScreen(
    onBack: () -> Unit,
    onPlayNext: (String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory)
)
```

Change the `Success` branch to pass the new values:

```kotlin
            is PlayerViewModel.UiState.Success -> {
                VideoPlayer(
                    streamUrl = state.stream.url,
                    resumeTicks = state.resumeTicks,
                    nextEpisode = state.nextEpisode,
                    onPlayNext = onPlayNext,
                    onReportStarted = viewModel::reportStarted,
                    onReportProgress = viewModel::reportProgress,
                    onReportStopped = viewModel::reportStopped,
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                )
            }
```

- [x] **Step 2: Update the private `VideoPlayer` signature and state**

Change the private composable signature to:

```kotlin
@Composable
private fun VideoPlayer(
    streamUrl: String,
    resumeTicks: Long,
    nextEpisode: PlayerViewModel.NextEpisode?,
    onPlayNext: (String) -> Unit,
    onReportStarted: (Long) -> Unit,
    onReportProgress: (Long, Boolean) -> Unit,
    onReportStopped: (Long) -> Unit,
    modifier: Modifier = Modifier
)
```

Add these state declarations next to the existing `remember(streamUrl)` state:

```kotlin
    var showCountdown by remember(streamUrl) { mutableStateOf(false) }
    var countdownRemaining by remember(streamUrl) { mutableStateOf(COUNTDOWN_SECONDS) }
```

Add these imports to the file:

```kotlin
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
```

- [x] **Step 3: Trigger the countdown on `STATE_ENDED`**

In the `playerListener`'s `onPlaybackStateChanged`, at the end of the method, add:

```kotlin
                if (state == Player.STATE_ENDED && nextEpisode != null) {
                    showCountdown = true
                }
```

Then, after the existing `LaunchedEffect(player)` block, add:

```kotlin
    LaunchedEffect(showCountdown, nextEpisode) {
        val next = nextEpisode
        if (showCountdown && next != null) {
            countdownRemaining = COUNTDOWN_SECONDS
            while (countdownRemaining > 0) {
                delay(1000)
                countdownRemaining -= 1
            }
            showCountdown = false
            onPlayNext(next.id)
        }
    }
```

- [x] **Step 4: Add the Next Episode button and countdown overlay**

In the bottom controls `Row`, after the duration `Text`, add:

```kotlin
                    if (nextEpisode != null) {
                        TextButton(onClick = { onPlayNext(nextEpisode.id) }) {
                            Text("Next Episode", color = Color.White)
                        }
                    }
```

Inside the outer `Box` (the one with `background(Color.Black)`), after the bottom-controls `Column` and before the closing brace of the `else` branch, add:

```kotlin
            if (showCountdown && nextEpisode != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Next episode in ${countdownRemaining}s",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row {
                        TextButton(onClick = { showCountdown = false }) {
                            Text("Cancel")
                        }
                        TextButton(onClick = { onPlayNext(nextEpisode.id) }) {
                            Text("Play Now")
                        }
                    }
                }
            }
```

- [x] **Step 5: Add the countdown constant**

At the bottom of the file, next to the existing `PROGRESS_INTERVAL_MS` constant, add:

```kotlin
private const val COUNTDOWN_SECONDS = 8
```

- [x] **Step 6: Wire navigation in `AppNavHost`**

In `android-app/app/src/main/kotlin/com/homeflix/app/navigation/AppNavHost.kt`, change the `PlayerScreen` call in the `Routes.PLAYER` composable to:

```kotlin
                    PlayerScreen(
                        onBack = { navController.popBackStack() },
                        onPlayNext = { nextId ->
                            navController.navigate(Routes.player(nextId)) {
                                popUpTo(Routes.PLAYER) { inclusive = true }
                            }
                        },
                        onLogout = sessionViewModel::logout
                    )
```

- [x] **Step 7: Compile and run tests**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. This compiles the `PlayerScreen`/`AppNavHost` changes and runs the unit tests; the existing UI tests are unaffected because `PlayerScreen` is only constructed by `AppNavHost`.

- [ ] **Step 8: Manual verification (device/emulator)**

Install the debug build and:
1. Play a mid-season episode to the end → countdown overlay appears, then the next episode plays.
2. Click **Next Episode** in the controls mid-episode → advances immediately.
3. Click **Cancel** on the overlay → overlay disappears and playback stays ended.
4. Press system Back after advancing → returns to the series detail screen (not the previous episode).
5. Play the last episode of the last season → no Next Episode button and no countdown.
6. Play a movie → no Next Episode button and no countdown.

- [x] **Step 9: Commit**

```bash
git add android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerScreen.kt android-app/app/src/main/kotlin/com/homeflix/app/navigation/AppNavHost.kt
git commit -m "feat(android): add next episode button and countdown auto-play"
```

---

## Self-Review Notes

- **Spec coverage:** R1/R2 (Tasks 1, 3), R3/R4 (Tasks 2, 5), R5 (no-next paths in Tasks 1–5), R6 parity (identical constants and logic across Tasks 1–5), error handling (Task 2 Step 1 `getNextEpisode(...).catch(() => null)`; Task 4 Step 3 try/catch), testing (Tasks 1, 3, 4), out-of-scope items untouched (movie routes, season 0, route arg name).
- **Countdown constant:** `COUNTDOWN_SECONDS = 8` in both `VideoPlayer.svelte` and `PlayerScreen.kt`.
- **No placeholders:** every code step contains the full code to write.
- **Type consistency:** `NextEpisode(id, title)` is used consistently in Tasks 4 and 5; `getNextEpisode(itemId: String)` in Task 3 matches the call in Task 4; `autoplay?: boolean` in Task 2 matches the route usage.
