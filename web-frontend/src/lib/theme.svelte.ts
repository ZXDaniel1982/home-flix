export type Theme = 'dark' | 'light';

export const STORAGE_KEY = 'home-flix-theme';

function readStoredTheme(): Theme {
	if (typeof localStorage === 'undefined') return 'dark';
	return localStorage.getItem(STORAGE_KEY) === 'light' ? 'light' : 'dark';
}

export const theme = $state<{ current: Theme }>({ current: readStoredTheme() });

export function setTheme(next: Theme): void {
	theme.current = next;
	if (typeof localStorage !== 'undefined') localStorage.setItem(STORAGE_KEY, next);
}

export function toggleTheme(): void {
	setTheme(theme.current === 'dark' ? 'light' : 'dark');
}
