# AGENTS.md

## Project: Home Media Server & Streaming System

This is a private home media system with:
- Jellyfin as backend media server (NO transcoding, direct play only)
- Custom web frontend built with SvelteKit
- Custom Android app built with Kotlin + Jetpack Compose

## Hard Constraints
- Server hardware: Orange Pi 3B (ARM64, Debian)
- No video transcoding on server. All decoding happens on clients.
- Media files must be in H.264/AAC/MP4 format for direct play.
- All services run inside Docker on the Orange Pi.
- Web frontend and Android app talk to Jellyfin's REST API.
- Network: local home network only, no public IP.
- Use hostname `orangepi3b.local` for production, `devserver.local` for development.

## Development Environment
- Windows PC → VS Code Remote-SSH → Arch Linux laptop (dev)
- Arch Linux laptop → Docker, Node.js, source code
- Orange Pi 3B → production Docker host (Debian)

## Code Style
- Web frontend: SvelteKit, JavaScript/TypeScript, Tailwind optional
- Android: Kotlin, Jetpack Compose, MVVM, jellyfin-sdk-kotlin, Media3 ExoPlayer
- Use environment variables for all URLs, never hardcode IP addresses.

## Source of Truth
- Jellyfin API endpoints are documented in docs/architecture.md
- All architectural decisions are in docs/architecture.md
