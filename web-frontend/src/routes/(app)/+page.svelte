<script lang="ts">
	import MoviePoster from '$lib/components/MoviePoster.svelte';
	import { getResume } from '$lib/api/items';
	import type { BaseItemDto } from '$lib/api/types';

	let items = $state<BaseItemDto[]>([]);
	let loading = $state(true);
	let error = $state('');

	$effect(() => {
		getResume()
			.then((res) => {
				items = res;
			})
			.catch(() => {
				error = 'Could not load continue watching.';
			})
			.finally(() => {
				loading = false;
			});
	});

	function playHref(item: BaseItemDto): string {
		if (item.Type === 'Episode' && item.SeriesId) {
			return `/tv/${item.SeriesId}/play/${item.Id}`;
		}
		return `/movies/${item.Id}/play`;
	}

	function progress(item: BaseItemDto): number {
		return Math.min(100, item.UserData?.PlayedPercentage ?? 0);
	}
</script>

<h1>Home Flix</h1>

{#if loading}
	<p>Loading…</p>
{:else if error}
	<p class="error" role="alert">{error}</p>
{:else if items.length > 0}
	<h2>Continue Watching</h2>
	<div class="row">
		{#each items as item (item.Id)}
			<a class="card" href={playHref(item)}>
				<MoviePoster item={item} />
				<div class="progress-track">
					<div class="progress-fill" style={`width: ${progress(item)}%`}></div>
				</div>
				<span class="title">{item.Name}</span>
			</a>
		{/each}
	</div>
{/if}

<style>
	h1 {
		margin: 1rem 0 0;
	}

	h2 {
		margin: 1.5rem 0 1rem;
	}

	.row {
		display: flex;
		gap: 1.25rem;
		overflow-x: auto;
		padding-bottom: 0.5rem;
	}

	.card {
		flex: 0 0 160px;
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
		color: var(--color-text);
	}

	.card:hover {
		text-decoration: none;
	}

	.progress-track {
		height: 4px;
		border-radius: 2px;
		background-color: var(--color-border);
		overflow: hidden;
	}

	.progress-fill {
		height: 100%;
		background-color: var(--color-accent);
	}

	.title {
		font-size: 0.875rem;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.error {
		color: #e5484d;
	}
</style>
