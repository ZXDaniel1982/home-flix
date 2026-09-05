import { describe, expect, it, vi } from 'vitest';
import { ApiError, apiFetch } from './client';
import { setSession } from './session';

function stubFetch(response: Response) {
	const fn = vi.fn<typeof fetch>().mockResolvedValue(response);
	vi.stubGlobal('fetch', fn);
	return fn;
}

function jsonResponse(body: unknown, status = 200): Response {
	return new Response(JSON.stringify(body), { status });
}

describe('apiFetch', () => {
	it('returns the parsed JSON body and prefixes the path with /api', async () => {
		const fn = stubFetch(jsonResponse({ Name: 'Frozen' }));

		const result = await apiFetch<{ Name: string }>('/Users/1/Items');

		expect(result).toEqual({ Name: 'Frozen' });
		const [input] = fn.mock.calls[0]!;
		expect(input as string).toBe('/api/Users/1/Items');
	});

	it('returns undefined for a 204 response', async () => {
		stubFetch(new Response(null, { status: 204 }));

		const result = await apiFetch('/Sessions/Playing');

		expect(result).toBeUndefined();
	});

	it('throws an ApiError with the status on a non-ok response', async () => {
		stubFetch(jsonResponse({}, 404));

		const error = await apiFetch('/missing').catch((e) => e);

		expect(error).toBeInstanceOf(ApiError);
		expect((error as ApiError).status).toBe(404);
		expect((error as ApiError).message).toContain('404');
	});

	it('throws an ApiError for 401 and 500 responses', async () => {
		stubFetch(jsonResponse({}, 401));
		const err401 = (await apiFetch('/x').catch((e) => e)) as ApiError;
		expect(err401.status).toBe(401);

		stubFetch(jsonResponse({}, 500));
		const err500 = (await apiFetch('/x').catch((e) => e)) as ApiError;
		expect(err500.status).toBe(500);
	});

	it('sends a MediaBrowser authorization header without a token when unauthenticated', async () => {
		const fn = stubFetch(jsonResponse({}));

		await apiFetch('/x');

		const [, init] = fn.mock.calls[0]!;
		const auth = (init!.headers as Headers).get('X-Emby-Authorization') ?? '';
		expect(auth).toContain('MediaBrowser');
		expect(auth).toContain('Client="Home Flix"');
		expect(auth).toContain('Device="Web Browser"');
		expect(auth).toContain('Version="0.1.0"');
		expect(auth).toContain('DeviceId="');
		expect(auth).not.toContain('Token=');
	});

	it('includes the token in the authorization header when a session exists', async () => {
		setSession('test-token', { Id: 'u1', Name: 'Test' });
		const fn = stubFetch(jsonResponse({}));

		await apiFetch('/x');

		const [, init] = fn.mock.calls[0]!;
		const auth = (init!.headers as Headers).get('X-Emby-Authorization') ?? '';
		expect(auth).toContain('Token="test-token"');
	});

	it('sets Content-Type to application/json when a body is present', async () => {
		const fn = stubFetch(jsonResponse({}));

		await apiFetch('/x', { method: 'POST', body: JSON.stringify({ a: 1 }) });

		const [, init] = fn.mock.calls[0]!;
		expect((init!.headers as Headers).get('Content-Type')).toBe('application/json');
		expect(init!.method).toBe('POST');
	});

	it('preserves an explicit Content-Type header', async () => {
		const fn = stubFetch(jsonResponse({}));

		await apiFetch('/x', {
			method: 'POST',
			body: 'abc',
			headers: { 'Content-Type': 'text/plain' }
		});

		const [, init] = fn.mock.calls[0]!;
		expect((init!.headers as Headers).get('Content-Type')).toBe('text/plain');
	});

	it('does not set Content-Type when there is no body', async () => {
		const fn = stubFetch(jsonResponse({}));

		await apiFetch('/x');

		const [, init] = fn.mock.calls[0]!;
		expect((init!.headers as Headers).get('Content-Type')).toBeNull();
	});
});
