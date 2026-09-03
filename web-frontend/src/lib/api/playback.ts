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

function sessionBody(itemId: string, mediaSourceId: string, positionTicks: number) {
	return { ItemId: itemId, MediaSourceId: mediaSourceId, PositionTicks: positionTicks };
}

export async function reportPlaybackStarted(
	itemId: string,
	mediaSourceId: string,
	positionTicks: number
): Promise<void> {
	await apiFetch('/Sessions/Playing', {
		method: 'POST',
		body: JSON.stringify({
			...sessionBody(itemId, mediaSourceId, positionTicks),
			CanSeek: true,
			IsPaused: false,
			IsMuted: false,
			PlayMethod: 'DirectPlay'
		})
	});
}

export async function reportPlaybackProgress(
	itemId: string,
	mediaSourceId: string,
	positionTicks: number,
	isPaused: boolean
): Promise<void> {
	await apiFetch('/Sessions/Playing/Progress', {
		method: 'POST',
		body: JSON.stringify({
			...sessionBody(itemId, mediaSourceId, positionTicks),
			IsPaused: isPaused,
			IsMuted: false,
			PlayMethod: 'DirectPlay'
		})
	});
}

export async function reportPlaybackStopped(
	itemId: string,
	mediaSourceId: string,
	positionTicks: number
): Promise<void> {
	await apiFetch('/Sessions/Playing/Stopped', {
		method: 'POST',
		body: JSON.stringify(sessionBody(itemId, mediaSourceId, positionTicks))
	});
}
