<script lang="ts">
	import { tick } from 'svelte';
	import { goto } from '$app/navigation';
	import { theme, toggleTheme } from '$lib/theme.svelte';
	import { clearSession, getUser } from '$lib/api/session';

	const label = $derived(theme.current === 'dark' ? 'Switch to light mode' : 'Switch to dark mode');
	const text = $derived(theme.current === 'dark' ? 'Light' : 'Dark');
	const user = getUser();

	let query = $state('');
	let menuOpen = $state(false);
	let menuToggle = $state<HTMLButtonElement | null>(null);

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

	function toggleMenu() {
		if (menuOpen) {
			closeMenu();
		} else {
			openMenu();
		}
	}

	async function openMenu() {
		menuOpen = true;
		await tick();
		const first = document.querySelector<HTMLAnchorElement>('.mobile-menu a');
		first?.focus();
	}

	function closeMenu() {
		menuOpen = false;
		menuToggle?.focus();
	}

	function handleMenuKeydown(event: KeyboardEvent) {
		if (event.key === 'Escape' && menuOpen) {
			closeMenu();
		}
	}

	$effect(() => {
		if (!menuOpen) return;
		function handleOutsideClick(event: MouseEvent) {
			const target = event.target as Element;
			if (!target.closest('.navbar') && !target.closest('.mobile-menu')) {
				closeMenu();
			}
		}
		document.addEventListener('click', handleOutsideClick);
		return () => document.removeEventListener('click', handleOutsideClick);
	});
</script>

<svelte:window onkeydown={handleMenuKeydown} />

<header class="navbar">
	<a class="brand" href="/">Home Flix</a>
	<button
		class="menu-toggle"
		type="button"
		aria-label="Toggle navigation menu"
		aria-expanded={menuOpen}
		aria-controls="mobile-menu"
		bind:this={menuToggle}
		onclick={toggleMenu}
		onkeydown={handleMenuKeydown}
	>
		<span class="bar"></span>
		<span class="bar"></span>
		<span class="bar"></span>
	</button>
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

{#if menuOpen}
	<nav id="mobile-menu" class="mobile-menu" aria-label="Main navigation">
		<a href="/" onclick={closeMenu}>Home</a>
		<a href="/movies" onclick={closeMenu}>Movies</a>
		<a href="/tv" onclick={closeMenu}>TV</a>
	</nav>
{/if}

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

	.menu-toggle {
		display: none;
		flex-direction: column;
		justify-content: center;
		gap: 4px;
		padding: 0.5rem;
		border: none;
		background: none;
		cursor: pointer;
	}

	.menu-toggle:focus-visible,
	.mobile-menu a:focus-visible {
		outline: 2px solid var(--color-accent);
		outline-offset: 2px;
		border-radius: 0.375rem;
	}

	.bar {
		width: 1.25rem;
		height: 2px;
		border-radius: 1px;
		background-color: var(--color-text);
	}

	.nav-links {
		display: flex;
		gap: 1rem;
	}

	.mobile-menu {
		display: none;
		flex-direction: column;
		gap: 0.25rem;
		padding: 0.5rem 1.25rem 0.75rem;
		background-color: var(--color-bg-alt);
		border-bottom: 1px solid var(--color-border);
	}

	.mobile-menu a {
		padding: 0.5rem 0.25rem;
		border-radius: 0.375rem;
		color: var(--color-text);
	}

	.mobile-menu a:hover {
		background-color: var(--color-bg);
		text-decoration: none;
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

	@media (max-width: 48rem) {
		.navbar {
			gap: 0.75rem;
			padding: 0.5rem 0.75rem;
		}

		.nav-links {
			display: none;
		}

		.menu-toggle {
			display: flex;
		}

		.mobile-menu {
			display: flex;
		}

		.search {
			flex: 1;
			min-width: 0;
		}

		.search input {
			width: 100%;
			min-width: 0;
		}

		.user {
			display: none;
		}
	}
</style>
