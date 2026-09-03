<script lang="ts">
	import {
		getPlaybackInfo,
		streamUrl,
		reportPlaybackStarted,
		reportPlaybackProgress,
		reportPlaybackStopped
	} from '$lib/api/playback';
	import { getItem } from '$lib/api/items';
	import type { MediaSourceInfo } from '$lib/api/types';

	let { itemId, backHref }: { itemId: string; backHref?: string } = $props();

	const PROGRESS_INTERVAL_MS = 10_000;

	let streamSrc = $state('');
	let loading = $state(true);
	let error = $state('');
	let playbackError = $state(false);
	let video = $state<HTMLVideoElement | null>(null);

	let mediaSourceId = '';
	let resumeTicks = 0;
	let lastReportAt = 0;

	$effect(() => {
		streamSrc = '';
		error = '';
		playbackError = false;
		loading = true;
		mediaSourceId = '';
		resumeTicks = 0;
		lastReportAt = 0;
		if (!itemId) {
			error = 'Invalid item.';
			loading = false;
			return;
		}
		load(itemId)
			.then(({ src, msId, ticks }) => {
				streamSrc = src;
				mediaSourceId = msId;
				resumeTicks = ticks;
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
		return {
			src: streamUrl(id, source.Id),
			msId: source.Id,
			ticks: item.UserData?.PlaybackPositionTicks ?? 0
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
			const ticks = Math.floor(el.currentTime * 10_000_000);
			if (mediaSourceId) {
				reportPlaybackStopped(itemId, mediaSourceId, ticks).catch(() => {});
			}
		};
	});
</script>

{#if loading}
	<p>Loading…</p>
{:else if error}
	<p class="error" role="alert">{error}</p>
{:else if streamSrc}
	{#if backHref}
		<a class="back" href={backHref}>Back</a>
	{/if}
	{#if playbackError}
		<p class="error" role="alert">
			This video couldn't be played. It may be in an unsupported format (H.264/AAC MP4 is
			required).
		</p>
	{:else}
		<div class="player">
			<!-- svelte-ignore a11y_media_has_caption -->
			<video
				controls
				src={streamSrc}
				bind:this={video}
				onloadedmetadata={onLoadedMetadata}
				onplay={onPlay}
				ontimeupdate={onTimeUpdate}
				onpause={onPause}
				onerror={() => (playbackError = true)}
			></video>
		</div>
	{/if}
{/if}

<style>
	.back {
		display: inline-block;
		margin-bottom: 1rem;
	}

	.player {
		width: 100%;
		max-width: 60rem;
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
