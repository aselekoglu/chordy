# Spotify to Chords (Android)

Android app that:

1. Accepts a Spotify track URL/URI/ID
2. Requests Spotify audio analysis data
3. Converts segment pitch vectors into estimated visible chords
4. Prints a compact progression and a timestamped timeline

## Current Scope

- Implemented: end-to-end chord estimation from Spotify Web API
- Implemented: URL/URI/ID parser and robust API error handling
- Implemented: bar-aligned chord timeline output
- Not implemented yet: social media features
- Not implemented yet: lyrics sync (Spotify Web API does not expose lyrics in this flow)

## Setup

### 1) Create Spotify API credentials

- Go to Spotify Developer Dashboard
- Create an app and copy `Client ID` and `Client Secret`

### 2) Provide credentials

Option A (recommended for local development): UI fields
- Launch app and paste `Client ID` and `Client Secret` directly
- Values are stored in app preferences on device

Option B (build-time defaults):
- In `~/.gradle/gradle.properties` or project-level `gradle.properties`:

```properties
SPOTIFY_CLIENT_ID=your_client_id
SPOTIFY_CLIENT_SECRET=your_client_secret
```

Or set environment variables with the same names.

### 3) Run

```bash
./gradlew assembleDebug
```

Install/run from Android Studio or with `adb`.

## How Chord Estimation Works

- Input: Spotify `audio-analysis` segments (`pitches[12]`, duration, confidence)
- Candidate chords: all 12 major + 12 minor triads
- For each segment:
  - Normalize pitch vector
  - Score against chord templates with cosine similarity
  - Apply slight key-aware bias from Spotify audio features (`key`, `mode`) when available
  - Reject low-confidence/ambiguous predictions as `N` (no chord)
- Post-processing:
  - Merge adjacent identical chords
  - Smooth short noisy `N` spans
  - Align final chords to Spotify bars for readable timeline

## Notes

- This is an estimation model, not ground-truth transcription.
- Accuracy varies by genre, mix complexity, and recording quality.
- Chord quality types beyond major/minor can be added later (7th, diminished, suspended).
