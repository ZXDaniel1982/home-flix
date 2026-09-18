import { describe, expect, it, vi } from 'vitest';
import { getItem, getMovies, getNextEpisode, search } from './items';
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

describe('getNextEpisode', () => {
	it('returns the next episode in the same season', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		vi.stubGlobal(
			'fetch',
			vi.fn<typeof fetch>().mockImplementation(async (input) => {
				const url = input as string;
				if (url.includes('IncludeItemTypes=Season')) {
					return jsonResponse({ Items: [{ Id: 's1', Name: 'Season 1', IndexNumber: 1 }] });
				}
				return jsonResponse({
					Items: [
						{ Id: 'e1', Name: 'One', Type: 'Episode', SeriesId: 'series1', SeasonId: 's1' },
						{ Id: 'e2', Name: 'Two', Type: 'Episode', SeriesId: 'series1', SeasonId: 's1' }
					]
				});
			})
		);

		const next = await getNextEpisode(episode({ Id: 'e1' }));

		expect(next?.Id).toBe('e2');
	});

	it('returns the first episode of the next season after a season finale', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		vi.stubGlobal(
			'fetch',
			vi.fn<typeof fetch>().mockImplementation(async (input) => {
				const url = input as string;
				if (url.includes('IncludeItemTypes=Season')) {
					return jsonResponse({
						Items: [
							{ Id: 's1', Name: 'Season 1', IndexNumber: 1 },
							{ Id: 's2', Name: 'Season 2', IndexNumber: 2 }
						]
					});
				}
				if (url.includes('ParentId=s2')) {
					return jsonResponse({
						Items: [
							{ Id: 'e2s1', Name: 'S2E1', Type: 'Episode', SeriesId: 'series1', SeasonId: 's2' }
						]
					});
				}
				return jsonResponse({
					Items: [{ Id: 'e1', Name: 'One', Type: 'Episode', SeriesId: 'series1', SeasonId: 's1' }]
				});
			})
		);

		const next = await getNextEpisode(episode({ Id: 'e1' }));

		expect(next?.Id).toBe('e2s1');
	});

	it('returns null for the last episode of the last season', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		vi.stubGlobal(
			'fetch',
			vi.fn<typeof fetch>().mockImplementation(async (input) => {
				const url = input as string;
				if (url.includes('IncludeItemTypes=Season')) {
					return jsonResponse({ Items: [{ Id: 's1', Name: 'Season 1', IndexNumber: 1 }] });
				}
				return jsonResponse({
					Items: [{ Id: 'e1', Name: 'One', Type: 'Episode', SeriesId: 'series1', SeasonId: 's1' }]
				});
			})
		);

		expect(await getNextEpisode(episode({ Id: 'e1' }))).toBeNull();
	});

	it('returns null for a non-episode item without fetching', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		const fn = vi.fn<typeof fetch>();
		vi.stubGlobal('fetch', fn);

		expect(await getNextEpisode({ Id: 'm1', Name: 'Movie', Type: 'Movie' })).toBeNull();
		expect(fn).not.toHaveBeenCalled();
	});

	it('returns null when the episode has no season id', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		const fn = vi.fn<typeof fetch>();
		vi.stubGlobal('fetch', fn);

		expect(
			await getNextEpisode({ Id: 'e1', Name: 'One', Type: 'Episode', SeriesId: 'series1' })
		).toBeNull();
		expect(fn).not.toHaveBeenCalled();
	});

	it('returns null when the episode has no series id', async () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		const fn = vi.fn<typeof fetch>();
		vi.stubGlobal('fetch', fn);

		expect(
			await getNextEpisode({ Id: 'e1', Name: 'One', Type: 'Episode', SeasonId: 's1' })
		).toBeNull();
		expect(fn).not.toHaveBeenCalled();
	});
});
