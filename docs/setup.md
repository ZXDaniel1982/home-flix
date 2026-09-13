# Orange Pi Setup Guide

**Version:** 1.0
**Last Updated:** 2026-08-31

This guide walks through a fresh install of the Home Flix server on an Orange Pi 3B,
from a blank OS to a working Jellyfin backend + custom web frontend behind Caddy.

---

## 1. Hardware & Prerequisites

| Component | Detail |
|-----------|--------|
| Board | Orange Pi 3B (Rockchip RK3566, 8 GB RAM) |
| OS | Armbian (Debian-based) |
| Storage | 64 GB eMMC (OS) + 512 GB SSD (streaming) + 2 TB USB HDD (archive) |
| Network | Local home network only (no public IP) |

Media must be **H.264/AAC in MP4** for direct play. The server never transcodes;
all decoding happens on the client. Files in H.265 or other codecs will not play.

---

## 2. Hostname & mDNS

Set the hostname and enable mDNS so the board is reachable as `orangepi3b.local`:

```bash
sudo hostnamectl set-hostname orangepi3b
sudo apt update && sudo apt install -y avahi-daemon
sudo systemctl enable --now avahi-daemon
```

Verify from another machine: `ping orangepi3b.local`.

---

## 3. Storage

Create mount points, mount the drives, and persist them in `/etc/fstab`:

```bash
sudo mkdir -p /mnt/ssd /mnt/hdd
# mount SSD and HDD, then add fstab entries (by UUID)
```

Directory layout:

```text
/mnt/ssd/
├── media/                 # staged-for-streaming files Jellyfin serves (read-only)
│   ├── movies/
│   └── tvshows/
├── jellyfin/
│   ├── config/
│   └── cache/
└── web-frontend/          # built static files for the web app (served by Caddy)
```

The HDD archive is **not** read by Jellyfin; files are copied to the SSD when staged
for streaming.

---

## 4. Docker & Docker Compose

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
# log out and back in
docker --version
docker compose version
```

---

## 5. Deploy Services

Copy the `docker/` directory from this repo to the board (or clone the repo), create
the environment file, and start the services:

```bash
mkdir -p ~/home-flix && cd ~/home-flix
# copy docker/docker-compose.yml and docker/Caddyfile here
cp docker/.env.example docker/.env
docker compose -f docker/docker-compose.yml up -d
```

`docker-compose.yml` defines two services on a shared `media-net` network:

- **caddy** — reverse proxy, publishes ports 80/443 (the only host ports).
- **jellyfin** — media server, internal only (no host port).

---

## 6. Jellyfin Setup Wizard

Open the Jellyfin UI and complete the setup wizard:

```bash
# while the frontend is not yet deployed, reach Jellyfin directly at:
#   http://<orange-pi-ip>:8096   (only if the old port is still published)
# once Caddy is serving /api, use:
#   http://orangepi3b.local/api/web/index.html
```

1. Create the admin user (e.g. `dzhang`) and set a password.
2. Add libraries:
   - **Movies** → `/media/movies`
   - **TV Shows** → `/media/tvshows`
3. Disable transcoding: **Dashboard → Playback → Transcoding → Off**, and set
   **Hardware Acceleration** to **None**.

---

## 7. Set Jellyfin Base URL

Caddy routes `/api/*` to Jellyfin, so Jellyfin must serve its API and web UI under
`/api`:

- **Dashboard → Networking → Base URL** → set to `/api`, save.

Jellyfin then generates `/api`-prefixed URLs, and its admin UI is reachable at
`http://orangepi3b.local/api/web/index.html`.

---

## 8. Deploy the Web Frontend

On the development machine, run the deploy script. It builds the SvelteKit app and
rsyncs the static output to the board:

```bash
scripts/deploy-web.sh
```

Prerequisites: `rsync` and SSH access to the board. Install rsync on both machines:

```bash
sudo pacman -S rsync                      # Arch laptop
ssh dzhang@orangepi3b.local 'sudo apt install -y rsync'   # Orange Pi
```

Override the defaults with environment variables:

```bash
SERVER=orangepi3b.local SSH_USER=dzhang REMOTE_DIR=/mnt/ssd/web-frontend scripts/deploy-web.sh
```

`rsync --delete` removes files on the board that are no longer in the build, so the
served directory stays in sync with the current source. No Caddy restart is needed.

Then serve those files from Caddy. The repo's `docker/Caddyfile` already does this:

```caddy
http://{$SERVER_HOSTNAME}, http://{$SERVER_IP} {
	handle /api/* {
		reverse_proxy jellyfin:8096
	}
	handle {
		root * /srv
		try_files {path} /index.html
		file_server
	}
}
```

`SERVER_HOSTNAME` / `SERVER_IP` are read from `docker/.env` (see `docker/.env.example`) and passed to the Caddy container by `docker-compose.yml`.

Add the frontend mount to the Caddy service in `docker/docker-compose.yml`:

```yaml
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - /mnt/ssd/web-frontend:/srv:ro
      - caddy_data:/data
      - caddy_config:/config
```

Reload:

```bash
docker compose -f docker/docker-compose.yml up -d
```

---

## 9. Client Access

`orangepi3b.local` uses mDNS, which some clients (Windows, some Android devices) do
not resolve. Add an entry to the client's `hosts` file (Windows:
`C:\Windows\System32\drivers\etc\hosts`, as Administrator):

```text
<orange-pi-ip>   orangepi3b.local
```

For a stable IP, reserve the board's address in your router's DHCP settings (or give
it a static IP).

---

## 10. Verify

After each stage, confirm it works:

1. **Services** — `docker compose ps` shows `caddy` and `jellyfin` running/healthy.
2. **Jellyfin** — `http://orangepi3b.local/api/System/Info/Public` returns JSON.
3. **Frontend** — `http://orangepi3b.local/` loads the login page; log in, browse
   Movies, and play a movie (direct stream, no transcode).

Run the API/streaming smoke tests from the repo:

```bash
JELLYFIN_API_KEY=<key> scripts/test-jellyfin-api.sh
JELLYFIN_API_KEY=<key> scripts/test-direct-stream.sh
```

---

## Recovery / Backup

Back up the Jellyfin config (database, metadata, users) periodically:

```bash
tar -czf jellyfin-config-$(date +%F).tar.gz /mnt/ssd/jellyfin/config
```

Recovery: reinstall the OS, install Docker, restore this repo and the config tarball,
remount the drives, and `docker compose up -d`.
