# Design — Web Frontend Scaffold (Task 2.1.1)

**Date:** 2026-08-31
**Status:** Approved
**Scope:** Story 2.1.1 — Initialize SvelteKit project (Epic 2.1 Project Scaffolding, Module 2)

## 1. Context

The system has a running Jellyfin backend and a Caddy reverse proxy (Module 1 complete).
Module 2 adds a custom web frontend that replaces Jellyfin's built-in UI for browsing and
playback. This story creates the empty, correctly-configured SvelteKit project that the
rest of Module 2 builds on.

The frontend is a static single-page application. It talks exclusively to Jellyfin's REST
API (architecture doc §9, §11) using a relative `/api` base URL, which Caddy routes to
Jellyfin (architecture doc §6).

## 2. Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Language | TypeScript (type checking on) | Type safety for the Jellyfin API client and components |
| Styling | Plain CSS (Svelte scoped styles) | No Tailwind; minimal dependencies, sufficient for a media grid |
| Adapter | `@sveltejs/adapter-static` with SPA fallback | Caddy serves static files at `/`; login-gated, client-driven app |
| Tooling | ESLint + Prettier | Keep a solo codebase consistent |
| Test tooling | None now | Vitest/Playwright are Module 4 stories |
| Template | SvelteKit "minimal" | No demo code to delete |

## 3. Layout

```
web-frontend/            # new monorepo subdir (development.md §4)
├── src/                 # SvelteKit app source (routes/, lib/)
├── static/              # static assets
├── package.json         # name "home-flix", private, no publish
├── vite.config.ts       # adapter-static with fallback: 'index.html'
├── tsconfig.json
└── ...
```

## 4. Components

- **SvelteKit project** (`web-frontend/`): the app shell. Nothing user-facing yet — no
  routes/layout beyond what the scaffold generates.
- **Build config**: `vite.config.ts` configures `@sveltejs/adapter-static` with
  `fallback: 'index.html'` (SvelteKit ≥2.62 accepts Kit config via the `sveltekit()`
  Vite plugin, so there is no `svelte.config.js`); root layout sets `ssr = false` so
  the build is a pure SPA. `npm run build` emits static files to `build/`.

## 5. Data flow

None yet. The app is an empty SPA at this stage. API/client wiring is 2.1.3; the `/api`
proxy and env vars are 2.1.4.

## 6. Error handling

Not applicable at this stage — no user-facing behavior exists.

## 7. Testing & verification (definition of done)

- `npm install` succeeds.
- `npm run build` completes and produces `build/index.html` (static SPA output).
- `npm run dev` starts the dev server on `:5173` and serves HTTP 200.
- `npm run check` (svelte-check) passes with no type errors.

## 8. Out of scope

- Root layout, navigation, and routing (2.1.2).
- Jellyfin API client and auth (2.1.3).
- `.env` and Vite `/api` proxy (2.1.4).
- Tailwind, UI components, and any actual screens.
