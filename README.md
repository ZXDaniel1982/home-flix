# home-flix

A private home media server and streaming system.

## Components

- **Jellyfin** — backend media server (direct play only, no transcoding)
- **Web frontend** — custom SvelteKit app for browsing and playback
- **Android app** — native Kotlin + Jetpack Compose app

## Documentation

- [Architecture](docs/architecture.md) — Jellyfin API endpoints and architectural decisions
- [Development](docs/development.md) — development environment guide
- [Project Plan](docs/project-plan.md) — modules, epics, and stories

## Hard Constraints

- Server: Orange Pi 3B (ARM64, Debian), all services in Docker
- No video transcoding on the server; all decoding happens on clients
- Media must be H.264/AAC/MP4 for direct play
- Local home network only, no public IP
