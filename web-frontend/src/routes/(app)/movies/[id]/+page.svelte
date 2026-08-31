<script lang="ts">
	import { page } from '$app/state';
	import { getItem } from '$lib/api/items';
	import { imageUrl } from '$lib/api/images';
	import type { BaseItemDto } from '$lib/api/types';

	let item = $state<BaseItemDto | null>(null);
	let loading = $state(true);
	let error = $state('');

	$effect(() => {
		item = null;
		error = '';
		loading = true;
		const id = page.params.id;
		if (!id) {
			error = 'Invalid movie.';
			loading = false;
			return;
		}
		getItem(id)
			.then((i) => {
				item = i;
			})
			.catch(() => {
				error = 'Could not load this movie.';
			})
			.finally(() => {
				loading = false;
			});
	});

	function formatRuntime(ticks: number): string {
		const minutes = Math.round(ticks / 600_000_000);
		const h = Math.floor(minutes / 60);
		const m = minutes % 60;
		if (h === 0) return `${m}m`;
		return `${h}h ${m}m`;
	}
</script>

{#if loading}
	<p>Loading…</p>
{:else if error}
	<p class="error" role="alert">{error}</p>
{:else if item}
	<article>
		{#if item.BackdropImageTags?.[0]}
			<div class="backdrop-wrap">
				<img
					class="backdrop"
					src={imageUrl(item.Id, item.BackdropImageTags[0], 'Backdrop')}
					alt=""
				/>
			</div>
		{/if}

		<div class="body">
			<h1>{item.Name}</h1>
			<p class="meta">
				{#if item.ProductionYear}<span>{item.ProductionYear}</span>{/if}
				{#if item.RunTimeTicks}<span>{formatRuntime(item.RunTimeTicks)}</span>{/if}
				{#if item.Genres?.length}<span>{item.Genres.join(', ')}</span>{/if}
			</p>

			{#if item.Overview}
				<p class="overview">{item.Overview}</p>
			{/if}

			<a class="play" href={`/movies/${item.Id}/play`}>Play</a>
		</div>
	</article>
{/if}

<style>
	.backdrop-wrap {
		width: 100%;
	}

	.backdrop {
		width: 100%;
		aspect-ratio: 16 / 9;
		object-fit: cover;
		border-radius: 0.5rem;
		background-color: var(--color-bg-alt);
	}

	.body {
		padding: 1.5rem 0;
	}

	h1 {
		margin: 0 0 0.5rem;
	}

	.meta {
		display: flex;
		flex-wrap: wrap;
		gap: 0.75rem;
		color: var(--color-text-muted);
		font-size: 0.875rem;
		margin: 0 0 1rem;
	}

	.overview {
		max-width: 48rem;
		line-height: 1.6;
		margin: 0 0 1.5rem;
	}

	.play {
		display: inline-block;
		padding: 0.6rem 1.5rem;
		border-radius: 0.375rem;
		background-color: var(--color-accent);
		color: #fff;
		font-weight: 600;
	}

	.play:hover {
		text-decoration: none;
	}

	.error {
		color: #e5484d;
	}
</style>
