# Torrent Playback Proof — Design

Date: 2026-08-29

## Goal

Prove that Android Stream Player can start and seek playback directly from a BitTorrent magnet without waiting for the whole torrent to download.

The proof accepts a magnet at runtime, downloads torrent metadata, selects the main video file, prioritizes the pieces needed for playback, and exposes the selected file to Media3 through a custom `DataSource`.

The supplied test magnet is runtime input only. It must not be committed to the repository.

## Scope

This proof includes:

- one active magnet at a time;
- magnet metadata acquisition through BitTorrent/DHT/trackers;
- automatic main-video-file selection;
- selective file download;
- streaming-oriented piece prioritization for startup and seek;
- direct random-access reads from the partially downloaded file;
- Media3 playback;
- D-pad play/pause and seek controls sufficient to exercise reprioritization;
- visible/logged torrent status so we can verify playback begins before the full file is downloaded;
- a `deploy-debug` path that passes a magnet to the app at launch.

It intentionally excludes:

- torrent search, Torrentio and Cinemeta;
- multiple simultaneous torrents;
- manual file selection;
- subtitle selection;
- persistence/history;
- background downloading;
- seeding controls beyond what is needed to terminate the proof session cleanly;
- production-grade retry/recovery UX.

## Torrent engine

Use `org.libtorrent4j`, not the legacy `com.frostwire:jlibtorrent` Maven artifacts.

Pin the proof to `org.libtorrent4j` version `2.1.0-38`. Version `2.1.0-39` is newer but raises the minimum Android API to 28; the application currently has `minSdk = 26`. The 2.1.0-38 line keeps the existing application minimum while using the actively maintained libtorrent 2.x wrapper.

Include the Android native artifacts needed by the application ABIs. For the proof, packaging all supported Android ABIs is acceptable; optimization/splits are out of scope.

Relevant upstream behavior:

- libtorrent piece priorities range from 0 to 7;
- priority 0 means do not download;
- higher piece priorities are picked ahead of lower ones;
- piece priorities can be changed after magnet metadata arrives;
- `set_piece_deadline` is specifically intended for time-critical pieces and is preferable to global sequential-download mode for streaming;
- file priorities and piece priorities must be applied in the correct order because changing file priorities resets affected piece priorities.

References:

- https://libtorrent.org/reference-Torrent_Handle.html
- https://libtorrent.org/streaming.html
- https://github.com/aldenml/libtorrent4j

## Runtime input and launch flow

`deploy-debug` will accept the magnet as its first argument and an optional ADB target as its second argument:

```text
./deploy-debug '<magnet>'
./deploy-debug '<magnet>' 192.168.0.200:5555
```

The script will:

1. run `./gradlew assembleDebug`;
2. install the debug APK;
3. force-stop the application;
4. launch `MainActivity` explicitly with an intent extra named `magnet`.

`MainActivity` will use the presence of the `magnet` extra to enter proof-playback mode. With no magnet extra, the existing search screen remains available.

## Components

### `LibtorrentSession`

Owns one `SessionManager`/libtorrent session for the proof.

Responsibilities:

- start and stop libtorrent;
- add the runtime magnet to a per-session cache directory;
- wait for metadata;
- expose torrent status updates;
- release the torrent/session on teardown.

The proof uses one active torrent only. Starting another proof session replaces the old one.

### `TorrentFileSelector`

After metadata arrives, inspect the torrent file list and select the largest supported video file.

Supported extensions for the proof:

- `.mkv`
- `.mp4`
- `.m4v`
- `.webm`
- `.ts`

All non-selected files get file priority 0. The selected file gets a normal non-zero priority before streaming-specific piece priorities are applied.

If no supported video exists, preparation fails with a clear error.

### `PieceMapper`

Maps a byte range inside the selected file to torrent piece indices using:

- the selected file's offset within torrent storage;
- its byte length;
- torrent piece length;
- the final shortened piece where applicable.

This mapping is pure logic and must be unit tested independently of libtorrent.

### `StreamingPriorityPlanner`

Controls which pieces libtorrent should request first.

Do **not** enable global `sequential_download`; libtorrent documents its deadline/time-critical mechanism as better suited to streaming.

The planner has three concerns.

#### Bootstrap

Before Media3 starts reading, prioritize container metadata from both ends of the selected file:

- first 8 MiB: priority 7;
- last 4 MiB: priority 7.

This avoids assuming all container indexes live at the beginning. MKV/MP4 probing may require data near the end.

Immediate bootstrap pieces also receive increasing piece deadlines so libtorrent treats them as time-critical.

The player may prepare as soon as the pieces actually requested by the Media3 extractor are available; it does not wait for the complete bootstrap windows if the extractor does not need them.

#### Active playback

Whenever Media3 opens or reopens the source at byte position `P`:

- clear obsolete piece deadlines from the previous read position;
- mark the current piece and a small immediate window as priority 7 with near-term deadlines;
- mark roughly the next 48 MiB as readahead priority 6;
- keep the rest of the selected file at low normal priority 1;
- keep unrelated files at priority 0.

The exact piece counts are derived from torrent piece size, not hard-coded.

#### Seek

A Media3 seek results in a new `DataSource.open(DataSpec)` with a new byte position. That new position immediately becomes the priority anchor.

Old time-critical deadlines are cleared and the current/readahead windows are moved to the seek target. This is the behavior the proof must demonstrate: seeking far forward must not require downloading all intervening bytes.

### `TorrentPieceAccess`

Provides the synchronous bridge required by Media3's `DataSource`.

Responsibilities:

- report selected file length/name;
- reprioritize around a requested byte position;
- block a reader until the torrent piece containing the requested bytes has completed and passed libtorrent verification;
- read verified bytes from the partially downloaded file using random access;
- wake/cancel blocked reads when the source is closed.

Media3 reads occur on its loading thread, so waiting here must not block the Android main thread.

A read must never expose bytes from an incomplete/unverified piece merely because the backing file already exists on disk.

### `TorrentDataSource`

Lives in the player/Media3 adapter layer and implements a custom Media3 `DataSource` over `TorrentPieceAccess`.

`open(DataSpec)`:

- validates `DataSpec.position` against selected file length;
- sets the current read cursor;
- triggers seek/start prioritization for that position;
- returns the remaining/requested resource length.

`read(...)`:

- asks `TorrentPieceAccess` to make the current range readable;
- reads only verified available bytes;
- advances the cursor;
- returns end-of-input only at the selected file boundary.

`close()` cancels that reader without tearing down the whole torrent session; Media3 may close/reopen a source during seeking.

### Media3 integration

The current `Media3PlayerPort` creates a `MediaItem` from a URI. For torrent playback it will instead create a `ProgressiveMediaSource` using `TorrentDataSource.Factory` and pass that media source to ExoPlayer.

The torrent layer itself should not depend on ExoPlayer. Media3-specific adaptation stays in the `player` package.

Reference:

- https://developer.android.com/reference/androidx/media3/exoplayer/source/ProgressiveMediaSource.Factory

### Proof UI

Add a minimal torrent playback screen, not a polished product screen.

It shows:

- selected filename;
- state: fetching metadata / buffering / playing / error;
- peers where available;
- current download speed;
- downloaded bytes versus selected-file size;
- video through Media3 `PlayerView`.

TV controls for the proof:

- center/play-pause: toggle playback;
- left: seek backward 30 seconds;
- right: seek forward 30 seconds.

The torrent status display is important because it proves that playback and seeking work while the file is still incomplete.

## Data flow

```text
runtime magnet
    |
    v
LibtorrentSession
    |
    | metadata
    v
TorrentFileSelector
    |
    v
selected torrent file
    |
    +--> StreamingPriorityPlanner
    |       ^
    |       | current byte position / seek
    |       |
    +--> TorrentPieceAccess <--> TorrentDataSource <--> Media3/ExoPlayer
```

## Storage and lifecycle

Use an app-private cache directory for proof torrent data. No storage permission is required.

The torrent engine must not be owned by a `Composable`. Ownership sits at the activity/application composition root and is released explicitly.

On activity destruction:

1. stop/close Media3 readers;
2. release ExoPlayer;
3. remove/stop the active torrent;
4. stop the libtorrent session.

Partial data may remain in `cacheDir` until the next proof session or OS cache cleanup. Production cache policy is out of scope.

## Error handling

Expose distinct proof errors for:

- missing/invalid magnet;
- metadata acquisition failure/timeout;
- no supported video file;
- torrent engine/native library startup failure;
- selected-file read failure;
- Media3 playback error.

A metadata timeout should fail rather than leave the screen indefinitely in an ambiguous state. Use a 60-second proof timeout; this is not a production policy.

## Testing

### Unit tests

Test without network access:

- main-video-file selection chooses the largest supported video;
- unsupported/non-video files are ignored;
- byte-range to piece-index mapping, including unaligned file starts and final pieces;
- bootstrap head/tail piece ranges;
- active playback priority windows;
- seeking moves the time-critical/readahead windows and clears obsolete deadlines;
- `TorrentDataSource` position/length/read behavior using a fake `TorrentPieceAccess`;
- closing a data source cancels a blocked fake read.

### Build/instrumentation gate

CI continues to run:

```text
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug :app:assembleAndroidTest
```

The build itself verifies that the chosen libtorrent4j Android artifacts package correctly with the Android application.

### Manual acceptance on the Android TV

Using the runtime magnet supplied by the user:

1. `./deploy-debug '<magnet>'` launches proof mode;
2. the app obtains magnet metadata and shows the selected video filename;
3. playback begins while downloaded bytes are still substantially below the complete file size;
4. seeking forward causes buffering only for the new target region rather than downloading all intervening content;
5. playback resumes after the target pieces arrive;
6. logs/status make the change in priority anchor observable;
7. exiting the app stops the player and torrent session cleanly.

## Success criterion

The proof succeeds when the supplied magnet can be played on the target Android TV before full download, and a forward seek can resume by prioritizing pieces around the seek target instead of requiring sequential completion of the file.
