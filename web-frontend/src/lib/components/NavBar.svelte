<script lang="ts">
	import { theme, toggleTheme } from '$lib/theme.svelte';

	const label = $derived(theme.current === 'dark' ? 'Switch to light mode' : 'Switch to dark mode');
	const text = $derived(theme.current === 'dark' ? 'Light' : 'Dark');
</script>

<header class="navbar">
	<a class="brand" href="/">Home Flix</a>
	<nav class="nav-links">
		<a href="/">Home</a>
	</nav>
	<button class="theme-toggle" type="button" aria-label={label} onclick={toggleTheme}>
		{text}
	</button>
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

	.theme-toggle {
		margin-left: auto;
		padding: 0.4rem 0.75rem;
		border: 1px solid var(--color-border);
		border-radius: 0.375rem;
		background-color: var(--color-bg);
		color: var(--color-text);
		cursor: pointer;
	}

	.theme-toggle:hover {
		border-color: var(--color-accent);
	}
</style>
