# Web Frontend Scaffold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the empty, correctly-configured SvelteKit project (`web-frontend/`) that the rest of Module 2 builds on.

**Architecture:** A TypeScript SvelteKit single-page app built with `@sveltejs/adapter-static` (SPA fallback). Caddy serves the built static files at `/`; the app will call Jellyfin's REST API via a relative `/api` base URL (wired in later stories). No SSR, no prerendering.

**Tech Stack:** SvelteKit, TypeScript (svelte-check), plain CSS, ESLint + Prettier, `@sveltejs/adapter-static`, npm.

**Spec:** `docs/superpowers/specs/2026-08-31-web-frontend-scaffold-design.md`

## Global Constraints

- Language: **TypeScript** with type checking (`--types ts`).
- Styling: **plain CSS** — no Tailwind.
- Adapter: **`@sveltejs/adapter-static`** with **SPA fallback** (`fallback: 'index.html'`), `ssr = false`, no prerendering.
- Tooling: **ESLint + Prettier**; no Vitest/Playwright yet.
- Package name: `home-flix`, `"private": true`.
- Location: `web-frontend/` at the repo root (`/home/dzhang/workspace/home-flix`).
- Toolchain: Node v26.8.1, npm 12.0.2. `sv create` CLI available via `npx`.
- Repo `.gitignore` already ignores `node_modules/`, `.svelte-kit/`, `build/`, `package-lock.json` — do not commit those.

---

### Task 1: Scaffold the SvelteKit project

**Files:**
- Create: `web-frontend/` (entire project tree, via CLI)

**Interfaces:**
- Consumes: nothing (greenfield).
- Produces: `web-frontend/package.json` (name `home-flix`, scripts `dev`/`build`/`check`/`lint`/`format`), `web-frontend/vite.config.ts`, `web-frontend/src/app.html`, `web-frontend/src/routes/+page.svelte`, `web-frontend/src/lib/`, `web-frontend/static/`, and an adapter-static dependency.

- [x] **Step 1: Scaffold non-interactively**

From the repo root, run:

```bash
npx sv create web-frontend --template minimal --types ts --add prettier eslint sveltekit-adapter=adapter:static --install npm
```

Expected: creates `web-frontend/` and runs `npm install`. No interactive prompts (all of `--template`, `--types`, `--add`, `--install` are supplied).

- [x] **Step 2: Verify the scaffold structure**

```bash
ls web-frontend
ls web-frontend/src
cat web-frontend/package.json
```

Expected: `package.json`, `vite.config.ts`, `src/` (contains `app.html`, `routes/+page.svelte`), `static/`, `node_modules/`. `@sveltejs/adapter-static` is in `devDependencies`.

- [x] **Step 3: Set the package name**

Edit `web-frontend/package.json` so the top-level `"name"` field is `"home-flix"` and `"private": true` is present (add it if missing). No other fields change.

- [x] **Step 4: Confirm type checking passes**

```bash
cd web-frontend && npm run check
```

Expected: exits 0 with no type errors.

- [x] **Step 5: Commit the scaffold**

```bash
git add web-frontend
git commit -m "Scaffold SvelteKit web frontend (2.1.1): TS, plain CSS, adapter-static"
```

---

### Task 2: Configure SPA mode and verify the build

**Files:**
- Modify: `web-frontend/vite.config.ts` (add SPA fallback)
- Create: `web-frontend/src/routes/+layout.ts` (disable SSR)

**Interfaces:**
- Consumes: the scaffolded `vite.config.ts` and `src/routes/` from Task 1.
- Produces: a pure-SPA build — `npm run build` emits `web-frontend/build/index.html` (ignored by git). Later stories rely on: `npm run dev` on `:5173`, `npm run build` → `build/`, and a client-side-only app.

- [x] **Step 1: Add SPA fallback to the adapter**

Edit `web-frontend/vite.config.ts` so the adapter line reads:

```js
adapter: adapter({ fallback: 'index.html' })
```

(The scaffolded file has `adapter: adapter()` inside the `sveltekit({ ... })` plugin — change only the argument.)

- [x] **Step 2: Disable SSR in the root layout**

Create `web-frontend/src/routes/+layout.ts` with:

```ts
export const ssr = false;
```

- [x] **Step 3: Build and confirm static SPA output**

```bash
cd web-frontend && npm run build
```

Expected: exits 0 and `web-frontend/build/index.html` exists (this is the SPA fallback document).

- [x] **Step 4: Smoke-test the dev server**

```bash
cd web-frontend && (npm run dev >/tmp/webdev.log 2>&1 &) && sleep 4 && curl -s -o /dev/null -w "%{http_code}\n" http://localhost:5173/ && pkill -f "vite dev"
```

Expected: prints `200`. (If the port is taken, note it and use the printed port.)

- [x] **Step 5: Re-run checks and commit**

```bash
cd web-frontend && npm run check && cd .. && git add web-frontend && git commit -m "Configure SPA mode for web frontend (2.1.1): adapter-static fallback, ssr off"
```

Expected: `check` exits 0; commit succeeds.
