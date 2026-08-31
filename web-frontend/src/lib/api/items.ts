import { apiFetch } from './client';
import { getUser } from './session';
import type { BaseItemDto, QueryResult } from './types';

export async function getMovies(): Promise<BaseItemDto[]> {
	const user = getUser();
	if (!user) {
		throw new Error('Not authenticated');
	}
	const result = await apiFetch<QueryResult<BaseItemDto>>(
		`/Users/${user.Id}/Items?IncludeItemTypes=Movie&Recursive=true&SortBy=SortName&SortOrder=Ascending`
	);
	return result.Items ?? [];
}

export async function getItem(itemId: string): Promise<BaseItemDto> {
	const user = getUser();
	if (!user) {
		throw new Error('Not authenticated');
	}
	return apiFetch<BaseItemDto>(`/Users/${user.Id}/Items/${itemId}`);
}
