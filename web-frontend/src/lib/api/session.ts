import type { User } from './types';

const TOKEN_KEY = 'home-flix-token';
const USER_KEY = 'home-flix-user';
const DEVICE_ID_KEY = 'home-flix-device-id';

export function getToken(): string | null {
	if (typeof localStorage === 'undefined') return null;
	return localStorage.getItem(TOKEN_KEY);
}

export function getUser(): User | null {
	if (typeof localStorage === 'undefined') return null;
	const raw = localStorage.getItem(USER_KEY);
	if (!raw) return null;
	try {
		return JSON.parse(raw) as User;
	} catch {
		return null;
	}
}

export function setSession(token: string, user: User): void {
	if (typeof localStorage === 'undefined') return;
	localStorage.setItem(TOKEN_KEY, token);
	localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearSession(): void {
	if (typeof localStorage === 'undefined') return;
	localStorage.removeItem(TOKEN_KEY);
	localStorage.removeItem(USER_KEY);
}

export function getDeviceId(): string {
	if (typeof localStorage === 'undefined') return 'unknown';
	let id = localStorage.getItem(DEVICE_ID_KEY);
	if (!id) {
		id = crypto.randomUUID();
		localStorage.setItem(DEVICE_ID_KEY, id);
	}
	return id;
}
