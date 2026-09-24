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

test('redirects to login when the stored session is invalid', async ({ page }) => {
	await page.route(
		(url) => url.pathname.startsWith('/api/'),
		(route) => route.fulfill({ status: 401, json: {} })
	);
	await page.addInitScript(() => {
		localStorage.setItem('home-flix-token', 'stale-token');
		localStorage.setItem('home-flix-user', JSON.stringify({ Id: 'user-1', Name: 'Alice' }));
	});

	await page.goto('/movies');

	await expect(page).toHaveURL(/\/login$/);
	await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
});

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

test('browse episodes and switch to another episode', async ({ page }) => {
	await page.route(
		(url) => url.pathname.startsWith('/api/'),
		(route) => {
			const request = route.request();
			const url = new URL(request.url());
			const path = url.pathname;
			const method = request.method();
			const params = url.searchParams;

			const ok = (body: unknown) => route.fulfill({ json: body });

			if (
				method === 'GET' &&
				/^\/api\/Users\/[^/]+\/Items$/.test(path) &&
				params.get('IncludeItemTypes') === 'Season'
			) {
				return ok({ Items: [{ Id: 's1', Name: 'Season 1', IndexNumber: 1 }] });
			}

			if (
				method === 'GET' &&
				/^\/api\/Users\/[^/]+\/Items$/.test(path) &&
				params.get('ParentId') === 's1' &&
				params.get('IncludeItemTypes') === 'Episode'
			) {
				return ok({
					Items: [
						{
							Id: 'e1',
							Name: 'Episode 1',
							Type: 'Episode',
							SeriesId: 'series-1',
							SeasonId: 's1',
							IndexNumber: 1
						},
						{
							Id: 'e2',
							Name: 'Episode 2',
							Type: 'Episode',
							SeriesId: 'series-1',
							SeasonId: 's1',
							IndexNumber: 2
						}
					]
				});
			}

			const itemMatch = path.match(/^\/api\/Users\/[^/]+\/Items\/([^/]+)$/);
			if (method === 'GET' && itemMatch) {
				const index = itemMatch[1] === 'e1' ? 1 : 2;
				return ok({
					Id: itemMatch[1],
					Name: `Episode ${index}`,
					Type: 'Episode',
					SeriesId: 'series-1',
					SeasonId: 's1',
					ParentIndexNumber: 1,
					IndexNumber: index,
					UserData: { PlaybackPositionTicks: 0 }
				});
			}

			if (method === 'POST' && /^\/api\/Items\/[^/]+\/PlaybackInfo$/.test(path)) {
				return ok({ MediaSources: [{ Id: 'ms-1', Container: 'mp4', SupportsDirectPlay: true }] });
			}

			if (method === 'GET' && /^\/api\/Videos\/[^/]+\/stream$/.test(path)) {
				// Leave the media request pending: an empty body makes the <video>
				// element fire an error, which hides the control bar (and the
				// Episodes button) behind the playback-error message.
				return;
			}

			if (method === 'POST' && /^\/api\/Sessions\/Playing/.test(path)) {
				return route.fulfill({ status: 204, body: '' });
			}

			return route.fulfill({ status: 404, json: {} });
		}
	);
	await page.addInitScript(() => {
		localStorage.setItem('home-flix-token', 'test-token');
		localStorage.setItem('home-flix-user', JSON.stringify({ Id: 'user-1', Name: 'Alice' }));
	});

	await page.goto('/tv/series-1/play/e1');

	await page.getByRole('button', { name: 'Episodes' }).click();

	const dialog = page.getByRole('dialog', { name: 'Episodes' });
	await expect(dialog).toBeVisible();
	await expect(dialog.getByText('Season 1')).toBeVisible();
	await expect(dialog.getByText('Episode 1')).toBeVisible();
	await expect(dialog.getByText('Episode 2')).toBeVisible();
	await expect(dialog.getByRole('button', { name: /Episode 1/ })).toBeDisabled();

	await dialog.getByRole('button', { name: /Episode 2/ }).click();

	await expect(page).toHaveURL(/\/play\/e2(\?|$)/);
});
