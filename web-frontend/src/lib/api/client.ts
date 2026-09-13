import { clearSession, getDeviceId, getToken } from './session';

export const API_BASE = '/api';

export interface ApiFetchOptions {
	handleUnauthorized?: boolean;
}

const CLIENT_NAME = 'Home Flix';
const DEVICE_NAME = 'Web Browser';
const CLIENT_VERSION = '0.1.0';

export class ApiError extends Error {
	readonly status: number;

	constructor(status: number, message: string) {
		super(message);
		this.name = 'ApiError';
		this.status = status;
	}
}

function buildAuthHeader(): string {
	const parts = [
		`Client="${CLIENT_NAME}"`,
		`Device="${DEVICE_NAME}"`,
		`DeviceId="${getDeviceId()}"`,
		`Version="${CLIENT_VERSION}"`
	];
	const token = getToken();
	if (token) {
		parts.push(`Token="${token}"`);
	}
	return `MediaBrowser ${parts.join(', ')}`;
}

function redirectToLogin(): void {
	if (typeof window !== 'undefined') {
		window.location.href = '/login';
	}
}

export async function apiFetch<T>(
	path: string,
	init: RequestInit = {},
	options: ApiFetchOptions = {}
): Promise<T> {
	const headers = new Headers(init.headers);
	headers.set('X-Emby-Authorization', buildAuthHeader());
	if (init.body && !headers.has('Content-Type')) {
		headers.set('Content-Type', 'application/json');
	}

	const res = await fetch(`${API_BASE}${path}`, { ...init, headers });

	if (!res.ok) {
		if (res.status === 401 && options.handleUnauthorized !== false) {
			clearSession();
			redirectToLogin();
		}
		throw new ApiError(res.status, `Jellyfin request failed: ${res.status} ${res.statusText}`);
	}

	if (res.status === 204) {
		return undefined as T;
	}

	return (await res.json()) as T;
}
