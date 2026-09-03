<script lang="ts">
	import { page } from '$app/state';
	import { getItem, getSeasons, getEpisodes } from '$lib/api/items';
	import { imageUrl } from '$lib/api/images';
	import { formatRuntime } from '$lib/format';
	import type { BaseItemDto } from '$lib/api/types';

	const seriesId = $derived(page.params.id ?? '');

	let series = $state<BaseItemDto | null>(null);
	let seasons = $state<BaseItemDto[]>([]);
	let episodesBySeason = $state<Record<string, BaseItemDto[]>>({});
	let loading = $state(true);
	let error = $state('');

	$effect(() => {
		series = null;
		seasons = [];
		episodesBySeason = {};
		error = '';
		loading = true;
		if (!seriesId) {
			error = 'Invalid show.';
			loading = false;
			return;
		}
		load(seriesId)
			.then(({ s, seasonList, episodes }) => {
				series = s;
				seasons = seasonList;
				episodesBySeason = episodes;
			})
			.catch(() => {
				error = 'Could not load this show.';
			})
			.finally(() => {
				loading = false;
			});
	});

	async function load(id: string) {
		const [s, seasonList] = await Promise.all([getItem(id), getSeasons(id)]);
		const episodes: Record<string, BaseItemDto[]> = {};
		const results = await Promise.allSettled(seasonList.map((season) => getEpisodes(season.Id)));
		seasonList.forEach((season, i) => {
			const result = results[i];
			episodes[season.Id] = result.status === 'fulfilled' ? result.value : [];
		});
		return { s, seasonList, episodes };
	}
</script>

{#if loading}
	<p>Loading…</p>
{:else if error}
	<p class="error" role="alert">{error}</p>
{:else if series}
	<article>
		{#if series.BackdropImageTags?.[0]}
			<div class="backdrop-wrap">
				<img
					class="backdrop"
					src={imageUrl(series.Id, series.BackdropImageTags[0], 'Backdrop')}
					alt=""
				/>
			</div>
		{/if}

		<div class="body">
			<h1>{series.Name}</h1>
			{#if series.ProductionYear}
				<p class="year">{series.ProductionYear}</p>
			{/if}
			{#if series.Overview}
				<p class="overview">{series.Overview}</p>
			{/if}
		</div>

		{#each seasons as season (season.Id)}
			<section class="season">
				<h2>{season.Name}</h2>
				<ul class="episodes">
					{#each episodesBySeason[season.Id] ?? [] as episode (episode.Id)}
						<li>
							<a class="episode" href={`/tv/${seriesId}/play/${episode.Id}`}>
							{#if season.IndexNumber != null && episode.IndexNumber != null}
								<span class="ep-num">S{season.IndexNumber}E{episode.IndexNumber}</span>
							{:else}
								<span class="ep-num"></span>
							{/if}
								<span class="ep-name">{episode.Name}</span>
								{#if episode.RunTimeTicks}
									<span class="ep-runtime">{formatRuntime(episode.RunTimeTicks)}</span>
								{/if}
							</a>
						</li>
					{/each}
				</ul>
			</section>
		{/each}
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
		margin: 0 0 0.25rem;
	}

	.year {
		color: var(--color-text-muted);
		font-size: 0.875rem;
		margin: 0 0 1rem;
	}

	.overview {
		max-width: 48rem;
		line-height: 1.6;
		margin: 0;
	}

	.season {
		margin-top: 1.5rem;
	}

	.season h2 {
		margin: 0 0 0.75rem;
	}

	.episodes {
		list-style: none;
		margin: 0;
		padding: 0;
	}

	.episode {
		display: flex;
		align-items: baseline;
		gap: 1rem;
		padding: 0.5rem 0.5rem;
		border-radius: 0.375rem;
		color: var(--color-text);
	}

	.episode:hover {
		background-color: var(--color-bg-alt);
		text-decoration: none;
	}

	.ep-num {
		color: var(--color-text-muted);
		font-size: 0.8125rem;
		min-width: 4.5rem;
	}

	.ep-name {
		flex: 1;
	}

	.ep-runtime {
		color: var(--color-text-muted);
		font-size: 0.8125rem;
	}

	.error {
		color: #e5484d;
	}
</style>
