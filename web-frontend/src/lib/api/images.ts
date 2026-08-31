import { API_BASE } from './client';

export function imageUrl(itemId: string, imageTag: string, type = 'Primary'): string {
	return `${API_BASE}/Items/${itemId}/Images/${type}?tag=${encodeURIComponent(imageTag)}`;
}
