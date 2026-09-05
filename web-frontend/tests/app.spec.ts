import { expect, test, type Page } from '@playwright/test';

const MOVIE_ID = 'movie-1';

async function mockJellyfin(page: Page): Promise<void> {
	await page.route(
		(url) => url.pathname.startsWith('/api/'),
		(route) => {
			const request = route.request();
			const url = new URL(request.url());
			const path = url.pathname;
			const method = request.method();

			const ok = (body: unknown) => route.fulfill({ json: body });

			if (method === 'POST' && path === '/api/Users/AuthenticateByName') {
				return ok({
					User: { Id: 'user-1', Name: 'Alice' },
					AccessToken: 'test-token',
					ServerId: 'server-1'
				});
			}

			if (method === 'GET' && /^\/api\/Users\/[^/]+\/Items\/Resume$/.test(path)) {
				return ok({ Items: [], TotalRecordCount: 0 });
			}

			if (method === 'GET' && /^\/api\/Users\/[^/]+\/Items$/.test(path)) {
				return ok({
					Items: [{ Id: MOVIE_ID, Name: 'Frozen', Type: 'Movie' }],
					TotalRecordCount: 1
				});
			}

			const itemMatch = path.match(/^\/api\/Users\/[^/]+\/Items\/([^/]+)$/);
			if (method === 'GET' && itemMatch) {
				return ok({
					Id: itemMatch[1],
					Name: 'Frozen',
					Type: 'Movie',
					ProductionYear: 2013,
					RunTimeTicks: 61200000000,
					Overview: 'A test movie for E2E.',
					Genres: ['Animation']
				});
			}

			if (method === 'POST' && /^\/api\/Items\/[^/]+\/PlaybackInfo$/.test(path)) {
				return ok({
					MediaSources: [{ Id: 'ms-1', Container: 'mp4', SupportsDirectPlay: true }]
				});
			}

			if (method === 'GET' && /^\/api\/Videos\/[^/]+\/stream$/.test(path)) {
				return route.fulfill({ status: 200, contentType: 'video/mp4', body: '' });
			}

			return route.fulfill({ status: 404, json: {} });
		}
	);
}

test('login, browse a movie, and start playback', async ({ page }) => {
	await mockJellyfin(page);

	await page.goto('/');
	await expect(page).toHaveURL(/\/login$/);

	await page.locator('input[name="username"]').fill('alice');
	await page.locator('input[name="password"]').fill('pw');
	await page.getByRole('button', { name: 'Sign in' }).click();

	await expect(page.getByRole('heading', { level: 1, name: 'Home Flix' })).toBeVisible();

	await page.getByRole('link', { name: 'Movies' }).click();
	await expect(page).toHaveURL(/\/movies$/);
	await expect(page.getByRole('link', { name: 'Frozen' })).toBeVisible();

	await page.getByRole('link', { name: 'Frozen' }).click();
	await expect(page).toHaveURL(new RegExp(`/movies/${MOVIE_ID}$`));
	await expect(page.getByRole('link', { name: 'Play' })).toBeVisible();

	const streamRequestPromise = page.waitForRequest((request) =>
		request.url().includes(`/Videos/${MOVIE_ID}/stream`)
	);
	await page.getByRole('link', { name: 'Play' }).click();

	await expect(page).toHaveURL(new RegExp(`/movies/${MOVIE_ID}/play$`));
	await expect(page.getByRole('link', { name: 'Back' })).toBeVisible();

	const streamRequest = await streamRequestPromise;
	expect(streamRequest.url()).toContain('MediaSourceId=ms-1');
	expect(streamRequest.url()).toContain('api_key=test-token');
});
