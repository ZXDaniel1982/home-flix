import { apiFetch } from './client';
import { getUser } from './session';
import type { BaseItemDto, QueryResult } from './types';

export interface ItemPage {
	items: BaseItemDto[];
	totalCount: number;
}

export async function getMovies(startIndex: number, limit: number): Promise<ItemPage> {
	const user = getUser();
	if (!user) {
		throw new Error('Not authenticated');
	}
	const result = await apiFetch<QueryResult<BaseItemDto>>(
		`/Users/${user.Id}/Items?IncludeItemTypes=Movie&Recursive=true&SortBy=SortName&SortOrder=Ascending&StartIndex=${startIndex}&Limit=${limit}`
	);
	return { items: result.Items ?? [], totalCount: result.TotalRecordCount ?? 0 };
}

export async function getTvSeries(startIndex: number, limit: number): Promise<ItemPage> {
	const user = getUser();
	if (!user) {
		throw new Error('Not authenticated');
	}
	const result = await apiFetch<QueryResult<BaseItemDto>>(
		`/Users/${user.Id}/Items?IncludeItemTypes=Series&Recursive=true&SortBy=SortName&SortOrder=Ascending&StartIndex=${startIndex}&Limit=${limit}`
	);
	return { items: result.Items ?? [], totalCount: result.TotalRecordCount ?? 0 };
}

export async function getItem(itemId: string): Promise<BaseItemDto> {
	const user = getUser();
	if (!user) {
		throw new Error('Not authenticated');
	}
	return apiFetch<BaseItemDto>(`/Users/${user.Id}/Items/${itemId}`);
}

export async function getSeasons(seriesId: string): Promise<BaseItemDto[]> {
	const user = getUser();
	if (!user) {
		throw new Error('Not authenticated');
	}
	const result = await apiFetch<QueryResult<BaseItemDto>>(
		`/Users/${user.Id}/Items?ParentId=${seriesId}&IncludeItemTypes=Season&SortBy=IndexNumber&Limit=100`
	);
	return result.Items ?? [];
}

export async function getEpisodes(seasonId: string): Promise<BaseItemDto[]> {
	const user = getUser();
	if (!user) {
		throw new Error('Not authenticated');
	}
	const result = await apiFetch<QueryResult<BaseItemDto>>(
		`/Users/${user.Id}/Items?ParentId=${seasonId}&IncludeItemTypes=Episode&SortBy=IndexNumber&Limit=500`
	);
	return result.Items ?? [];
}

export interface EpisodeNeighbors {
	previous: BaseItemDto | null;
	next: BaseItemDto | null;
}

export async function getEpisodeNeighbors(episode: BaseItemDto): Promise<EpisodeNeighbors | null> {
	if (episode.Type !== 'Episode') {
		return null;
	}
	if (!episode.SeriesId || !episode.SeasonId) {
		return { previous: null, next: null };
	}
	const episodes = await getEpisodes(episode.SeasonId);
	const index = episodes.findIndex((candidate) => candidate.Id === episode.Id);
	if (index === -1) {
		return { previous: null, next: null };
	}
	return {
		previous: episodes[index - 1] ?? null,
		next: episodes[index + 1] ?? null
	};
}

export interface SeasonEpisodes {
	season: BaseItemDto;
	episodes: BaseItemDto[];
}

export async function getSeriesEpisodes(seriesId: string): Promise<SeasonEpisodes[]> {
	const seasons = await getSeasons(seriesId);
	return Promise.all(
		seasons.map(async (season) => ({
			season,
			episodes: await getEpisodes(season.Id).catch(() => [])
		}))
	);
}

export async function search(query: string, limit = 50): Promise<BaseItemDto[]> {
	const user = getUser();
	if (!user) {
		throw new Error('Not authenticated');
	}
	const result = await apiFetch<QueryResult<BaseItemDto>>(
		`/Users/${user.Id}/Items?searchTerm=${encodeURIComponent(query)}&Recursive=true&IncludeItemTypes=Movie,Series&SortBy=SortName&Limit=${limit}`
	);
	return result.Items ?? [];
}

export async function getResume(limit = 30): Promise<BaseItemDto[]> {
	const user = getUser();
	if (!user) {
		throw new Error('Not authenticated');
	}
	const result = await apiFetch<QueryResult<BaseItemDto>>(
		`/Users/${user.Id}/Items/Resume?IncludeItemTypes=Movie,Episode&Limit=${limit}`
	);
	return result.Items ?? [];
}
