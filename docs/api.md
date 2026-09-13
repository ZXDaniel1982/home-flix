# Jellyfin API Usage

Reference for the subset of Jellyfin's REST API used by the Home Flix clients (web frontend and Android app). For the high-level design see [architecture.md](architecture.md); for deployment see [setup.md](setup.md) and [development.md](development.md).

## Overview

- **Base URL:** `/api` — Jellyfin's *Base URL* setting is `/api`, and Caddy proxies `/api/*` to Jellyfin. Clients therefore talk to `http://<host>/api/...` (e.g. `http://orangepi3b.local/api/...`).
- **Clients:** the SvelteKit web app and the Kotlin/Compose Android app use the same endpoint subset.
- **Authentication:**
  - JSON requests carry the access token in the `X-Emby-Authorization` header:
    ```
    X-Emby-Authorization: MediaBrowser Client="Home Flix", Device="...", DeviceId="...", Version="...", Token="<accessToken>"
    ```
  - Media and image requests (`<video>` / ExoPlayer / `<img>` / Coil) can't set headers, so the token is passed as the `api_key` query parameter instead.
- **Playback model:** direct play only (no transcoding). Stream URLs always use `static=true`.

## Authentication

| Method | Endpoint | Notes |
|--------|----------|-------|
| `POST` | `/Users/AuthenticateByName` | Body `{ "Username": "...", "Pw": "..." }`. Returns `{ AccessToken, User }`. Both clients store the token; the Android app also stores the user id for `/Users/{userId}/...` calls. |

## Library browsing

All library queries are on `/Users/{userId}/Items` (a `GET` with query parameters), plus the item-detail and resume endpoints.

| Method | Endpoint | Notes |
|--------|----------|-------|
| `GET` | `/Users/{userId}/Items` | General item query. Parameters used by the clients: |
| `GET` | `/Users/{userId}/Items/{itemId}` | Single item details (movie, series, or episode). Includes `UserData.PlaybackPositionTicks` for resume. |
| `GET` | `/Users/{userId}/Items/Resume` | Continue-watching list. Used with `IncludeItemTypes=Movie,Episode&Limit=30`. |

### `/Users/{userId}/Items` parameter combinations

| Use | Parameters |
|-----|------------|
| Movies grid | `IncludeItemTypes=Movie&Recursive=true&SortBy=SortName&SortOrder=Ascending&StartIndex={n}&Limit={n}` |
| TV series grid | `IncludeItemTypes=Series&Recursive=true&SortBy=SortName&SortOrder=Ascending&StartIndex={n}&Limit={n}` |
| Seasons of a series | `ParentId={seriesId}&IncludeItemTypes=Season&SortBy=IndexNumber&Limit=100` |
| Episodes of a season | `ParentId={seasonId}&IncludeItemTypes=Episode&SortBy=IndexNumber&Limit=500` |
| Search | `searchTerm={q}&Recursive=true&IncludeItemTypes=Movie,Series&SortBy=SortName&Limit=50` |

## Images

| Method | Endpoint | Notes |
|--------|----------|-------|
| `GET` | `/Items/{itemId}/Images/{type}` | `{type}` is `Primary`, `Backdrop`, or `Thumb`. Pass `?tag={imageTag}` (cache key from the item's `ImageTags`). The Android app additionally appends `&api_key={token}`. |

## Playback

| Method | Endpoint | Notes |
|--------|----------|-------|
| `POST` | `/Items/{itemId}/PlaybackInfo` | Empty body. Returns `MediaSources`; the client picks the first source with `SupportsDirectPlay` and reads its `Id`. |
| `GET` | `/Videos/{itemId}/stream` | Direct stream. Query: `static=true&MediaSourceId={mediaSourceId}&api_key={token}`. Used as the `<video src>` (web) / ExoPlayer `MediaItem` (Android). |
| `POST` | `/Sessions/Playing` | Notify playback started. Body includes `ItemId`, `MediaSourceId`, `PositionTicks`, `PlayMethod: "DirectPlay"`. |
| `POST` | `/Sessions/Playing/Progress` | Periodic progress update (throttled to every 10s, and on pause). |
| `POST` | `/Sessions/Playing/Stopped` | Notify playback stopped (on leaving the player). Persists the resume position. |

### Tick conversion

Jellyfin positions are in ticks (10,000,000 ticks = 1 second; 1 ms = 10,000 ticks). Clients convert between media time and ticks when seeking and when reporting progress.
