<script lang="ts">
	import NavBar from '$lib/components/NavBar.svelte';

	let { children } = $props();
</script>

<NavBar />
<main>
	{@render children()}
</main>
