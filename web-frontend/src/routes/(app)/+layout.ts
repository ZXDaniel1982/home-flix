import { redirect } from '@sveltejs/kit';
import { getToken } from '$lib/api/session';

export function load() {
	if (!getToken()) {
		redirect(302, '/login');
	}
}
