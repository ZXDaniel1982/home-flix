import { describe, expect, it, vi } from 'vitest';
import {
	getPlaybackInfo,
	reportPlaybackProgress,
	reportPlaybackStarted,
	reportPlaybackStopped,
	streamUrl
} from './playback';
import { setSession } from './session';

function noContent(): Response {
	return new Response(null, { status: 204 });
}

function jsonResponse(body: unknown): Response {
	return new Response(JSON.stringify(body));
}

describe('playback', () => {
	it('throws when building a stream URL without a token', () => {
		expect(() => streamUrl('m1', 'ms1')).toThrow('Not authenticated');
	});

	it('builds the direct stream URL with the token', () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });

		const url = streamUrl('m1', 'ms1');

		expect(url).toContain('/api/Videos/m1/stream');
		expect(url).toContain('static=true');
		expect(url).toContain('MediaSourceId=ms1');
		expect(url).toContain('api_key=tok');
	});

	it('posts playback info', async () => {
		const fn = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse({ MediaSources: [] }));
		vi.stubGlobal('fetch', fn);

		await getPlaybackInfo('m1');

		const [input, init] = fn.mock.calls[0]!;
		expect(input as string).toBe('/api/Items/m1/PlaybackInfo');
		expect(init!.method).toBe('POST');
	});

	it('reports playback started', async () => {
		const fn = vi.fn<typeof fetch>().mockResolvedValue(noContent());
		vi.stubGlobal('fetch', fn);

		await reportPlaybackStarted('m1', 'ms1', 123);

		const [input, init] = fn.mock.calls[0]!;
		expect(input as string).toBe('/api/Sessions/Playing');
		expect(JSON.parse(init!.body as string)).toMatchObject({
			ItemId: 'm1',
			MediaSourceId: 'ms1',
			PositionTicks: 123,
			PlayMethod: 'DirectPlay'
		});
	});

	it('reports playback progress with the paused state', async () => {
		const fn = vi.fn<typeof fetch>().mockResolvedValue(noContent());
		vi.stubGlobal('fetch', fn);

		await reportPlaybackProgress('m1', 'ms1', 456, true);

		const [input, init] = fn.mock.calls[0]!;
		expect(input as string).toBe('/api/Sessions/Playing/Progress');
		expect(JSON.parse(init!.body as string)).toMatchObject({ PositionTicks: 456, IsPaused: true });
	});

	it('reports playback stopped', async () => {
		const fn = vi.fn<typeof fetch>().mockResolvedValue(noContent());
		vi.stubGlobal('fetch', fn);

		await reportPlaybackStopped('m1', 'ms1', 789);

		const [input, init] = fn.mock.calls[0]!;
		expect(input as string).toBe('/api/Sessions/Playing/Stopped');
		expect(JSON.parse(init!.body as string)).toMatchObject({ ItemId: 'm1', PositionTicks: 789 });
	});
});
