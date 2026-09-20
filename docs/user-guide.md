# Home Flix User Guide

How to watch media with the Home Flix web app and Android app, and how to add new media. For server installation see [setup.md](setup.md); for the design see [architecture.md](architecture.md).

## 1. Overview

Home Flix is a private, local-network media server:

- **Jellyfin** manages the library and streams the files.
- **Direct play only** — the server never transcodes. Files must be **H.264 video + AAC audio in an MP4 container**; other formats may fail to play.
- Two clients: the **web app** (any browser on the network) and the **Android app**.

## 2. Accessing Home Flix

- **Web:** open `http://orangepi3b.local` in a browser on the same network (or `http://<server-ip>` if the name doesn't resolve).
- **Android:** install the APK, then enter the server address on first launch.
- **Sign in** with a Jellyfin account (ask the server admin to create one if needed).

## 3. Web app

### Sign in

Enter your username and password on the login page.

### Home — Continue Watching

The home page shows a **Continue Watching** row of in-progress movies and episodes with a progress bar. Select one to resume.

### Movies

The **Movies** tab shows a poster grid. Select a poster to open the detail page (poster, year, runtime, overview) and press **Play**.

### TV Shows

The **TV** tab lists series. Select a series to see its seasons and episodes, then select an episode to play it.

### Search

Use the search bar to find movies and series by name. Selecting a result opens its detail page.

### Playback

- Controls are a custom bar: previous/next episode, play/pause, seek, and fullscreen. There is no in-player volume control (use system/keyboard volume).
- For TV episodes, `|<` and `>|` play the previous/next episode **within the current season**; each is greyed out at the first/last episode of a season.
- When an episode ends, a countdown auto-plays the next episode **within the same season** (none at a season finale).
- Playback **resumes** from where you left off; progress is saved back to Jellyfin.
- If a file can't be played, a message appears — it is usually an unsupported format (only H.264/AAC/MP4 plays).

## 4. Android app

### First run — server address

On first launch the app asks for the **server address**. Enter `http://orangepi3b.local` (or `http://<server-ip>`). The app appends `/api` automatically. Then sign in with your Jellyfin username and password.

### Navigation

The bottom bar has four tabs:

- **Home** — Continue Watching.
- **Movies** — poster grid → detail → play.
- **TV Shows** — series → seasons → episodes → play.
- **Search** — find movies and series.

### Playback

- Custom controls: previous/next episode, play/pause, and a seek bar with elapsed/total time.
- For TV episodes, previous/next stay **within the current season** and are greyed out at the first/last episode of a season.
- When an episode ends, a countdown auto-plays the next in-season episode.
- Playback **resumes** from the last position; progress is reported to Jellyfin.
- Unsupported files show an error message instead of playing.

## 5. Adding media

### Format

Files must be **H.264 / AAC / MP4** for direct play. Convert other formats before adding them.

### Where files live

- **HDD archive** (`/mnt/hdd/Entertainment/video/`) — the master copy of movies/TV, not read by Jellyfin. (The drive also holds non-media personal data such as `Study/`, photos, and backups.)
- **SSD** (`/mnt/ssd/media/`) — the files Jellyfin actually serves. Media is copied here from the archive when you want to watch it.

### Naming and layout

Jellyfin matches metadata from the folder/file names, so follow these conventions:

```text
/mnt/ssd/media/
├── movies/
│   └── MovieName (Year)/
│       └── MovieName (Year) - 1080p.mp4
└── tvshows/
    └── SeriesName (Year)/
        └── Season 01/
            └── SeriesName - S01E01 - EpisodeTitle.mp4
```

### Staging a file for streaming

1. Copy the file from the archive to the matching SSD folder, e.g.:

   ```bash
   # adjust the source path to where the title lives on the HDD
   cp -r "/mnt/hdd/Entertainment/video/Movie/MovieName (Year)" /mnt/ssd/media/movies/
   ```

2. In Jellyfin (**Dashboard → Libraries → Scan All Libraries**), or wait for the next scheduled scan, so the new file appears in the clients.

Once scanned, the title shows up in Movies/TV and is ready to play.

## 6. Troubleshooting

| Symptom | Likely cause / fix |
|---------|--------------------|
| Can't reach the server | The client can't resolve `orangepi3b.local`. Use the server's IP instead, or add a hosts entry (see [setup.md](setup.md) §9). |
| "Could not reach the server" on Android | Wrong address, or the device isn't on the same network. Verify the address and that Jellyfin is running. |
| Video won't play | Unsupported format. Only H.264/AAC/MP4 is supported (no transcoding). |
| Title missing after adding a file | The library hasn't scanned yet — run **Scan All Libraries** in Jellyfin. |
| No Continue Watching entry | Nothing is in progress yet, or playback progress hasn't been reported. |
