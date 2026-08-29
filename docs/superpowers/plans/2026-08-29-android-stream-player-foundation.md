# Android Stream Player Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first runnable Android TV scaffold for `android-stream-player` with a fake torrent search flow, TV-friendly Compose UI, clean torrent/player boundaries, tests, and ADB deploy helper.

**Architecture:** Keep a single `:app` module and separate responsibilities by focused Kotlin packages: `search` owns query/results/state, `torrent` owns the future streaming boundary, `player` owns Media3 lifecycle, and `ui` renders TV-first Compose screens. The UI depends on interfaces, so fake implementations can later be replaced by real torrent discovery and streaming without rewriting presentation code.

**Tech Stack:** Kotlin 2.3.21, Android Gradle Plugin 9.3.1, Jetpack Compose BOM 2026.06.00, Android SDK 36, minSdk 26, Java 17, Media3 1.10.1, OkHttp 5.1.0, kotlinx.coroutines 1.10.2, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-08-29-android-stream-player-foundation-design.md`

## Global Constraints

- Namespace/application id: `sk.ziacik.androidstreamplayer`.
- Single `:app` module.
- `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`.
- Java 17 source/target compatibility.
- Android TV launcher with Leanback required and touchscreen not required.
- No IPTV channel/EPG/TvInput code or permissions.
- No real torrent discovery or BitTorrent implementation in this foundation.
- Search UI must be D-pad usable and must not parse magnet URIs.
- Media3 and OkHttp are available for the next implementation phase.

---

### Task 1: Create runnable Android TV project skeleton

**Files:**
- Create: `.gitignore`
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/MainActivity.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/theme/Theme.kt`

**Interfaces:**
- Produces a runnable Compose Android TV app with `MainActivity` as the launcher entry point.

- [ ] **Step 1: Add Gradle/version-catalog files** using the versions from Global Constraints and dependencies for activity-compose, Compose foundation/material3/ui, Media3 ExoPlayer/UI, OkHttp, coroutines, JUnit, Compose test, and AndroidX test runner.
- [ ] **Step 2: Add Android TV manifest** with `INTERNET`, required Leanback feature, optional touchscreen, landscape `MainActivity`, and `LEANBACK_LAUNCHER` + `LAUNCHER` categories.
- [ ] **Step 3: Add minimal theme/resources** with app name `Android Stream Player`.
- [ ] **Step 4: Add `MainActivity`** that sets immersive fullscreen Compose content and initially renders `SearchScreen` once Task 4 exists.
- [ ] **Step 5: Verify configuration** with `./gradlew :app:assembleDebug` once the remaining source files exist.

### Task 2: Implement torrent search domain boundary with tests

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentSearchResult.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentSearchProvider.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/search/FakeTorrentSearchProvider.kt`
- Create: `app/src/test/java/sk/ziacik/androidstreamplayer/search/FakeTorrentSearchProviderTest.kt`

**Interfaces:**
- Produces: `suspend fun TorrentSearchProvider.search(query: String): List<TorrentSearchResult>`.
- `TorrentSearchResult` fields: `id`, `title`, `magnetUri`, `quality`, `sizeBytes`, `seeders`, `source`.

- [ ] **Step 1: Write failing provider tests** asserting non-empty queries return deterministic fake releases containing the query and an empty/blank query returns no releases.
- [ ] **Step 2: Implement model and provider interface** exactly as defined in the design spec.
- [ ] **Step 3: Implement `FakeTorrentSearchProvider`** with three deterministic results (2160p, 1080p, 720p), dummy magnet URIs, realistic sizes/seeder counts, and `source = "Fake"`.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest --tests '*FakeTorrentSearchProviderTest'` and require PASS.

### Task 3: Implement search state/controller with tests

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/search/SearchUiState.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/search/SearchController.kt`
- Create: `app/src/test/java/sk/ziacik/androidstreamplayer/search/SearchControllerTest.kt`

**Interfaces:**
- Consumes: `TorrentSearchProvider.search(query)`.
- Produces: `StateFlow<SearchUiState>` plus `setQuery`, `search`, `retry`, and `select` actions.

- [ ] **Step 1: Write failing tests** for blank query, successful search, empty result, provider exception, retry, and selection placeholder state.
- [ ] **Step 2: Define `SearchUiState`** with `query`, `isSearching`, `results`, `errorMessage`, `selectedResult`, and `streamStatus`.
- [ ] **Step 3: Implement `SearchController`** using an injected `CoroutineScope` and `TorrentSearchProvider`; catch provider exceptions and convert them to `errorMessage` rather than throwing into the UI.
- [ ] **Step 4: Implement selection behavior** as `Preparing stream…` followed by `Streaming not implemented yet` without invoking any real torrent engine.
- [ ] **Step 5: Run** `./gradlew :app:testDebugUnitTest --tests '*SearchControllerTest'` and require PASS.

### Task 4: Implement TV-first Compose search UI

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/SearchScreen.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/MainActivity.kt`
- Create: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/SearchScreenTest.kt`

**Interfaces:**
- Consumes: `SearchController.state` and controller actions.
- Produces: TV-friendly search screen with focusable result rows and placeholder selection state.

- [ ] **Step 1: Write instrumentation test** that enters a movie title, triggers Search, observes fake results, selects the first result, and observes `Streaming not implemented yet`.
- [ ] **Step 2: Implement `SearchScreen`** with a large query field, explicit focusable Search button, loading/error/empty states, and vertically scrollable result cards.
- [ ] **Step 3: Render result metadata** as release title plus quality, human-readable size, seeder count, and source; metadata must remain visually secondary.
- [ ] **Step 4: Add visible focus treatment** using Compose focus APIs so D-pad navigation is obvious from TV distance.
- [ ] **Step 5: Wire `MainActivity`** to `FakeTorrentSearchProvider`, activity `CoroutineScope`, `SearchController`, and `SearchScreen`.
- [ ] **Step 6: Run** `./gradlew :app:connectedDebugAndroidTest` when an emulator/device is available; otherwise at minimum compile instrumentation tests with `./gradlew :app:assembleDebug :app:assembleAndroidTest`.

### Task 5: Add future torrent/player boundaries and deploy helper

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/TorrentSource.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/torrent/TorrentStreamer.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/player/PlayerPort.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/player/Media3PlayerPort.kt`
- Create: `deploy-debug`

**Interfaces:**
- Produces: `suspend fun TorrentStreamer.prepare(result: TorrentSearchResult): TorrentSource`.
- Produces a minimal `PlayerPort` implemented by `Media3PlayerPort`, ready for a later playable URI/source.

- [ ] **Step 1: Add torrent boundary types** without a concrete real streamer implementation.
- [ ] **Step 2: Add minimal Media3 player port** that owns ExoPlayer construction, `prepare(uri)`, play/pause, and release lifecycle, but is not yet invoked by the fake flow.
- [ ] **Step 3: Add `deploy-debug`** based on `android-tv-player`, targeting package `sk.ziacik.androidstreamplayer` and default ADB target `192.168.0.200:5555`.
- [ ] **Step 4: Mark `deploy-debug` executable in git mode when committing from a local checkout; if the GitHub contents API cannot preserve executable mode, document that local `chmod +x deploy-debug` may be needed after clone.

### Task 6: Final verification and merge

**Files:**
- Review all files created by Tasks 1-5.

**Interfaces:**
- Validates the complete foundation against the design spec.

- [ ] **Step 1: Run** `./gradlew :app:testDebugUnitTest` and require all unit tests PASS.
- [ ] **Step 2: Run** `./gradlew :app:assembleDebug :app:assembleAndroidTest` and require successful compilation.
- [ ] **Step 3: Inspect manifest** to confirm there are no EPG/TvInput permissions/services and TV launcher configuration is present.
- [ ] **Step 4: Inspect package graph** to confirm UI depends on search interfaces/state and no fake implementation leaks into reusable UI components.
- [ ] **Step 5: Fast-forward `main` to the verified `feature/foundation` branch commit.
