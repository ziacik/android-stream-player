# Uptodown publishing checklist

## One-time signing setup

1. Generate and permanently archive the production Android signing key. Do not use the debug keystore.
2. Add these GitHub Actions secrets to `ziacik/android-stream-player`:
   - `ANDROID_KEYSTORE_BASE64`
   - `ANDROID_KEYSTORE_PASSWORD`
   - `ANDROID_KEY_ALIAS`
   - `ANDROID_KEY_PASSWORD`
   - `TMDB_API_KEY`
3. Keep the original keystore and passwords backed up. Future updates must be signed with the same key.

## Build a release

Run the `Release APK` GitHub Actions workflow and enter a version name and monotonically increasing version code. The workflow builds one signed APK containing both supported TorrServer ABIs, verifies its signature and publishes `kino-<version>.apk` as a GitHub Release asset.

## Uptodown listing assets

Upload:

- `icon-512.png` — 512 x 512 app icon
- `feature-1024x500.png` — 1024 x 500 feature artwork
- `short-description.txt`
- `full-description-en.txt`
- the matching `changelog-<version>.txt`
- real app screenshots from `screenshots/`

Recommended first screenshots:

1. `01-home.png` — Home screen with Resume/Trending rows
2. `02-search.png` — movie search results
3. `03-detail.png` — movie detail and torrent source cards/badges
4. `04-player.png` — playback with Kino OSD visible

Use `./capture-store-screenshot.sh <name> [adb-target]` to capture screenshots from the real Android TV device.

## Uptodown developer account fields

Set in the Uptodown Developers Console:

- public support/contact email
- organization nationality
- optional official website/social profiles

These belong to the developer account rather than the APK.
