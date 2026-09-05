import { afterEach, beforeEach, vi } from 'vitest';

function createMemoryStorage(): Storage {
	const map = new Map<string, string>();
	return {
		get length() {
			return map.size;
		},
		clear: () => map.clear(),
		getItem: (key) => (map.has(key) ? map.get(key)! : null),
		key: (index) => [...map.keys()][index] ?? null,
		removeItem: (key) => {
			map.delete(key);
		},
		setItem: (key, value) => {
			map.set(key, String(value));
		}
	} as Storage;
}

beforeEach(() => {
	vi.stubGlobal('localStorage', createMemoryStorage());
});

afterEach(() => {
	vi.unstubAllGlobals();
});
