<script lang="ts">
	import { goto } from '$app/navigation';
	import { authenticate } from '$lib/api/auth';
	import { ApiError } from '$lib/api/client';

	let username = $state('');
	let password = $state('');
	let loading = $state(false);
	let error = $state('');

	async function handleSubmit(event: SubmitEvent) {
		event.preventDefault();
		error = '';
		loading = true;
		try {
			await authenticate(username, password);
			await goto('/');
		} catch (e) {
			if (e instanceof ApiError && e.status === 401) {
				error = 'Invalid username or password.';
			} else {
				error = 'Could not reach the server. Please try again.';
			}
		} finally {
			loading = false;
		}
	}
</script>

<div class="login">
	<h1>Sign in</h1>
	<form class="login-form" onsubmit={handleSubmit}>
		<label>
			Username
			<input type="text" name="username" bind:value={username} autocomplete="username" required />
		</label>
		<label>
			Password
			<input
				type="password"
				name="password"
				bind:value={password}
				autocomplete="current-password"
				required
			/>
		</label>

		{#if error}
			<p class="error" role="alert">{error}</p>
		{/if}

		<button type="submit" disabled={loading}>
			{loading ? 'Signing in…' : 'Sign in'}
		</button>
	</form>
</div>

<style>
	.login {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding-top: 4rem;
	}

	.login h1 {
		margin-bottom: 1.5rem;
	}

	.login-form {
		display: flex;
		flex-direction: column;
		gap: 1rem;
		width: 100%;
		max-width: 20rem;
	}

	.login-form label {
		display: flex;
		flex-direction: column;
		gap: 0.375rem;
		font-size: 0.875rem;
		color: var(--color-text-muted);
	}

	.login-form input {
		padding: 0.5rem 0.75rem;
		border: 1px solid var(--color-border);
		border-radius: 0.375rem;
		background-color: var(--color-bg-alt);
		color: var(--color-text);
	}

	.login-form button {
		margin-top: 0.5rem;
		padding: 0.6rem 1rem;
		border: none;
		border-radius: 0.375rem;
		background-color: var(--color-accent);
		color: #fff;
		font-weight: 600;
		cursor: pointer;
	}

	.login-form button:disabled {
		opacity: 0.6;
		cursor: default;
	}

	.error {
		color: #e5484d;
		font-size: 0.875rem;
		margin: 0;
	}
</style>
