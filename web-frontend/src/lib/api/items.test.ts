import { describe, expect, it, vi } from 'vitest';
import { getItem, getMovies, search } from './items';
import { setSession } from './session';

function jsonResponse(body: unknown): Response {
	return new Response(JSON.stringify(body));
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
