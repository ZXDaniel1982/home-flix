import { describe, expect, it, vi } from 'vitest';
import { authenticate } from './auth';
import { getToken, getUser } from './session';

function jsonResponse(body: unknown, status = 200): Response {
	return new Response(JSON.stringify(body), { status });
}

describe('authenticate', () => {
	it('posts credentials and stores the session', async () => {
		const fn = vi
			.fn<typeof fetch>()
			.mockResolvedValue(
				jsonResponse({ User: { Id: 'u1', Name: 'Alice' }, AccessToken: 'tok', ServerId: 's1' })
			);
		vi.stubGlobal('fetch', fn);

		const result = await authenticate('alice', 'pw');

		expect(result.AccessToken).toBe('tok');
		const [input, init] = fn.mock.calls[0]!;
		expect(input as string).toBe('/api/Users/AuthenticateByName');
		expect(init!.method).toBe('POST');
		expect(JSON.parse(init!.body as string)).toEqual({ Username: 'alice', Pw: 'pw' });
		expect(getToken()).toBe('tok');
		expect(getUser()).toEqual({ Id: 'u1', Name: 'Alice' });
	});

	it('rejects when the server returns an error', async () => {
		vi.stubGlobal(
			'fetch',
			vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 401 }))
		);

		await expect(authenticate('alice', 'wrong')).rejects.toThrow();
		expect(getToken()).toBeNull();
	});
});
