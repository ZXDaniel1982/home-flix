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
	import {
		getItem,
		getEpisodeNeighbors,
		getSeriesEpisodes,
		type EpisodeNeighbors,
		type SeasonEpisodes
	} from '$lib/api/items';
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
	let showEpisodes = $state(false);
	let episodesLoading = $state(false);
	let episodesError = $state('');
	let seasons = $state<SeasonEpisodes[]>([]);

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
		showEpisodes = false;
		episodesLoading = false;
		episodesError = '';
		seasons = [];
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
		if (!seriesId) {
			episodesError = 'This item has no series.';
			return;
		}
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

<svelte:window onfullscreenchange={onFullscreenChange} onkeydown={onKeydown} />

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

			{#if showEpisodes}
				<button
					class="episodes-backdrop"
					type="button"
					aria-label="Close episodes"
					onclick={closeEpisodes}
				></button>
				<div class="episodes" role="dialog" aria-modal="true" aria-label="Episodes">
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

	.player:fullscreen {
		max-width: none;
		width: 100%;
		height: 100%;
		background-color: #000;
	}

	.player:fullscreen video {
		height: 100%;
		max-height: 100%;
		object-fit: contain;
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

	.error {
		color: #e5484d;
	}
</style>
