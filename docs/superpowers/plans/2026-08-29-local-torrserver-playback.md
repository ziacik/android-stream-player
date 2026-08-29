# Local TorrServer Playback Implementation Plan

> Execute inline on `feature/torrent-playback-proof`. Keep the runtime magnet out of git. Each behavior change follows RED → GREEN and is verified in GitHub Actions because this environment has no local Android checkout/runtime.

## Goal

Replace the libtorrent4j sparse-file playback backend with a local TorrServer `MatriX.143` process on the Android TV. TorrServer keeps torrent payload in bounded RAM cache (`UseDisk=false`) and exposes a loopback HTTP stream consumed by Media3.

## Task 1: Package the pinned TorrServer executable

**Files**
- Modify `app/build.gradle.kts`
- Modify `.github/workflows/android-ci.yml`

1. Add a Gradle property `torrserverAbi`, default `arm64-v8a`.
2. Map the two supported proof ABIs to pinned release assets and SHA-256 values:
   - `arm64-v8a` → `TorrServer-android-arm64` → `23cea145c38e948f1a967c7fdbcb9c71506cd21a2fe7b3723903e233a323465b`
   - `armeabi-v7a` → `TorrServer-android-arm7` → `9bab078a0976b86ff392c9eee756194643f4e939ee2c9504dfd4ab7094ef9490`
3. Create `prepareTorrServerBinary` that downloads the selected asset into `build/generated/torrserver/jniLibs/<abi>/libtorrserver.so`, verifies SHA-256, and reuses a correct cached output.
4. Add the generated directory to `main` jniLibs, use legacy JNI packaging, and keep debug symbols for `libtorrserver.so` so AGP does not attempt to strip a renamed executable.
5. Make Android build preparation depend on `prepareTorrServerBinary`.
6. CI builds the default arm64 variant and verifies APK contents contain `lib/arm64-v8a/libtorrserver.so`.
7. Run CI. Fix only packaging/config failures until unit tests + debug APK + androidTest packaging are green.
8. Commit: `build: package local TorrServer binary`.

## Task 2: TorrServer control client

**Files**
- Create `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/TorrServerClient.kt`
- Create `app/src/test/java/sk/ziacik/androidstreamplayer/torrent/TorrServerClientTest.kt`

1. RED: test `streamUrl()` for a magnet containing multiple `&tr=` parameters. Assert localhost host/port, path `/stream/video`, `index=1`, presence of `play`, and decoded `link` exactly equals the original magnet.
2. RED: test RAM settings parsing accepts `{"UseDisk":false,"CacheSize":67108864}` and rejects `UseDisk=true`.
3. RED: test readiness retries failed `/echo` requests then succeeds; separately test finite timeout.
4. Add a small injected HTTP transport seam so JVM tests never need a real TorrServer.
5. GREEN: production OkHttp transport performs synchronous calls on `Dispatchers.IO`.
6. `/settings` is POSTed with JSON `{"action":"get"}`; `assertRamCache()` requires `UseDisk=false` and logs cache size.
7. `/shutdown` uses GET and tolerates the child disappearing during shutdown.
8. Run `:app:testDebugUnitTest` and commit: `feat: add TorrServer control client`.

## Task 3: Local process lifecycle

**Files**
- Create `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/TorrServerProcess.kt`
- Create `app/src/test/java/sk/ziacik/androidstreamplayer/torrent/TorrServerProcessTest.kt`

1. RED: pure command-builder test requires binary absolute path followed by `--ip 127.0.0.1 --port 18090 --path <private-dir>`.
2. RED: lifecycle seam verifies repeated `ensureStarted()` does not launch a second process when already healthy.
3. GREEN: resolve packaged binary from `applicationInfo.nativeLibraryDir`, data directory from `noBackupFilesDir/torrserver`, set `GODEBUG=madvdontneed=1`, `redirectErrorStream(true)`, and start with `ProcessBuilder` on IO.
4. Drain stdout/stderr continuously to Android log tag `TorrServer`.
5. After launch call `client.awaitReady()` then `client.assertRamCache()`; if either fails, terminate child and rethrow.
6. `stop()` requests `/shutdown`, waits briefly, then `destroy()` / `destroyForcibly()` as necessary and stops log draining.
7. Run unit tests and compile APK; commit: `feat: manage local TorrServer process`.

## Task 4: TorrentStreamer adapter

**Files**
- Create `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/TorrServerRuntime.kt`
- Create `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/TorrServerTorrentStreamer.kt`
- Create `app/src/test/java/sk/ziacik/androidstreamplayer/torrent/TorrServerTorrentStreamerTest.kt`

1. Define a narrow runtime seam exposing `ensureReady()`, `streamUrl(magnet, fileIndex=1)`, and `stop()`.
2. RED: valid magnet causes runtime readiness and returns `TorrentSource` with the runtime's localhost stream URI.
3. RED: blank/non-magnet input fails before starting the process.
4. GREEN: implement adapter using file index `1` for this single-file proof.
5. Run tests and commit: `feat: stream torrents through local TorrServer`.

## Task 5: Wire Media3, Activity, and localhost HTTP policy

**Files**
- Modify `app/src/main/AndroidManifest.xml`
- Modify `app/src/main/java/sk/ziacik/androidstreamplayer/MainActivity.kt`
- Modify `app/src/main/java/sk/ziacik/androidstreamplayer/player/Media3PlayerPort.kt`
- Modify `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/TorrentSource.kt`

1. Add `android:usesCleartextTraffic="true"` for the localhost proof.
2. Build `TorrServerClient`, `TorrServerProcess`, runtime, and `TorrServerTorrentStreamer` in `MainActivity` instead of `SessionManager`/libtorrent4j.
3. Keep the existing direct-magnet intent flow unchanged.
4. On destroy release Media3 first, cancel UI work, then stop TorrServer on IO.
5. Simplify `TorrentSource` to a URI-only source and `Media3PlayerPort.prepare()` to normal HTTP `MediaItem` playback.
6. Run all search/controller tests plus APK/androidTest compilation; commit: `feat: wire local TorrServer playback runtime`.

## Task 6: Remove the abandoned libtorrent4j backend

**Files**
- Modify `app/build.gradle.kts`
- Modify `gradle/libs.versions.toml`
- Delete libtorrent-specific production helpers and tests
- Delete `player/TorrentDataSource.kt` and its test

Delete production files no longer referenced:
- `LibtorrentSessionBackend.kt`
- `LibtorrentTorrentPieceBackend.kt`
- `LibtorrentTorrentStreamer.kt`
- `PieceMapper.kt`
- `StreamingPriorityPlanner.kt`
- `StreamingTorrentPieceAccess.kt`
- `TorrentFileEntry.kt`
- `TorrentFileSelector.kt`
- `TorrentMetadata.kt`
- `TorrentPieceAccess.kt`
- `TorrentPieceBackend.kt`
- `TorrentSessionBackend.kt`
- `player/TorrentDataSource.kt`

Delete their old tests and remove all `libtorrent4j` version-catalog/dependency entries.

Run full unit suite + APK/androidTest compile. Verify APK no longer contains libtorrent4j native libraries. Commit: `refactor: remove libtorrent4j playback backend`.

## Task 7: ABI-aware deploy helper

**Files**
- Modify `deploy-debug`

1. Before Gradle build, query `adb -s "$target" shell getprop ro.product.cpu.abi` and strip CR.
2. Accept only `arm64-v8a` and `armeabi-v7a`; print a clear unsupported ABI error otherwise.
3. Build with `./gradlew assembleDebug -PtorrserverAbi="$device_abi"`.
4. Preserve existing safe magnet quoting and executable mode `100755`.
5. Commit: `build: select TorrServer ABI from Android TV`.

## Task 8: Final verification

1. Fresh CI on final feature head:
   - `:app:testDebugUnitTest`
   - `:app:assembleDebug`
   - `:app:assembleAndroidTest`
   - APK contains `lib/arm64-v8a/libtorrserver.so`
   - APK contains no libtorrent4j native libraries.
2. Search tracked source for the supplied release title/info hash; expected zero matches.
3. Verify `deploy-debug` remains mode `100755`.
4. Do not claim live torrent playback is verified until it runs on the physical Android TV.
5. Manual acceptance command:

```bash
./deploy-debug '<runtime magnet>'
```

Useful live diagnostics:

```bash
adb -s 192.168.0.200:5555 logcat | grep -iE 'TorrServer|androidstreamplayer|Media3|ExoPlayer'
```

Manual acceptance is: local server starts, reports `UseDisk=false`, playback begins, seek works, no torrent-sized sparse media file appears, and destroying the app stops the owned process.
