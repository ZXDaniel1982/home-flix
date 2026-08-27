# Development Environment Guide

**Version:** 1.0  
**Last Updated:** [Date]

---

## 1. Machines Overview

| Machine | OS | Hostname | IP (example) | Role |
|---------|----|----------|--------------|------|
| Windows PC | Windows 10/11 | — | 192.168.1.20 | Android development, VS Code Remote‑SSH client |
| Arch Linux laptop | Arch Linux (rolling) | `devserver` | DHCP-assigned | Primary development machine (Docker, Node.js, source code) |
| Orange Pi 3B | Armbian 13 | `orangepi3b` | 192.168.1.100 | Production server (Docker, Jellyfin, Caddy) |

---

## 2. Network & Hostnames

- **Orange Pi 3B**: `orangepi3b.local` (Avahi/mDNS enabled)  
- **Arch Linux laptop**: `devserver.local` (mDNS/Avahi is NOT installed)  
- **Windows PC**: Because mDNS is not available on the dev laptop, add an entry to `C:\Windows\System32\drivers\etc\hosts` (edit as Administrator) pointing the laptop's IP to `devserver.local`:
  ```
  192.168.1.100   orangepi3b.local
  <laptop-ip>     devserver.local
  ```
  The laptop's IP is DHCP-assigned; check it with `ip -4 addr` or `hostname -I`. Port forwarding is **not** required — Windows and the laptop are on the same LAN.

All services use these hostnames. **Never hardcode IP addresses in application code.**

---

## 3. Arch Linux Laptop Setup

### 3.1 Base System Update & Packages

```bash
# Update system
sudo pacman -Syu

# Install core development packages
sudo pacman -S git base-devel openssh docker docker-compose nodejs npm

# Optional: text editors, monitoring tools
sudo pacman -S vim htop
```

### 3.2 Enable SSH (for Remote Access from Windows)

```bash
sudo systemctl enable --now sshd
```

Verify SSH is running: `sudo systemctl status sshd`

### 3.3 Configure Docker

```bash
# Enable Docker service
sudo systemctl enable --now docker

# Add your user to docker group (so you can run docker without sudo)
sudo usermod -aG docker $USER
```

**Important:** Log out and log back in for the group change to take effect.  
Test with: `docker run hello-world`

### 3.4 VS Code Remote‑SSH from Windows

The laptop's SSH server (`sshd`) is enabled and listening on port 22. No port forwarding is needed — the connection is direct on the local network.

1. Install VS Code on Windows and the **Remote – SSH** extension.  
2. Generate SSH key pair on Windows (PowerShell):
   ```powershell
   ssh-keygen -t ed25519
   ```
3. Copy the public key to the Arch laptop:
   ```powershell
   ssh-copy-id youruser@devserver.local
   ```
   (If `devserver.local` does not resolve, add the hosts entry from §2 or use the laptop's IP.)
4. In VS Code, press `F1`, choose **Remote‑SSH: Connect to Host…**, enter `youruser@devserver.local`.

You will now have a full Linux development environment accessible from Windows.

---

## 4. Repository Structure

Clone the repository on the Arch laptop (and optionally on Windows for Android Studio):

```bash
git clone https://github.com/yourusername/home-media-server.git
cd home-media-server
```

The repository is a **monorepo** containing:

```
home-media-server/
├── web-frontend/      # SvelteKit app
├── android-app/       # Android Studio project
├── docker/            # Docker Compose and Caddyfile
├── scripts/           # Utility scripts
├── docs/              # Documentation (including this file)
└── media-samples/     # Small test files (gitignored)
```

---

## 5. Web Frontend Development

### 5.1 Install Dependencies

```bash
cd web-frontend
npm install
```

### 5.2 Start Development Server

```bash
npm run dev
```

This starts Vite at `http://localhost:5173`.  
The Vite dev server is configured to proxy `/api` requests to Jellyfin. See `vite.config.js`.

### 5.3 Build for Production

```bash
npm run build
```

Output goes to `build/` (or `dist/` depending on config).  
The built static files are served by Caddy or Nginx on the Orange Pi.

### 5.4 Environment Variables

- Development: `.env.development` (optional)  
- Production: `.env.production` (optional)  
- The API base URL is set to `/api` by default (relative path). In dev, Vite proxy redirects to the Jellyfin instance.

---

## 6. Android App Development

### 6.1 Environment

- **IDE**: Android Studio (runs natively on Windows).  
- **SDK**: Minimum API 24, target latest.  
- **Language**: Kotlin with Jetpack Compose.  
- **Key libraries**: `jellyfin-sdk-kotlin`, `Media3 ExoPlayer`, `Coil`, `Navigation Compose`.

### 6.2 Build

Open the `android-app/` folder in Android Studio (on Windows).  
Debug build:

```bash
./gradlew assembleDebug
```

Release build (requires signing config):

```bash
./gradlew assembleRelease
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### 6.3 Server URL Configuration

The Android app should **not** hardcode the server address. Instead:

- Store the server URL in `DataStore` (or `SharedPreferences`).  
- Provide a settings screen where the user can enter an address (e.g., `http://orangepi3b.local`).  
- Optionally provide a default via `BuildConfig` field set in `build.gradle.kts`.

**For testing**, set the app to point to `http://devserver.local` or `http://orangepi3b.local`.

---

## 7. Production Deployment (Orange Pi 3B)

### 7.1 SSH and Clone

The board has users `root` and `dzhang`; SSH as `dzhang`.

```bash
ssh dzhang@orangepi3b.local
cd ~
git clone https://github.com/yourusername/home-media-server.git
cd home-media-server/docker
```

### 7.2 Configure Environment

Create a `.env` file from the example:

```bash
cp .env.example .env
nano .env
```

Set at least:

```
SERVER_HOSTNAME=orangepi3b.local
```

### 7.3 Start Services

```bash
docker compose up -d
```

Check status:

```bash
docker compose ps
docker compose logs -f
```

### 7.4 Set Jellyfin Base URL

Caddy serves Jellyfin under the `/api` path. After Jellyfin starts, set its base URL so Jellyfin generates `/api`-prefixed URLs:

1. Open `http://orangepi3b.local/api/web/` and log in as admin.
2. Go to **Dashboard → Networking** and set **Base URL** to `/api`.
3. Save. Jellyfin restarts and is reachable at `http://orangepi3b.local/api/`.

### 7.5 Update Services

When the code or configuration changes:

```bash
git pull
docker compose pull
docker compose up -d
```

---

## 8. Common Commands Reference

### Web Frontend

| Action | Command |
|--------|---------|
| Install deps | `cd web-frontend && npm install` |
| Dev server | `npm run dev` |
| Build | `npm run build` |
| Deploy to Orange Pi | `scp -r build/* dzhang@orangepi3b.local:/mnt/ssd/web-frontend/` |

### Android

| Action | Command |
|--------|---------|
| Debug APK | `cd android-app && ./gradlew assembleDebug` |
| Install on device | `adb install app/build/outputs/apk/debug/app-debug.apk` |
| Run unit tests | `./gradlew test` |

### Docker (Arch / Orange Pi)

| Action | Command |
|--------|---------|
| Start services | `docker compose up -d` |
| Stop services | `docker compose down` |
| View logs | `docker compose logs -f` |
| Rebuild | `docker compose up -d --build` |

### Backup Jellyfin Config (on Orange Pi)

```bash
tar -czf jellyfin-config-$(date +%F).tar.gz /mnt/ssd/jellyfin/config
scp jellyfin-config-*.tar.gz user@devserver.local:~/backups/
```

---

## 9. Notes & Troubleshooting

- **Arch Linux** is rolling release. Run `sudo pacman -Syu` regularly.  
- If Docker requires `sudo` after group addition, log out and back in.  
- mDNS may not work on all Android devices; if so, use the IP address directly.  
- The Orange Pi runs **Armbian** (Debian‑based); use `apt` there, not `pacman`.  
- Always use environment variables for URLs; never commit `.env` files to Git.

---

*This document is part of the project documentation. Update it when the development environment changes.*
