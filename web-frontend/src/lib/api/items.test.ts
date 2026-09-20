import { describe, expect, it, vi } from 'vitest';
import { getItem, getMovies, getEpisodeNeighbors, search } from './items';
import { setSession } from './session';
import type { BaseItemDto } from './types';

function jsonResponse(body: unknown): Response {
	return new Response(JSON.stringify(body));
}

function episode(overrides: Partial<BaseItemDto>): BaseItemDto {
	return {
		Id: 'e1',
		Name: 'Episode',
		Type: 'Episode',
		SeriesId: 'series1',
		SeasonId: 's1',
		IndexNumber: 1,
		...overrides
	};
}

describe('items', () => {
	it('throws when not authenticated', async () => {
		await expect(getMovies(0, 50)).rejects.toThrow('Not authenticated');
		await expect(getItem('m1')).rejects.toThrow('Not authenticated');
		await expect(search('frozen')).rejects.toThrow('Not authenticated');
	});

	it('lists movies and maps the response to an ItemPage', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		const fn = vi
			.fn<typeof fetch>()
			.mockResolvedValue(
				jsonResponse({ Items: [{ Id: 'm1', Name: 'Frozen' }], TotalRecordCount: 1 })
			);
		vi.stubGlobal('fetch', fn);

		const page = await getMovies(0, 50);

		expect(page.items).toHaveLength(1);
		expect(page.items[0].Id).toBe('m1');
		expect(page.totalCount).toBe(1);

		const [input] = fn.mock.calls[0]!;
		const url = input as string;
		expect(url).toContain('/Users/u1/Items');
		expect(url).toContain('IncludeItemTypes=Movie');
		expect(url).toContain('StartIndex=0');
		expect(url).toContain('Limit=50');
	});

	it('returns an empty page when the response has no Items', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(jsonResponse({})));

		const page = await getMovies(0, 50);

		expect(page).toEqual({ items: [], totalCount: 0 });
	});

	it('URL-encodes the search query', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		const fn = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse({ Items: [] }));
		vi.stubGlobal('fetch', fn);

		await search('frozen 2');

		const [input] = fn.mock.calls[0]!;
		expect(input as string).toContain('searchTerm=frozen%202');
	});
});

describe('getEpisodeNeighbors', () => {
	const list = [
		{ Id: 'e1', Name: 'One', Type: 'Episode', SeriesId: 'series1', SeasonId: 's1', IndexNumber: 1 },
		{ Id: 'e2', Name: 'Two', Type: 'Episode', SeriesId: 'series1', SeasonId: 's1', IndexNumber: 2 },
		{
			Id: 'e3',
			Name: 'Three',
			Type: 'Episode',
			SeriesId: 'series1',
			SeasonId: 's1',
			IndexNumber: 3
		}
	];

	function stubEpisodes(items: unknown[]) {
		const fn = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse({ Items: items }));
		vi.stubGlobal('fetch', fn);
		return fn;
	}

	it('returns the previous and next episodes in the same season', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		stubEpisodes(list);

		const neighbors = await getEpisodeNeighbors(episode({ Id: 'e2' }));

		expect(neighbors?.previous?.Id).toBe('e1');
		expect(neighbors?.next?.Id).toBe('e3');
	});

	it('returns null previous for the first episode of a season', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		stubEpisodes(list);

		const neighbors = await getEpisodeNeighbors(episode({ Id: 'e1' }));

		expect(neighbors?.previous).toBeNull();
		expect(neighbors?.next?.Id).toBe('e2');
	});

	it('returns null next for the last episode of a season', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		stubEpisodes(list);

		const neighbors = await getEpisodeNeighbors(episode({ Id: 'e3' }));

		expect(neighbors?.next).toBeNull();
		expect(neighbors?.previous?.Id).toBe('e2');
	});

	it('returns both null for a single-episode season', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		stubEpisodes([list[0]]);

		const neighbors = await getEpisodeNeighbors(episode({ Id: 'e1' }));

		expect(neighbors).toEqual({ previous: null, next: null });
	});

	it('returns both null when the current episode is absent from its season', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		stubEpisodes(list);

		const neighbors = await getEpisodeNeighbors(episode({ Id: 'missing-episode' }));

		expect(neighbors).toEqual({ previous: null, next: null });
	});

	it('returns null for a non-episode item without fetching', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		const fn = vi.fn<typeof fetch>();
		vi.stubGlobal('fetch', fn);

		expect(await getEpisodeNeighbors({ Id: 'm1', Name: 'Movie', Type: 'Movie' })).toBeNull();
		expect(fn).not.toHaveBeenCalled();
	});

	it('returns both null when the episode has no season id, without fetching', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		const fn = vi.fn<typeof fetch>();
		vi.stubGlobal('fetch', fn);

		expect(await getEpisodeNeighbors(episode({ SeasonId: undefined }))).toEqual({
			previous: null,
			next: null
		});
		expect(fn).not.toHaveBeenCalled();
	});

	it('returns both null when the episode has no series id, without fetching', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		const fn = vi.fn<typeof fetch>();
		vi.stubGlobal('fetch', fn);

		expect(await getEpisodeNeighbors(episode({ SeriesId: undefined }))).toEqual({
			previous: null,
			next: null
		});
		expect(fn).not.toHaveBeenCalled();
	});
});
