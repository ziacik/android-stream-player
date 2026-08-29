# Torrent Playback Proof Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Play the runtime-supplied magnet on Android TV before full download, and make Media3 seeks move libtorrent piece priorities/deadlines to the new byte region.

**Architecture:** Keep BitTorrent ownership in the `torrent` package and Media3 adaptation in `player`. `LibtorrentSession` owns `SessionManager` and the active torrent, pure helpers select/map/prioritize pieces, `TorrentPieceAccess` exposes verified random-access bytes, and `TorrentDataSource` bridges that access into `ProgressiveMediaSource`. `MainActivity` switches into a minimal proof UI only when the `magnet` intent extra is present; existing fake search remains unchanged otherwise.

**Tech Stack:** Kotlin, Android API 26+, Media3 1.10.1, Kotlin coroutines 1.10.2, JUnit 4, `org.libtorrent4j` 2.1.0-38 Android native artifacts.

**Spec:** `docs/superpowers/specs/2026-08-29-torrent-playback-proof-design.md`

## Global Constraints

- Keep `minSdk = 26`.
- Pin libtorrent4j to `2.1.0-38`; do not use `2.1.0-39` because it requires Android API 28.
- The supplied magnet is runtime input only and must not be committed anywhere.
- One active torrent and one automatically selected video file only.
- Do not add Torrentio, Cinemeta, torrent search, history, persistence, subtitle selection, or background downloading.
- Do not use global sequential-download mode for streaming; use piece priorities plus piece deadlines.
- Apply file priorities before special piece priorities because libtorrent resets piece priorities when file priorities change.
- Only expose bytes from pieces for which `TorrentHandle.havePiece(piece)` is true.
- Use app-private cache storage; no storage permission.
- CI gate remains `./gradlew :app:testDebugUnitTest` plus `./gradlew :app:assembleDebug :app:assembleAndroidTest`.

---

## File Structure

Create or modify the following focused units:

- `gradle/libs.versions.toml` — libtorrent4j version/catalog aliases.
- `app/build.gradle.kts` — Android native libtorrent dependencies.
- `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/TorrentFileEntry.kt` — engine-neutral file metadata.
- `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/TorrentFileSelector.kt` — choose the main video file.
- `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/PieceMapper.kt` — byte-range ↔ piece mapping.
- `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/StreamingPriorityPlanner.kt` — pure priority/deadline plans.
- `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/TorrentPieceAccess.kt` — engine-neutral verified random access contract.
- `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/LibtorrentPieceAccess.kt` — production adapter over `TorrentHandle` + `RandomAccessFile`.
- `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/LibtorrentSession.kt` — session/magnet/metadata/file-selection lifecycle.
- `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/TorrentPlaybackSource.kt` — prepared selected-file metadata plus `TorrentPieceAccess`.
- `app/src/main/java/sk/ziacik/androidstreamplayer/player/TorrentDataSource.kt` — custom Media3 `DataSource`.
- `app/src/main/java/sk/ziacik/androidstreamplayer/player/Media3PlayerPort.kt` — add progressive torrent source preparation.
- `app/src/main/java/sk/ziacik/androidstreamplayer/playback/TorrentPlaybackController.kt` — proof orchestration/state.
- `app/src/main/java/sk/ziacik/androidstreamplayer/playback/TorrentPlaybackUiState.kt` — proof UI state.
- `app/src/main/java/sk/ziacik/androidstreamplayer/ui/TorrentPlaybackScreen.kt` — minimal TV proof UI + `PlayerView`.
- `app/src/main/java/sk/ziacik/androidstreamplayer/MainActivity.kt` — runtime magnet mode and lifecycle ownership.
- `deploy-debug` — pass magnet extra and optional ADB target.
- Unit tests under matching `app/src/test/...` paths; one instrumentation smoke test under `app/src/androidTest/...`.

---

### Task 1: Add libtorrent4j Android dependencies and prove packaging

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: existing Android application module, `minSdk = 26`.
- Produces: compile-time access to `org.libtorrent4j.SessionManager`, `TorrentHandle`, `TorrentInfo`, `Priority` and packaged Android JNI libraries.

- [ ] **Step 1: Add version/catalog entries**

Add:

```toml
[versions]
libtorrent4j = "2.1.0-38"

[libraries]
libtorrent4j-android-arm = { module = "org.libtorrent4j:libtorrent4j-android-arm", version.ref = "libtorrent4j" }
libtorrent4j-android-arm64 = { module = "org.libtorrent4j:libtorrent4j-android-arm64", version.ref = "libtorrent4j" }
libtorrent4j-android-x86 = { module = "org.libtorrent4j:libtorrent4j-android-x86", version.ref = "libtorrent4j" }
libtorrent4j-android-x86-64 = { module = "org.libtorrent4j:libtorrent4j-android-x86_64", version.ref = "libtorrent4j" }
```

- [ ] **Step 2: Add all four Android artifacts to `app/build.gradle.kts`**

```kotlin
implementation(libs.libtorrent4j.android.arm)
implementation(libs.libtorrent4j.android.arm64)
implementation(libs.libtorrent4j.android.x86)
implementation(libs.libtorrent4j.android.x86.x64)
```

- [ ] **Step 3: Run a packaging build**

Run:

```text
./gradlew :app:assembleDebug --stacktrace
```

Expected: PASS. If dependency packaging itself fails because multiple architecture jars collide, keep the core API transitively from one artifact and move the native architecture jars to ABI-specific packaging rather than raising `minSdk` or changing version.

- [ ] **Step 4: Commit**

```text
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add libtorrent4j android runtime"
```

---

### Task 2: Main-video selection and piece mapping

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/TorrentFileEntry.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/TorrentFileSelector.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/PieceMapper.kt`
- Test: `app/src/test/java/sk/ziacik/androidstreamplayer/torrent/TorrentFileSelectorTest.kt`
- Test: `app/src/test/java/sk/ziacik/androidstreamplayer/torrent/PieceMapperTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class TorrentFileEntry(
    val index: Int,
    val path: String,
    val sizeBytes: Long,
    val torrentOffsetBytes: Long,
)

object TorrentFileSelector {
    fun selectMainVideo(files: List<TorrentFileEntry>): TorrentFileEntry?
}

data class PieceRange(val first: Int, val lastInclusive: Int)

class PieceMapper(
    private val fileOffsetBytes: Long,
    val fileSizeBytes: Long,
    val pieceLengthBytes: Int,
    private val torrentPieceCount: Int,
) {
    fun pieceForFileOffset(fileOffsetBytes: Long): Int
    fun piecesForFileRange(fileOffsetBytes: Long, lengthBytes: Long): PieceRange
    fun bytesUntilPieceEnd(fileOffsetBytes: Long): Int
}
```

- [ ] **Step 1: Write failing selector tests**

Cover largest supported video, case-insensitive extensions, and ignoring unsupported files:

```kotlin
@Test
fun selectsLargestSupportedVideo() {
    val files = listOf(
        TorrentFileEntry(0, "sample.txt", 9_000_000, 0),
        TorrentFileEntry(1, "episode.mkv", 8_000_000, 9_000_000),
        TorrentFileEntry(2, "trailer.mp4", 1_000_000, 17_000_000),
    )
    assertEquals(1, TorrentFileSelector.selectMainVideo(files)?.index)
}

@Test
fun returnsNullWithoutSupportedVideo() {
    assertNull(TorrentFileSelector.selectMainVideo(listOf(TorrentFileEntry(0, "readme.nfo", 100, 0))))
}
```

- [ ] **Step 2: Run selector tests and verify RED**

```text
./gradlew :app:testDebugUnitTest --tests '*TorrentFileSelectorTest' --stacktrace
```

Expected: FAIL because production types do not exist.

- [ ] **Step 3: Implement minimal selector**

Supported suffixes exactly: `.mkv`, `.mp4`, `.m4v`, `.webm`, `.ts`; return `maxByOrNull { sizeBytes }` among matches.

- [ ] **Step 4: Write failing mapping tests**

Use a deliberately unaligned file start:

```kotlin
private val mapper = PieceMapper(
    fileOffsetBytes = 300L,
    fileSizeBytes = 3_000L,
    pieceLengthBytes = 1_024,
    torrentPieceCount = 10,
)

@Test fun mapsStartToContainingTorrentPiece() {
    assertEquals(0, mapper.pieceForFileOffset(0))
    assertEquals(1, mapper.pieceForFileOffset(724))
}

@Test fun mapsRangeAcrossPieces() {
    assertEquals(PieceRange(0, 2), mapper.piecesForFileRange(0, 2_000))
}

@Test fun limitsReadsAtPieceBoundary() {
    assertEquals(724, mapper.bytesUntilPieceEnd(0))
}
```

Also test exact file end and reject positions `< 0` or `> fileSizeBytes`.

- [ ] **Step 5: Run mapping tests and verify RED**

```text
./gradlew :app:testDebugUnitTest --tests '*PieceMapperTest' --stacktrace
```

- [ ] **Step 6: Implement mapping using global torrent offsets**

Core formula:

```kotlin
val torrentOffset = this.fileOffsetBytes + fileOffsetBytes
val piece = (torrentOffset / pieceLengthBytes).toInt()
```

For non-empty ranges calculate the last piece from `torrentOffset + lengthBytes - 1`; clamp only to the valid torrent/file boundary, never silently accept an out-of-range request.

- [ ] **Step 7: Run both test classes**

```text
./gradlew :app:testDebugUnitTest --tests '*TorrentFileSelectorTest' --tests '*PieceMapperTest' --stacktrace
```

Expected: PASS.

- [ ] **Step 8: Commit**

```text
git add app/src/main/java/sk/ziacik/androidstreamplayer/torrent app/src/test/java/sk/ziacik/androidstreamplayer/torrent
git commit -m "feat: map torrent video files to pieces"
```

---

### Task 3: Pure streaming priority planner

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/StreamingPriorityPlanner.kt`
- Test: `app/src/test/java/sk/ziacik/androidstreamplayer/torrent/StreamingPriorityPlannerTest.kt`

**Interfaces:**
- Consumes: `PieceMapper`.
- Produces:

```kotlin
data class PieceDeadline(val piece: Int, val deadlineMs: Int)

data class StreamingPriorityPlan(
    val topPriorityPieces: IntRange,
    val readaheadPieces: IntRange,
    val deadlines: List<PieceDeadline>,
)

class StreamingPriorityPlanner(
    private val mapper: PieceMapper,
    private val immediateBytes: Long = 4L * 1024 * 1024,
    private val readaheadBytes: Long = 48L * 1024 * 1024,
) {
    fun bootstrapRanges(): List<IntRange>
    fun plan(positionBytes: Long): StreamingPriorityPlan
}
```

- [ ] **Step 1: Write failing bootstrap tests**

Verify the head 8 MiB and tail 4 MiB map to piece ranges and overlap safely for small files.

- [ ] **Step 2: Write failing active-position tests**

For a 1 MiB piece size and a position around 100 MiB, verify:

```kotlin
assertEquals(100..103, plan.topPriorityPieces)
assertEquals(104..151, plan.readaheadPieces)
assertTrue(plan.deadlines.first().deadlineMs < plan.deadlines.last().deadlineMs)
```

Use a mapper whose selected file begins on a piece boundary for this readable expectation. Add a second test where position is near EOF and ranges clamp to the selected file's last piece.

- [ ] **Step 3: Run tests and verify RED**

```text
./gradlew :app:testDebugUnitTest --tests '*StreamingPriorityPlannerTest' --stacktrace
```

- [ ] **Step 4: Implement planner**

`bootstrapRanges()` maps `[0, min(8 MiB,fileSize))` and `[max(0,fileSize-4 MiB), fileSize)`.

`plan(positionBytes)` maps:

```text
immediate = [position, position + 4 MiB)
readahead = [end(immediate), position + 4 MiB + 48 MiB)
```

Generate deadlines only for immediate pieces, e.g. `250 + ordinal * 250` ms. Deadlines are relative libtorrent deadlines, not wall-clock timestamps.

- [ ] **Step 5: Run tests and commit**

```text
./gradlew :app:testDebugUnitTest --tests '*StreamingPriorityPlannerTest' --stacktrace
git add app/src/main/java/sk/ziacik/androidstreamplayer/torrent/StreamingPriorityPlanner.kt app/src/test/java/sk/ziacik/androidstreamplayer/torrent/StreamingPriorityPlannerTest.kt
git commit -m "feat: plan streaming torrent priorities"
```

---

### Task 4: TorrentPieceAccess contract and Media3 DataSource

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/TorrentPieceAccess.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/player/TorrentDataSource.kt`
- Test: `app/src/test/java/sk/ziacik/androidstreamplayer/player/TorrentDataSourceTest.kt`

**Interfaces:**
- Produces:

```kotlin
interface TorrentPieceAccess {
    val fileName: String
    val fileLength: Long
    fun reprioritize(positionBytes: Long)
    @Throws(IOException::class)
    fun readVerified(positionBytes: Long, buffer: ByteArray, offset: Int, length: Int): Int
    fun cancelReader()
}
```

`TorrentDataSource` extends `BaseDataSource(false)` and has:

```kotlin
class TorrentDataSource(private val access: TorrentPieceAccess) : BaseDataSource(false) {
    class Factory(private val access: TorrentPieceAccess) : DataSource.Factory {
        override fun createDataSource(): DataSource = TorrentDataSource(access)
    }
}
```

Use a synthetic URI such as `torrent://selected-file`; no network/localhost server.

- [ ] **Step 1: Write fake access and failing `open` tests**

Verify `open(DataSpec(position = 100, length = C.LENGTH_UNSET))` returns `fileLength - 100` and calls `reprioritize(100)`.

Verify position equal to EOF opens and then immediately returns `C.RESULT_END_OF_INPUT`; position greater than EOF throws `DataSourceException` with `PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE` as the cause/code path supported by Media3 1.10.1.

- [ ] **Step 2: Write failing read/close tests**

Fake `readVerified` should expose known bytes and record positions. Assert cursor advancement and that `close()` calls `cancelReader()` but does not destroy the torrent session.

- [ ] **Step 3: Run test and verify RED**

```text
./gradlew :app:testDebugUnitTest --tests '*TorrentDataSourceTest' --stacktrace
```

- [ ] **Step 4: Implement `TorrentDataSource`**

Track `uri`, `readPosition`, `bytesRemaining`, `opened`, and call `transferInitializing`, `transferStarted`, `bytesTransferred`, `transferEnded` consistently. Bound each read by `bytesRemaining` when known.

- [ ] **Step 5: Run test and commit**

```text
./gradlew :app:testDebugUnitTest --tests '*TorrentDataSourceTest' --stacktrace
git add app/src/main/java/sk/ziacik/androidstreamplayer/torrent/TorrentPieceAccess.kt app/src/main/java/sk/ziacik/androidstreamplayer/player/TorrentDataSource.kt app/src/test/java/sk/ziacik/androidstreamplayer/player/TorrentDataSourceTest.kt
git commit -m "feat: expose torrent pieces as media3 data source"
```

---

### Task 5: Libtorrent adapter, metadata preparation, and verified file reads

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/TorrentPlaybackSource.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/LibtorrentPieceAccess.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/LibtorrentSession.kt`
- Test: `app/src/test/java/sk/ziacik/androidstreamplayer/torrent/LibtorrentPieceAccessTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class TorrentPlaybackSource(
    val fileName: String,
    val fileLength: Long,
    val pieceAccess: TorrentPieceAccess,
)

data class TorrentRuntimeStatus(
    val peers: Int,
    val downloadRateBytesPerSecond: Int,
    val downloadedBytes: Long,
)

class LibtorrentSession(
    private val cacheDir: File,
) : Closeable {
    suspend fun prepare(magnet: String, timeoutSeconds: Long = 60): TorrentPlaybackSource
    fun status(): TorrentRuntimeStatus?
    override fun close()
}
```

- [ ] **Step 1: Add a test seam around native handle operations**

Inside `LibtorrentPieceAccess.kt`, define a package-private interface:

```kotlin
internal interface PieceHandle {
    fun havePiece(piece: Int): Boolean
    fun clearPieceDeadlines()
    fun setPiecePriority(piece: Int, priority: Int)
    fun setPieceDeadline(piece: Int, deadlineMs: Int)
}
```

Production adapter maps integers exactly to libtorrent4j priorities:

```text
0 -> Priority.IGNORE
1 -> Priority.LOW
6 -> Priority.SIX
7 -> Priority.TOP_PRIORITY
```

and delegates to `TorrentHandle.piecePriority`, `setPieceDeadline`, `clearPieceDeadlines`, `havePiece`.

- [ ] **Step 2: Write failing reprioritization tests**

Using fake `PieceHandle`, assert `reprioritize(position)` first clears old deadlines, lowers selected-file pieces to priority 1, applies top range as 7, readahead as 6, then installs new immediate deadlines.

Do not call file priority methods from `reprioritize`; file priorities are a one-time preparation concern.

- [ ] **Step 3: Write failing verified-read tests**

Use a temporary file and fake piece availability. Test that a read never crosses into an unavailable piece: the implementation waits until the current piece is available and caps a single file read to `mapper.bytesUntilPieceEnd(position)`.

Make waiting cancellable with a monitor/condition and a short test polling interval injected into the constructor. `cancelReader()` wakes the wait and causes `IOException("Torrent read cancelled")`.

- [ ] **Step 4: Run tests and verify RED**

```text
./gradlew :app:testDebugUnitTest --tests '*LibtorrentPieceAccessTest' --stacktrace
```

- [ ] **Step 5: Implement `LibtorrentPieceAccess`**

Use `RandomAccessFile(file, "r")`. In `readVerified`, loop while `!handle.havePiece(piece)` and not cancelled, waiting on the monitor (e.g. 100 ms production interval), then seek/read only within the verified piece. `reprioritize` applies the planner atomically under a lock so concurrent Media3 opens do not interleave plans.

- [ ] **Step 6: Implement `LibtorrentSession.prepare`**

On `Dispatchers.IO`:

1. validate `magnet.startsWith("magnet:?")`;
2. create `SessionManager()` and `start()`;
3. call `fetchMagnet(magnet, timeoutSeconds.toInt(), cacheDir)` to obtain metadata bytes;
4. fail with `IOException("Torrent metadata timeout")` when null;
5. create `TorrentInfo(metadataBytes)`;
6. map `TorrentInfo.files()` entries using `filePath`, `fileSize`, `fileOffset`;
7. select the main video;
8. call `SessionManager.download(torrentInfo, cacheDir, null, priorities, null, torrent_flags_t())` with selected file `Priority.DEFAULT`, others `Priority.IGNORE`;
9. find the active handle using `SessionManager.find(torrentInfo.infoHash())`, waiting briefly for async add if necessary;
10. re-apply `handle.prioritizeFiles(...)` once the handle is valid, then build `PieceMapper`, `StreamingPriorityPlanner` and apply bootstrap piece priority/deadlines;
11. resolve selected path with `torrentInfo.files().filePath(selected.index, cacheDir.absolutePath)`;
12. return `TorrentPlaybackSource` backed by `LibtorrentPieceAccess`.

`status()` uses `handle.status(true)` and exposes `numPeers()`, `downloadRate()`, and selected-file progress from `handle.fileProgress(TorrentHandle.PIECE_GRANULARITY)[selected.index]`.

`close()` cancels active piece access, removes active torrent when valid, then calls `SessionManager.stop()` on a non-main thread/caller context.

- [ ] **Step 7: Run unit tests plus assembleDebug**

```text
./gradlew :app:testDebugUnitTest --tests '*LibtorrentPieceAccessTest' --stacktrace
./gradlew :app:assembleDebug --stacktrace
```

Expected: PASS and JNI packaging succeeds.

- [ ] **Step 8: Commit**

```text
git add app/src/main/java/sk/ziacik/androidstreamplayer/torrent app/src/test/java/sk/ziacik/androidstreamplayer/torrent
git commit -m "feat: prepare magnets with libtorrent"
```

---

### Task 6: Media3 progressive playback integration

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/player/PlayerPort.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/player/Media3PlayerPort.kt`

**Interfaces:**
- Consumes: `TorrentPlaybackSource`, `TorrentDataSource.Factory`.
- Produces:

```kotlin
fun PlayerPort.prepare(source: TorrentPlaybackSource)
```

The existing `prepare(TorrentSource)` remains for the foundation code until later cleanup.

- [ ] **Step 1: Add overloaded interface method**

```kotlin
fun prepare(source: TorrentPlaybackSource)
```

- [ ] **Step 2: Implement progressive source in `Media3PlayerPort`**

```kotlin
val mediaItem = MediaItem.fromUri("torrent:///${Uri.encode(source.fileName)}")
val mediaSource = ProgressiveMediaSource.Factory(
    TorrentDataSource.Factory(source.pieceAccess),
).createMediaSource(mediaItem)
player.setMediaSource(mediaSource)
player.prepare()
```

Annotate the implementation with Media3 `@OptIn(UnstableApi::class)` or the corresponding Kotlin opt-in required by version 1.10.1.

- [ ] **Step 3: Compile**

```text
./gradlew :app:compileDebugKotlin --stacktrace
```

Expected: PASS.

- [ ] **Step 4: Commit**

```text
git add app/src/main/java/sk/ziacik/androidstreamplayer/player
git commit -m "feat: play torrent data through media3"
```

---

### Task 7: Proof controller, TV UI, and lifecycle

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/playback/TorrentPlaybackUiState.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/playback/TorrentPlaybackController.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/TorrentPlaybackScreen.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/MainActivity.kt`
- Test: `app/src/test/java/sk/ziacik/androidstreamplayer/playback/TorrentPlaybackControllerTest.kt`
- Test: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/TorrentPlaybackScreenTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class TorrentPlaybackUiState(
    val phase: Phase = Phase.FETCHING_METADATA,
    val fileName: String? = null,
    val peers: Int = 0,
    val downloadRateBytesPerSecond: Int = 0,
    val downloadedBytes: Long = 0,
    val fileSizeBytes: Long = 0,
    val errorMessage: String? = null,
) {
    enum class Phase { FETCHING_METADATA, BUFFERING, PLAYING, PAUSED, ERROR }
}

class TorrentPlaybackController(
    private val scope: CoroutineScope,
    private val magnet: String,
    private val session: LibtorrentSession,
    val playerPort: PlayerPort,
) {
    val state: StateFlow<TorrentPlaybackUiState>
    fun start()
    fun togglePlayPause()
    fun seekBy(deltaMs: Long)
    fun close()
}
```

- [ ] **Step 1: Write controller tests against fakes**

Test prepare success populates filename/size and calls `playerPort.prepare(source)`, preparation failure maps to `Phase.ERROR`, `seekBy(30_000)` clamps to `>= 0`, and toggle calls play/pause based on player state.

- [ ] **Step 2: Run controller test and verify RED**

```text
./gradlew :app:testDebugUnitTest --tests '*TorrentPlaybackControllerTest' --stacktrace
```

- [ ] **Step 3: Implement controller**

`start()` prepares on a coroutine, starts playback after `PlayerPort.prepare`, polls `session.status()` about once per second while active, and listens to Media3 player state/error enough to map buffering/playing/paused/error into UI state. Do not start any background service.

- [ ] **Step 4: Build minimal `TorrentPlaybackScreen`**

Use Compose `AndroidView` hosting `androidx.media3.ui.PlayerView` with `useController = false`. Show filename, state, peers, formatted download rate, downloaded/total bytes above/below the player.

Use a focusable full-screen key handler:

```text
DPAD_CENTER / MEDIA_PLAY_PAUSE -> controller.togglePlayPause()
DPAD_LEFT -> controller.seekBy(-30_000)
DPAD_RIGHT -> controller.seekBy(+30_000)
```

Add stable test tags `torrent-player`, `torrent-state`, and `torrent-file-name`.

- [ ] **Step 5: Add instrumentation smoke test**

Do not invoke native/network torrent logic in the test. Render the screen/controller with fakes and assert the torrent status UI is displayed and D-pad right invokes `seekBy(+30_000)`.

- [ ] **Step 6: Switch `MainActivity` based on intent extra**

```kotlin
val magnet = intent.getStringExtra("magnet")
if (magnet.isNullOrBlank()) {
    // existing FakeTorrentSearchProvider/SearchScreen path unchanged
} else {
    // create LibtorrentSession(cacheDir/torrent-proof), Media3PlayerPort,
    // TorrentPlaybackController and TorrentPlaybackScreen
}
```

Keep the torrent session/player/controller as activity-owned fields and close them before cancelling `appScope` in `onDestroy`.

- [ ] **Step 7: Run unit + instrumentation compile**

```text
./gradlew :app:testDebugUnitTest --stacktrace
./gradlew :app:assembleDebug :app:assembleAndroidTest --stacktrace
```

Expected: PASS.

- [ ] **Step 8: Commit**

```text
git add app/src/main/java/sk/ziacik/androidstreamplayer app/src/test app/src/androidTest
git commit -m "feat: add torrent playback proof screen"
```

---

### Task 8: Runtime magnet deploy path and final verification

**Files:**
- Modify: `deploy-debug`

**Interfaces:**
- Produces:

```text
./deploy-debug '<magnet>'
./deploy-debug '<magnet>' 192.168.0.200:5555
```

No magnet is persisted in files.

- [ ] **Step 1: Replace deploy argument parsing**

Use:

```sh
#!/bin/sh
set -eu

magnet="${1:-}"
target="${2:-192.168.0.200:5555}"
package="sk.ziacik.androidstreamplayer"
activity="$package/.MainActivity"

./gradlew assembleDebug
adb -s "$target" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$target" shell am force-stop "$package"

if [ -n "$magnet" ]; then
    adb -s "$target" shell am start -n "$activity" --es magnet "$magnet"
else
    adb -s "$target" shell am start -n "$activity"
fi
```

Preserve executable mode `100755`.

- [ ] **Step 2: Run final automated verification**

```text
./gradlew :app:testDebugUnitTest --stacktrace
./gradlew :app:assembleDebug :app:assembleAndroidTest --stacktrace
```

Expected: both PASS.

- [ ] **Step 3: Static secret/magnet check**

Search repository content for the supplied info hash and `Anna Pigeon`; expected zero matches outside transient command history, which is not part of git.

- [ ] **Step 4: Commit**

```text
git add deploy-debug
git commit -m "build: pass runtime magnet to torrent proof"
```

- [ ] **Step 5: Manual Android TV acceptance**

Run locally with the supplied runtime magnet:

```text
./deploy-debug '<runtime magnet>'
```

Record these observations:

1. metadata resolves and selected filename appears;
2. playback starts while `downloadedBytes < fileSizeBytes`;
3. right seek moves playback ~30 seconds forward;
4. downloaded bytes do not have to fill every byte between the old and new position before playback resumes;
5. peers/download rate remain visible while buffering;
6. Back/exit stops the activity without leaving torrent playback/audio running.

- [ ] **Step 6: Push branch and verify GitHub Actions**

Require the branch-head workflow to report success for unit tests and debug/androidTest assembly before merging to `master`.

---

## Self-Review Notes

- Spec coverage: dependency/version floor, runtime-only magnet, metadata timeout, main-file selection, file priorities, bootstrap head/tail, active/seek piece priorities and deadlines, verified reads, custom Media3 `DataSource`, proof UI/status, D-pad controls, lifecycle, deploy path, unit tests, CI, and manual Android TV acceptance all have explicit tasks.
- No production search/Torrentio work is included.
- Type flow is consistent: `LibtorrentSession.prepare` -> `TorrentPlaybackSource` -> `TorrentPieceAccess` -> `TorrentDataSource.Factory` -> `ProgressiveMediaSource` -> `Media3PlayerPort`.
- `TorrentHandle.clearPieceDeadlines`, `piecePriority`, `setPieceDeadline`, `prioritizeFiles`, and `havePiece` are the libtorrent4j APIs used for streaming; file priorities are applied before piece priorities.
