# Torrent Release Metadata Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Parse useful video/audio release metadata from torrent titles and show it as compact, stable-order badges in the Android TV torrent result rows.

**Architecture:** Keep provider adapters transport-focused. A pure `TorrentReleaseParser` converts the raw title into a normalized `TorrentReleaseInfo`; `TorrentSearchController` enriches provider results before exposing UI state. `TorrentResults` renders the normalized values while preserving the raw title, size, seeds, and provider source.

**Tech Stack:** Kotlin, Android Compose Material 3/Foundation, kotlinx.coroutines, JUnit 4, Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-09-01-torrent-release-metadata-design.md`

## Global Constraints

- Parsing must not depend on Knaben-specific response fields.
- Do not add a remote metadata service or MediaInfo probing.
- Unknown/ambiguous tokens remain only in the raw title; do not invent `UNKNOWN`/`AUTO` badges.
- Badge order is resolution, release source, HDR, video codec, audio codec, audio features, channels, languages, year.
- Preserve raw title, size, seeder count, and provider/source text.
- Parsing must be deterministic, case-insensitive, token-aware, and safe for malformed input.

---

### Task 1: Normalized release model and parser

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentReleaseInfo.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentReleaseParser.kt`
- Create: `app/src/test/java/sk/ziacik/androidstreamplayer/search/TorrentReleaseParserTest.kt`

**Interfaces:**
- Produces: `data class TorrentReleaseInfo(...)`
- Produces: enums `VideoResolution`, `ReleaseSource`, `VideoCodec`, `HdrFormat`, `AudioCodec`, `AudioFeature`
- Produces: `fun interface TorrentReleaseParser { fun parse(title: String): TorrentReleaseInfo }`
- Produces: `object DefaultTorrentReleaseParser : TorrentReleaseParser`

- [ ] **Step 1: Write parser tests first**

Cover exact representative expectations, including:

```kotlin
val info = DefaultTorrentReleaseParser.parse(
    "Dune.Part.Two.2024.2160p.UHD.BluRay.REMUX.DV.HDR10.HEVC.TrueHD.Atmos.7.1-GROUP",
)
assertEquals(VideoResolution.P2160, info.resolution)
assertEquals(ReleaseSource.REMUX, info.releaseSource)
assertEquals(VideoCodec.HEVC, info.videoCodec)
assertEquals(setOf(HdrFormat.DOLBY_VISION, HdrFormat.HDR10), info.hdrFormats)
assertEquals(AudioCodec.TRUE_HD, info.audioCodec)
assertEquals(setOf(AudioFeature.ATMOS), info.audioFeatures)
assertEquals("7.1", info.audioChannels)
assertEquals(2024, info.year)
```

Add cases for `WEB-DL + DDP5.1 + H.264`, `BluRay + x264 + DTS`, `HDR10Plus + AV1 + EAC3`, aliases/mixed separators, no metadata, false positives, and implausible year-like numbers.

- [ ] **Step 2: Run the focused parser test and confirm failure**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests sk.ziacik.androidstreamplayer.search.TorrentReleaseParserTest
```

Expected: FAIL because parser/model types do not exist yet.

- [ ] **Step 3: Implement the normalized model**

Use closed enums with display labels, for example:

```kotlin
enum class VideoResolution(val label: String) {
    P2160("4K"), P1080("1080p"), P720("720p"), P480("480p")
}

data class TorrentReleaseInfo(
    val resolution: VideoResolution? = null,
    val releaseSource: ReleaseSource? = null,
    val videoCodec: VideoCodec? = null,
    val hdrFormats: Set<HdrFormat> = emptySet(),
    val audioCodec: AudioCodec? = null,
    val audioFeatures: Set<AudioFeature> = emptySet(),
    val audioChannels: String? = null,
    val bitDepth: Int? = null,
    val languages: List<String> = emptyList(),
    val year: Int? = null,
    val releaseGroup: String? = null,
)
```

- [ ] **Step 4: Implement ordered, token-aware parsing**

Use regex helpers with explicit precedence: `DTS-HD MA` before `DTS`, `E-AC-3/DDP/DD+` before `AC-3/DD`, `HDR10+` before `HDR10` before generic `HDR`, and `WEB-DL` before other WEB forms. Treat `.`, `_`, spaces, brackets and parentheses as separators without modifying the display title.

- [ ] **Step 5: Run parser tests until green**

Run the focused test command again. Expected: PASS.

- [ ] **Step 6: Commit parser/model work**

```bash
git add app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentReleaseInfo.kt \
        app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentReleaseParser.kt \
        app/src/test/java/sk/ziacik/androidstreamplayer/search/TorrentReleaseParserTest.kt
git commit -m "feat: parse torrent release metadata"
```

---

### Task 2: Enrich search results outside providers

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentSearchResult.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/search/KnabenTorrentSearchProvider.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentSearchController.kt`
- Modify: `app/src/test/java/sk/ziacik/androidstreamplayer/search/KnabenTorrentSearchProviderTest.kt`
- Modify: `app/src/test/java/sk/ziacik/androidstreamplayer/search/TorrentSearchControllerTest.kt`

**Interfaces:**
- `TorrentSearchResult` gains `releaseInfo: TorrentReleaseInfo = TorrentReleaseInfo()` and removes `quality`.
- `TorrentSearchController` gains optional constructor dependency `releaseParser: TorrentReleaseParser = DefaultTorrentReleaseParser`.

- [ ] **Step 1: Update tests to require provider neutrality and controller enrichment**

Provider mapping test should expect raw provider fields only, with empty `releaseInfo`. Controller test should return a raw title such as `The.Matrix.1999.2160p.BluRay.x265.DTS` and assert that controller state contains `P2160`, `BLURAY`, `HEVC`, `DTS`, and year `1999`.

- [ ] **Step 2: Run search unit tests and confirm failure**

```bash
./gradlew :app:testDebugUnitTest --tests sk.ziacik.androidstreamplayer.search.KnabenTorrentSearchProviderTest --tests sk.ziacik.androidstreamplayer.search.TorrentSearchControllerTest
```

- [ ] **Step 3: Remove `inferQuality()` from Knaben and migrate `TorrentSearchResult`**

Knaben maps only `id`, `title`, `magnetUri`, `sizeBytes`, `seeders`, and provider `source`.

- [ ] **Step 4: Enrich results in `TorrentSearchController`**

Immediately after `provider.search(request)` succeeds:

```kotlin
val results = provider.search(request).map { result ->
    result.copy(releaseInfo = releaseParser.parse(result.title))
}
```

Keep generation/cancellation behavior unchanged.

- [ ] **Step 5: Run focused search tests until green**

Expected: PASS.

- [ ] **Step 6: Commit enrichment boundary**

```bash
git add app/src/main/java/sk/ziacik/androidstreamplayer/search \
        app/src/test/java/sk/ziacik/androidstreamplayer/search
git commit -m "refactor: enrich torrent results after provider search"
```

---

### Task 3: Render TV-friendly metadata badges

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/TorrentResults.kt`
- Modify: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/MovieDetailScreenTest.kt`

**Interfaces:**
- Add a small private `ReleaseBadges` composable that consumes `TorrentReleaseInfo`.
- Add a pure private helper `badgeLabels(info: TorrentReleaseInfo): List<String>` to lock stable ordering.

- [ ] **Step 1: Extend Compose test fixtures with parsed release metadata**

Build the fixture with explicit `releaseInfo` and assert the visible labels `4K`, `REMUX`, `DV`, `HDR10`, `HEVC`, `TrueHD`, `Atmos`, `7.1`, `ENG`, `2024`. Also assert raw title and `42 seeds` remain visible.

- [ ] **Step 2: Run the UI test target and confirm failure on missing badges**

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=sk.ziacik.androidstreamplayer.ui.MovieDetailScreenTest
```

If no emulator/device is attached locally, at minimum compile instrumentation tests with:

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```

- [ ] **Step 3: Replace the single quality badge with a wrapping badge row**

Use Compose Foundation `FlowRow` so badges wrap instead of squeezing the title/action. Do not show placeholders for absent fields.

Stable labels are produced in this order:

```kotlin
buildList {
    info.resolution?.let { add(it.label) }
    info.releaseSource?.let { add(it.label) }
    info.hdrFormats.sortedBy { it.displayOrder }.forEach { add(it.label) }
    info.videoCodec?.let { add(it.label) }
    info.audioCodec?.let { add(it.label) }
    info.audioFeatures.sortedBy { it.displayOrder }.forEach { add(it.label) }
    info.audioChannels?.let(::add)
    addAll(info.languages)
    info.year?.let { add(it.toString()) }
}
```

- [ ] **Step 4: Keep title/secondary metadata hierarchy unchanged**

Raw `result.title` stays one-line ellipsized; size/seeds/provider stay secondary text. Keep current focus border, scale, play/start feedback, and click behavior.

- [ ] **Step 5: Compile/run UI tests until green**

Expected: instrumentation compile succeeds; connected tests PASS when a device is available.

- [ ] **Step 6: Commit UI work**

```bash
git add app/src/main/java/sk/ziacik/androidstreamplayer/ui/TorrentResults.kt \
        app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/MovieDetailScreenTest.kt
git commit -m "feat: show torrent release metadata badges"
```

---

### Task 4: Full verification and PR cleanup

**Files:**
- Verify all files changed by Tasks 1-3.
- Update PR #21 metadata after implementation.

- [ ] **Step 1: Run all JVM tests**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 2: Compile the debug app and Android tests**

```bash
./gradlew :app:assembleDebug :app:compileDebugAndroidTestKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run connected Android tests when a device/emulator is available**

```bash
./gradlew :app:connectedDebugAndroidTest
```

Record explicitly if this cannot run because no Android target is attached.

- [ ] **Step 4: Review diff for accidental provider coupling and placeholders**

Confirm no `inferQuality`, no `AUTO` quality fallback, no external metadata calls, and no badge ordering dependent on set iteration order.

- [ ] **Step 5: Update PR #21**

Set title to `Add parsed torrent release metadata badges`, body to summarize parser/model/controller/UI/test changes, and mark ready for review only after verification passes.
