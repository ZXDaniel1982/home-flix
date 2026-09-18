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
		stoppedReported = false;
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
		reportStopped();
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
		reportStopped();
		goto(resolve(`/tv/${seriesId}/play/${nextEpisode.Id}?autoplay=1`));
	}

	function sendProgress() {
		if (!mediaSourceId) return;
		reportPlaybackProgress(itemId, mediaSourceId, currentTicks(), video?.paused ?? true).catch(
			() => {}
		);
	}

	function reportStopped() {
		if (stoppedReported || !mediaSourceId) return;
		stoppedReported = true;
		const ticks = Math.floor((video?.currentTime ?? 0) * 10_000_000);
		reportPlaybackStopped(itemId, mediaSourceId, ticks).catch(() => {});
	}

	$effect(() => {
		const el = video;
		if (!el) return;
		return () => {
			clearCountdown();
			reportStopped();
		};
	});
</script>

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
		<div class="player">
			<!-- svelte-ignore a11y_media_has_caption -->
			<video
				controls
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

	video {
		width: 100%;
		max-height: 70vh;
		background-color: #000;
		border-radius: 0.5rem;
	}

	.error {
		color: #e5484d;
	}
</style>
