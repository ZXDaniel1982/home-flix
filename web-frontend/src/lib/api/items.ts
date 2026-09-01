import { apiFetch } from './client';
import { getUser } from './session';
import type { BaseItemDto, QueryResult } from './types';

export interface MoviePage {
	items: BaseItemDto[];
	totalCount: number;
}

export async function getMovies(startIndex: number, limit: number): Promise<MoviePage> {
	const user = getUser();
	if (!user) {
		throw new Error('Not authenticated');
	}
	const result = await apiFetch<QueryResult<BaseItemDto>>(
		`/Users/${user.Id}/Items?IncludeItemTypes=Movie&Recursive=true&SortBy=SortName&SortOrder=Ascending&StartIndex=${startIndex}&Limit=${limit}`
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
