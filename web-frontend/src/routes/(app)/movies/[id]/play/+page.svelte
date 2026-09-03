<script lang="ts">
	import { page } from '$app/state';
	import VideoPlayer from '$lib/components/VideoPlayer.svelte';

	const id = $derived(page.params.id ?? '');
</script>

<VideoPlayer itemId={id} backHref={`/movies/${id}`} />
