import { apiFetch } from './client';
import { setSession } from './session';
import type { AuthenticateByNameRequest, AuthenticationResult } from './types';

export async function authenticate(
	username: string,
	password: string
): Promise<AuthenticationResult> {
	const body: AuthenticateByNameRequest = { Username: username, Pw: password };
	const result = await apiFetch<AuthenticationResult>('/Users/AuthenticateByName', {
		method: 'POST',
		body: JSON.stringify(body)
	});
	setSession(result.AccessToken, result.User);
	return result;
}
