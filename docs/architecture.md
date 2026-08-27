# Home Media Server — System Architecture

**Version:** 1.2
**Last Updated:** 2026-08-27

---

## 1. Overview

This document describes the architecture of the **Home Media Server** system, a private, home‑network media management and streaming solution. The system provides:

- A custom web application for browsing and playing movies and TV series.
- A custom Android application with the same capabilities.
- A local backend media server (Jellyfin) that manages the library, metadata, and streaming.
- Direct‑play streaming only; no server‑side transcoding.

The system is designed for personal use within a home network and does **not** require public internet access.

---

## 2. System Components

| Component | Technology | Responsibility |
|-----------|------------|----------------|
| **Media Server** | Jellyfin (Docker container, ARM64) | Media library management, metadata fetching, user authentication, REST API, direct streaming. |
| **Reverse Proxy** | Caddy | TLS termination (self‑signed), routing requests to Jellyfin and web frontend. |
| **Web Frontend** | SvelteKit (static SPA) | Custom user interface for browsing and playing media in a browser. |
| **Android App** | Kotlin + Jetpack Compose | Native Android client with media browsing and playback. |
| **Database** | SQLite (inside Jellyfin) | Stores user data, library items, metadata, play states. Managed entirely by Jellyfin. |
| **Streaming Storage** | SSD (512 GB) at `/mnt/ssd` | Video files staged for streaming; Jellyfin configuration, database, metadata, image cache. |
| **Archive Storage** | USB 3.0 HDD (2 TB) at `/mnt/hdd` | Long‑term archive of all video files. Copied to the SSD when needed for streaming. |

---

## 3. Architecture Diagram

```text
┌───────────────┐       ┌───────────────┐
│  Web Browser  │       │  Android App  │
│  (LAN device) │       │ (phone/tablet)│
└──────┬────────┘       └──────┬────────┘
       │                       │
       │  HTTP/HTTPS           │  HTTP/HTTPS
       │                       │
       └───────────┬───────────┘
                   │
          ┌────────▼────────┐
          │  Reverse Proxy  │   Caddy on Orange Pi 3B
          │ mediaserver.local│  Ports 80/443
          └──┬───────────┬──┘
             │           │
   /api/*  ┌─▼─────┐     │  /*  (static files)
           │Jellyfin│     │
           │Server  │     │  ┌─────────────────┐
           │(Docker)│     └──►   Web Frontend  │
           └───┬────┘        │   (Static SPA)  │
               │             └─────────────────┘
                │ reads/writes
       ┌────────┴────────┐
       │  SSD (streaming │
       │  media, config, │
       │  DB, cache)     │
       └─────────────────┘
                ▲ copies media to SSD
       ┌────────┴────────┐
       │  HDD (media     │
       │  archive)       │
       └─────────────────┘
```

---

## 4. Hardware & Environment

| Machine | OS | Hostname | Purpose |
|---------|----|----------|---------|
| Orange Pi 3B | Armbian 13 | `orangepi3b.local` | Production server, runs Docker containers. |
| Arch Linux laptop | Arch Linux (rolling) | `devserver.local` | Development and testing environment. |
| Windows PC | Windows 10/11 | — | Android development (Android Studio) and VS Code remote access. |

### 4.1 Hardware Resources

- **Orange Pi 3B**  
  - SoC: Rockchip RK3566 (quad‑core Cortex‑A55)  
  - RAM: **8 GB LPDDR4** (ample for direct‑play streaming)  
  - Storage: 64 GB eMMC (OS) + 512 GB SSD (streaming) + 2 TB USB 3.0 HDD (archive)  
  - Network: Gigabit Ethernet  
  - OS: Armbian 13 (kernel 6.18.46‑current‑rockchip64)

- **Arch Linux laptop** (development)  
- **Windows PC** (Android development and remote access)

---

## 5. Storage Layout

### 5.1 Physical Drives

- **64 GB eMMC** — OS (Armbian 13)
- **512 GB SSD** mounted at `/mnt/ssd` — video files staged for streaming + Jellyfin config/cache
- **2 TB USB 3.0 HDD** mounted at `/mnt/hdd` — long‑term media archive

### 5.2 Directory Structure

```text
/mnt/ssd/
├── media/                # Video files staged for streaming (copied from the HDD archive)
│   ├── movies/
│   │   └── MovieName (Year)/
│   │       └── MovieName (Year) - 1080p.mp4
│   └── tvshows/
│       └── SeriesName (Year)/
│           └── Season 01/
│               └── SeriesName - S01E01 - EpisodeTitle.mp4
├── jellyfin/
│   ├── config/           # Jellyfin database, metadata, user settings
│   └── cache/            # Image cache, temporary files (no transcoding)
└── web-frontend/         # Built static files for web app (optional, if not using Docker)

/mnt/hdd/
└── archive/              # Master copy of all media (not served directly by Jellyfin)
    ├── movies/
    └── tvshows/
```

Note: Media files must be in a client‑friendly format (H.264/AAC in MP4 recommended) to avoid transcoding.

### 5.3 Media Flow (HDD archive → SSD streaming)

The HDD is the long‑term archive and is not read by Jellyfin. When a video is to be streamed, it is copied from the HDD to the SSD; Jellyfin serves only the files staged on the SSD. This isolates the archive from streaming reads and serves media from fast SSD storage.

## 6. Network Architecture

- All devices are on the same local network.
- **Orange Pi 3B** has a static IP (e.g., 192.168.1.100) and mDNS hostname `orangepi3b.local`.
- **Arch laptop** uses `devserver.local` (mDNS) for development.
- **Windows PC** may require manual hosts entries if mDNS is not supported.
- Reverse proxy (Caddy) listens on ports 80/443 and routes:
  - `/api/*` → Jellyfin (internal port 8096)
  - `/*` → Web frontend (static files or container)
- No public IP or external access is required.

Future remote access: Can be added via VPN (Tailscale/WireGuard) without changing the architecture.

---

## 7. Docker Architecture (Production)

The Orange Pi 3B runs the following Docker services:

| Service | Image | Purpose | Config |
|---------|-------|---------|--------|
| `caddy` | `caddy:2-alpine` | Reverse proxy and TLS | `Caddyfile`, volumes for data |
| `jellyfin` | `jellyfin/jellyfin` (arm64) | Media server backend | Mounts SSD (media + config), transcoding disabled |
| `web-frontend` | `nginx:alpine` (or static file served by Caddy) | Serves built SPA | Mounts build output |

All services share a Docker network (`media-net`). Only Caddy publishes ports to the host.

Transcoding is disabled inside Jellyfin (Dashboard → Playback → Transcoding → Off). No hardware acceleration devices are passed.

---

## 8. Jellyfin Configuration

- **Base URL:** `/api` — set in Dashboard → Networking → Base URL so Jellyfin is served under the `/api` path by Caddy. This makes all Jellyfin-generated URLs (images, stream URLs, web UI) carry the `/api` prefix.

- **Libraries:**  
  - Movies → `/media/movies`  
  - TV Shows → `/media/tvshows`  
  `/media` maps to `/mnt/ssd/media` on the host (files staged for streaming from the HDD archive).

- **Authentication:**  
  Jellyfin manages user accounts. Access tokens are obtained via `/Users/AuthenticateByName`.

- **Playback:**  
  Only direct play is used. Clients receive a `static=true` stream URL and play the file as‑is.

- **Metadata:**  
  Jellyfin fetches posters, descriptions, and other metadata from online providers and stores them in `/config`.

---

## 9. Web Frontend Design (SvelteKit)

The web app is a single‑page application that communicates exclusively with Jellyfin’s REST API.

**Key Screens (MVP)**

| Screen | API Calls | Notes |
|--------|-----------|-------|
| Login | `POST /Users/AuthenticateByName` | Store access token |
| Movies List | `GET /Users/{userId}/Items?IncludeItemTypes=Movie` | Grid of posters |
| Movie Detail | `GET /Users/{userId}/Items/{itemId}` | Metadata, play button |
| Video Player | `GET /Items/{itemId}/PlaybackInfo` | Obtain direct stream URL |

**Playback Flow**

1. User clicks a movie.
2. App requests playback info and extracts the direct stream URL.
3. URL is set as the `src` of an HTML5 `<video>` element.
4. Browser decodes and plays the video locally.

---

## 10. Android App Design (Kotlin + Compose)

The Android app mirrors the web functionality and uses the jellyfin‑sdk‑kotlin for API access.

**Key Screens (MVP)**

| Screen | Purpose |
|--------|---------|
| Server URL | Allows user to enter Jellyfin server address (stored in DataStore) |
| Login | Authenticate and receive token |
| Movies Grid | Browse movies |
| Movie Detail | Metadata and play button |
| Player | Media3 ExoPlayer with direct stream URL |

Playback: ExoPlayer plays the direct stream URL. The app sends playback progress updates to Jellyfin for resume functionality (optional MVP).

---

## 11. API Endpoints (Jellyfin)

Clients use a subset of Jellyfin’s REST API. All requests include the access token in the `X-Emby-Authorization` header.

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/Users/AuthenticateByName` | Login, returns user and token |
| GET | `/Users/{userId}/Items` | List library items (filterable) |
| GET | `/Users/{userId}/Items/{itemId}` | Get item details |
| GET | `/Users/{userId}/Items/Resume` | Continue watching list |
| GET | `/Users/{userId}/Items/Latest` | Recently added |
| GET | `/Users/{userId}/Items?searchTerm={q}` | Search |
| POST | `/Items/{itemId}/PlaybackInfo` | Get media sources and stream URLs |
| GET | `/Videos/{itemId}/stream?static=true&...` | Direct stream URL |
| GET | `/Items/{itemId}/Images/Primary` | Poster/thumbnail |
| POST | `/Sessions/Playing` | Notify playback started |
| POST | `/Sessions/Playing/Progress` | Update playback progress |
| POST | `/Sessions/Playing/Stopped` | Notify playback stopped |

---

## 12. Security Considerations

- System is local‑only; no exposure to the public internet.
- Jellyfin requires authentication for all API calls.
- Caddy provides TLS with self‑signed certificates (optional but recommended).
- Streaming media (SSD) mounted read‑only into Jellyfin container; the archive HDD is not mounted into Jellyfin.
- Docker containers run as non‑root where possible.
- Secrets (if any) are passed via environment variables or `.env` files (not committed to Git).

**Required**: Authentication for Jellyfin, read‑only media mount.
**Optional**: HTTPS, firewall rules on Orange Pi.

---

## 13. Backup Strategy

| What | Method | Frequency |
|------|--------|-----------|
| Jellyfin config/database | `tar` or `rsync` to another machine | Weekly |
| Media archive (HDD) | Master copy; SSD staging is replaceable | Manual if desired |
| Source code | Git repository (GitHub) | Every commit |
| Docker compose files | Git repository | Every commit |

**Recovery**: Reinstall OS, Docker, pull repo, restore config, remount drives.

---

## 14. Deployment Workflow

1. Web frontend
   - Build on Arch laptop (`npm run build`).
   - Copy static files to Orange Pi (`scp` or via Docker image).
2. Android app
   - Build APK on Windows PC.
   - Install directly on device.
3. Docker configs
   - Update `docker-compose.yml` in repo.
   - Pull on Orange Pi and restart containers.

---

## 15. Future Improvements (No Redesign Required)

- TV series browsing (add UI screens)
- Continue Watching and resume sync
- Subtitles and multiple audio track selection
- User profiles and permissions
- Offline downloads in Android
- Hardware acceleration (if ever needed, but not recommended on this board)
- Remote access via VPN
- More advanced caching and PWA
- Optional NPU‑powered features: smart thumbnail selection, content tagging, voice control (separate project, not part of core architecture)

---

# 16. Key Decisions & Rationale

| Decision | Reason |
|----------|--------|
| Use Jellyfin as backend | Avoid building media scanner, metadata, and streaming from scratch. |
| No transcoding | Orange Pi 3B’s CPU/GPU is sufficient for direct play; transcoding is unnecessary for our client devices. |
| Custom frontends | Required by project goals; Jellyfin API is stable and well‑documented. |
| Single repository (monorepo) | Simplifies management for a solo project. |
| Caddy as reverse proxy | Easy HTTPS, automatic mDNS support, lightweight. |
| SQLite (inside Jellyfin) | Adequate for home scale; no external database needed. |

---

This document is the source of truth for architectural decisions. Update it whenever a major change is made.
