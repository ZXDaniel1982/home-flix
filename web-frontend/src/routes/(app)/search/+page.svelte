<script lang="ts">
	import { page } from '$app/state';
	import MoviePoster from '$lib/components/MoviePoster.svelte';
	import { search } from '$lib/api/items';
	import type { BaseItemDto } from '$lib/api/types';

	const q = $derived(page.url.searchParams.get('q') ?? '');

	let results = $state<BaseItemDto[]>([]);
	let loading = $state(true);
	let error = $state('');
	let requestId = 0;

	$effect(() => {
		results = [];
		error = '';
		loading = true;
		const id = ++requestId;
		if (!q) {
			loading = false;
			return;
		}
		search(q)
			.then((items) => {
				if (id === requestId) results = items;
			})
			.catch(() => {
				if (id === requestId) error = 'Search failed. Please try again.';
			})
			.finally(() => {
				if (id === requestId) loading = false;
			});
	});

	function href(item: BaseItemDto): string {
		return item.Type === 'Series' ? `/tv/${item.Id}` : `/movies/${item.Id}`;
	}
</script>

<h1>Search</h1>

{#if !q}
	<p>Type something in the search bar above.</p>
{:else if loading}
	<p>Searching…</p>
{:else if error}
	<p class="error" role="alert">{error}</p>
{:else if results.length === 0}
	<p>No results for "{q}".</p>
{:else}
	<p class="count">{results.length} result(s) for "{q}"</p>
	<div class="grid">
		{#each results as item (item.Id)}
			<a class="card" href={href(item)}>
				<MoviePoster {item} />
				<span class="title">{item.Name}</span>
			</a>
		{/each}
	</div>
{/if}

<style>
	h1 {
		margin: 1rem 0;
	}

	.count {
		color: var(--color-text-muted);
		font-size: 0.875rem;
		margin: 0 0 1rem;
	}

	.grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
		gap: 1.25rem;
	}

	@media (max-width: 40rem) {
		.grid {
			grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
		}
	}

	.card {
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
		color: var(--color-text);
	}

	.card:hover {
		text-decoration: none;
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
