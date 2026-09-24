# Episode Browser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an episodes button to both players that opens a season-grouped list of the series and lets the user play any episode.

**Architecture:** A new `getSeriesEpisodes(seriesId)` in each client builds a seasons-with-episodes structure from the existing `getSeasons`/`getEpisodes` helpers. The web player renders an overlay panel; the Android player renders a `ModalBottomSheet`. Both reuse the existing episode navigation (`goTo` / `onPlayEpisode`).

**Tech Stack:** SvelteKit 2 / Svelte 5 runes / TypeScript / Vitest (web); Kotlin / Jetpack Compose / Material3 / jellyfin-sdk-kotlin / JUnit4 (Android).

**Spec:** `docs/superpowers/specs/2026-09-24-episode-browser-design.md`

## Global Constraints

- **No transcoding.** Clients direct-play H.264/AAC MP4; no server changes.
- **Web:** SvelteKit + TypeScript, plain scoped CSS, **no Tailwind, no new dependencies**.
- **Android:** Kotlin + Jetpack Compose, MVVM, jellyfin-sdk-kotlin.
- **Episodes only:** the button appears only when playing an Episode (`neighbors !== null` on web, `isEpisode == true` on Android).
- **Lazy:** fetch episodes on first open, not during player startup. A fetch failure never affects playback.
- **Reuse navigation:** selecting an episode uses the existing `goTo()` (web) / `onPlayEpisode(id)` (Android).
- **Current episode** is highlighted and disabled; playback continues while the panel is open.
- **No images** in the panel.
- **Commit after each task** with the message shown in the task.

---

## File Structure

**Web (`web-frontend/`)**
- `src/lib/api/items.ts` — add `SeasonEpisodes` + `getSeriesEpisodes`.
- `src/lib/api/items.test.ts` — tests for `getSeriesEpisodes`.
- `src/lib/components/VideoPlayer.svelte` — episodes button + overlay panel.

**Android (`android-app/`)**
- `app/src/main/kotlin/com/homeflix/app/data/MovieRepository.kt` — `SeasonEpisodes` + `getSeriesEpisodes`.
- `app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerViewModel.kt` — `EpisodesState` + `loadEpisodes()` + `playingItemId`.
- `app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerScreen.kt` — episodes button + `ModalBottomSheet`.
- `app/src/test/kotlin/com/homeflix/app/TestDoubles.kt` — fake update.
- `app/src/test/kotlin/com/homeflix/app/PlayerViewModelTest.kt` — episode-state tests.

---

## Task 1: Web `getSeriesEpisodes` API

**Files:**
- Modify: `web-frontend/src/lib/api/items.ts`
- Test: `web-frontend/src/lib/api/items.test.ts`

**Interfaces:**
- Consumes: existing `getSeasons(seriesId)`, `getEpisodes(seasonId)`.
- Produces: `SeasonEpisodes { season: BaseItemDto; episodes: BaseItemDto[] }` and `getSeriesEpisodes(seriesId: string): Promise<SeasonEpisodes[]>`.

- [ ] **Step 1: Write the failing tests**

In `web-frontend/src/lib/api/items.test.ts`, change the import on line 2 to:

```ts
import { getItem, getMovies, getEpisodeNeighbors, getSeriesEpisodes, search } from './items';
```

Append a new describe block at the end of the file:

```ts
describe('getSeriesEpisodes', () => {
	function seasonsResponse(): Response {
		return jsonResponse({
			Items: [
				{ Id: 's1', Name: 'Season 1', IndexNumber: 1 },
				{ Id: 's2', Name: 'Season 2', IndexNumber: 2 }
			]
		});
	}

	it('groups episodes under their season in order', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		vi.stubGlobal(
			'fetch',
			vi.fn<typeof fetch>().mockImplementation(async (input) => {
				const url = input as string;
				if (url.includes('IncludeItemTypes=Season')) return seasonsResponse();
				if (url.includes('ParentId=s1')) {
					return jsonResponse({ Items: [{ Id: 'e1', Name: 'S1E1', Type: 'Episode' }] });
				}
				return jsonResponse({
					Items: [
						{ Id: 'e2', Name: 'S2E1', Type: 'Episode' },
						{ Id: 'e3', Name: 'S2E2', Type: 'Episode' }
					]
				});
			})
		);

		const result = await getSeriesEpisodes('series1');

		expect(result.map((s) => s.season.Id)).toEqual(['s1', 's2']);
		expect(result[0].episodes.map((e) => e.Id)).toEqual(['e1']);
		expect(result[1].episodes.map((e) => e.Id)).toEqual(['e2', 'e3']);
	});

	it('returns an empty array for a series with no seasons', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(jsonResponse({ Items: [] })));

		expect(await getSeriesEpisodes('series1')).toEqual([]);
	});

	it('returns an empty episode list for a season whose fetch fails', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		vi.stubGlobal(
			'fetch',
			vi.fn<typeof fetch>().mockImplementation(async (input) => {
				const url = input as string;
				if (url.includes('IncludeItemTypes=Season')) return seasonsResponse();
				if (url.includes('ParentId=s1')) return new Response(JSON.stringify({}), { status: 500 });
				return jsonResponse({ Items: [{ Id: 'e2', Name: 'S2E1', Type: 'Episode' }] });
			})
		);

		const result = await getSeriesEpisodes('series1');

		expect(result[0].episodes).toEqual([]);
		expect(result[1].episodes.map((e) => e.Id)).toEqual(['e2']);
	});

	it('rejects when the season list itself fails', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({}), { status: 500 })));

		await expect(getSeriesEpisodes('series1')).rejects.toThrow();
	});
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run (from `web-frontend/`): `npm test -- src/lib/api/items.test.ts`
Expected: FAIL — `getSeriesEpisodes` is not exported.

- [ ] **Step 3: Implement `getSeriesEpisodes`**

In `web-frontend/src/lib/api/items.ts`, add after `getEpisodeNeighbors` (after line 83):

```ts
export interface SeasonEpisodes {
	season: BaseItemDto;
	episodes: BaseItemDto[];
}

export async function getSeriesEpisodes(seriesId: string): Promise<SeasonEpisodes[]> {
	const seasons = await getSeasons(seriesId);
	return Promise.all(
		seasons.map(async (season) => ({
			season,
			episodes: await getEpisodes(season.Id).catch(() => [])
		}))
	);
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run (from `web-frontend/`): `npm test -- src/lib/api/items.test.ts`
Expected: PASS.

- [ ] **Step 5: Type check, lint, full tests**

Run (from `web-frontend/`): `npm run check && npm run lint && npm test`
Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add web-frontend/src/lib/api/items.ts web-frontend/src/lib/api/items.test.ts
git commit -m "feat(web): add getSeriesEpisodes API"
```

---

## Task 2: Web episodes panel

**Files:**
- Modify: `web-frontend/src/lib/components/VideoPlayer.svelte`

**Interfaces:**
- Consumes: `getSeriesEpisodes` / `SeasonEpisodes` (Task 1), existing `goTo()`, `cancelCountdown()`, `neighbors`, `itemId`, `seriesId`.
- Produces: nothing used by later tasks.

- [ ] **Step 1: Update the import**

In `web-frontend/src/lib/components/VideoPlayer.svelte`, replace line 11:

```ts
	import { getItem, getEpisodeNeighbors, type EpisodeNeighbors } from '$lib/api/items';
```

with:

```ts
	import {
		getItem,
		getEpisodeNeighbors,
		getSeriesEpisodes,
		type EpisodeNeighbors,
		type SeasonEpisodes
	} from '$lib/api/items';
```

- [ ] **Step 2: Add state**

After the line `let countdownRemaining = $state(COUNTDOWN_SECONDS);`, add:

```ts
	let showEpisodes = $state(false);
	let episodesLoading = $state(false);
	let episodesError = $state('');
	let seasons = $state<SeasonEpisodes[]>([]);
```

- [ ] **Step 3: Reset the panel state on item change**

In the load `$effect`'s reset block, after `scrubValue = null;`, add:

```ts
		showEpisodes = false;
		episodesLoading = false;
		episodesError = '';
		seasons = [];
```

- [ ] **Step 4: Add the panel functions**

After `function goToPrevious() { ... }`, add:

```ts
	function openEpisodes() {
		showEpisodes = true;
		cancelCountdown();
		if (seasons.length === 0 && !episodesLoading) {
			loadEpisodes();
		}
	}

	function closeEpisodes() {
		showEpisodes = false;
	}

	function loadEpisodes() {
		if (!seriesId) return;
		episodesLoading = true;
		episodesError = '';
		getSeriesEpisodes(seriesId)
			.then((result) => {
				seasons = result;
			})
			.catch(() => {
				episodesError = 'Could not load episodes.';
			})
			.finally(() => {
				episodesLoading = false;
			});
	}

	function onKeydown(event: KeyboardEvent) {
		if (event.key === 'Escape' && showEpisodes) {
			closeEpisodes();
		}
	}

	function selectEpisode(episode: BaseItemDto) {
		if (episode.Id === itemId) return;
		closeEpisodes();
		goTo(episode);
	}

	function seasonLabel(season: BaseItemDto): string {
		return season.Name ?? (season.IndexNumber != null ? `Season ${season.IndexNumber}` : 'Season');
	}
```

- [ ] **Step 5: Add the Escape handler to the window**

Replace `<svelte:window onfullscreenchange={onFullscreenChange} />` with:

```svelte
<svelte:window onfullscreenchange={onFullscreenChange} onkeydown={onKeydown} />
```

- [ ] **Step 6: Add the episodes button to the control bar**

After the `Next episode` button block (`{/if}` following the next button, at line 345) and before `<span class="time">`, add:

```svelte
				{#if neighbors}
					<button class="icon" type="button" aria-label="Episodes" onclick={openEpisodes}>
						<svg viewBox="0 0 24 24" aria-hidden="true">
							<path d="M4 6h2v2H4z" />
							<path d="M8 6h12v2H8z" />
							<path d="M4 11h2v2H4z" />
							<path d="M8 11h12v2H8z" />
							<path d="M4 16h2v2H4z" />
							<path d="M8 16h12v2H8z" />
						</svg>
					</button>
				{/if}
```

- [ ] **Step 7: Add the panel markup**

After the countdown block (`{/if}` at line 384) and before the closing `</div>` of `.player`, add:

```svelte
			{#if showEpisodes}
				<button
					class="episodes-backdrop"
					type="button"
					aria-label="Close episodes"
					onclick={closeEpisodes}
				></button>
				<div class="episodes" role="dialog" aria-label="Episodes">
					<div class="episodes-head">
						<strong>Episodes</strong>
						<button class="icon" type="button" aria-label="Close" onclick={closeEpisodes}>
							<svg viewBox="0 0 24 24" aria-hidden="true">
								<path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2" fill="none" />
							</svg>
						</button>
					</div>
					{#if episodesLoading}
						<p class="episodes-msg">Loading…</p>
					{:else if episodesError}
						<p class="episodes-msg error">{episodesError}</p>
						<button class="episodes-retry" type="button" onclick={loadEpisodes}>Retry</button>
					{:else}
						<div class="episodes-list">
							{#each seasons as season (season.season.Id)}
								<p class="episodes-season">{seasonLabel(season.season)}</p>
								{#each season.episodes as episode (episode.Id)}
									<button
										class="episode-row"
										class:current={episode.Id === itemId}
										type="button"
										disabled={episode.Id === itemId}
										onclick={() => selectEpisode(episode)}
									>
										<span class="ep-num">
											{#if season.season.IndexNumber != null && episode.IndexNumber != null}
												S{season.season.IndexNumber}E{episode.IndexNumber}
											{/if}
										</span>
										<span class="ep-name">{episode.Name}</span>
									</button>
								{/each}
							{/each}
						</div>
					{/if}
				</div>
			{/if}
```

- [ ] **Step 8: Add styles**

In the `<style>` block, after the `.countdown-actions button` rule, add:

```css
	.episodes-backdrop {
		position: absolute;
		inset: 0;
		border: none;
		background: none;
		cursor: default;
	}

	.episodes {
		position: absolute;
		top: 0;
		right: 0;
		bottom: 0;
		width: min(22rem, 85%);
		display: flex;
		flex-direction: column;
		background-color: rgba(0, 0, 0, 0.92);
		color: #fff;
		border-radius: 0.5rem;
	}

	.episodes-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 0.75rem 1rem;
		border-bottom: 1px solid rgba(255, 255, 255, 0.15);
	}

	.episodes-list {
		overflow-y: auto;
		padding: 0.5rem 0 1rem;
	}

	.episodes-season {
		margin: 0.75rem 1rem 0.25rem;
		font-size: 0.8125rem;
		text-transform: uppercase;
		letter-spacing: 0.04em;
		color: var(--color-text-muted);
	}

	.episode-row {
		display: flex;
		align-items: baseline;
		gap: 0.75rem;
		width: 100%;
		padding: 0.5rem 1rem;
		border: none;
		background: none;
		color: inherit;
		text-align: left;
		cursor: pointer;
	}

	.episode-row:hover:not(:disabled) {
		background-color: rgba(255, 255, 255, 0.1);
	}

	.episode-row.current {
		color: var(--color-accent);
	}

	.episode-row:disabled {
		cursor: default;
	}

	.ep-num {
		min-width: 4rem;
		font-size: 0.8125rem;
		color: var(--color-text-muted);
	}

	.ep-name {
		flex: 1;
	}

	.episodes-msg {
		padding: 1rem;
		margin: 0;
	}

	.episodes-retry {
		margin: 0 1rem 1rem;
		align-self: flex-start;
		padding: 0.5rem 1rem;
		border: none;
		border-radius: 0.375rem;
		cursor: pointer;
	}
```

- [ ] **Step 9: Verify**

Run (from `web-frontend/`): `npm run check && npm run lint && npm run build && npm test`
Expected: all pass.

Manual (deferred if no server): from an episode, open Episodes; confirm seasons/episodes and the highlighted current episode; select another → plays and panel closes; Retry after failure; Escape/outside-click/close dismiss; a movie shows no Episodes button.

- [ ] **Step 10: Commit**

```bash
git add web-frontend/src/lib/components/VideoPlayer.svelte
git commit -m "feat(web): episode browser panel in the player"
```

---

## Task 3: Android `getSeriesEpisodes` and player state

**Files:**
- Modify: `android-app/app/src/main/kotlin/com/homeflix/app/data/MovieRepository.kt`
- Modify: `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerViewModel.kt`
- Modify: `android-app/app/src/test/kotlin/com/homeflix/app/TestDoubles.kt`
- Test: `android-app/app/src/test/kotlin/com/homeflix/app/PlayerViewModelTest.kt`

**Interfaces:**
- Consumes: existing `getSeasons`, `getEpisodes`.
- Produces:
  - `data class SeasonEpisodes(val season: BaseItemDto, val episodes: List<BaseItemDto>)`
  - `MovieRepository.getSeriesEpisodes(seriesId: String): List<SeasonEpisodes>`
  - `FakeMovieRepository.seriesEpisodes` / `.seriesEpisodesError` / `.seriesEpisodesCalls`
  - `PlayerViewModel.EpisodesState` + `episodesState: StateFlow<EpisodesState>` + `loadEpisodes()` + `playingItemId: String`

- [ ] **Step 1: Add `SeasonEpisodes` and the repository method**

In `android-app/app/src/main/kotlin/com/homeflix/app/data/MovieRepository.kt`:

Add the import `import kotlinx.coroutines.CancellationException`.

Add after `data class AdjacentEpisodes(...)`:

```kotlin
data class SeasonEpisodes(val season: BaseItemDto, val episodes: List<BaseItemDto>)
```

Add to the `MovieRepository` interface after `getAdjacentEpisodes`:

```kotlin
    suspend fun getSeriesEpisodes(seriesId: String): List<SeasonEpisodes>
```

Add to `JellyfinMovieRepository` after the `getAdjacentEpisodes` override:

```kotlin
    override suspend fun getSeriesEpisodes(seriesId: String): List<SeasonEpisodes> {
        val seasons = getSeasons(seriesId)
        return seasons.map { season ->
            val episodes = try {
                getEpisodes(season.id.toString())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            SeasonEpisodes(season, episodes)
        }
    }
```

- [ ] **Step 2: Update `FakeMovieRepository`**

In `android-app/app/src/test/kotlin/com/homeflix/app/TestDoubles.kt`:

Add the import `com.homeflix.app.data.SeasonEpisodes`.

Add fields next to `adjacentEpisodes`:

```kotlin
    var seriesEpisodes: List<SeasonEpisodes> = emptyList()
    var seriesEpisodesError: Exception? = null
    var seriesEpisodesCalls: Int = 0
```

Add the override after `getAdjacentEpisodes`:

```kotlin
    override suspend fun getSeriesEpisodes(seriesId: String): List<SeasonEpisodes> {
        seriesEpisodesCalls += 1
        seriesEpisodesError?.let { throw it }
        error?.let { throw it }
        return seriesEpisodes
    }
```

- [ ] **Step 3: Add the ViewModel state and loader**

In `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerViewModel.kt`:

Add the import `import com.homeflix.app.data.SeasonEpisodes`.

Add after `private var mediaSourceId: String = ""`:

```kotlin
    val playingItemId: String get() = movieId

    private var currentSeriesId: String? = null
```

Add inside the class, after the `UiState` declaration:

```kotlin
    sealed interface EpisodesState {
        data object Idle : EpisodesState
        data object Loading : EpisodesState
        data class Error(val message: String) : EpisodesState
        data class Loaded(val seasons: List<SeasonEpisodes>) : EpisodesState
    }
```

Add after `val unauthorizedEvents: Flow<Unit> = ...`:

```kotlin
    private val _episodesState = MutableStateFlow<EpisodesState>(EpisodesState.Idle)
    val episodesState: StateFlow<EpisodesState> = _episodesState.asStateFlow()
```

In `load()`, after `val movie = movieRepository.getMovie(movieId)`, add:

```kotlin
                currentSeriesId = movie.seriesId?.toString()
```

Add after `load()`:

```kotlin
    fun loadEpisodes() {
        val current = _episodesState.value
        if (current is EpisodesState.Loading || current is EpisodesState.Loaded) return
        val seriesId = currentSeriesId
        if (seriesId.isNullOrEmpty()) {
            _episodesState.value = EpisodesState.Error("This item has no series.")
            return
        }
        viewModelScope.launch {
            _episodesState.value = EpisodesState.Loading
            try {
                val seasons = movieRepository.getSeriesEpisodes(seriesId)
                _episodesState.value = EpisodesState.Loaded(seasons)
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidStatusException) {
                if (e.status == 401) {
                    _unauthorizedEvents.send(Unit)
                }
                _episodesState.value = EpisodesState.Error("Could not load episodes.")
            } catch (_: Exception) {
                _episodesState.value = EpisodesState.Error("Could not load episodes.")
            }
        }
    }
```

- [ ] **Step 4: Write the ViewModel tests**

In `android-app/app/src/test/kotlin/com/homeflix/app/PlayerViewModelTest.kt`, add the imports `com.homeflix.app.data.SeasonEpisodes` and `org.jellyfin.sdk.model.api.BaseItemKind` (if not present) and append:

```kotlin
    @Test
    fun loadEpisodes_success_setsLoadedSeasons() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply {
            stream = PlaybackStream("http://host/stream", "ms1")
            movie = baseItem(EPISODE_ID, "Episode 1", type = BaseItemKind.EPISODE, seriesId = SERIES_ID)
            seriesEpisodes = listOf(
                SeasonEpisodes(
                    season = baseItem("00000000-0000-0000-0000-000000000301", "Season 1", type = BaseItemKind.SEASON),
                    episodes = listOf(baseItem(EPISODE_ID, "Episode 1", type = BaseItemKind.EPISODE))
                )
            )
        }
        val viewModel = createViewModel(repo, movieId = EPISODE_ID)
        advanceUntilIdle()

        viewModel.loadEpisodes()
        advanceUntilIdle()

        val state = viewModel.episodesState.value as PlayerViewModel.EpisodesState.Loaded
        assertEquals(1, state.seasons.size)
        assertEquals("Season 1", state.seasons[0].season.name)
    }

    @Test
    fun loadEpisodes_failure_setsErrorAndKeepsPlayback() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply {
            stream = PlaybackStream("http://host/stream", "ms1")
            movie = baseItem(EPISODE_ID, "Episode 1", type = BaseItemKind.EPISODE, seriesId = SERIES_ID)
            seriesEpisodesError = RuntimeException("boom")
        }
        val viewModel = createViewModel(repo, movieId = EPISODE_ID)
        advanceUntilIdle()

        viewModel.loadEpisodes()
        advanceUntilIdle()

        assertTrue(viewModel.episodesState.value is PlayerViewModel.EpisodesState.Error)
        assertTrue(viewModel.uiState.value is PlayerViewModel.UiState.Success)
    }

    @Test
    fun loadEpisodes_twice_loadsOnce() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply {
            stream = PlaybackStream("http://host/stream", "ms1")
            movie = baseItem(EPISODE_ID, "Episode 1", type = BaseItemKind.EPISODE, seriesId = SERIES_ID)
        }
        val viewModel = createViewModel(repo, movieId = EPISODE_ID)
        advanceUntilIdle()

        viewModel.loadEpisodes()
        advanceUntilIdle()
        viewModel.loadEpisodes()
        advanceUntilIdle()

        assertEquals(1, repo.seriesEpisodesCalls)
    }

    @Test
    fun loadEpisodes_movieWithNoSeries_setsError() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply {
            stream = PlaybackStream("http://host/stream", "ms1")
            movie = baseItem(MOVIE_ID, "Movie")
        }
        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        viewModel.loadEpisodes()
        advanceUntilIdle()

        assertTrue(viewModel.episodesState.value is PlayerViewModel.EpisodesState.Error)
    }
```

- [ ] **Step 5: Run the Android unit tests**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all tests pass.

- [ ] **Step 6: Commit**

```bash
git add android-app/app/src/main/kotlin/com/homeflix/app/data/MovieRepository.kt android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerViewModel.kt android-app/app/src/test/kotlin/com/homeflix/app/TestDoubles.kt android-app/app/src/test/kotlin/com/homeflix/app/PlayerViewModelTest.kt
git commit -m "feat(android): series episodes data and player episode state"
```

---

## Task 4: Android episodes button and sheet

**Files:**
- Modify: `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerScreen.kt`

**Interfaces:**
- Consumes: `PlayerViewModel.EpisodesState`, `episodesState`, `loadEpisodes`, `playingItemId` from Task 3.
- Produces: nothing used later.

- [ ] **Step 1: Add imports**

In `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerScreen.kt`, add:

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
```

- [ ] **Step 2: Pass the new values into `VideoPlayer`**

In the `PlayerViewModel.UiState.Success` branch, change the `VideoPlayer(...)` call to add:

```kotlin
                    isEpisode = state.isEpisode,
                    episodesState = viewModel.episodesState.collectAsState().value,
                    currentEpisodeId = viewModel.playingItemId,
                    onLoadEpisodes = viewModel::loadEpisodes,
```

(The existing `previousEpisode`, `nextEpisode`, `onPlayEpisode`, report callbacks and `modifier` stay.)

- [ ] **Step 3: Extend the private `VideoPlayer` signature**

Change the private composable signature to (and add the Material3 opt-in, since the sheet is experimental):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoPlayer(
    streamUrl: String,
    resumeTicks: Long,
    isEpisode: Boolean,
    previousEpisode: PlayerViewModel.EpisodeRef?,
    nextEpisode: PlayerViewModel.EpisodeRef?,
    episodesState: PlayerViewModel.EpisodesState,
    currentEpisodeId: String,
    onLoadEpisodes: () -> Unit,
    onPlayEpisode: (String) -> Unit,
    onReportStarted: (Long) -> Unit,
    onReportProgress: (Long, Boolean) -> Unit,
    onReportStopped: (Long) -> Unit,
    modifier: Modifier = Modifier
)
```

- [ ] **Step 4: Add sheet state**

Next to the other `remember(streamUrl)` state, add:

```kotlin
    var showEpisodes by remember(streamUrl) { mutableStateOf(false) }
    val episodesSheetState = rememberModalBottomSheetState()
```

- [ ] **Step 5: Add the episodes button**

In the controls `Row`, after the `SkipNext` `IconButton` block (`}` closing the `if (isEpisode)` that wraps it), add:

```kotlin
                    if (isEpisode) {
                        IconButton(
                            onClick = {
                                onLoadEpisodes()
                                showEpisodes = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VideoLibrary,
                                contentDescription = "Episodes",
                                tint = Color.White
                            )
                        }
                    }
```

- [ ] **Step 6: Add the bottom sheet**

After the countdown overlay block, still inside the outer `Box`, add:

```kotlin
            if (showEpisodes) {
                ModalBottomSheet(
                    onDismissRequest = { showEpisodes = false },
                    sheetState = episodesSheetState
                ) {
                    when (val episodes = episodesState) {
                        is PlayerViewModel.EpisodesState.Loading -> {
                            Text(
                                text = "Loading episodes…",
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }

                        is PlayerViewModel.EpisodesState.Error -> {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(episodes.message, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = onLoadEpisodes) {
                                    Text("Retry")
                                }
                            }
                        }

                        is PlayerViewModel.EpisodesState.Loaded -> {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                episodes.seasons.forEach { seasonEpisodes ->
                                    item(key = seasonEpisodes.season.id.toString()) {
                                        Text(
                                            text = seasonEpisodes.season.name.orEmpty(),
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                    items(
                                        items = seasonEpisodes.episodes,
                                        key = { it.id.toString() }
                                    ) { episode ->
                                        val current = episode.id.toString() == currentEpisodeId
                                        val seasonNumber = seasonEpisodes.season.indexNumber
                                        val episodeNumber = episode.indexNumber
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = !current) {
                                                    showEpisodes = false
                                                    onPlayEpisode(episode.id.toString())
                                                }
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (seasonNumber != null && episodeNumber != null) {
                                                    "S${seasonNumber}E${episodeNumber}"
                                                } else {
                                                    ""
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.width(56.dp)
                                            )
                                            Text(
                                                text = episode.name.orEmpty(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        is PlayerViewModel.EpisodesState.Idle -> Unit
                    }
                }
            }
```

- [ ] **Step 7: Compile and run tests**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Manual verification (deferred if no device)**

Install on Windows/MuMu and verify: from an episode, tap the Episodes button; seasons/episodes appear and the current episode is highlighted; selecting another plays it and closes the sheet; a movie shows no Episodes button; Android Back closes the sheet.

- [ ] **Step 9: Commit**

```bash
git add android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/PlayerScreen.kt
git commit -m "feat(android): episode browser sheet in the player"
```

---

## Self-Review Notes

- **Spec coverage:** R1 (Tasks 1,3 data + Tasks 2,4 button gating), R2 (Tasks 2,4 control button), R3 (season grouping in Tasks 1,3 and rendering in 2,4), R4 (highlight/disable in Tasks 2,4), R5 (Tasks 2 `goTo`, 4 `onPlayEpisode`), R6 (lazy `openEpisodes`/`loadEpisodes` + error/Retry), R7 (playback untouched), R8 (Escape/backdrop on web; sheet dismissal on Android), R9 (shared behavior), §7 error handling (per-season catch + error state), §8 tests (Tasks 1,3).
- **No placeholders:** every code step contains the complete code to write.
- **Type consistency:** `SeasonEpisodes`/`getSeriesEpisodes` (web and Android), `EpisodesState`, `episodesState`, `loadEpisodes`, `playingItemId`, `currentEpisodeId`, `onLoadEpisodes` are used consistently across Tasks 2-4.
- **Reused APIs:** `goTo` (web) and `onPlayEpisode` (Android) already exist; no navigation changes.

## Rollout (after merge)

- Web: redeploy with `scripts/deploy-web.sh`.
- Android: rebuild the APK and install on Windows/MuMu per `docs/development.md` §6.4.
