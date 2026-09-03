<script lang="ts">
	import MoviePoster from '$lib/components/MoviePoster.svelte';
	import { getTvSeries } from '$lib/api/items';
	import type { BaseItemDto } from '$lib/api/types';

	const PAGE_SIZE = 50;

	let series = $state<BaseItemDto[]>([]);
	let totalCount = $state(0);
	let loading = $state(true);
	let loadingMore = $state(false);
	let error = $state('');
	let sentinel = $state<HTMLDivElement | null>(null);

	$effect(() => {
		getTvSeries(0, PAGE_SIZE)
			.then((page) => {
				series = page.items;
				totalCount = page.totalCount;
			})
			.catch(() => {
				error = 'Could not load TV shows. Please try again.';
			})
			.finally(() => {
				loading = false;
			});
	});

	$effect(() => {
		const el = sentinel;
		if (!el) return;
		const observer = new IntersectionObserver(
			(entries) => {
				if (entries[0].isIntersecting) {
					loadMore();
				}
			},
			{ rootMargin: '200px' }
		);
		observer.observe(el);
		return () => observer.disconnect();
	});

	async function loadMore() {
		if (loadingMore || series.length >= totalCount) return;
		loadingMore = true;
		try {
			const page = await getTvSeries(series.length, PAGE_SIZE);
			series = [...series, ...page.items];
			totalCount = page.totalCount;
		} catch {
			// keep the already-loaded list on failure
		} finally {
			loadingMore = false;
		}
	}
</script>

<h1>TV Shows</h1>

{#if loading}
	<p>Loading…</p>
{:else if error}
	<p class="error" role="alert">{error}</p>
{:else if series.length === 0}
	<p>No TV shows found.</p>
{:else}
	<div class="grid">
		{#each series as show (show.Id)}
			<a class="card" href={`/tv/${show.Id}`}>
				<MoviePoster item={show} />
				<span class="title">{show.Name}</span>
			</a>
		{/each}
	</div>
	<div bind:this={sentinel} class="sentinel"></div>
	{#if loadingMore}
		<p class="loading-more">Loading more…</p>
	{/if}
{/if}

<style>
	h1 {
		margin: 1rem 0;
	}

	.grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
		gap: 1.25rem;
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

	.sentinel {
		height: 1px;
	}

	.loading-more {
		text-align: center;
		color: var(--color-text-muted);
		font-size: 0.875rem;
	}

	.error {
		color: #e5484d;
	}
</style>
