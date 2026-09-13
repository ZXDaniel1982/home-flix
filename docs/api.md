# Jellyfin API Usage

Reference for the subset of Jellyfin's REST API used by the Home Flix clients (web frontend and Android app). For the high-level design see [architecture.md](architecture.md); for deployment see [setup.md](setup.md) and [development.md](development.md).

## Overview

- **Base URL:** `/api` — Jellyfin's *Base URL* setting is `/api`, and Caddy proxies `/api/*` to Jellyfin. Clients therefore talk to `http://<host>/api/...` (e.g. `http://orangepi3b.local/api/...`).
- **Clients:** the SvelteKit web app and the Kotlin/Compose Android app use the same *data*, but not always the same routes — the Android app talks through `jellyfin-sdk-kotlin`, which prefers the item routes (`/Items`, `/UserItems/Resume`) with `userId` as a query parameter, while the web app uses the user-scoped routes (`/Users/{userId}/Items`). Both forms are documented below.
- **Authentication:** both clients use the `MediaBrowser` scheme, but in different headers:
  - **Web frontend:** `X-Emby-Authorization: MediaBrowser Client="Home Flix", Device="Web Browser", DeviceId="...", Version="0.1.0", Token="<accessToken>"`
  - **Android app (SDK):** `Authorization: MediaBrowser Client="Home Flix", Device="Android", DeviceId="...", Version="0.1.0", Token="<accessToken>"`
  - Media/image requests (`<video>` / ExoPlayer / `<img>` / Coil) can't set headers, so the Android app passes the token as `?api_key=<accessToken>`. The web app's image URLs omit it.
- **Playback model:** direct play only (no transcoding). Stream URLs always use `static=true`.

## Authentication

| Method | Endpoint | Notes |
|--------|----------|-------|
| `POST` | `/Users/AuthenticateByName` | Body `{ "Username": "...", "Pw": "..." }`. Returns `{ AccessToken, User }`. Both clients store the token; the Android app also stores the user id for its `userId` query parameter. |

## Library browsing

Both clients read the same data through different routes:

| Data | Web frontend | Android app (SDK) |
|------|--------------|-------------------|
| Item query | `GET /Users/{userId}/Items` | `GET /Items?userId={userId}` |
| Item detail | `GET /Users/{userId}/Items/{itemId}` | `GET /Items/{itemId}?userId={userId}` |
| Continue watching | `GET /Users/{userId}/Items/Resume` | `GET /UserItems/Resume?userId={userId}` |

Item detail includes `UserData.PlaybackPositionTicks` for resume. Continue-watching uses `IncludeItemTypes=Movie,Episode&Limit=30`.

### Item-query parameter combinations

Applied to the item-query route (web `/Users/{userId}/Items`, Android `/Items`):

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
| `POST` | `/Items/{itemId}/PlaybackInfo` | Empty (`{}`) body. Returns `MediaSources`; the client picks the first source with `SupportsDirectPlay` and reads its `Id`. |
| `GET` | `/Videos/{itemId}/stream` | Direct stream. Query: `static=true&MediaSourceId={mediaSourceId}&api_key={token}`. Used as the `<video src>` (web) / ExoPlayer `MediaItem` (Android). |
| `POST` | `/Sessions/Playing` | Notify playback started. Body includes `ItemId`, `MediaSourceId`, `PositionTicks`, `PlayMethod: "DirectPlay"`. |
| `POST` | `/Sessions/Playing/Progress` | Periodic progress update (throttled to every 10s, and on pause). |
| `POST` | `/Sessions/Playing/Stopped` | Notify playback stopped (on leaving the player). Persists the resume position. |

### Tick conversion

Jellyfin positions are in ticks (10,000,000 ticks = 1 second; 1 ms = 10,000 ticks). Clients convert between media time and ticks when seeking and when reporting progress.
