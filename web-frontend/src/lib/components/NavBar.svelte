<script lang="ts">
	import { goto } from '$app/navigation';
	import { theme, toggleTheme } from '$lib/theme.svelte';
	import { clearSession, getUser } from '$lib/api/session';

	const label = $derived(theme.current === 'dark' ? 'Switch to light mode' : 'Switch to dark mode');
	const text = $derived(theme.current === 'dark' ? 'Light' : 'Dark');
	const user = getUser();

	let query = $state('');

	function handleLogout() {
		clearSession();
		goto('/login');
	}

	function handleSearch(event: SubmitEvent) {
		event.preventDefault();
		const q = query.trim();
		if (q) {
			goto(`/search?q=${encodeURIComponent(q)}`);
		}
	}
</script>

<header class="navbar">
	<a class="brand" href="/">Home Flix</a>
	<nav class="nav-links">
		<a href="/">Home</a>
		<a href="/movies">Movies</a>
		<a href="/tv">TV</a>
	</nav>
	<form class="search" onsubmit={handleSearch}>
		<input type="search" bind:value={query} placeholder="Search" aria-label="Search" />
	</form>
	<div class="actions">
		{#if user}
			<span class="user">{user.Name}</span>
			<button class="logout" type="button" onclick={handleLogout}>Sign out</button>
		{/if}
		<button class="theme-toggle" type="button" aria-label={label} onclick={toggleTheme}>
			{text}
		</button>
	</div>
</header>

<style>
	.navbar {
		display: flex;
		align-items: center;
		gap: 1.5rem;
		padding: 0.75rem 1.25rem;
		background-color: var(--color-bg-alt);
		border-bottom: 1px solid var(--color-border);
	}

	.brand {
		font-weight: 700;
		font-size: 1.25rem;
		color: var(--color-text);
	}

	.brand:hover {
		text-decoration: none;
	}

	.nav-links {
		display: flex;
		gap: 1rem;
	}

	.search input {
		padding: 0.4rem 0.75rem;
		border: 1px solid var(--color-border);
		border-radius: 0.375rem;
		background-color: var(--color-bg);
		color: var(--color-text);
		width: 12rem;
	}

	.actions {
		margin-left: auto;
		display: flex;
		align-items: center;
		gap: 0.75rem;
	}

	.user {
		font-size: 0.875rem;
		color: var(--color-text-muted);
	}

	.logout,
	.theme-toggle {
		padding: 0.4rem 0.75rem;
		border: 1px solid var(--color-border);
		border-radius: 0.375rem;
		background-color: var(--color-bg);
		color: var(--color-text);
		cursor: pointer;
	}

	.logout:hover,
	.theme-toggle:hover {
		border-color: var(--color-accent);
	}
</style>
