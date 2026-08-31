<script lang="ts">
	import MoviePoster from '$lib/components/MoviePoster.svelte';
	import { getMovies } from '$lib/api/items';
	import type { BaseItemDto } from '$lib/api/types';

	let movies = $state<BaseItemDto[]>([]);
	let loading = $state(true);
	let error = $state('');

	$effect(() => {
		getMovies()
			.then((items) => {
				movies = items;
			})
			.catch(() => {
				error = 'Could not load movies. Please try again.';
			})
			.finally(() => {
				loading = false;
			});
	});
</script>

<h1>Movies</h1>

{#if loading}
	<p>Loading…</p>
{:else if error}
	<p class="error" role="alert">{error}</p>
{:else if movies.length === 0}
	<p>No movies found.</p>
{:else}
	<div class="grid">
		{#each movies as movie (movie.Id)}
			<a class="card" href={`/movies/${movie.Id}`}>
				<MoviePoster item={movie} />
				<span class="title">{movie.Name}</span>
			</a>
		{/each}
	</div>
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

	.error {
		color: #e5484d;
	}
</style>
