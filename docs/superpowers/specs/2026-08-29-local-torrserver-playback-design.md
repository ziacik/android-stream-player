# Local TorrServer Playback Backend Design

## Goal

Replace the current in-process libtorrent4j proof backend with a local TorrServer process running on the same Android TV. The proof must stream the supplied runtime magnet to Media3 without creating a torrent-sized sparse video file on Android storage.

The existing search/controller UI may remain. The backend boundary changes from libtorrent4j piece reads to a localhost HTTP stream.

## Scope

This proof supports one active torrent at a time and the current single-file runtime magnet. The selected file index is therefore `0` for this spike. Multi-file torrent inspection/selection is explicitly deferred.

No remote TorrServer is required. No TorrServer binary is downloaded at app runtime. No magnet is committed to git.

## Why TorrServer

TorrServer is designed around bounded torrent streaming cache rather than a normal full-size destination file. Its current defaults use a 64 MiB cache and `UseDisk=false`, so torrent payload remains in bounded RAM cache instead of creating the large sparse `.mkv` that caused problems on the TV.

Pin the server to release `MatriX.143` (2026-08-17). For the Android TV proof support these ABIs:

| Android ABI | Release asset | SHA-256 |
| --- | --- | --- |
| `arm64-v8a` | `TorrServer-android-arm64` | `23cea145c38e948f1a967c7fdbcb9c71506cd21a2fe7b3723903e233a323465b` |
| `armeabi-v7a` | `TorrServer-android-arm7` | `9bab078a0976b86ff392c9eee756194643f4e939ee2c9504dfd4ab7094ef9490` |

Other ABIs fail the proof build with a clear unsupported-ABI error instead of silently packaging the wrong binary.

## Build-time binary packaging

Android 10+ does not allow executing a newly downloaded binary from normal writable app storage. The TorrServer executable will therefore be downloaded at **build time**, renamed to `libtorrserver.so`, and packaged under the Android native-library path for the chosen ABI.

A Gradle task reads `-PtorrserverAbi=<abi>`, defaulting to `arm64-v8a` for CI. It downloads the pinned GitHub release asset into a generated jniLibs directory, verifies the pinned SHA-256, and exposes it to the Android source set as:

```text
build/generated/torrserver/jniLibs/<abi>/libtorrserver.so
```

Native-lib legacy extraction must be enabled so PackageManager installs a real executable file under `applicationInfo.nativeLibraryDir`. The TorrServer binary must be excluded from native symbol stripping because it is an executable renamed as `.so`, not a shared library intended for `System.loadLibrary`.

`deploy-debug` queries the target TV first:

```text
adb ... shell getprop ro.product.cpu.abi
```

and invokes Gradle with that ABI. This keeps the debug APK to one TorrServer binary rather than packaging both ARM builds.

CI builds the default arm64 proof and verifies that the final APK contains `lib/arm64-v8a/libtorrserver.so`.

## Runtime process lifecycle

Create `TorrServerProcess` as the owner of the child process.

On `start()` (IO dispatcher):

1. Resolve `<applicationInfo.nativeLibraryDir>/libtorrserver.so` and fail clearly if missing.
2. Create an app-private settings/database directory, e.g. `<noBackupFilesDir>/torrserver`.
3. Launch:

```text
libtorrserver.so --ip 127.0.0.1 --port 18090 --path <data-dir>
```

4. Continuously drain merged stdout/stderr into Android Log so the child cannot block on a full pipe and `adb logcat` contains TorrServer diagnostics.
5. Poll `GET http://127.0.0.1:18090/echo` until healthy, with a finite startup timeout (10 seconds).
6. Read current settings through `/settings` and assert for the proof that `UseDisk=false`. The fresh app-private config uses TorrServer defaults (64 MiB RAM cache). Do not enable a disk torrent path.

The HTTP server is explicitly bound to `127.0.0.1`; it is not exposed on the LAN.

On `stop()` (IO dispatcher):

1. Request `GET /shutdown` with a short timeout.
2. Wait briefly for graceful exit.
3. Call `destroy()`, then `destroyForcibly()` if needed.
4. Stop the log-draining coroutine/thread.

The process is activity-owned for this proof. No foreground service is introduced yet. `MainActivity.onDestroy()` must stop Media3 first and then stop TorrServer off the main thread.

## Local HTTP policy

Media3 reads a plain HTTP URL on `127.0.0.1`. Because the app targets SDK 36, cleartext HTTP must be explicitly allowed for this proof. Set `android:usesCleartextTraffic="true"` on the application. The server itself remains bound to loopback only, so this does not expose TorrServer on the LAN.

This is a proof-only policy; a production hardening pass can replace it with a narrower network-security configuration.

## HTTP client

Create `TorrServerClient` around the existing OkHttp dependency.

It exposes:

```kotlin
suspend fun awaitReady()
suspend fun assertRamCache()
fun streamUrl(magnet: String, fileIndex: Int = 0): String
suspend fun shutdown()
```

The stream URL uses TorrServer's HTTP streaming API:

```text
http://127.0.0.1:18090/stream/video?link=<url-encoded-magnet>&index=0&play
```

The magnet must be encoded as a query parameter by `HttpUrl`/OkHttp rather than hand-concatenated, because magnets contain `&` tracker parameters.

## TorrentStreamer integration

Replace the proof's `LibtorrentTorrentStreamer` with `TorrServerTorrentStreamer`.

`prepare(result)` performs:

1. validate a nonblank `magnetUri`;
2. ensure the local TorrServer process is healthy;
3. build the `streamUrl` with `index=0`;
4. return a normal `TorrentSource(uri = streamUrl)`.

The existing `TorrentSource` URI path is deliberately reused. This means Media3 no longer needs the custom `TorrentDataSource` for TorrServer playback: `Media3PlayerPort.prepare(TorrentSource)` can play the localhost HTTP URL normally and Media3/TorrServer handle HTTP range/seek behavior.

The libtorrent4j-specific classes and dependencies are removed from the active runtime after the TorrServer path is working. Pure helper tests may be deleted if they only exist for the abandoned piece-storage backend.

## MainActivity flow

The current direct-magnet intent flow remains:

```text
deploy-debug '<magnet>'
  -> MainActivity intent extra
  -> SearchController direct magnet result
  -> TorrServerTorrentStreamer
  -> localhost HTTP URL
  -> Media3 PlayerView
```

No search provider call is needed for a direct magnet. The existing UI status flow (`Preparing stream…` / `Playing` / error) stays intact for the first proof.

## Error handling

Surface concise failures through the current controller error state:

- unsupported device ABI / binary absent;
- TorrServer process exits during startup;
- `/echo` startup timeout;
- TorrServer reports disk cache enabled unexpectedly;
- HTTP playback error from Media3.

The child-process log is tagged `TorrServer` in logcat so runtime failures can be diagnosed separately from Media3 logs.

## Testing

TDD seams:

1. `TorrServerClientTest`
   - builds a correctly encoded localhost stream URL for a magnet containing multiple `&tr=` parameters;
   - health polling succeeds/fails deterministically through an injected request seam.
2. `TorrServerTorrentStreamerTest`
   - starts/ensures server and returns the HTTP `TorrentSource`;
   - rejects missing magnet.
3. process command construction is a pure function and is unit-tested for `--ip 127.0.0.1`, port `18090`, and app-private data path.
4. existing direct-magnet `SearchController` tests remain green.
5. CI runs unit tests, `assembleDebug`, `assembleAndroidTest`, and checks APK contents for the packaged TorrServer executable.

Automated CI proves compilation, packaging, URL construction and orchestration. Only the physical Android TV can prove that the packaged `MatriX.143` binary executes correctly on that firmware and that a live torrent reaches Media3.

## Manual acceptance

On the TV:

```text
./deploy-debug '<runtime magnet>'
```

Acceptance criteria:

1. TorrServer starts and `/echo` returns successfully.
2. Logs show `UseDisk=false` and bounded RAM cache settings.
3. No torrent-sized sparse `.mkv` appears in app storage.
4. Media3 begins playback from localhost.
5. Seeking causes TorrServer to refill around the new position without allocating the full media file.
6. Leaving/destroying the app stops playback and the owned TorrServer process.

## Deferred

- multi-file torrent metadata and file picker;
- foreground service / playback surviving Activity recreation;
- persistent TorrServer process shared across launches;
- configurable cache size;
- remote TorrServer support;
- production distribution/GPL compliance work;
- x86/x86_64 packaging.
