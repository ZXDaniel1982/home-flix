<script lang="ts">
	import { page } from '$app/state';
	import VideoPlayer from '$lib/components/VideoPlayer.svelte';

	const seriesId = $derived(page.params.seriesId ?? '');
	const episodeId = $derived(page.params.episodeId ?? '');
	const autoplay = $derived(page.url.searchParams.get('autoplay') === '1');
</script>

<VideoPlayer itemId={episodeId} backHref={`/tv/${seriesId}`} {autoplay} />
