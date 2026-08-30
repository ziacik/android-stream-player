# Bounded torrent storage for Philips Android TV

## Problem

The current `SessionManager.download()` call uses libtorrent's default file storage.
For a selected film it creates a sparse MP4 whose logical length is the complete
film. Philips' Internal Memory Optimizer counts that logical length as application
storage even when only a few megabytes of blocks are allocated. It consequently
reports Android Stream Player as using more than 1 GB and drives the TV below its
1 GiB free-space threshold. The same torrent work is associated with long UI
frames on the TPM191E.

## Goal

Play a torrent on the TV without creating a file whose logical size is the
selected video length. Torrent cache must be bounded to 128 MiB of real `/data`
storage, must be removed when playback stops, and Media3 must continue to receive
byte-range reads for the selected file.

## Chosen architecture

Replace the prebuilt `libtorrent4j` Android runtime with an NDK-backed torrent
adapter that installs a custom libtorrent storage backend. The backend stores
verified pieces in fixed-size cache slots rather than in a file laid out like the
torrent. It exposes reads by torrent piece and byte offset to Kotlin through a
small JNI surface.

`TorrentDataSource` remains the Media3 boundary. It maps file offsets to pieces,
requests a moving playback window, and reads through the JNI piece store. It does
not open a `RandomAccessFile` and does not receive a path to a downloaded movie.

### Data flow

1. Kotlin resolves metadata and selects one video file as today.
2. Kotlin starts the native torrent handle with all ordinary files and pieces
   ignored.
3. `StreamingPriorityPlanner` assigns top priority to the current 4 MiB and
   readahead priority to the following 48 MiB. Reprioritization occurs while
   reading, not solely in `TorrentDataSource.open()`.
4. The native storage backend receives verified pieces and writes them to a
   fixed-slot 128 MiB cache. Slots outside the active window are evicted and their
   pieces are made unavailable to libtorrent so they can be downloaded again if a
   seek needs them.
5. `TorrentDataSource.read()` waits for the relevant verified piece and copies
   only the requested bytes to Media3.
6. Stopping playback removes the torrent handle and cache slots. No file has the
   logical size of the video.

## Native boundary

The Kotlin-facing interface owns the following operations:

- start a torrent with selected-file metadata;
- update the active playback window and piece priorities;
- block-cancellable read of verified bytes at a torrent offset;
- report a piece becoming available and current cache usage;
- stop a torrent and delete its fixed-slot cache.

The JNI layer is the sole owner of libtorrent handles and storage callbacks. It
serializes native calls on one worker thread and never invokes Kotlin callbacks on
the Android main thread.

## Error handling and lifecycle

- A missing piece blocks the Media3 loader thread, never the main thread.
- A cancelled data source unblocks the pending read with an I/O cancellation.
- Metadata timeout, no peers, or native-storage errors become an existing
  user-visible stream failure.
- Cache allocation is capped at 128 MiB; an allocation failure fails playback
  rather than consuming more storage.
- `onStop` pauses playback; `onDestroy` stops the native torrent and removes its
  cache deterministically.

## Verification

- Unit tests cover moving-window reprioritization, fixed-slot eviction, mapping
  between selected-file offsets and torrent pieces, and cancellation.
- Native integration tests verify a written piece is returned byte-for-byte and
  evicted pieces are no longer reported as present.
- Device smoke test starts the supplied magnet, confirms Media3 reaches playback,
  and checks `stat`/`du` under the app cache: no single file may have a logical
  size near the video length and total allocated cache must remain at or below
  128 MiB.
- On the Philips optimizer, Android Stream Player must no longer be attributed
  with the selected film's full length. Logcat and `gfxinfo` are recorded to
  distinguish build/startup evidence from observed playback smoothness.

## Non-goals

- No server-side torrent service.
- No change to search UI, account data, or unrelated app storage.
- No claim that code-side verification alone proves physical playback is smooth;
  that requires TV observation after deployment.
