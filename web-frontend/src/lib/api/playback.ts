import { API_BASE, apiFetch } from './client';
import { getToken } from './session';
import type { PlaybackInfoResponse } from './types';

export async function getPlaybackInfo(itemId: string): Promise<PlaybackInfoResponse> {
	return apiFetch<PlaybackInfoResponse>(`/Items/${itemId}/PlaybackInfo`, {
		method: 'POST',
		body: JSON.stringify({})
	});
}

export function streamUrl(itemId: string, mediaSourceId: string): string {
	const token = getToken();
	if (!token) {
		throw new Error('Not authenticated');
	}
	return `${API_BASE}/Videos/${itemId}/stream?static=true&MediaSourceId=${encodeURIComponent(mediaSourceId)}&api_key=${encodeURIComponent(token)}`;
}
