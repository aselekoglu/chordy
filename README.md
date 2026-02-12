# Chordy (Spotify to Chords)

Android app for searching Spotify content and viewing estimated chord progression for tracks.

## What The App Does (Current)

- OAuth login with Spotify (Authorization Code + PKCE)
- Search tabs:
  - Songs: searchable list, play preview, add to library
  - Artists: opens artist placeholder page (`#TODO`)
  - Albums: opens album placeholder page (`#TODO`)
- Bottom mini-player in `MainActivity` (Spotify-style)
  - Tap a song to open mini-player and start preview
  - Tap mini-player to open full `PlayerActivity`
- Full player screen:
  - 30s preview playback controls
  - Chord timeline display when Spotify audio endpoints are available

## Important Limitation

Spotify may return `403` for `audio-analysis` / `audio-features` depending on your app access tier.
When that happens, playback still works but chord/key/tempo data cannot be loaded.

## Project Setup

### 1) Create a Spotify app

In Spotify Developer Dashboard:
- Create app
- Add redirect URI: `spotifytochords://auth/callback`
- Package used by this app: `com.example.spotifytochords`

### 2) Configure credentials

Add values to `local.properties` (project root):

```properties
SPOTIFY_CLIENT_ID=your_spotify_client_id
SPOTIFY_REDIRECT_URI=spotifytochords://auth/callback
```

`SPOTIFY_CLIENT_ID` is required for OAuth.

Optional alternatives:
- `~/.gradle/gradle.properties`
- environment variables with the same names

Resolution order is:
1. Gradle project property
2. `local.properties`
3. environment variable
4. default fallback

### 3) Build

```bash
./gradlew :app:assembleDebug
```

## How To Use

1. Open Account tab
2. Tap **Login with Spotify**
3. Complete OAuth in browser
4. Go to Search tab and search songs/artists/albums
5. For songs:
   - tap item/play button -> opens mini-player
   - tap mini-player -> opens full player (`activity_player.xml`)

## Current Architecture Notes

- Auth/session:
  - `SpotifyAuthManager.kt`
  - `SpotifyLoginActivity.kt`
  - session storage and token refresh in `ChordyRepository.kt`
- Spotify API client:
  - `SpotifyApiClient.kt`
- Chord processing:
  - `ChordEstimator.kt`
  - loading/orchestration in `ChordyRepository.loadPlayerData(...)`

## Known TODOs

- Artist page implementation
- Album page implementation
- Better handling/alternative strategy when Spotify audio endpoints are restricted
