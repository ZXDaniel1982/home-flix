<script lang="ts">
	import { page } from '$app/state';
	import { getPlaybackInfo, streamUrl } from '$lib/api/playback';
	import type { MediaSourceInfo } from '$lib/api/types';

	const id = $derived(page.params.id ?? '');

	let streamSrc = $state('');
	let loading = $state(true);
	let error = $state('');
	let playbackError = $state(false);

	$effect(() => {
		streamSrc = '';
		error = '';
		playbackError = false;
		loading = true;
		if (!id) {
			error = 'Invalid movie.';
			loading = false;
			return;
		}
		load(id)
			.then((src) => {
				streamSrc = src;
			})
			.catch(() => {
				error = 'Could not load the video.';
			})
			.finally(() => {
				loading = false;
			});
	});

	async function load(movieId: string): Promise<string> {
		const info = await getPlaybackInfo(movieId);
		const sources = info.MediaSources ?? [];
		const source: MediaSourceInfo | undefined =
			sources.find((s) => s.SupportsDirectPlay) ?? sources[0];
		if (!source) {
			throw new Error('No playable media source');
		}
		return streamUrl(movieId, source.Id);
	}
</script>

{#if loading}
	<p>Loading…</p>
{:else if error}
	<p class="error" role="alert">{error}</p>
{:else if streamSrc}
	<a class="back" href={`/movies/${id}`}>Back to movie</a>
	{#if playbackError}
		<p class="error" role="alert">
			This video couldn't be played. It may be in an unsupported format (H.264/AAC MP4 is
			required).
		</p>
	{:else}
		<div class="player">
			<!-- svelte-ignore a11y_media_has_caption -->
			<video controls src={streamSrc} onerror={() => (playbackError = true)}></video>
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
