# Project Plan — Home Media Server

**Version:** 1.0
**Last Updated:** 2026-08-24

This document breaks the project into manageable **modules**, **epics**, and **stories** to guide incremental development. Each story is marked with a priority (MVP, Recommended, Optional) and a rough size (S, M, L, XL). Tick the boxes as you complete each story to track your progress.

---

## Module 1: Infrastructure & Environment Setup

**Goal:** Get Jellyfin running on the Orange Pi 3B, accessible from the network, with reverse proxy and proper storage.

### Epic 1.1 — Local Development Environment

- [x] 1.1.1 — Set up Git repository: Create repo, add `.gitignore`, initial README. *(MVP, S)*
- [x] 1.1.2 — Configure Arch Linux laptop for development: Install Docker, Node.js, SSH server, VS Code Remote-SSH. *(MVP, M)*
- [x] 1.1.3 — Set up remote access from Windows PC: Configure VS Code Remote-SSH, SSH keys, port forwarding. *(MVP, S)*

### Epic 1.2 — Orange Pi 3B Server Setup

- [x] 1.2.1 — Install Docker and Docker Compose: Use official script, verify ARM64 images work. *(MVP, S)*
- [x] 1.2.2 — Mount SSD and HDD: Create mount points, fstab entries, set permissions. *(MVP, S)*
- [x] 1.2.3 — Enable mDNS/hostname: Set hostname to `orangepi3b`, install Avahi, verify resolution. *(MVP, S)*

### Epic 1.3 — Jellyfin Deployment

- [x] 1.3.1 — Create docker-compose for Jellyfin: Define service with config/cache/media volumes, no transcoding. *(MVP, M)*
- [x] 1.3.2 — Deploy Jellyfin container: Start container, access web UI, complete setup wizard. *(MVP, S)*
- [x] 1.3.3 — Configure media libraries: Add Movies and TV Shows libraries. *(MVP, S)*
- [x] 1.3.4 — Disable transcoding globally: Set hardware acceleration to None, disable transcoding. *(MVP, S)*
- [x] 1.3.5 — Test direct play with sample media: Add compatible H.264/AAC MP4, verify playback in Jellyfin UI. *(MVP, S)*

### Epic 1.4 — Reverse Proxy & Networking

- [x] 1.4.1 — Deploy Caddy container: Add Caddy service, configure reverse proxy. *(MVP, M)*
- [x] 1.4.2 — Configure Caddyfile: Proxy `/api` to Jellyfin and `/` to static frontend later. *(MVP, S)*
- [x] 1.4.3 — Verify access from Windows and Android: Test `http://orangepi3b.local` from both. *(MVP, S)*

**Dependencies:** 1.1 → 1.2 → 1.3 → 1.4 (some tasks can overlap after Docker is installed).

---

## Module 2: Web Frontend (SvelteKit)

**Goal:** Custom web app that replaces Jellyfin's built-in UI for browsing and playback.

### Epic 2.1 — Project Scaffolding

- [x] 2.1.1 — Initialize SvelteKit project: Use `npm create svelte@latest`, set up Tailwind if desired. *(MVP, S)*
- [x] 2.1.2 — Set up routing and layout: Create root layout, navigation bar, dark theme. *(MVP, M)*
- [x] 2.1.3 — Implement Jellyfin API client: Module for fetch calls, auth token handling. *(MVP, M)*
- [x] 2.1.4 — Configure environment variables: `.env` for dev API URL, Vite proxy to Jellyfin. *(MVP, S)*

### Epic 2.2 — Authentication

- [ ] 2.2.1 — Login page: Form for username/password, call authenticate, store token. *(MVP, M)*
- [ ] 2.2.2 — Route guard: Redirect to login if no token, logout button. *(MVP, S)*

### Epic 2.3 — Movies Browsing

- [ ] 2.3.1 — Movies list page: Fetch movies, display grid with posters. *(MVP, L)*
- [ ] 2.3.2 — Movie poster component: Load images via Jellyfin image endpoint. *(MVP, M)*
- [ ] 2.3.3 — Movie detail page: Show backdrop, metadata, play button. *(MVP, L)*
- [ ] 2.3.4 — Pagination / infinite scroll: Load more movies when scrolling. *(Recommended, M)*

### Epic 2.4 — Video Playback

- [ ] 2.4.1 — Video player page: Embed HTML5 video, use direct stream URL. *(MVP, L)*
- [ ] 2.4.2 — Playback error handling: Show friendly message if file cannot be played. *(MVP, S)*
- [ ] 2.4.3 — Basic player controls: Native controls for MVP; later custom controls with resume. *(MVP, S)*
- [ ] 2.4.4 — Resume playback: Fetch progress, seek, send updates. *(Recommended, M)*

### Epic 2.5 — TV Series (Phase 2)

- [ ] 2.5.1 — TV series list page: Fetch series, display posters. *(Recommended, M)*
- [ ] 2.5.2 — Season and episode navigation: Show seasons, then episodes. *(Recommended, L)*
- [ ] 2.5.3 — Episode playback: Reuse video player for episodes. *(Recommended, M)*

### Epic 2.6 — Search & Continue Watching

- [ ] 2.6.1 — Search bar: Search endpoint, display results. *(Recommended, M)*
- [ ] 2.6.2 — Continue Watching row: Fetch `/Items/Resume`, display on home. *(Recommended, M)*

### Epic 2.7 — Polish & Responsive

- [ ] 2.7.1 — Responsive layout: Ensure mobile/tablet usability. *(Recommended, M)*
- [ ] 2.7.2 — Loading skeletons: Show placeholders while fetching. *(Optional, S)*
- [ ] 2.7.3 — Error pages: Handle API failures gracefully. *(Optional, S)*

**Dependencies:** 2.1 → 2.2 → 2.3 → 2.4 (MVP flow). 2.5, 2.6, 2.7 can be done later.

---

## Module 3: Android App (Kotlin + Jetpack Compose)

**Goal:** Native Android application with the same functionality as the web app.

### Epic 3.1 — Project Setup

- [ ] 3.1.1 — Create Android project: Use Compose template, minSdk 24+. *(MVP, S)*
- [ ] 3.1.2 — Add dependencies: jellyfin-sdk-kotlin, Media3 ExoPlayer, Coil, Navigation Compose. *(MVP, M)*
- [ ] 3.1.3 — Set up navigation graph: Routes for login, movies, detail, player. *(MVP, M)*

### Epic 3.2 — Server Connection & Auth

- [ ] 3.2.1 — Server URL configuration screen: User can enter server address, store in DataStore. *(MVP, M)*
- [ ] 3.2.2 — Login screen: Authenticate with Jellyfin API, store token. *(MVP, M)*
- [ ] 3.2.3 — Session management: Handle token expiry, logout. *(MVP, S)*

### Epic 3.3 — Movies Browsing

- [ ] 3.3.1 — Movies list screen: Fetch movies, display grid using LazyVerticalGrid. *(MVP, L)*
- [ ] 3.3.2 — Movie detail screen: Show poster, metadata, play button. *(MVP, L)*
- [ ] 3.3.3 — Image loading with Coil: Load posters efficiently. *(MVP, S)*

### Epic 3.4 — Video Playback

- [ ] 3.4.1 — Player screen with ExoPlayer: Use direct stream URL, configure ExoPlayer. *(MVP, L)*
- [ ] 3.4.2 — Playback controls: Custom controls (play/pause, seek). *(MVP, M)*
- [ ] 3.4.3 — Handle playback errors: Error UI on failure. *(MVP, S)*
- [ ] 3.4.4 — Resume playback: Send progress updates, resume from last position. *(Recommended, M)*

### Epic 3.5 — TV Series & Search (Phase 2)

- [ ] 3.5.1 — TV series list and episode navigation: Similar to web but for Android. *(Recommended, L)*
- [ ] 3.5.2 — Search functionality: Search screen with results. *(Recommended, M)*
- [ ] 3.5.3 — Continue watching row on home: Use Resume endpoint. *(Recommended, M)*

### Epic 3.6 — Offline Caching (Optional)

- [ ] 3.6.1 — Cache metadata locally: Use Room to store movie info for offline browsing. *(Optional, L)*
- [ ] 3.6.2 — Download for offline playback: Request download URL, save to app storage, play locally. *(Optional, XL)*

**Dependencies:** 3.1 → 3.2 → 3.3 → 3.4 (MVP). 3.5, 3.6 later.

---

## Module 4: Testing & Quality Assurance

**Goal:** Ensure the system works reliably across devices and media formats.

### Epic 4.1 — API & Streaming Tests

- [ ] 4.1.1 — Test Jellyfin API: Verify auth, library, playback info endpoints using curl/Postman. *(MVP, S)*
- [ ] 4.1.2 — Test direct streaming: Try multiple formats (H.264 MP4, H.265 MKV, subtitles). *(MVP, M)*
- [ ] 4.1.3 — Performance test: Stream 2-3 concurrent videos, monitor CPU/RAM on Orange Pi. *(Recommended, M)*

### Epic 4.2 — Web Frontend Tests

- [ ] 4.2.1 — Unit tests for API client: Mock fetch, test error handling. *(Recommended, M)*
- [ ] 4.2.2 — E2E test with Playwright: Automate login → browse movie → play. *(Recommended, L)*

### Epic 4.3 — Android Tests

- [ ] 4.3.1 — Unit tests for ViewModels: Test API calls, state management. *(Recommended, M)*
- [ ] 4.3.2 — UI tests with Compose test: Test navigation, login screen. *(Recommended, M)*
- [ ] 4.3.3 — Manual testing on device: Install APK, test playback, seek, resume. *(MVP, M)*

**Dependencies:** 4.1 after 1.3; 4.2 after 2.3; 4.3 after 3.3.

---

## Module 5: Documentation & Deployment Automation

**Goal:** Make the system maintainable and repeatable.

### Epic 5.1 — Project Documentation

- [ ] 5.1.1 — Write setup guide: Instructions for fresh install on Orange Pi. *(MVP, M)*
- [ ] 5.1.2 — Document API usage: List key endpoints used by clients. *(Recommended, S)*
- [ ] 5.1.3 — Create user guide: How to use web/app, add media. *(Optional, S)*

### Epic 5.2 — Deployment Automation

- [ ] 5.2.1 — Web frontend deployment script: Build and copy to Orange Pi (or push Docker image). *(Recommended, M)*
- [ ] 5.2.2 — Docker Compose production setup: Finalize compose with all services, env vars. *(Recommended, M)*
- [ ] 5.2.3 — CI/CD pipeline: GitHub Actions to build and deploy automatically. *(Optional, L)*

### Epic 5.3 — Backup & Recovery

- [ ] 5.3.1 — Backup Jellyfin config: Script to tar config and copy offsite. *(Recommended, M)*
- [ ] 5.3.2 — Media backup strategy: Document rsync or cron job. *(Optional, M)*

**Dependencies:** 5.1 after stable; 5.2 after MVP; 5.3 after setup.

---

## Suggested Sprint Plan

### Sprint 1 (MVP Infrastructure) — 1-2 weeks
- All of Module 1 (Infra & Jellyfin)
- Optionally start 2.1 and 3.1 in parallel if time permits

### Sprint 2 (Web MVP) — 1-2 weeks
- 2.1 → 2.2 → 2.3 → 2.4 (minimal movie browsing + playback)

### Sprint 3 (Android MVP) — 1-2 weeks
- 3.1 → 3.2 → 3.3 → 3.4 (same functionality on Android)

### Sprint 4 (Enhancements & Testing) — 2 weeks
- Add TV series, search, resume (2.5, 2.6, 3.5)
- Start testing (4.1, 4.2, 4.3)
- Documentation (5.1)

### Sprint 5 (Deployment & Polish) — 1 week
- 5.2 deployment scripts, finalize compose
- 2.7 responsive, error states
- Manual testing on multiple devices

---

## How to Use This Document

1. **Tick the boxes** as you complete each story (`[ ]` → `[x]` in the markdown).
2. **Assign priorities** (MVP, Recommended, Optional) and labels (frontend, android, infra).
3. **Pick stories for each sprint** based on the order above.
4. **Break stories into sub-stories** when you start working — for example, story 2.3.1 "Movies list page" can have sub-stories: "fetch data", "render grid", "add poster loading", "handle empty state".
5. **Update the list** as you learn — this is a living document.

This breakdown gives you a clear starting point: **Module 1, Story 1.1.1** (now complete). Once infrastructure is up, you'll have quick wins by deploying Jellyfin and seeing your media in a browser, which will motivate the rest.
