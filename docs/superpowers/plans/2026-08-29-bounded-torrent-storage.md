# Bounded Torrent Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the torrent's full-length sparse movie file with a 128 MiB bounded piece cache that the Philips storage optimizer does not attribute as a complete film.

**Architecture:** Move libtorrent ownership behind an NDK bridge that provides a custom storage implementation and exposes selected-file piece reads to Kotlin. Kotlin retains Media3's `TorrentDataSource`, but updates priorities as the read cursor advances and consumes data through the bridge rather than `RandomAccessFile`.

**Tech Stack:** Kotlin, Media3 1.10.1, coroutines, Android NDK/CMake, libtorrent 2.1-compatible native source, JUnit 4, Android instrumentation.

---

## File structure

- Create: `app/src/main/cpp/CMakeLists.txt` — builds the JNI bridge for each Android ABI.
- Create: `app/src/main/cpp/bounded_piece_store.h` — fixed-slot cache and eviction contract.
- Create: `app/src/main/cpp/bounded_piece_store.cpp` — bounded cache implementation.
- Create: `app/src/main/cpp/torrent_bridge.cpp` — JNI entry points, libtorrent session and storage callbacks.
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/NativeTorrentBridge.kt` — Kotlin ownership and cancellation-safe native API.
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/NativeTorrentPieceBackend.kt` — adapts the bridge to `TorrentPieceBackend`.
- Modify: `app/build.gradle.kts` — enable external native build and replace prebuilt libtorrent Android artifacts with the bridge artifact.
- Modify: `gradle/libs.versions.toml` — add the pinned native source/build input only if Gradle resolves it.
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/LibtorrentSessionBackend.kt` — create the native session/backend and remove `SessionManager.download()` storage-path usage.
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/LibtorrentTorrentPieceBackend.kt` — remove `RandomAccessFile` use after the native backend is wired.
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/StreamingTorrentPieceAccess.kt` — expose an active-piece window and update it as the reader advances.
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/player/TorrentDataSource.kt` — move reprioritization from one-time `open()` work into bounded cursor progress.
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/MainActivity.kt` — release the native session deterministically.
- Create: `app/src/test/java/sk/ziacik/androidstreamplayer/torrent/BoundedPieceStorePlanTest.kt` — cache window and eviction tests.
- Create: `app/src/test/java/sk/ziacik/androidstreamplayer/player/TorrentDataSourceReprioritizationTest.kt` — moving-read-cursor regression tests.
- Create: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/torrent/NativeTorrentBridgeTest.kt` — byte-copy and cache-cap instrumentation checks.

### Task 1: Establish the moving-priority contract in Kotlin

**Files:**
- Create: `app/src/test/java/sk/ziacik/androidstreamplayer/player/TorrentDataSourceReprioritizationTest.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/player/TorrentDataSource.kt`

- [ ] **Step 1: Write the failing reader-progress test**

```kotlin
@Test
fun `reprioritizes when the reader crosses a one MiB boundary`() {
    val access = RecordingPieceAccess(fileLength = 4L * MEBIBYTE)
    val source = TorrentDataSource(access, reprioritizeStepBytes = MEBIBYTE)
    source.open(DataSpec(Uri.parse("torrent://stream/movie.mp4")))

    val buffer = ByteArray(MEBIBYTE.toInt() + 1)
    source.read(buffer, 0, buffer.size)

    assertThat(access.reprioritizedAt).containsExactly(0L, MEBIBYTE)
}
```

- [ ] **Step 2: Run the test and confirm it fails because `TorrentDataSource` has no cursor threshold**

Run: `./gradlew testDebugUnitTest --tests '*TorrentDataSourceReprioritizationTest'`

Expected: failure at the constructor argument or the missing second `reprioritize()` call.

- [ ] **Step 3: Add a one-MiB moving cursor threshold**

```kotlin
private var nextReprioritizePosition = 0L

private fun reprioritizeIfNeeded(positionBytes: Long) {
    if (positionBytes >= nextReprioritizePosition) {
        access.reprioritize(positionBytes)
        nextReprioritizePosition = positionBytes + reprioritizeStepBytes
    }
}
```

Call it from `open()` after resetting `nextReprioritizePosition` and from `read()` after advancing `readPosition`.

- [ ] **Step 4: Run the focused test and the existing data-source suite**

Run: `./gradlew testDebugUnitTest --tests '*TorrentDataSourceReprioritizationTest' --tests '*TorrentDataSourceTest'`

Expected: both classes pass.

### Task 2: Define and test bounded piece-slot semantics

**Files:**
- Create: `app/src/test/java/sk/ziacik/androidstreamplayer/torrent/BoundedPieceStorePlanTest.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/BoundedPieceStorePlan.kt`

- [ ] **Step 1: Write failing cache-cap and eviction tests**

```kotlin
@Test
fun `evicts the least recently used piece outside the active window`() {
    val plan = BoundedPieceStorePlan(maxBytes = 8, pieceLengthBytes = 4)
    plan.put(piece = 10, byteCount = 4, activePieces = setOf(10))
    plan.put(piece = 11, byteCount = 4, activePieces = setOf(10, 11))
    plan.put(piece = 12, byteCount = 4, activePieces = setOf(11, 12))

    assertThat(plan.presentPieces()).containsExactly(11, 12)
    assertThat(plan.allocatedBytes()).isEqualTo(8)
}
```

- [ ] **Step 2: Run the test and confirm `BoundedPieceStorePlan` does not exist**

Run: `./gradlew testDebugUnitTest --tests '*BoundedPieceStorePlanTest'`

Expected: compilation failure for `BoundedPieceStorePlan`.

- [ ] **Step 3: Implement the pure Kotlin eviction plan**

```kotlin
class BoundedPieceStorePlan(
    private val maxBytes: Long,
    private val pieceLengthBytes: Int,
) {
    private val slots = LinkedHashMap<Int, Int>(16, 0.75f, true)

    fun put(piece: Int, byteCount: Int, activePieces: Set<Int>) {
        slots[piece] = byteCount
        while (allocatedBytes() > maxBytes) {
            val evicted = slots.keys.first { it !in activePieces }
            slots.remove(evicted)
        }
    }

    fun presentPieces(): Set<Int> = slots.keys
    fun allocatedBytes(): Long = slots.values.sumOf(Int::toLong)
}
```

- [ ] **Step 4: Run the focused tests**

Run: `./gradlew testDebugUnitTest --tests '*BoundedPieceStorePlanTest'`

Expected: PASS.

### Task 3: Add the native bounded storage bridge

**Files:**
- Create: `app/src/main/cpp/CMakeLists.txt`
- Create: `app/src/main/cpp/bounded_piece_store.h`
- Create: `app/src/main/cpp/bounded_piece_store.cpp`
- Create: `app/src/main/cpp/torrent_bridge.cpp`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add a failing JNI instrumentation test**

```kotlin
@Test
fun nativeStore_readsWrittenPiece_and_neverExceedsCap() {
    val store = NativeTorrentBridge.createPieceStore(maxBytes = 8)
    store.writePiece(piece = 4, bytes = byteArrayOf(1, 2, 3, 4), activePieces = intArrayOf(4))
    store.writePiece(piece = 5, bytes = byteArrayOf(5, 6, 7, 8), activePieces = intArrayOf(4, 5))
    store.writePiece(piece = 6, bytes = byteArrayOf(9, 10, 11, 12), activePieces = intArrayOf(5, 6))

    assertThat(store.readPiece(5)).isEqualTo(byteArrayOf(5, 6, 7, 8))
    assertThat(store.allocatedBytes()).isAtMost(8)
}
```

- [ ] **Step 2: Run it and confirm the native bridge is absent**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=sk.ziacik.androidstreamplayer.torrent.NativeTorrentBridgeTest`

Expected: compilation failure for `NativeTorrentBridge`.

- [ ] **Step 3: Configure CMake and ABI packaging**

Add `externalNativeBuild` for CMake in `app/build.gradle.kts` and a CMake target named `torrent_bridge`. Link the vendored libtorrent target, `android`, and `log`; set C++17 and build only `armeabi-v7a` and `arm64-v8a` for the TV targets.

```cmake
add_library(torrent_bridge SHARED torrent_bridge.cpp bounded_piece_store.cpp)
target_compile_features(torrent_bridge PRIVATE cxx_std_17)
target_link_libraries(torrent_bridge PRIVATE libtorrent android log)
```

- [ ] **Step 4: Implement fixed-size slot files, never a selected-file path**

```cpp
class bounded_piece_store {
public:
  explicit bounded_piece_store(std::size_t max_bytes);
  void write_piece(int piece, std::span<const std::byte> bytes, std::span<const int> active);
  std::vector<std::byte> read_piece(int piece) const;
  std::size_t allocated_bytes() const noexcept;
};
```

Persist each slot under `cache/torrent-pieces/<torrent-id>/<piece-number>`. Allocate and delete only individual piece files. Reject a write that cannot evict a non-active slot; do not extend or truncate a file to the movie size.

- [ ] **Step 5: Run the instrumentation test**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=sk.ziacik.androidstreamplayer.torrent.NativeTorrentBridgeTest`

Expected: PASS on `192.168.0.200:5555`.

### Task 4: Bind libtorrent's custom storage callbacks to the bounded store

**Files:**
- Modify: `app/src/main/cpp/torrent_bridge.cpp`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/NativeTorrentBridge.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/LibtorrentSessionBackend.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/NativeTorrentPieceBackend.kt`

- [ ] **Step 1: Add a failing backend contract test**

```kotlin
@Test
fun `native backend reads only verified bytes at selected-file offset`() = runTest {
    val backend = FakeNativeBridgeBackend(pieceLength = 4).apply {
        provide(piece = 2, bytes = byteArrayOf(20, 21, 22, 23))
    }
    val subject = NativeTorrentPieceBackend(backend)
    val destination = ByteArray(2)

    subject.awaitVerifiedPiece(2) { false }
    assertThat(subject.readSelectedFile(8, destination, 0, 2)).isEqualTo(2)
    assertThat(destination).isEqualTo(byteArrayOf(20, 21))
}
```

- [ ] **Step 2: Run it and confirm the native backend does not exist**

Run: `./gradlew testDebugUnitTest --tests '*NativeTorrentPieceBackendTest'`

Expected: compilation failure for `NativeTorrentPieceBackend`.

- [ ] **Step 3: Implement the Kotlin JNI adapter**

```kotlin
interface NativeTorrentBridgeBackend {
    fun setWindow(topPriorityPieces: IntRange, readaheadPieces: IntRange)
    fun awaitPiece(piece: Int, cancelled: () -> Boolean)
    fun readFile(positionBytes: Long, buffer: ByteArray, offset: Int, length: Int): Int
    fun cancelRead()
    fun stop()
}
```

Implement `NativeTorrentPieceBackend` with this interface and delete the `RandomAccessReader` path. Keep its blocking work on Media3's loader thread.

- [ ] **Step 4: Install the custom storage constructor in the native session**

Build libtorrent from the pinned source with the bridge's storage constructor registered before adding the torrent. Its read, write, move, rename, and release operations must use `bounded_piece_store`; `release_files()` removes every per-piece file. When eviction occurs, notify the torrent state that the piece is unavailable before it can be selected for a read.

- [ ] **Step 5: Run Kotlin and connected tests**

Run: `./gradlew testDebugUnitTest connectedDebugAndroidTest`

Expected: all existing unit tests and the native backend contract pass.

### Task 5: Wire lifecycle, storage limit, and TV proof

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/StreamingTorrentPieceAccess.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/player/TorrentDataSource.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/MainActivity.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/LibtorrentSessionBackend.kt`

- [ ] **Step 1: Write lifecycle regression tests**

```kotlin
@Test
fun `cancelReader cancels native wait and stop removes the cache`() = runTest {
    val bridge = FakeNativeBridgeBackend()
    val access = streamingAccess(bridge)

    access.cancelReader()
    bridge.stop()

    assertThat(bridge.cancelled).isTrue()
    assertThat(bridge.cacheReleased).isTrue()
}
```

- [ ] **Step 2: Run the lifecycle test and confirm it fails**

Run: `./gradlew testDebugUnitTest --tests '*NativeTorrentPieceBackendTest'`

Expected: failure until cancellation and stop delegate to the bridge.

- [ ] **Step 3: Implement deterministic teardown and the 128 MiB cap**

Define `MAX_TORRENT_CACHE_BYTES = 128L * 1024 * 1024` once in `LibtorrentSessionBackend`. Pass it to the native bridge. Make `stop()` cancel reads, remove the torrent, and remove `cache/torrent-pieces/<torrent-id>`; do not remove app preferences or unrelated cache.

- [ ] **Step 4: Run all local verification**

Run: `./gradlew testDebugUnitTest assembleDebug && git diff --check`

Expected: `BUILD SUCCESSFUL` and no whitespace errors.

- [ ] **Step 5: Install and run the Philips smoke proof**

Run:

```sh
adb -s 192.168.0.200:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.0.200:5555 shell am force-stop sk.ziacik.androidstreamplayer
adb -s 192.168.0.200:5555 shell am start -n sk.ziacik.androidstreamplayer/.MainActivity --es magnet '<approved test magnet>'
adb -s 192.168.0.200:5555 shell 'run-as sk.ziacik.androidstreamplayer sh -c "find cache/torrent-pieces -type f -printf \"%s %n %p\\n\"; du -sk cache/torrent-pieces"'
adb -s 192.168.0.200:5555 shell df -k /data
```

Expected: playback starts; all files are piece-sized rather than movie-sized; aggregate cache is at most 131072 KiB; `/data` is measured before and after. Observe the Philips optimizer manually to confirm it no longer attributes the complete movie length to Android Stream Player.

## Plan self-review

- Spec coverage: Tasks 1 and 5 cover moving priority, lifecycle, cancellation, error propagation, and verification. Tasks 2–4 cover fixed-size storage, native ownership, selected-file reads, and eviction.
- Placeholder scan: no deferred or unspecified implementation steps remain; the required external input is an approved legal test magnet supplied at device verification time.
- Type consistency: `NativeTorrentBridgeBackend` is the shared Kotlin seam in Tasks 4 and 5; its `cancelRead()` and `stop()` operations are used consistently.
- Commit policy: do not stage or commit any task output unless the user separately authorizes it.
