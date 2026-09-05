import { describe, expect, it } from 'vitest';
import { clearSession, getDeviceId, getToken, getUser, setSession } from './session';

describe('session', () => {
	it('stores and retrieves the token and user', () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });

		expect(getToken()).toBe('tok');
		expect(getUser()).toEqual({ Id: 'u1', Name: 'Alice' });
	});

	it('returns null when there is no session', () => {
		expect(getToken()).toBeNull();
		expect(getUser()).toBeNull();
	});

	it('returns null for a corrupt user payload', () => {
		localStorage.setItem('home-flix-user', '{not valid json');

		expect(getUser()).toBeNull();
	});

	it('clears the session', () => {
		setSession('tok', { Id: 'u1', Name: 'Alice' });
		clearSession();

		expect(getToken()).toBeNull();
		expect(getUser()).toBeNull();
	});

	it('returns a stable device id', () => {
		const first = getDeviceId();
		const second = getDeviceId();

		expect(first).toBeTruthy();
		expect(first).toBe(second);
	});
});
