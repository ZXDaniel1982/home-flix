# Season-Scoped Episode Navigation Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the text Next Episode button with season-scoped `|<` / `>|` icon buttons flanking play/pause in both clients, and give the web player a custom control bar in place of native controls.

**Architecture:** Neighbor resolution becomes purely within-season: each client fetches the current season's episodes and returns the adjacent entries (web `getEpisodeNeighbors`, Android `getAdjacentEpisodes`). The web player drops native `<video controls>` for a custom bar (prev, play/pause, next, seek, time, fullscreen) and the Android player adds `SkipPrevious`/`SkipNext` icon buttons around its existing play/pause. Cross-season rollover is removed, so auto-play also stops at a season finale.

**Tech Stack:** SvelteKit 2 / Svelte 5 runes / TypeScript / Vitest (web); Kotlin / Jetpack Compose / Media3 ExoPlayer / jellyfin-sdk-kotlin / JUnit4 (Android).

**Spec:** `docs/superpowers/specs/2026-09-20-episode-navigation-controls-design.md`

## Global Constraints

- **No transcoding.** Clients direct-play H.264/AAC MP4 only; no server-side changes.
- **Web:** SvelteKit + TypeScript, plain scoped CSS, **no Tailwind, no new dependencies**.
- **Android:** Kotlin + Jetpack Compose, MVVM, jellyfin-sdk-kotlin, Media3 ExoPlayer.
- **Season-scoped only:** previous = previous episode in the same season; next = next episode in the same season. Never cross a season. No cross-season rollover or auto-play.
- **Layout:** `|<` immediately left of play/pause, `>|` immediately right. Icon-only, no text.
- **Boundaries:** a neighbor that is `null` renders disabled (greyed, non-clickable). Movies show no prev/next controls at all.
- **Countdown:** 8 seconds, only when an in-season next exists.
- **No failed neighbor lookup may break playback** — treat it as "no neighbors".
- **Commit after each task** with the message shown in the task.

---

## File Structure

**Web (`web-frontend/`)**
- `src/lib/api/items.ts` — replace `getNextEpisode` with `getEpisodeNeighbors` + `EpisodeNeighbors`.
- `src/lib/api/items.test.ts` — replace the `getNextEpisode` tests.
- `src/lib/components/VideoPlayer.svelte` — custom control bar, prev/next, season-scoped countdown.

**Android (`android-app/`)**
- `app/src/main/kotlin/com/homeflix/app/data/NextEpisode.kt` — add `previousEpisodeInSeason`, remove rollover helpers.
- `app/src/main/kotlin/com/homeflix/app/data/MovieRepository.kt` — `getAdjacentEpisodes` + `AdjacentEpisodes`, remove `getNextEpisode`.
- `app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerViewModel.kt` — `EpisodeRef`, `isEpisode`, previous/next.
- `app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerScreen.kt` — icon buttons, remove text button.
- `app/src/main/kotlin/com/homeflix/app/navigation/AppNavHost.kt` — `onPlayEpisode`.
- `app/src/test/kotlin/com/homeflix/app/NextEpisodeTest.kt` — previous/next helper tests.
- `app/src/test/kotlin/com/homeflix/app/PlayerViewModelTest.kt` — neighbor state tests.
- `app/src/test/kotlin/com/homeflix/app/TestDoubles.kt` — fake update.

---

## Task 1: Web neighbor-resolution API

**Files:**
- Modify: `web-frontend/src/lib/api/items.ts`
- Test: `web-frontend/src/lib/api/items.test.ts`

**Interfaces:**
- Consumes: existing `getEpisodes(seasonId): Promise<BaseItemDto[]>`.
- Produces: `EpisodeNeighbors { previous: BaseItemDto | null; next: BaseItemDto | null }` and `getEpisodeNeighbors(episode: BaseItemDto): Promise<EpisodeNeighbors | null>`.

- [x] **Step 1: Replace the resolver tests**

In `web-frontend/src/lib/api/items.test.ts`, change the import on line 2 to:

```ts
import { getItem, getMovies, getEpisodeNeighbors, search } from './items';
```

Then replace the entire `describe('getNextEpisode', () => { ... });` block (lines 73-177) with:

```ts
describe('getEpisodeNeighbors', () => {
	const list = [
		{ Id: 'e1', Name: 'One', Type: 'Episode', SeriesId: 'series1', SeasonId: 's1', IndexNumber: 1 },
		{ Id: 'e2', Name: 'Two', Type: 'Episode', SeriesId: 'series1', SeasonId: 's1', IndexNumber: 2 },
		{ Id: 'e3', Name: 'Three', Type: 'Episode', SeriesId: 'series1', SeasonId: 's1', IndexNumber: 3 }
	];

	function stubEpisodes(items: unknown[]) {
		const fn = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse({ Items: items }));
		vi.stubGlobal('fetch', fn);
		return fn;
	}

	it('returns the previous and next episodes in the same season', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		stubEpisodes(list);

		const neighbors = await getEpisodeNeighbors(episode({ Id: 'e2' }));

		expect(neighbors?.previous?.Id).toBe('e1');
		expect(neighbors?.next?.Id).toBe('e3');
	});

	it('returns null previous for the first episode of a season', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		stubEpisodes(list);

		const neighbors = await getEpisodeNeighbors(episode({ Id: 'e1' }));

		expect(neighbors?.previous).toBeNull();
		expect(neighbors?.next?.Id).toBe('e2');
	});

	it('returns null next for the last episode of a season', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		stubEpisodes(list);

		const neighbors = await getEpisodeNeighbors(episode({ Id: 'e3' }));

		expect(neighbors?.next).toBeNull();
		expect(neighbors?.previous?.Id).toBe('e2');
	});

	it('returns both null for a single-episode season', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		stubEpisodes([list[0]]);

		const neighbors = await getEpisodeNeighbors(episode({ Id: 'e1' }));

		expect(neighbors).toEqual({ previous: null, next: null });
	});

	it('returns null for a non-episode item without fetching', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		const fn = vi.fn<typeof fetch>();
		vi.stubGlobal('fetch', fn);

		expect(await getEpisodeNeighbors({ Id: 'm1', Name: 'Movie', Type: 'Movie' })).toBeNull();
		expect(fn).not.toHaveBeenCalled();
	});

	it('returns both null when the episode has no season id, without fetching', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		const fn = vi.fn<typeof fetch>();
		vi.stubGlobal('fetch', fn);

		expect(await getEpisodeNeighbors(episode({ SeasonId: undefined }))).toEqual({
			previous: null,
			next: null
		});
		expect(fn).not.toHaveBeenCalled();
	});

	it('returns both null when the episode has no series id, without fetching', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		const fn = vi.fn<typeof fetch>();
		vi.stubGlobal('fetch', fn);

		expect(await getEpisodeNeighbors(episode({ SeriesId: undefined }))).toEqual({
			previous: null,
			next: null
		});
		expect(fn).not.toHaveBeenCalled();
	});
});
```

- [x] **Step 2: Run tests to verify they fail**

Run (from `web-frontend/`): `npm test -- src/lib/api/items.test.ts`
Expected: FAIL — `getEpisodeNeighbors` is not exported.

- [x] **Step 3: Implement `getEpisodeNeighbors`**

In `web-frontend/src/lib/api/items.ts`, replace the entire `getNextEpisode` function (lines 62-83) with:

```ts
export interface EpisodeNeighbors {
	previous: BaseItemDto | null;
	next: BaseItemDto | null;
}

export async function getEpisodeNeighbors(
	episode: BaseItemDto
): Promise<EpisodeNeighbors | null> {
	if (episode.Type !== 'Episode') {
		return null;
	}
	if (!episode.SeriesId || !episode.SeasonId) {
		return { previous: null, next: null };
	}
	const episodes = await getEpisodes(episode.SeasonId);
	const index = episodes.findIndex((candidate) => candidate.Id === episode.Id);
	if (index === -1) {
		return { previous: null, next: null };
	}
	return {
		previous: episodes[index - 1] ?? null,
		next: episodes[index + 1] ?? null
	};
}
```

- [x] **Step 4: Run tests to verify they pass**

Run (from `web-frontend/`): `npm test -- src/lib/api/items.test.ts`
Expected: PASS (all `getEpisodeNeighbors` cases).

- [x] **Step 5: Verify no other callers of the old function**

Run (from the repo root): `grep -rn "getNextEpisode" web-frontend/src` — expected: no matches.

- [x] **Step 6: Type check and lint**

Run (from `web-frontend/`): `npm run check && npm run lint`
Expected: no errors.

- [x] **Step 7: Commit**

```bash
git add web-frontend/src/lib/api/items.ts web-frontend/src/lib/api/items.test.ts
git commit -m "feat(web): resolve season-scoped episode neighbors"
```

---

## Task 2: Web custom control bar with prev/next

**Files:**
- Modify: `web-frontend/src/lib/components/VideoPlayer.svelte`

**Interfaces:**
- Consumes: `getEpisodeNeighbors` / `EpisodeNeighbors` from Task 1.
- Produces: nothing consumed by later tasks (Android tasks are independent).

- [x] **Step 1: Replace the whole component**

Replace the entire contents of `web-frontend/src/lib/components/VideoPlayer.svelte` with:

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
	import { getItem, getEpisodeNeighbors, type EpisodeNeighbors } from '$lib/api/items';
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
	let playerEl = $state<HTMLDivElement | null>(null);
	let neighbors = $state<EpisodeNeighbors | null>(null);
	let seriesId = $state('');
	let isPlaying = $state(false);
	let currentTime = $state(0);
	let duration = $state(0);
	let scrubValue = $state<number | null>(null);
	let isFullscreen = $state(false);
	let showCountdown = $state(false);
	let countdownRemaining = $state(COUNTDOWN_SECONDS);

	let previousEpisode = $derived(neighbors?.previous ?? null);
	let nextEpisode = $derived(neighbors?.next ?? null);

	let mediaSourceId = '';
	let resumeTicks = 0;
	let lastReportAt = 0;
	let stoppedReported = false;
	let countdownTimer: ReturnType<typeof setInterval> | null = null;

	$effect(() => {
		streamSrc = '';
		error = '';
		playbackError = false;
		loading = true;
		mediaSourceId = '';
		resumeTicks = 0;
		lastReportAt = 0;
		stoppedReported = false;
		neighbors = null;
		seriesId = '';
		isPlaying = false;
		currentTime = 0;
		duration = 0;
		scrubValue = null;
		cancelCountdown();
		if (!itemId) {
			error = 'Invalid item.';
			loading = false;
			return;
		}
		load(itemId)
			.then(({ src, msId, ticks, adjacent, series }) => {
				streamSrc = src;
				mediaSourceId = msId;
				resumeTicks = ticks;
				neighbors = adjacent;
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
		const adjacent = await getEpisodeNeighbors(item).catch(() =>
			item.Type === 'Episode' ? { previous: null, next: null } : null
		);
		return {
			src: streamUrl(id, source.Id),
			msId: source.Id,
			ticks: item.UserData?.PlaybackPositionTicks ?? 0,
			adjacent,
			series: item.SeriesId ?? ''
		};
	}

	function formatTime(seconds: number): string {
		const total = Math.max(0, Math.floor(seconds));
		const hours = Math.floor(total / 3600);
		const minutes = Math.floor((total % 3600) / 60);
		const secs = total % 60;
		const mm = hours > 0 ? String(minutes).padStart(2, '0') : String(minutes);
		const ss = String(secs).padStart(2, '0');
		return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`;
	}

	function currentTicks(): number {
		return Math.floor((video?.currentTime ?? 0) * 10_000_000);
	}

	function onLoadedMetadata() {
		const el = video;
		if (!el) return;
		duration = Number.isFinite(el.duration) ? el.duration : 0;
		if (resumeTicks > 0) {
			el.currentTime = resumeTicks / 10_000_000;
		}
	}

	function onPlay() {
		stoppedReported = false;
		isPlaying = true;
		reportPlaybackStarted(itemId, mediaSourceId, currentTicks()).catch(() => {});
	}

	function onPause() {
		isPlaying = false;
		sendProgress();
	}

	function onTimeUpdate() {
		const el = video;
		if (el) {
			currentTime = el.currentTime;
			if (Number.isFinite(el.duration)) duration = el.duration;
		}
		const now = Date.now();
		if (now - lastReportAt < PROGRESS_INTERVAL_MS) return;
		lastReportAt = now;
		sendProgress();
	}

	function onEnded() {
		isPlaying = false;
		reportStopped();
		if (!nextEpisode) return;
		startCountdown();
	}

	function togglePlay() {
		const el = video;
		if (!el) return;
		if (el.ended) {
			el.currentTime = 0;
			el.play().catch(() => {});
		} else if (el.paused) {
			el.play().catch(() => {});
		} else {
			el.pause();
		}
	}

	function onSeekInput(event: Event) {
		scrubValue = Number((event.currentTarget as HTMLInputElement).value);
	}

	function onSeekCommit() {
		if (scrubValue !== null && video) {
			video.currentTime = scrubValue;
			currentTime = scrubValue;
		}
		scrubValue = null;
	}

	async function toggleFullscreen() {
		const container = playerEl;
		if (!container) return;
		try {
			if (document.fullscreenElement) {
				await document.exitFullscreen();
			} else {
				await container.requestFullscreen();
			}
		} catch {
			// Fullscreen may be unavailable; ignore.
		}
	}

	function onFullscreenChange() {
		isFullscreen = document.fullscreenElement != null;
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

	function goTo(target: BaseItemDto | null) {
		if (!target) return;
		clearCountdown();
		showCountdown = false;
		reportStopped();
		goto(resolve(`/tv/${seriesId}/play/${target.Id}?autoplay=1`));
	}

	function goToNext() {
		goTo(nextEpisode);
	}

	function goToPrevious() {
		goTo(previousEpisode);
	}

	function sendProgress() {
		if (!mediaSourceId) return;
		reportPlaybackProgress(itemId, mediaSourceId, currentTicks(), video?.paused ?? true).catch(
			() => {}
		);
	}

	function reportStopped(el: HTMLVideoElement | null = video) {
		if (stoppedReported || !mediaSourceId) return;
		stoppedReported = true;
		const ticks = Math.floor((el?.currentTime ?? 0) * 10_000_000);
		reportPlaybackStopped(itemId, mediaSourceId, ticks).catch(() => {});
	}

	$effect(() => {
		const el = video;
		if (!el) return;
		return () => {
			clearCountdown();
			reportStopped(el);
		};
	});
</script>

<svelte:window onfullscreenchange={onFullscreenChange} />

{#if loading}
	<p>Loading…</p>
{:else if error}
	<p class="error" role="alert">{error}</p>
{:else if streamSrc}
	{#if backHref}
		<a class="back" href={resolve(backHref)}>Back</a>
	{/if}
	{#if playbackError}
		<p class="error" role="alert">
			This video couldn't be played. It may be in an unsupported format (H.264/AAC MP4 is required).
		</p>
	{:else}
		<div class="player" bind:this={playerEl}>
			<!-- svelte-ignore a11y_media_has_caption -->
			<video
				{autoplay}
				src={streamSrc}
				bind:this={video}
				onloadedmetadata={onLoadedMetadata}
				onplay={onPlay}
				ontimeupdate={onTimeUpdate}
				onpause={onPause}
				onended={onEnded}
				onerror={() => (playbackError = true)}
			></video>

			<div class="controls">
				{#if neighbors}
					<button
						class="icon"
						type="button"
						aria-label="Previous episode"
						disabled={!previousEpisode}
						onclick={goToPrevious}
					>
						<svg viewBox="0 0 24 24" aria-hidden="true">
							<path d="M6 5h2v14H6z" />
							<path d="M20 5v14L9 12z" />
						</svg>
					</button>
				{/if}

				<button
					class="icon"
					type="button"
					aria-label={isPlaying ? 'Pause' : 'Play'}
					onclick={togglePlay}
				>
					{#if isPlaying}
						<svg viewBox="0 0 24 24" aria-hidden="true">
							<path d="M7 5h4v14H7z" />
							<path d="M13 5h4v14h-4z" />
						</svg>
					{:else}
						<svg viewBox="0 0 24 24" aria-hidden="true">
							<path d="M8 5v14l11-7z" />
						</svg>
					{/if}
				</button>

				{#if neighbors}
					<button
						class="icon"
						type="button"
						aria-label="Next episode"
						disabled={!nextEpisode}
						onclick={goToNext}
					>
						<svg viewBox="0 0 24 24" aria-hidden="true">
							<path d="M4 5v14l11-7z" />
							<path d="M18 5h2v14h-2z" />
						</svg>
					</button>
				{/if}

				<span class="time">{formatTime(scrubValue ?? currentTime)}</span>
				<input
					class="seek"
					type="range"
					aria-label="Seek"
					min="0"
					max={duration || 0}
					step="0.1"
					value={scrubValue ?? currentTime}
					oninput={onSeekInput}
					onchange={onSeekCommit}
				/>
				<span class="time">{formatTime(duration)}</span>

				<button
					class="icon"
					type="button"
					aria-label={isFullscreen ? 'Exit fullscreen' : 'Fullscreen'}
					onclick={toggleFullscreen}
				>
					<svg viewBox="0 0 24 24" aria-hidden="true">
						<path d="M4 4h6v2H6v4H4z" />
						<path d="M20 4v6h-2V6h-4V4z" />
						<path d="M4 20v-6h2v4h4v2z" />
						<path d="M20 20h-6v-2h4v-4h2z" />
					</svg>
				</button>
			</div>

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
	{/if}
{/if}

<style>
	.back {
		display: inline-block;
		margin-bottom: 1rem;
	}

	.player {
		position: relative;
		width: 100%;
		max-width: 60rem;
	}

	video {
		display: block;
		width: 100%;
		max-height: 70vh;
		background-color: #000;
		border-radius: 0.5rem;
	}

	.controls {
		position: absolute;
		left: 0;
		right: 0;
		bottom: 0;
		display: flex;
		align-items: center;
		gap: 0.5rem;
		padding: 0.5rem 0.75rem;
		background-color: rgba(0, 0, 0, 0.65);
		color: #fff;
		border-bottom-left-radius: 0.5rem;
		border-bottom-right-radius: 0.5rem;
	}

	.icon {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		padding: 0.25rem;
		border: none;
		background: none;
		color: #fff;
		cursor: pointer;
	}

	.icon:disabled {
		opacity: 0.35;
		cursor: default;
	}

	.icon svg {
		width: 1.5rem;
		height: 1.5rem;
		fill: currentColor;
	}

	.time {
		font-variant-numeric: tabular-nums;
		font-size: 0.8125rem;
		white-space: nowrap;
	}

	.seek {
		flex: 1;
		min-width: 4rem;
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
		border-radius: 0.5rem;
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

	.error {
		color: #e5484d;
	}
</style>
```

- [x] **Step 2: Type check, lint, build, tests**

Run (from `web-frontend/`): `npm run check && npm run lint && npm run build && npm test`
Expected: all pass. (In particular, no `a11y` lint errors from the custom controls.)

- [ ] **Step 3: Manual verification (deferred if no server)**

If a Jellyfin server is reachable, run `npm run dev` and verify: first episode of a season (prev greyed), middle episode (both active), last episode of a season (next greyed and no countdown at the end), single-episode season (both greyed), a movie (no prev/next), prev/next navigation, play/pause, seek, and fullscreen. If no server is available in this environment, record that this was deferred to the human.

- [x] **Step 4: Commit**

```bash
git add web-frontend/src/lib/components/VideoPlayer.svelte
git commit -m "feat(web): season-scoped prev/next episode controls with custom control bar"
```

---

## Task 3: Android neighbor resolution and player state

**Files:**
- Modify: `android-app/app/src/main/kotlin/com/homeflix/app/data/NextEpisode.kt`
- Modify: `android-app/app/src/main/kotlin/com/homeflix/app/data/MovieRepository.kt`
- Modify: `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerViewModel.kt`
- Modify: `android-app/app/src/test/kotlin/com/homeflix/app/TestDoubles.kt`
- Test: `android-app/app/src/test/kotlin/com/homeflix/app/NextEpisodeTest.kt`
- Test: `android-app/app/src/test/kotlin/com/homeflix/app/PlayerViewModelTest.kt`

**Interfaces:**
- Consumes: existing `JellyfinMovieRepository.getMovie`, `getEpisodes`; `FakeMovieRepository`.
- Produces:
  - `nextEpisodeInSeason(episodes, currentId): BaseItemDto?`
  - `previousEpisodeInSeason(episodes, currentId): BaseItemDto?`
  - `data class AdjacentEpisodes(val previous: BaseItemDto?, val next: BaseItemDto?)`
  - `MovieRepository.getAdjacentEpisodes(itemId: String): AdjacentEpisodes?`
  - `FakeMovieRepository.adjacentEpisodes` / `.adjacentEpisodesError`
  - `PlayerViewModel.EpisodeRef(id: String)`; `UiState.Success(stream, resumeTicks, isEpisode: Boolean, previousEpisode: EpisodeRef?, nextEpisode: EpisodeRef?)`

> This task replaces the removed `getNextEpisode` call site in the same task so the Android module keeps compiling throughout.

- [x] **Step 1: Replace the helper tests**

Replace the entire contents of `android-app/app/src/test/kotlin/com/homeflix/app/NextEpisodeTest.kt` with:

```kotlin
package com.homeflix.app

import com.homeflix.app.data.nextEpisodeInSeason
import com.homeflix.app.data.previousEpisodeInSeason
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextEpisodeTest {

    private val episode1 = "00000000-0000-0000-0000-000000000201"
    private val episode2 = "00000000-0000-0000-0000-000000000202"
    private val episode3 = "00000000-0000-0000-0000-000000000203"

    private val episodes = listOf(
        baseItem(episode1, "One", type = BaseItemKind.EPISODE, indexNumber = 1),
        baseItem(episode2, "Two", type = BaseItemKind.EPISODE, indexNumber = 2),
        baseItem(episode3, "Three", type = BaseItemKind.EPISODE, indexNumber = 3)
    )

    @Test
    fun nextEpisodeInSeason_returnsFollowingEpisode() {
        assertEquals(episode2, nextEpisodeInSeason(episodes, episode1)?.id?.toString())
    }

    @Test
    fun nextEpisodeInSeason_returnsNullForLastEpisode() {
        assertNull(nextEpisodeInSeason(episodes, episode3))
    }

    @Test
    fun nextEpisodeInSeason_returnsNullWhenCurrentIsMissing() {
        assertNull(nextEpisodeInSeason(episodes, "00000000-0000-0000-0000-000000000999"))
    }

    @Test
    fun previousEpisodeInSeason_returnsPrecedingEpisode() {
        assertEquals(episode2, previousEpisodeInSeason(episodes, episode3)?.id?.toString())
    }

    @Test
    fun previousEpisodeInSeason_returnsNullForFirstEpisode() {
        assertNull(previousEpisodeInSeason(episodes, episode1))
    }

    @Test
    fun previousEpisodeInSeason_returnsNullWhenCurrentIsMissing() {
        assertNull(previousEpisodeInSeason(episodes, "00000000-0000-0000-0000-000000000999"))
    }
}
```

- [x] **Step 2: Run tests to verify they fail**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest --tests "com.homeflix.app.NextEpisodeTest"`
Expected: FAIL to compile — `previousEpisodeInSeason` is unresolved.

- [x] **Step 3: Replace the helper implementation**

Replace the entire contents of `android-app/app/src/main/kotlin/com/homeflix/app/data/NextEpisode.kt` with:

```kotlin
package com.homeflix.app.data

import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Returns the episode immediately before [currentId] in [episodes] (which must be
 * sorted by IndexNumber), or null if [currentId] is absent or is the first episode.
 */
internal fun previousEpisodeInSeason(episodes: List<BaseItemDto>, currentId: String): BaseItemDto? {
    val index = episodes.indexOfFirst { it.id.toString() == currentId }
    if (index == -1) return null
    return if (index == 0) null else episodes[index - 1]
}

/**
 * Returns the episode immediately after [currentId] in [episodes] (which must be
 * sorted by IndexNumber), or null if [currentId] is absent or is the last episode.
 */
internal fun nextEpisodeInSeason(episodes: List<BaseItemDto>, currentId: String): BaseItemDto? {
    val index = episodes.indexOfFirst { it.id.toString() == currentId }
    if (index == -1) return null
    return episodes.getOrNull(index + 1)
}
```

- [x] **Step 4: Run helper tests to verify they pass**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest --tests "com.homeflix.app.NextEpisodeTest"`
Expected: PASS.

- [x] **Step 5: Update the repository interface and implementation**

In `android-app/app/src/main/kotlin/com/homeflix/app/data/MovieRepository.kt`:

Replace the interface line `suspend fun getNextEpisode(itemId: String): BaseItemDto?` with:

```kotlin
    suspend fun getAdjacentEpisodes(itemId: String): AdjacentEpisodes?
```

Add the data class after `data class PlaybackStream(...)`:

```kotlin
data class AdjacentEpisodes(val previous: BaseItemDto?, val next: BaseItemDto?)
```

Replace the `JellyfinMovieRepository.getNextEpisode` override (lines 190-200) with:

```kotlin
    override suspend fun getAdjacentEpisodes(itemId: String): AdjacentEpisodes? {
        val item = getMovie(itemId)
        if (item.type != BaseItemKind.EPISODE) return null
        val seasonId = item.seasonId?.toString() ?: return AdjacentEpisodes(null, null)
        val episodes = getEpisodes(seasonId)
        return AdjacentEpisodes(
            previous = previousEpisodeInSeason(episodes, itemId),
            next = nextEpisodeInSeason(episodes, itemId)
        )
    }
```

- [x] **Step 6: Update `FakeMovieRepository`**

In `android-app/app/src/test/kotlin/com/homeflix/app/TestDoubles.kt`, replace the fields

```kotlin
    var nextEpisode: BaseItemDto? = null
    var nextEpisodeError: Exception? = null
```

with

```kotlin
    var adjacentEpisodes: AdjacentEpisodes? = null
    var adjacentEpisodesError: Exception? = null
```

Add the import `com.homeflix.app.data.AdjacentEpisodes`, and replace the `getNextEpisode` override with:

```kotlin
    override suspend fun getAdjacentEpisodes(itemId: String): AdjacentEpisodes? {
        adjacentEpisodesError?.let { throw it }
        error?.let { throw it }
        return adjacentEpisodes
    }
```

- [x] **Step 7: Update `PlayerViewModel` neighbor state and mapping**

In `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerViewModel.kt`, add `import com.homeflix.app.data.AdjacentEpisodes` before `import com.homeflix.app.data.MovieRepository`.

Replace

```kotlin
    data class NextEpisode(val id: String, val title: String)
```

with

```kotlin
    data class EpisodeRef(val id: String)
```

Replace the `UiState.Success` declaration with:

```kotlin
        data class Success(
            val stream: PlaybackStream,
            val resumeTicks: Long,
            val isEpisode: Boolean,
            val previousEpisode: EpisodeRef?,
            val nextEpisode: EpisodeRef?
        ) : UiState
```

In `load()`, replace the `nextEpisode` computation and the `_uiState.value = UiState.Success(...)` block with:

```kotlin
                val adjacent = if (movie.type == BaseItemKind.EPISODE) {
                    try {
                        movieRepository.getAdjacentEpisodes(movieId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        AdjacentEpisodes(previous = null, next = null)
                    }
                } else {
                    null
                }
                _uiState.value = UiState.Success(
                    stream = stream,
                    resumeTicks = resumeTicks,
                    isEpisode = movie.type == BaseItemKind.EPISODE,
                    previousEpisode = adjacent?.previous?.let { EpisodeRef(it.id.toString()) },
                    nextEpisode = adjacent?.next?.let { EpisodeRef(it.id.toString()) }
                )
```

- [x] **Step 8: Replace the neighbor tests**

In `android-app/app/src/test/kotlin/com/homeflix/app/PlayerViewModelTest.kt`, add the import `com.homeflix.app.data.AdjacentEpisodes` and replace the five tests from `load_episodeWithNext_setsNextEpisode` through the end of the class with:

```kotlin
    @Test
    fun load_episodeWithNeighbors_setsBoth() = runTest(mainDispatcherRule.testDispatcher) {
        val previousId = "00000000-0000-0000-0000-000000000004"
        val nextId = "00000000-0000-0000-0000-000000000005"
        val repo = FakeMovieRepository().apply {
            stream = PlaybackStream("http://host/stream", "ms1")
            movie = baseItem(EPISODE_ID, "Episode 2", type = BaseItemKind.EPISODE, seriesId = SERIES_ID)
            adjacentEpisodes = AdjacentEpisodes(
                previous = baseItem(previousId, "Episode 1", type = BaseItemKind.EPISODE, seriesId = SERIES_ID),
                next = baseItem(nextId, "Episode 3", type = BaseItemKind.EPISODE, seriesId = SERIES_ID)
            )
        }
        val viewModel = createViewModel(repo, movieId = EPISODE_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlayerViewModel.UiState.Success
        assertEquals(true, state.isEpisode)
        assertEquals(previousId, state.previousEpisode?.id)
        assertEquals(nextId, state.nextEpisode?.id)
    }

    @Test
    fun load_firstEpisodeOfSeason_hasNoPrevious() = runTest(mainDispatcherRule.testDispatcher) {
        val nextId = "00000000-0000-0000-0000-000000000005"
        val repo = FakeMovieRepository().apply {
            stream = PlaybackStream("http://host/stream", "ms1")
            movie = baseItem(EPISODE_ID, "Episode 1", type = BaseItemKind.EPISODE, seriesId = SERIES_ID)
            adjacentEpisodes = AdjacentEpisodes(
                previous = null,
                next = baseItem(nextId, "Episode 2", type = BaseItemKind.EPISODE, seriesId = SERIES_ID)
            )
        }
        val viewModel = createViewModel(repo, movieId = EPISODE_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlayerViewModel.UiState.Success
        assertNull(state.previousEpisode)
        assertEquals(nextId, state.nextEpisode?.id)
    }

    @Test
    fun load_lastEpisodeOfSeason_hasNoNext() = runTest(mainDispatcherRule.testDispatcher) {
        val previousId = "00000000-0000-0000-0000-000000000004"
        val repo = FakeMovieRepository().apply {
            stream = PlaybackStream("http://host/stream", "ms1")
            movie = baseItem(EPISODE_ID, "Finale", type = BaseItemKind.EPISODE, seriesId = SERIES_ID)
            adjacentEpisodes = AdjacentEpisodes(
                previous = baseItem(previousId, "Episode 1", type = BaseItemKind.EPISODE, seriesId = SERIES_ID),
                next = null
            )
        }
        val viewModel = createViewModel(repo, movieId = EPISODE_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlayerViewModel.UiState.Success
        assertEquals(previousId, state.previousEpisode?.id)
        assertNull(state.nextEpisode)
    }

    @Test
    fun load_movie_isNotEpisode() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply {
            stream = PlaybackStream("http://host/stream", "ms1")
            movie = baseItem(MOVIE_ID, "Movie")
            adjacentEpisodes = AdjacentEpisodes(
                previous = baseItem("00000000-0000-0000-0000-000000000004", "Ignored"),
                next = baseItem("00000000-0000-0000-0000-000000000005", "Ignored")
            )
        }
        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlayerViewModel.UiState.Success
        assertEquals(false, state.isEpisode)
        assertNull(state.previousEpisode)
        assertNull(state.nextEpisode)
    }

    @Test
    fun load_neighborLookupFailure_episodeHasNoNeighbors() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply {
            stream = PlaybackStream("http://host/stream", "ms1")
            movie = baseItem(EPISODE_ID, "Episode 1", type = BaseItemKind.EPISODE, seriesId = SERIES_ID)
            adjacentEpisodesError = RuntimeException("boom")
        }
        val viewModel = createViewModel(repo, movieId = EPISODE_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlayerViewModel.UiState.Success
        assertEquals(true, state.isEpisode)
        assertNull(state.previousEpisode)
        assertNull(state.nextEpisode)
    }
```

- [x] **Step 9: Run the Android unit test suite**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all tests pass.

- [x] **Step 10: Commit**

```bash
git add android-app/app/src/main/kotlin/com/homeflix/app/data/NextEpisode.kt android-app/app/src/main/kotlin/com/homeflix/app/data/MovieRepository.kt android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerViewModel.kt android-app/app/src/test/kotlin/com/homeflix/app/TestDoubles.kt android-app/app/src/test/kotlin/com/homeflix/app/NextEpisodeTest.kt android-app/app/src/test/kotlin/com/homeflix/app/PlayerViewModelTest.kt
git commit -m "feat(android): season-scoped adjacent episodes and player state"
```

---

## Task 4: Android player buttons and navigation

**Files:**
- Modify: `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerScreen.kt`
- Modify: `android-app/app/src/main/kotlin/com/homeflix/app/navigation/AppNavHost.kt`

**Interfaces:**
- Consumes: `PlayerViewModel.EpisodeRef`, `UiState.Success(isEpisode, previousEpisode, nextEpisode)` from Task 3.
- Produces: `PlayerScreen(onBack, onPlayEpisode: (String) -> Unit, onLogout, …)`.

- [x] **Step 1: Add the skip icons import**

In `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerScreen.kt`, add after the existing `import androidx.compose.material.icons.filled.PlayArrow`:

```kotlin
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
```

- [x] **Step 2: Rename the callback in `PlayerScreen`**

Change the `PlayerScreen` signature parameter from `onPlayNext: (String) -> Unit` to `onPlayEpisode: (String) -> Unit`, and change the `Success` branch to:

```kotlin
            is PlayerViewModel.UiState.Success -> {
                VideoPlayer(
                    streamUrl = state.stream.url,
                    resumeTicks = state.resumeTicks,
                    isEpisode = state.isEpisode,
                    previousEpisode = state.previousEpisode,
                    nextEpisode = state.nextEpisode,
                    onPlayEpisode = onPlayEpisode,
                    onReportStarted = viewModel::reportStarted,
                    onReportProgress = viewModel::reportProgress,
                    onReportStopped = viewModel::reportStopped,
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                )
            }
```

- [x] **Step 3: Update the private `VideoPlayer` signature**

Change the private composable signature to:

```kotlin
@Composable
private fun VideoPlayer(
    streamUrl: String,
    resumeTicks: Long,
    isEpisode: Boolean,
    previousEpisode: PlayerViewModel.EpisodeRef?,
    nextEpisode: PlayerViewModel.EpisodeRef?,
    onPlayEpisode: (String) -> Unit,
    onReportStarted: (Long) -> Unit,
    onReportProgress: (Long, Boolean) -> Unit,
    onReportStopped: (Long) -> Unit,
    modifier: Modifier = Modifier
)
```

- [x] **Step 4: Replace the control-row buttons**

In the bottom controls `Row`, replace the play/pause `IconButton` and remove the trailing `TextButton`. The row should read:

```kotlin
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isEpisode) {
                        IconButton(
                            onClick = { previousEpisode?.let { onPlayEpisode(it.id) } },
                            enabled = previousEpisode != null
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = "Previous episode",
                                tint = if (previousEpisode != null) Color.White else Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (player.playbackState == Player.STATE_ENDED) {
                                player.seekTo(0)
                                player.playWhenReady = true
                            } else {
                                player.playWhenReady = !player.playWhenReady
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White
                        )
                    }

                    if (isEpisode) {
                        IconButton(
                            onClick = { nextEpisode?.let { onPlayEpisode(it.id) } },
                            enabled = nextEpisode != null
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Next episode",
                                tint = if (nextEpisode != null) Color.White else Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }

                    Text(
                        text = formatTime(positionMs),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )

                    val max = durationMs.coerceAtLeast(1L).toFloat()
                    Slider(
                        value = scrubPosition ?: positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L)).toFloat(),
                        onValueChange = { scrubPosition = it },
                        onValueChangeFinished = {
                            scrubPosition?.let {
                                player.seekTo(it.toLong())
                                positionMs = it.toLong()
                            }
                            scrubPosition = null
                        },
                        valueRange = 0f..max,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )

                    Text(
                        text = formatTime(durationMs),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
```

This removes the previous `TextButton { Text("Next Episode", …) }` block at the end of the row.

- [x] **Step 5: Update the countdown callback**

In the `LaunchedEffect(showCountdown, nextEpisode)` block, change `onPlayNext(next.id)` to `onPlayEpisode(next.id)`. In the countdown overlay, change the `Play Now` button's `onClick = { onPlayNext(nextEpisode.id) }` to `onClick = { onPlayEpisode(nextEpisode.id) }`. The `STATE_ENDED` guard `nextEpisode != null` stays as-is (now an `EpisodeRef`).

- [x] **Step 6: Wire navigation in `AppNavHost`**

In `android-app/app/src/main/kotlin/com/homeflix/app/navigation/AppNavHost.kt`, change the `PlayerScreen` call to:

```kotlin
                    PlayerScreen(
                        onBack = { navController.popBackStack() },
                        onPlayEpisode = { episodeId ->
                            navController.navigate(Routes.player(episodeId)) {
                                popUpTo(Routes.PLAYER) { inclusive = true }
                            }
                        },
                        onLogout = sessionViewModel::logout
                    )
```

- [x] **Step 7: Compile and run tests**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Manual verification (deferred if no device)**

Install the APK on the Windows emulator per `docs/development.md` §6.4 and verify: first/last episode of a season (one button greyed), middle (both active), single-episode season (both greyed), movie (no prev/next), prev/next navigation, and that the countdown does not appear at a season finale. Record if this is deferred to the human.

- [x] **Step 9: Commit**

```bash
git add android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerScreen.kt android-app/app/src/main/kotlin/com/homeflix/app/navigation/AppNavHost.kt
git commit -m "feat(android): season-scoped prev/next buttons around play/pause"
```

---

## Self-Review Notes

- **Spec coverage:** R1 (Tasks 2, 4 hide controls for movies), R2 (Tasks 1, 3 in-season resolution), R3 (Tasks 2, 4 layout/icons), R4 (disabled styling in Tasks 2, 4), R5 (Tasks 2, 4 countdown requires in-season next), R6 (navigation), R7 (Task 2 custom bar), R8 (parity). §7 error handling covered by the `.catch` fallbacks; §9 out-of-scope untouched.
- **No placeholders:** each code step contains the complete code.
- **Type consistency:** `EpisodeNeighbors`/`getEpisodeNeighbors` (web) and `AdjacentEpisodes`/`getAdjacentEpisodes` (Android) are used consistently; `EpisodeRef(id)` replaces `NextEpisode(id, title)` everywhere; `onPlayEpisode` replaces `onPlayNext` at the single call site.
- **Compilation continuity:** the Android repository change and its `PlayerViewModel` call-site update live in the same task (Task 3), so `./gradlew :app:testDebugUnitTest` compiles green at every commit.

## Rollout (after merge)

- Web: redeploy to the Orange Pi with `scripts/deploy-web.sh`.
- Android: rebuild the APK and install on the Windows emulator (`docs/development.md` §6.4).
