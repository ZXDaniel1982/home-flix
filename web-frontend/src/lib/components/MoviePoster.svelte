<script lang="ts">
	import { imageUrl } from '$lib/api/images';
	import type { BaseItemDto } from '$lib/api/types';

	let { item }: { item: BaseItemDto } = $props();

	const tag = $derived(item.ImageTags?.Primary);
	let failed = $state(false);
</script>

{#if tag && !failed}
	<img
		class="poster"
		src={imageUrl(item.Id, tag)}
		alt={item.Name}
		loading="lazy"
		onerror={() => (failed = true)}
	/>
{:else}
	<div class="poster placeholder">{item.Name}</div>
{/if}

<style>
	.poster {
		width: 100%;
		aspect-ratio: 2 / 3;
		object-fit: cover;
		border-radius: 0.5rem;
		background-color: var(--color-bg-alt);
	}

	.placeholder {
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 1rem;
		text-align: center;
		font-size: 0.875rem;
		color: var(--color-text-muted);
	}
</style>
