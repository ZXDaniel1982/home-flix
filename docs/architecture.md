# Home Media Server — System Architecture

**Version:** 1.0  
**Last Updated:** [Date]

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
| **Media Storage** | USB 3.0 HDD (2 TB) | Raw video files, organized in a Jellyfin‑compatible folder structure. |
| **Metadata / Cache** | M.2 NVMe SSD (500 GB) | Jellyfin configuration, database, fetched metadata, image cache. |

---

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
      │  SSD (metadata, │
      │  DB, cache)     │
      └─────────────────┘
               │ reads media
      ┌────────┴────────┐
      │  HDD (media     │
      │  library)       │
      └─────────────────┘
```

---

## 4. Hardware & Environment

| Machine | OS | Hostname | Purpose |
|---------|----|----------|---------|
| Orange Pi 3B | Debian (official/Armbian) | `orangepi3b.local` | Production server, runs Docker containers. |
| Arch Linux laptop | Arch Linux (rolling) | `devserver.local` | Development and testing environment. |
| Windows PC | Windows 10/11 | — | Android development (Android Studio) and VS Code remote access. |

---

## 5. Storage Layout

### 5.1 Physical Drives

- **500 GB M.2 NVMe SSD** mounted at `/mnt/ssd`
- **2 TB USB 3.0 HDD** mounted at `/mnt/hdd`

### 5.2 Directory Structure

```text
/mnt/ssd/
├── jellyfin/
│   ├── config/          # Jellyfin database, metadata, user settings
│   └── cache/           # Image cache, temporary files (no transcoding)
└── web-frontend/        # Built static files for web app (optional, if not using Docker)

/mnt/hdd/
└── media/
    ├── movies/
    │   └── MovieName (Year)/
    │       └── MovieName (Year) - 1080p.mp4
    └── tvshows/
        └── SeriesName (Year)/
            └── Season 01/
                └── SeriesName - S01E01 - EpisodeTitle.mp4
