# Kino Movie Catalog Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the torrent-first landing screen with a zero-configuration, TMDB-backed movie catalog where users browse posters, open a cinematic detail, choose a Knaben torrent release, and play it through the existing TorrServer/Media3 player.

**Architecture:** Keep the app single-module and split responsibilities into three boundaries: `MovieCatalog` for TMDB metadata, `TorrentSearchProvider`/`TorrentSearchController` for movie-aware torrent discovery, and `PlaybackController` for torrent preparation/player transition. `KinoApp` composes the linear Catalog → Detail → Player flow while preserving catalog state when navigating back.

**Tech Stack:** Kotlin 2.3.21, Android SDK 36/minSdk 26, Jetpack Compose BOM 2026.06.00, OkHttp 5.1.0, Coroutines 1.10.2, Media3 1.11.0, Coil 3.6.0, org.json, JUnit 4, Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-08-30-movie-catalog-search-design.md`

## Global Constraints

- Movies only; no TV series, seasons, or episodes.
- End users configure nothing: TMDB and Knaben are built into the app flow.
- TMDB API v3 is the catalog source; search uses `/3/search/movie`, external IDs use `/3/movie/{movie_id}/external_ids`.
- `include_adult=false`; initial TMDB language is `en-US`.
- TMDB credential is supplied at build/release time and embedded as `BuildConfig.TMDB_API_KEY`; never commit a real credential.
- Poster/backdrop loading uses Coil and TMDB image URLs.
- Knaben remains the only built-in torrent provider in this iteration.
- Knaben movie fallback order is `originalTitle + year`, `title + year`, `originalTitle`, `title`, skipping duplicate query strings.
- Merge all useful fallback results, deduplicate by BitTorrent info-hash when possible, and sort primarily by seeders descending.
- Knaben requests for this flow use the movie category only (`3_000_000`).
- Keep existing TorrServer, Media3 player, and Kino player OSD behavior unless integration requires a narrow adapter change.
- Back from detail restores the previous catalog query/results and returns focus to the selected poster.
- D-pad behavior must be deterministic: Up from the first poster row reaches search; Down from search enters the poster grid; on detail load the first torrent row receives focus once results are ready.
- All feature work stays on `feature/movie-catalog-search`; do not modify `master` directly.

---

## File Structure

### New catalog boundary

- `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/Movie.kt` — immutable movie identity/metadata model.
- `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/MovieCatalog.kt` — catalog interface plus external-ID model.
- `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/TmdbMovieCatalog.kt` — TMDB HTTP calls, JSON mapping, image URL helpers.
- `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchUiState.kt` — catalog-only UI state.
- `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchController.kt` — query debounce, cancellation/supersession, error/result state, focused movie restoration.

### Reshaped torrent/playback boundary

- `app/src/main/java/sk/ziacik/androidstreamplayer/search/MovieTorrentSearchRequest.kt` — movie identity handed to torrent providers.
- `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentSearchProvider.kt` — change from free-text query to movie request.
- `app/src/main/java/sk/ziacik/androidstreamplayer/search/KnabenTorrentSearchProvider.kt` — fallback queries, movie-only category, result merge/dedupe/sort.
- `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentSearchUiState.kt` — detail-page release discovery state.
- `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentSearchController.kt` — resolve IMDb ID, search torrents, retry.
- `app/src/main/java/sk/ziacik/androidstreamplayer/playback/PlaybackUiState.kt` — selected release/preparation status.
- `app/src/main/java/sk/ziacik/androidstreamplayer/playback/PlaybackController.kt` — existing select/prepare/play/exit behavior extracted from `SearchController`.

### New UI flow

- `app/src/main/java/sk/ziacik/androidstreamplayer/ui/KinoApp.kt` — Catalog/Detail/Player coordinator.
- `app/src/main/java/sk/ziacik/androidstreamplayer/ui/MovieSearchScreen.kt` — search field + Netflix-style poster grid.
- `app/src/main/java/sk/ziacik/androidstreamplayer/ui/MoviePosterCard.kt` — focused poster component.
- `app/src/main/java/sk/ziacik/androidstreamplayer/ui/MovieDetailScreen.kt` — backdrop/poster/metadata + torrent section.
- `app/src/main/java/sk/ziacik/androidstreamplayer/ui/TorrentResults.kt` — compact TV-focused release rows and loading/error/empty states.

### Existing files modified/removed

- `gradle/libs.versions.toml` — add Coil 3.6.0.
- `app/build.gradle.kts` — enable BuildConfig, wire TMDB key, add Coil dependencies.
- `.github/workflows/android-ci.yml` — include feature branch and use a non-secret placeholder key for compile/test tasks; real release builds still require a real key.
- `app/src/main/java/sk/ziacik/androidstreamplayer/MainActivity.kt` — construct new controllers and render `KinoApp`.
- `app/src/main/java/sk/ziacik/androidstreamplayer/search/FakeTorrentSearchProvider.kt` — update test/fake contract or remove if no longer used.
- `app/src/main/java/sk/ziacik/androidstreamplayer/search/SearchController.kt` — remove after responsibilities have migrated.
- `app/src/main/java/sk/ziacik/androidstreamplayer/search/SearchUiState.kt` — remove after responsibilities have migrated.
- `app/src/main/java/sk/ziacik/androidstreamplayer/ui/SearchScreen.kt` — remove after new UI is integrated.
- `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/SearchScreenTest.kt` — replace with movie catalog flow test.

---

### Task 1: Add TMDB build configuration and catalog HTTP client

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `.github/workflows/android-ci.yml`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/Movie.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/MovieCatalog.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/TmdbMovieCatalog.kt`
- Create: `app/src/test/java/sk/ziacik/androidstreamplayer/catalog/TmdbMovieCatalogTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class Movie(
      val tmdbId: Int,
      val imdbId: String? = null,
      val title: String,
      val originalTitle: String,
      val releaseYear: Int?,
      val overview: String?,
      val voteAverage: Double?,
      val posterPath: String?,
      val backdropPath: String?,
  )

  data class MovieExternalIds(val imdbId: String?)

  interface MovieCatalog {
      suspend fun search(query: String): List<Movie>
      suspend fun externalIds(tmdbId: Int): MovieExternalIds
  }
  ```

- [ ] **Step 1: Add failing TMDB mapping/request tests**

Create `TmdbMovieCatalogTest.kt` with a recording HTTP transport. Cover both endpoints and missing optional fields:

```kotlin
@Test
fun `search requests movie catalog without adult content and maps results`() = runTest {
    val transport = RecordingTmdbTransport(
        response = TmdbHttpResponse(
            200,
            """{
              "results": [{
                "id": 603,
                "title": "The Matrix",
                "original_title": "The Matrix",
                "release_date": "1999-03-30",
                "overview": "A hacker discovers reality is a simulation.",
                "vote_average": 8.2,
                "poster_path": "/poster.jpg",
                "backdrop_path": "/backdrop.jpg"
              }]
            }""",
        ),
    )
    val catalog = TmdbMovieCatalog(apiKey = "test-key", transport = transport)

    val result = catalog.search("Matrix")

    assertEquals(603, result.single().tmdbId)
    assertEquals(1999, result.single().releaseYear)
    assertEquals("Matrix", transport.request!!.url.queryParameter("query"))
    assertEquals("false", transport.request!!.url.queryParameter("include_adult"))
    assertEquals("en-US", transport.request!!.url.queryParameter("language"))
}

@Test
fun `external ids returns imdb id`() = runTest {
    val transport = RecordingTmdbTransport(TmdbHttpResponse(200, """{"imdb_id":"tt0133093"}"""))
    val catalog = TmdbMovieCatalog(apiKey = "test-key", transport = transport)

    assertEquals("tt0133093", catalog.externalIds(603).imdbId)
    assertEquals("/3/movie/603/external_ids", transport.request!!.url.encodedPath)
}

@Test
fun `missing optional movie metadata stays valid`() = runTest {
    val transport = RecordingTmdbTransport(
        TmdbHttpResponse(200, """{"results":[{"id":1,"title":"X","original_title":"X"}]}"""),
    )
    val movie = TmdbMovieCatalog("test-key", transport).search("X").single()

    assertNull(movie.releaseYear)
    assertNull(movie.posterPath)
    assertNull(movie.backdropPath)
}
```

- [ ] **Step 2: Run the new unit test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*TmdbMovieCatalogTest*'
```

Expected: FAIL because catalog types/client do not exist.

- [ ] **Step 3: Add Coil and BuildConfig wiring**

In `gradle/libs.versions.toml` add:

```toml
coil = "3.6.0"

coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-okhttp = { module = "io.coil-kt.coil3:coil-network-okhttp", version.ref = "coil" }
```

In `app/build.gradle.kts`, resolve the credential and expose it:

```kotlin
val tmdbApiKey = providers.gradleProperty("tmdbApiKey")
    .orElse(providers.environmentVariable("TMDB_API_KEY"))
    .orElse("")

android {
    defaultConfig {
        buildConfigField("String", "TMDB_API_KEY", "\"${tmdbApiKey.get()}\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
```

Add:

```kotlin
implementation(libs.coil.compose)
implementation(libs.coil.network.okhttp)
```

Add a release-only validation task so debug/unit CI can compile with a placeholder but release cannot silently ship without a key:

```kotlin
val verifyTmdbApiKey = tasks.register("verifyTmdbApiKey") {
    doLast {
        require(tmdbApiKey.get().isNotBlank()) { "TMDB_API_KEY/tmdbApiKey is required for release builds" }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyTmdbApiKey)
}
```

- [ ] **Step 4: Update CI for this branch and compile-time key**

Add `feature/movie-catalog-search` to push branches and set a harmless build-only value at job level:

```yaml
env:
  TMDB_API_KEY: ci-placeholder
```

No tests may contact the real TMDB service.

- [ ] **Step 5: Implement the catalog model and TMDB transport/client**

Use OkHttp exactly like the existing Knaben transport, but GET requests with an `api_key` query parameter. `TmdbMovieCatalog.search()` calls `https://api.themoviedb.org/3/search/movie`; `externalIds()` calls `/3/movie/{id}/external_ids`. Throw `IOException` for non-2xx or malformed top-level JSON.

Use these helpers:

```kotlin
private fun releaseYear(date: String?): Int? =
    date?.takeIf { it.length >= 4 }?.take(4)?.toIntOrNull()

fun tmdbPosterUrl(path: String?): String? =
    path?.let { "https://image.tmdb.org/t/p/w500$it" }

fun tmdbBackdropUrl(path: String?): String? =
    path?.let { "https://image.tmdb.org/t/p/w1280$it" }
```

Do not treat absent overview/rating/images as errors.

- [ ] **Step 6: Run catalog tests and full unit suite**

```bash
./gradlew :app:testDebugUnitTest --tests '*TmdbMovieCatalogTest*'
./gradlew :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts .github/workflows/android-ci.yml app/src/main/java/sk/ziacik/androidstreamplayer/catalog app/src/test/java/sk/ziacik/androidstreamplayer/catalog
git commit -m "feat: add TMDB movie catalog"
```

---

### Task 2: Add debounced movie catalog search state

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchUiState.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchController.kt`
- Create: `app/src/test/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchControllerTest.kt`

**Interfaces:**
- Consumes: `MovieCatalog.search(query)` and `Movie` from Task 1.
- Produces:
  ```kotlin
  data class MovieSearchUiState(
      val query: String = "",
      val isSearching: Boolean = false,
      val results: List<Movie> = emptyList(),
      val errorMessage: String? = null,
      val focusedMovieId: Int? = null,
  )

  class MovieSearchController(
      private val scope: CoroutineScope,
      private val catalog: MovieCatalog,
      private val debounceMs: Long = 400L,
  ) {
      val state: StateFlow<MovieSearchUiState>
      fun setQuery(query: String)
      fun searchNow()
      fun retry()
      fun setFocusedMovie(tmdbId: Int?)
  }
  ```

- [ ] **Step 1: Write failing controller tests**

Cover minimum length, debounce, supersession, clear, error, retry, and focus memory:

```kotlin
@Test
fun `two characters search after debounce`() = runTest {
    val catalog = RecordingMovieCatalog()
    val controller = MovieSearchController(this, catalog, debounceMs = 400)

    controller.setQuery("Al")
    advanceTimeBy(399)
    assertTrue(catalog.queries.isEmpty())
    advanceTimeBy(1)
    advanceUntilIdle()

    assertEquals(listOf("Al"), catalog.queries)
}

@Test
fun `one character does not search and clears previous results`() = runTest { /* seed results, then setQuery("A") */ }

@Test
fun `newer query wins over slower older request`() = runTest { /* delayed fake responses */ }

@Test
fun `clearing query resets landing state`() = runTest { /* assert query/results/error/isSearching */ }

@Test
fun `focused movie id is retained independently from search results`() = runTest {
    val controller = MovieSearchController(this, RecordingMovieCatalog())
    controller.setFocusedMovie(603)
    assertEquals(603, controller.state.value.focusedMovieId)
}
```

- [ ] **Step 2: Run tests and verify failure**

```bash
./gradlew :app:testDebugUnitTest --tests '*MovieSearchControllerTest*'
```

Expected: FAIL because controller/state are missing.

- [ ] **Step 3: Implement cancellation-safe debounced search**

Use one `Job?` for debounce/search. Every `setQuery` cancels the previous job. Trim only for deciding/searching; preserve typed text in state. Use a monotonically increasing request generation or cancellation semantics so a slow old response cannot overwrite a newer query.

Core behavior:

```kotlin
fun setQuery(query: String) {
    searchJob?.cancel()
    _state.value = _state.value.copy(query = query, errorMessage = null)
    val normalized = query.trim()
    if (normalized.length < 2) {
        _state.value = _state.value.copy(isSearching = false, results = emptyList())
        return
    }
    searchJob = scope.launch {
        delay(debounceMs)
        performSearch(normalized)
    }
}
```

`searchNow()` cancels the pending debounce and immediately searches if length >= 2. `retry()` reuses the latest normalized query.

- [ ] **Step 4: Run targeted and full unit tests**

```bash
./gradlew :app:testDebugUnitTest --tests '*MovieSearchControllerTest*'
./gradlew :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchUiState.kt app/src/main/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchController.kt app/src/test/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchControllerTest.kt
git commit -m "feat: add debounced movie search state"
```

---

### Task 3: Convert Knaben to movie-aware fallback search

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/search/MovieTorrentSearchRequest.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentSearchProvider.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/search/KnabenTorrentSearchProvider.kt`
- Modify or remove: `app/src/main/java/sk/ziacik/androidstreamplayer/search/FakeTorrentSearchProvider.kt`
- Modify: `app/src/test/java/sk/ziacik/androidstreamplayer/search/KnabenTorrentSearchProviderTest.kt`
- Modify/remove legacy expectations in: `app/src/test/java/sk/ziacik/androidstreamplayer/search/FakeTorrentSearchProviderTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class MovieTorrentSearchRequest(
      val tmdbId: Int,
      val imdbId: String?,
      val title: String,
      val originalTitle: String,
      val year: Int?,
  )

  fun interface TorrentSearchProvider {
      suspend fun search(movie: MovieTorrentSearchRequest): List<TorrentSearchResult>
  }
  ```

- [ ] **Step 1: Replace old Knaben test expectations with movie-aware tests**

Add tests that inspect every recorded request body:

```kotlin
@Test
fun `movie search issues ordered unique fallback queries and movie category only`() = runTest {
    val transport = QueueTorrentSearchTransport(emptyList(4))
    val provider = KnabenTorrentSearchProvider(transport)

    provider.search(
        MovieTorrentSearchRequest(
            tmdbId = 603,
            imdbId = "tt0133093",
            title = "The Matrix",
            originalTitle = "The Matrix",
            year = 1999,
        ),
    )

    assertEquals(listOf("The Matrix 1999", "The Matrix"), transport.queries())
    transport.requests.forEach { request ->
        val categories = JSONObject(request.bodyUtf8()).getJSONArray("categories")
        assertEquals(listOf(3_000_000), List(categories.length()) { categories.getInt(it) })
    }
}
```

Add one movie whose translated/original titles differ and assert all four query forms in exact order.

- [ ] **Step 2: Add failing merge/dedupe/sort tests**

Return overlapping magnets from different fallback responses:

```kotlin
@Test
fun `fallback results merge by btih and sort by seeders descending`() = runTest {
    // response 1: hash ABC, 20 seeders
    // response 2: same hash ABC, 40 seeders + hash DEF, 100 seeders
    // expect DEF first and only one ABC, retaining the healthier duplicate
}
```

Also cover lowercase/uppercase BTIH normalization and a magnet without usable `xt=urn:btih:` falling back to stable result ID.

- [ ] **Step 3: Run Knaben tests and verify failure**

```bash
./gradlew :app:testDebugUnitTest --tests '*KnabenTorrentSearchProviderTest*'
```

Expected: FAIL because provider still accepts a string and searches movie+TV once.

- [ ] **Step 4: Implement query generation**

Add a pure helper inside the provider or as an internal function:

```kotlin
internal fun fallbackQueries(movie: MovieTorrentSearchRequest): List<String> = buildList {
    fun addQuery(value: String) {
        val normalized = value.trim().replace(Regex("\\s+"), " ")
        if (normalized.isNotBlank() && none { it.equals(normalized, ignoreCase = true) }) add(normalized)
    }
    movie.year?.let { year -> addQuery("${movie.originalTitle} $year") }
    movie.year?.let { year -> addQuery("${movie.title} $year") }
    addQuery(movie.originalTitle)
    addQuery(movie.title)
}
```

- [ ] **Step 5: Split one-query HTTP search from fallback orchestration**

Retain existing parsing in a private `searchQuery(query: String)` method. Change categories to movie only. Public `search(movie)` loops through every fallback query, collects results, deduplicates, and sorts.

Use a case-insensitive BTIH key:

```kotlin
private fun infoHash(magnetUri: String): String? =
    Regex("(?i)(?:[?&]xt=urn:btih:)([A-Za-z0-9]+)")
        .find(magnetUri)
        ?.groupValues
        ?.get(1)
        ?.lowercase()
```

When duplicates share a hash, keep the item with the higher `seeders ?: -1`.

Final ordering:

```kotlin
.sortedWith(compareByDescending<TorrentSearchResult> { it.seeders ?: -1 }.thenBy { it.title })
```

- [ ] **Step 6: Update fake provider to the new contract**

If instrumentation tests still benefit from `FakeTorrentSearchProvider`, make it accept `MovieTorrentSearchRequest` and derive fake release names from `movie.title`. Otherwise delete it and use inline fakes in tests.

- [ ] **Step 7: Run search tests and full unit suite**

```bash
./gradlew :app:testDebugUnitTest --tests '*KnabenTorrentSearchProviderTest*'
./gradlew :app:testDebugUnitTest
```

At this point legacy `SearchControllerTest` may fail to compile because its provider contract is obsolete. Do not patch the old controller deeply; Task 4 replaces its responsibilities. If compilation blocks Task 3, minimally adapt the legacy test/provider call sites to `MovieTorrentSearchRequest` without changing behavior, then remove them in Task 4.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/sk/ziacik/androidstreamplayer/search app/src/test/java/sk/ziacik/androidstreamplayer/search
git commit -m "feat: search Knaben by movie identity"
```

---

### Task 4: Split torrent discovery from playback preparation

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentSearchUiState.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentSearchController.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/playback/PlaybackUiState.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/playback/PlaybackController.kt`
- Create: `app/src/test/java/sk/ziacik/androidstreamplayer/search/TorrentSearchControllerTest.kt`
- Create: `app/src/test/java/sk/ziacik/androidstreamplayer/playback/PlaybackControllerTest.kt`
- Remove after tests migrate: `app/src/main/java/sk/ziacik/androidstreamplayer/search/SearchController.kt`
- Remove after tests migrate: `app/src/main/java/sk/ziacik/androidstreamplayer/search/SearchUiState.kt`
- Remove after tests migrate: `app/src/test/java/sk/ziacik/androidstreamplayer/search/SearchControllerTest.kt`

**Interfaces:**
- Consumes: `MovieCatalog.externalIds`, `TorrentSearchProvider.search(movieRequest)`, existing `TorrentStreamer`.
- Produces:
  ```kotlin
  data class TorrentSearchUiState(
      val movie: Movie? = null,
      val isSearching: Boolean = false,
      val results: List<TorrentSearchResult> = emptyList(),
      val errorMessage: String? = null,
  )

  class TorrentSearchController(...) {
      val state: StateFlow<TorrentSearchUiState>
      fun open(movie: Movie)
      fun retry()
      fun clear()
  }

  data class PlaybackUiState(
      val selectedResult: TorrentSearchResult? = null,
      val status: String? = null,
  )

  class PlaybackController(...) {
      val state: StateFlow<PlaybackUiState>
      fun play(result: TorrentSearchResult)
      fun playMagnet(magnet: String)
      fun exit()
  }
  ```

- [ ] **Step 1: Write failing torrent controller tests**

```kotlin
@Test
fun `opening movie resolves imdb id then searches torrents`() = runTest {
    val catalog = FakeMovieCatalog(externalIds = MovieExternalIds("tt0133093"))
    val provider = RecordingTorrentProvider(results = listOf(result))
    val controller = TorrentSearchController(this, catalog, provider)

    controller.open(matrixMovie)
    advanceUntilIdle()

    assertEquals("tt0133093", provider.requests.single().imdbId)
    assertEquals(listOf(result), controller.state.value.results)
}

@Test
fun `external id failure still searches by title and year`() = runTest {
    val catalog = FakeMovieCatalog(externalIdsError = IOException("boom"))
    val provider = RecordingTorrentProvider()
    val controller = TorrentSearchController(this, catalog, provider)

    controller.open(matrixMovie)
    advanceUntilIdle()

    assertEquals(null, provider.requests.single().imdbId)
    assertNull(controller.state.value.errorMessage)
}

@Test
fun `torrent provider failure exposes retryable error`() = runTest { /* then retry() repeats same movie */ }
```

- [ ] **Step 2: Write failing playback controller tests by moving existing behavior**

Port the useful assertions from `SearchControllerTest`:

```kotlin
@Test
fun `play prepares torrent and reports playing`() = runTest { /* existing streamer/source assertion */ }

@Test
fun `stream preparation failure reports stream failed`() = runTest { /* existing failure assertion */ }

@Test
fun `play magnet creates direct torrent result without search provider`() = runTest {
    val controller = PlaybackController(this, streamer, onStreamReady)
    controller.playMagnet("magnet:?xt=urn:btih:0123456789abcdef")
    advanceUntilIdle()
    assertEquals("Magnet", controller.state.value.selectedResult!!.source)
}

@Test
fun `exit clears transient playback state`() = runTest { /* selected/status null */ }
```

- [ ] **Step 3: Run targeted tests and verify failure**

```bash
./gradlew :app:testDebugUnitTest --tests '*TorrentSearchControllerTest*' --tests '*PlaybackControllerTest*'
```

Expected: FAIL because new controllers are missing.

- [ ] **Step 4: Implement `TorrentSearchController`**

`open(movie)` sets loading immediately, then tries external IDs with `runCatching`. Build `MovieTorrentSearchRequest` with the resolved IMDb ID when available. External-ID failure is swallowed for lookup purposes; provider failure becomes `errorMessage = "Search failed"`. `retry()` repeats the same `movie`.

When `open()` is called for a different movie, cancel the previous job so stale results cannot appear on the new detail.

- [ ] **Step 5: Implement `PlaybackController`**

Move the existing preparation logic verbatim in behavior:

```text
play(result)
  -> selectedResult=result, status="Preparing stream…"
  -> streamer.prepare(result)
  -> onStreamReady(source)
  -> status="Playing"
```

Preserve current failure strings (`Streaming unavailable`, `Playback failed`, `Stream failed`) so `KinoPlayerScreen` integration does not regress.

`playMagnet()` constructs a `TorrentSearchResult(id="direct-magnet", title="Magnet torrent", source="Magnet", magnetUri=magnet)` and calls `play()`.

- [ ] **Step 6: Remove legacy combined controller/state and run unit suite**

Delete `SearchController.kt`, `SearchUiState.kt`, and old `SearchControllerTest.kt` once all callers have migrated or temporarily stubbed in preparation for Tasks 5–7.

```bash
./gradlew :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/sk/ziacik/androidstreamplayer/search app/src/main/java/sk/ziacik/androidstreamplayer/playback app/src/test/java/sk/ziacik/androidstreamplayer/search app/src/test/java/sk/ziacik/androidstreamplayer/playback
git commit -m "refactor: split torrent search from playback"
```

---

### Task 5: Build the Netflix-style movie poster search screen

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/MoviePosterCard.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/MovieSearchScreen.kt`
- Create: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/MovieSearchScreenTest.kt`

**Interfaces:**
- Consumes: `MovieSearchController`, `Movie`, `tmdbPosterUrl()`.
- Produces:
  ```kotlin
  @Composable
  fun MovieSearchScreen(
      controller: MovieSearchController,
      onMovieSelected: (Movie) -> Unit,
      modifier: Modifier = Modifier,
  )
  ```

- [ ] **Step 1: Write the failing Compose UI behavior test**

Use a fake `MovieCatalog` and `MovieSearchController(debounceMs = 0)`:

```kotlin
@Test
fun movieSearchShowsPosterResultsAndSelection() {
    var selected: Movie? = null
    composeRule.setContent {
        MovieSearchScreen(controller = controller, onMovieSelected = { selected = it })
    }

    composeRule.onNodeWithTag("movie-search-field").performTextInput("Matrix")
    composeRule.waitUntil(5_000) {
        composeRule.onAllNodesWithTag("movie-603").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("movie-603").performClick()

    assertEquals(603, selected?.tmdbId)
}
```

Add a test for empty/error copy and semantics tags rather than pixel styling.

- [ ] **Step 2: Run instrumentation compile/test target and verify failure**

```bash
./gradlew :app:assembleAndroidTest
```

Expected: compilation FAIL because screen/card do not exist.

- [ ] **Step 3: Implement `MoviePosterCard`**

Use Coil `AsyncImage` with poster URL, 2:3 aspect ratio, rounded corners, `ContentScale.Crop`, and a deterministic focus animation around 1.05×. The card must have `Modifier.testTag("movie-${movie.tmdbId}")`.

When focused, show title/year immediately below the poster or in a compact overlay; unfocused posters should remain visually clean. Missing poster uses a styled placeholder containing the movie title rather than a broken image icon.

- [ ] **Step 4: Implement `MovieSearchScreen`**

Landing state keeps the current Kino cinematic background/brand language. Results state collapses the hero so posters dominate.

Use a `LazyVerticalGrid` with fixed 5 or 6 columns appropriate for 1080p TV spacing. Search field stays at the top, starts focused on launch, and has `testTag("movie-search-field")`.

Implement deterministic focus requesters:

- keep a `FocusRequester` for search;
- keep a `FocusRequester` for the first visible/result item when results arrive;
- set `focusProperties { down = firstResultRequester }` on search when results exist;
- first-row poster cards use `focusProperties { up = searchRequester }`;
- each card calls `controller.setFocusedMovie(movie.tmdbId)` on focus.

On recomposition/back-navigation, request focus for the card whose ID equals `state.focusedMovieId` after the grid is populated.

- [ ] **Step 5: Add catalog loading/error/empty states without replacing the screen**

Loading is a compact line/spinner under search. Error exposes a Retry action calling `controller.retry()`. Empty results display `No movies found` while keeping the search field and background visible.

- [ ] **Step 6: Compile instrumentation and run unit suite**

```bash
./gradlew :app:assembleAndroidTest :app:testDebugUnitTest
```

Expected: PASS compile/unit. Run the instrumentation test on an emulator/device when available:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=sk.ziacik.androidstreamplayer.ui.MovieSearchScreenTest
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/sk/ziacik/androidstreamplayer/ui/MoviePosterCard.kt app/src/main/java/sk/ziacik/androidstreamplayer/ui/MovieSearchScreen.kt app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/MovieSearchScreenTest.kt
git commit -m "feat: add TV movie poster search"
```

---

### Task 6: Build cinematic movie detail with torrent results

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/TorrentResults.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/MovieDetailScreen.kt`
- Create: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/MovieDetailScreenTest.kt`

**Interfaces:**
- Consumes: `Movie`, `TorrentSearchController`, `PlaybackController`, `tmdbPosterUrl`, `tmdbBackdropUrl`.
- Produces:
  ```kotlin
  @Composable
  fun MovieDetailScreen(
      movie: Movie,
      torrentController: TorrentSearchController,
      onPlay: (TorrentSearchResult) -> Unit,
      onBack: () -> Unit,
      modifier: Modifier = Modifier,
  )
  ```

- [ ] **Step 1: Write failing detail UI test**

```kotlin
@Test
fun detailShowsMovieAndSelectableTorrent() {
    var selected: TorrentSearchResult? = null
    composeRule.setContent {
        MovieDetailScreen(
            movie = matrixMovie,
            torrentController = controllerWithResults,
            onPlay = { selected = it },
            onBack = {},
        )
    }

    composeRule.onNodeWithText("The Matrix").assertIsDisplayed()
    composeRule.onNodeWithText("1999").assertIsDisplayed()
    composeRule.onNodeWithTag("torrent-hit-1").performClick()
    assertEquals("hit-1", selected?.id)
}
```

Add tests for inline loading, no versions, and retry button.

- [ ] **Step 2: Run instrumentation compile and verify failure**

```bash
./gradlew :app:assembleAndroidTest
```

Expected: compilation FAIL because detail components do not exist.

- [ ] **Step 3: Implement `TorrentResults`**

Extract the useful release-card styling from current `SearchScreen` but make rows denser. Each row shows:

```text
[2160p] Release.Title...              184 seeds   17.5 GB
```

Use real seed count (`"$seeders seeds"`), not `Great availability`. Preserve quality badge. Focused row gets a strong border/scale and `PLAY` accent. Tag rows `torrent-${result.id}`.

Use a `FocusRequester` on the first result and request it once when the result list changes from empty to non-empty.

- [ ] **Step 4: Implement `MovieDetailScreen`**

On first composition for `movie.tmdbId`, call:

```kotlin
LaunchedEffect(movie.tmdbId) {
    torrentController.open(movie)
}
```

Render backdrop with `AsyncImage` and a dark horizontal/vertical readability gradient. Poster on leading side; title, year, rating, overview beside it. The release section stays visible in the same screen and reflects `TorrentSearchUiState`.

Use explicit back handling (`BackHandler(onBack = onBack)`) so Back never accidentally exits the Activity while on detail.

- [ ] **Step 5: Keep detail visible through all torrent states**

`isSearching` → compact `Finding versions…` indicator in release area.

`errorMessage != null` → `Couldn’t find versions` + Retry.

`results.isEmpty()` after completed search → `No versions found`.

Results → focusable `LazyColumn` rows.

- [ ] **Step 6: Compile/run tests**

```bash
./gradlew :app:assembleAndroidTest :app:testDebugUnitTest
```

When a device/emulator is available:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=sk.ziacik.androidstreamplayer.ui.MovieDetailScreenTest
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/sk/ziacik/androidstreamplayer/ui/TorrentResults.kt app/src/main/java/sk/ziacik/androidstreamplayer/ui/MovieDetailScreen.kt app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/MovieDetailScreenTest.kt
git commit -m "feat: add cinematic movie detail"
```

---

### Task 7: Wire Catalog → Detail → Player in `KinoApp` and `MainActivity`

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/KinoApp.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/MainActivity.kt`
- Remove: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/SearchScreen.kt`
- Remove: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/SearchScreenTest.kt`
- Create: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/KinoAppFlowTest.kt`

**Interfaces:**
- Consumes: `MovieSearchController`, `TorrentSearchController`, `PlaybackController`, existing `KinoPlayerScreen` and `Player`.
- Produces:
  ```kotlin
  @Composable
  fun KinoApp(
      movieSearchController: MovieSearchController,
      torrentSearchController: TorrentSearchController,
      playbackController: PlaybackController,
      player: Player,
      modifier: Modifier = Modifier,
  )
  ```

- [ ] **Step 1: Write the failing end-to-end Compose flow test with fakes**

Test the state transitions without network/TorrServer:

```text
enter Matrix
→ movie-603 appears
→ select movie-603
→ detail title appears
→ torrent-hit-1 appears
→ select torrent-hit-1
→ playback status transitions
→ Back/exit returns to detail
→ Back returns to catalog
→ movie-603 is present and stored focusedMovieId remains 603
```

Use fake catalog/provider/streamer and a fake or nullable player seam if needed. If `KinoPlayerScreen` requires a real `Player`, test the app coordinator with playback state up to the Player branch and retain existing `KinoPlayerOverlayTest` for player rendering.

- [ ] **Step 2: Run instrumentation compile and verify failure**

```bash
./gradlew :app:assembleAndroidTest
```

Expected: FAIL because `KinoApp` does not exist.

- [ ] **Step 3: Implement `KinoApp` linear coordinator**

Keep only the selected movie as top-level navigation state:

```kotlin
var selectedMovie by remember { mutableStateOf<Movie?>(null) }
val playbackState by playbackController.state.collectAsState()

when {
    playbackState.status == "Playing" -> KinoPlayerScreen(
        player = player,
        result = playbackState.selectedResult,
        onExit = playbackController::exit,
    )
    selectedMovie != null -> MovieDetailScreen(
        movie = selectedMovie!!,
        torrentController = torrentSearchController,
        onPlay = playbackController::play,
        onBack = {
            torrentSearchController.clear()
            selectedMovie = null
        },
    )
    else -> MovieSearchScreen(
        controller = movieSearchController,
        onMovieSelected = { selectedMovie = it },
    )
}
```

When playback exits, `selectedMovie` stays intact, so the user returns to the same detail. When detail backs out, `MovieSearchController` still owns query/results/focused movie, so catalog context is restored.

Also render the existing preparation/error state over detail while `PlaybackController.status` is non-null but not `Playing`, so selecting a torrent still shows `Preparing stream…` feedback.

- [ ] **Step 4: Rewire `MainActivity`**

Construct:

```kotlin
val movieCatalog = TmdbMovieCatalog(apiKey = BuildConfig.TMDB_API_KEY)
val movieSearchController = MovieSearchController(appScope, movieCatalog)
val torrentProvider = KnabenTorrentSearchProvider()
val torrentSearchController = TorrentSearchController(appScope, movieCatalog, torrentProvider)
val playbackController = PlaybackController(
    scope = appScope,
    streamer = torrentStreamer,
    onStreamReady = { source ->
        playerPort.prepare(source)
        playerPort.play()
    },
)
```

Pass those to `KinoApp`.

Preserve current lifecycle behavior (`pause` on stop, release on destroy, TorrServer shutdown).

Replace old magnet routing with:

```kotlin
private fun startMagnet(magnet: String) {
    playbackController.playMagnet(magnet)
}
```

Do not route magnets through the movie catalog.

- [ ] **Step 5: Delete old torrent-first UI**

Remove `SearchScreen.kt` and its instrumentation test. Confirm no production references to `SearchController`, `SearchUiState`, or old free-text torrent search remain:

```bash
! grep -R "SearchController\|SearchUiState\|provider.search(query" app/src/main app/src/test app/src/androidTest
```

- [ ] **Step 6: Compile all debug/test artifacts**

```bash
TMDB_API_KEY=ci-placeholder ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleAndroidTest --stacktrace
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/sk/ziacik/androidstreamplayer/MainActivity.kt app/src/main/java/sk/ziacik/androidstreamplayer/ui app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui
git commit -m "feat: wire movie catalog to Kino player"
```

---

### Task 8: Hardening, TV navigation verification, and final branch validation

**Files:**
- Modify as needed: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/MovieSearchScreenTest.kt`
- Modify as needed: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/MovieDetailScreenTest.kt`
- Modify as needed: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/KinoAppFlowTest.kt`
- Modify as needed: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/MovieSearchScreen.kt`
- Modify as needed: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/MovieDetailScreen.kt`
- Modify as needed: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/TorrentResults.kt`

**Interfaces:**
- Consumes all completed feature boundaries.
- Produces a branch ready for review/PR.

- [ ] **Step 1: Add/finish D-pad focus assertions**

Where Compose test APIs are reliable, use key input/focus assertions to verify:

```text
initial focus = movie-search-field
search results loaded
Down from search = first movie
Up from first row = search
select movie
first torrent receives focus when results load
Back from detail = selected movie focus restored
```

If the test framework cannot reliably synthesize TV key events for one transition, keep a unit-testable pure focus/navigation helper rather than dropping the behavior. Do not rely only on manual testing.

- [ ] **Step 2: Verify no TV content leaks into movie-only Knaben requests**

```bash
grep -R "TV_CATEGORY\|2_000_000" app/src/main/java/sk/ziacik/androidstreamplayer/search && exit 1 || true
```

Expected: no production TV category in the movie provider.

- [ ] **Step 3: Verify no real TMDB credential is committed**

```bash
git grep -nE 'TMDB_API_KEY\s*[=:]\s*[A-Za-z0-9_-]{20,}|api_key=[A-Za-z0-9_-]{20,}' -- . ':!docs/superpowers/plans/*'
```

Expected: no credential literal; only property/env/BuildConfig wiring.

- [ ] **Step 4: Run full unit and compile validation**

```bash
TMDB_API_KEY=ci-placeholder ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleAndroidTest --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Run instrumentation tests on Android TV/emulator when available**

```bash
TMDB_API_KEY=ci-placeholder ./gradlew :app:connectedDebugAndroidTest --stacktrace
```

Expected: existing player overlay tests plus new catalog/detail/flow tests PASS.

- [ ] **Step 6: Smoke-test with a real TMDB key on a TV device**

Build/deploy using a non-committed key:

```bash
./gradlew :app:assembleDebug -PtmdbApiKey="$TMDB_API_KEY"
./deploy-debug
```

Verify manually with `The Matrix` or `Alien`:

1. posters appear from TMDB;
2. D-pad grid navigation is predictable;
3. detail shows correct backdrop/title/year/overview;
4. Knaben returns movie releases automatically;
5. release rows show real seed count/size/quality;
6. selected release reaches the existing player;
7. Back returns player → detail → same poster/search context.

- [ ] **Step 7: Compare branch against master and inspect scope**

```bash
git diff --stat master...HEAD
git diff master...HEAD -- app/src/main/java app/src/test app/src/androidTest app/build.gradle.kts gradle/libs.versions.toml .github/workflows/android-ci.yml
```

Confirm there are no unrelated player/TorrServer/theme rewrites.

- [ ] **Step 8: Commit any hardening fixes**

```bash
git add app/src .github/workflows/android-ci.yml app/build.gradle.kts gradle/libs.versions.toml
git commit -m "test: harden movie catalog TV flow"
```

If there are no changes after validation, skip the commit.

---

## Final Acceptance Checklist

- [ ] Fresh launch shows Kino movie search, not raw torrent search.
- [ ] Query length < 2 does not call TMDB.
- [ ] Search is debounced ~400 ms and stale requests cannot overwrite newer ones.
- [ ] TMDB posters are cached/loaded through Coil and missing images degrade gracefully.
- [ ] Movie detail retains TMDB ID and resolves IMDb ID without blocking title fallback.
- [ ] Knaben uses movie-only category and all four defined fallback forms when distinct.
- [ ] Torrent results merge, deduplicate by info-hash, and sort by seeders descending.
- [ ] Detail remains visible while torrent discovery loads/fails/returns empty.
- [ ] Real seeder counts are shown.
- [ ] Selecting a release uses existing TorrServer → Media3 playback.
- [ ] External magnet intent still works without going through TMDB.
- [ ] Back from player returns to detail; Back from detail returns to same catalog query/result/focus.
- [ ] Unit tests pass.
- [ ] Debug APK and instrumentation APK compile.
- [ ] Connected instrumentation tests pass when a TV/emulator is available.
- [ ] No real TMDB credential is committed.
- [ ] `master` remains untouched until deliberate merge/review.
