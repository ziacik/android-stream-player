# Kino Movie Catalog Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the torrent-first landing screen with a zero-configuration TMDB movie catalog where users browse posters, open a cinematic detail, choose a Knaben torrent release, and play it through the existing TorrServer/Media3 player.

**Architecture:** Keep the app single-module. `MovieCatalog` owns TMDB metadata; `MovieSearchController` owns catalog query/results/focus; `TorrentSearchController` owns movie-to-release discovery; `PlaybackController` owns torrent preparation/player transition. `KinoApp` composes the linear Catalog → Detail → Player flow and keeps catalog state alive while detail/player screens are shown.

**Tech Stack:** Kotlin 2.3.21, Android SDK 36/minSdk 26, Jetpack Compose BOM 2026.06.00, OkHttp 5.1.0, Coroutines 1.10.2, Media3 1.11.0, Coil 3.4.0, org.json, JUnit 4, Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-08-30-movie-catalog-search-design.md`

## Global Constraints

- Movies only; no TV series, seasons, or episodes.
- End users configure nothing: TMDB and Knaben are built into the app flow.
- TMDB API v3 search uses `/3/search/movie`; IMDb lookup uses `/3/movie/{movie_id}/external_ids`.
- Send `include_adult=false` and `language=en-US` to TMDB movie search.
- TMDB credential is embedded as `BuildConfig.TMDB_API_KEY`; never commit a real credential.
- Release builds must fail clearly when the TMDB key is missing; debug/test builds may use `ci-placeholder`.
- Use Coil 3.4.0 for poster/backdrop loading. It is compiled with Kotlin 2.3.10 and is compatible with the project’s Kotlin 2.3.21; do not upgrade the project Kotlin as part of this feature.
- Knaben remains the only torrent provider in this iteration and requires no user configuration.
- Knaben fallback order is `originalTitle + year`, `title + year`, `originalTitle`, `title`; skip duplicate strings but run every distinct fallback.
- Merge fallback results, deduplicate primarily by normalized BTIH info-hash, keep the healthier duplicate, then sort by seeders descending.
- Knaben uses only the movie category `3_000_000` in this flow.
- Preserve existing TorrServer, Media3, and Kino player OSD behavior.
- Back from Player → same Detail; Back from Detail → same catalog query/results/scroll/focused poster.
- Search focus: initial focus on input; Down enters the poster grid; Up from first grid row returns to input.
- Detail focus: once torrent results arrive, first torrent receives focus exactly once for that result set.
- All work remains on `feature/movie-catalog-search` until deliberate review/merge.

---

## File Map

### Create

- `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/Movie.kt`
- `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/MovieCatalog.kt`
- `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/TmdbMovieCatalog.kt`
- `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchUiState.kt`
- `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchController.kt`
- `app/src/main/java/sk/ziacik/androidstreamplayer/search/MovieTorrentSearchRequest.kt`
- `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentSearchUiState.kt`
- `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentSearchController.kt`
- `app/src/main/java/sk/ziacik/androidstreamplayer/playback/PlaybackUiState.kt`
- `app/src/main/java/sk/ziacik/androidstreamplayer/playback/PlaybackController.kt`
- `app/src/main/java/sk/ziacik/androidstreamplayer/ui/MoviePosterCard.kt`
- `app/src/main/java/sk/ziacik/androidstreamplayer/ui/MovieSearchScreen.kt`
- `app/src/main/java/sk/ziacik/androidstreamplayer/ui/TorrentResults.kt`
- `app/src/main/java/sk/ziacik/androidstreamplayer/ui/MovieDetailScreen.kt`
- `app/src/main/java/sk/ziacik/androidstreamplayer/ui/KinoApp.kt`
- corresponding unit/instrumentation test files listed per task below.

### Modify

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `.github/workflows/android-ci.yml`
- `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentSearchProvider.kt`
- `app/src/main/java/sk/ziacik/androidstreamplayer/search/KnabenTorrentSearchProvider.kt`
- `app/src/main/java/sk/ziacik/androidstreamplayer/search/FakeTorrentSearchProvider.kt`
- `app/src/main/java/sk/ziacik/androidstreamplayer/MainActivity.kt`

### Delete after migration

- `app/src/main/java/sk/ziacik/androidstreamplayer/search/SearchController.kt`
- `app/src/main/java/sk/ziacik/androidstreamplayer/search/SearchUiState.kt`
- `app/src/main/java/sk/ziacik/androidstreamplayer/ui/SearchScreen.kt`
- `app/src/test/java/sk/ziacik/androidstreamplayer/search/SearchControllerTest.kt`
- `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/SearchScreenTest.kt`

---

### Task 1: TMDB build config, model, and HTTP client

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `.github/workflows/android-ci.yml`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/Movie.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/MovieCatalog.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/TmdbMovieCatalog.kt`
- Test: `app/src/test/java/sk/ziacik/androidstreamplayer/catalog/TmdbMovieCatalogTest.kt`

**Interfaces:**

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

internal data class TmdbHttpResponse(val code: Int, val body: String)
internal fun interface TmdbHttpTransport {
    suspend fun execute(request: Request): TmdbHttpResponse
}
```

- [ ] **Step 1: Write failing TMDB request/mapping tests**

Add these concrete cases to `TmdbMovieCatalogTest`:

```kotlin
@Test
fun `search requests movie endpoint and maps metadata`() = runTest {
    val transport = RecordingTmdbTransport(
        TmdbHttpResponse(200, """{
          "results":[{
            "id":603,
            "title":"The Matrix",
            "original_title":"The Matrix",
            "release_date":"1999-03-30",
            "overview":"A hacker discovers reality is a simulation.",
            "vote_average":8.2,
            "poster_path":"/poster.jpg",
            "backdrop_path":"/backdrop.jpg"
          }]
        }"""),
    )
    val result = TmdbMovieCatalog("test-key", transport).search("Matrix").single()

    assertEquals(603, result.tmdbId)
    assertEquals(1999, result.releaseYear)
    assertEquals("/3/search/movie", transport.request!!.url.encodedPath)
    assertEquals("Matrix", transport.request!!.url.queryParameter("query"))
    assertEquals("false", transport.request!!.url.queryParameter("include_adult"))
    assertEquals("en-US", transport.request!!.url.queryParameter("language"))
    assertEquals("test-key", transport.request!!.url.queryParameter("api_key"))
}

@Test
fun `missing optional metadata maps to null`() = runTest {
    val transport = RecordingTmdbTransport(
        TmdbHttpResponse(200, """{"results":[{"id":1,"title":"X","original_title":"X"}]}"""),
    )
    val movie = TmdbMovieCatalog("test-key", transport).search("X").single()

    assertNull(movie.releaseYear)
    assertNull(movie.overview)
    assertNull(movie.voteAverage)
    assertNull(movie.posterPath)
    assertNull(movie.backdropPath)
}

@Test
fun `external ids returns imdb id`() = runTest {
    val transport = RecordingTmdbTransport(TmdbHttpResponse(200, """{"imdb_id":"tt0133093"}"""))
    val ids = TmdbMovieCatalog("test-key", transport).externalIds(603)

    assertEquals("tt0133093", ids.imdbId)
    assertEquals("/3/movie/603/external_ids", transport.request!!.url.encodedPath)
}

@Test(expected = IOException::class)
fun `non successful response throws`() = runTest {
    TmdbMovieCatalog("test-key", RecordingTmdbTransport(TmdbHttpResponse(401, "{}"))).search("Matrix")
}
```

- [ ] **Step 2: Verify tests fail**

```bash
./gradlew :app:testDebugUnitTest --tests '*TmdbMovieCatalogTest*'
```

Expected: compile/test failure because catalog classes do not exist.

- [ ] **Step 3: Add Coil 3.4.0 and BuildConfig key wiring**

Add to `gradle/libs.versions.toml`:

```toml
coil = "3.4.0"

coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-okhttp = { module = "io.coil-kt.coil3:coil-network-okhttp", version.ref = "coil" }
```

Add near the top of `app/build.gradle.kts`:

```kotlin
val tmdbApiKey = providers.gradleProperty("tmdbApiKey")
    .orElse(providers.environmentVariable("TMDB_API_KEY"))
    .orElse("")
```

Inside `android`:

```kotlin
defaultConfig {
    buildConfigField("String", "TMDB_API_KEY", "\"${tmdbApiKey.get()}\"")
}

buildFeatures {
    compose = true
    buildConfig = true
}
```

Add dependencies:

```kotlin
implementation(libs.coil.compose)
implementation(libs.coil.network.okhttp)
```

Add release validation:

```kotlin
val verifyTmdbApiKey = tasks.register("verifyTmdbApiKey") {
    doLast {
        require(tmdbApiKey.get().isNotBlank()) {
            "TMDB_API_KEY or -PtmdbApiKey is required for release builds"
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyTmdbApiKey)
}
```

- [ ] **Step 4: Update CI**

In `.github/workflows/android-ci.yml`, add `feature/movie-catalog-search` to push branches and add at job level:

```yaml
env:
  TMDB_API_KEY: ci-placeholder
```

Tests must use fake transports and must not make TMDB network calls.

- [ ] **Step 5: Implement `TmdbMovieCatalog`**

Use OkHttp GET requests to:

```text
https://api.themoviedb.org/3/search/movie
https://api.themoviedb.org/3/movie/{movie_id}/external_ids
```

Map `release_date.take(4).toIntOrNull()` to `releaseYear`; map missing/blank optional fields to null; throw `IOException` on non-2xx or malformed top-level JSON.

Add image helpers in the same file:

```kotlin
fun tmdbPosterUrl(path: String?): String? =
    path?.takeIf(String::isNotBlank)?.let { "https://image.tmdb.org/t/p/w500$it" }

fun tmdbBackdropUrl(path: String?): String? =
    path?.takeIf(String::isNotBlank)?.let { "https://image.tmdb.org/t/p/w1280$it" }
```

- [ ] **Step 6: Run tests**

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

### Task 2: Debounced catalog search controller

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchUiState.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchController.kt`
- Test: `app/src/test/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchControllerTest.kt`

**Interfaces:**

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

- [ ] **Step 1: Write failing behavior tests**

Use a `RecordingMovieCatalog` whose `search` records queries and returns values from a mutable map.

```kotlin
@Test
fun `one character never searches`() = runTest {
    val catalog = RecordingMovieCatalog()
    val controller = MovieSearchController(this, catalog, 400)

    controller.setQuery("A")
    advanceUntilIdle()

    assertTrue(catalog.queries.isEmpty())
    assertTrue(controller.state.value.results.isEmpty())
}

@Test
fun `two characters search after debounce`() = runTest {
    val catalog = RecordingMovieCatalog()
    val controller = MovieSearchController(this, catalog, 400)

    controller.setQuery("Al")
    advanceTimeBy(399)
    assertTrue(catalog.queries.isEmpty())
    advanceTimeBy(1)
    advanceUntilIdle()

    assertEquals(listOf("Al"), catalog.queries)
}

@Test
fun `clearing query resets results and error`() = runTest {
    val movie = Movie(1, null, "Alien", "Alien", 1979, null, null, null, null)
    val catalog = RecordingMovieCatalog(results = mutableMapOf("Alien" to listOf(movie)))
    val controller = MovieSearchController(this, catalog, 0)

    controller.setQuery("Alien")
    advanceUntilIdle()
    controller.setQuery("")
    advanceUntilIdle()

    assertEquals("", controller.state.value.query)
    assertTrue(controller.state.value.results.isEmpty())
    assertNull(controller.state.value.errorMessage)
    assertFalse(controller.state.value.isSearching)
}

@Test
fun `failed query exposes error and retry repeats it`() = runTest {
    val catalog = RecordingMovieCatalog(errorQueries = mutableSetOf("Alien"))
    val controller = MovieSearchController(this, catalog, 0)

    controller.setQuery("Alien")
    advanceUntilIdle()
    assertEquals("Search failed", controller.state.value.errorMessage)

    catalog.errorQueries.clear()
    controller.retry()
    advanceUntilIdle()

    assertEquals(listOf("Alien", "Alien"), catalog.queries)
    assertNull(controller.state.value.errorMessage)
}

@Test
fun `new query cancels delayed old query`() = runTest {
    val catalog = DelayedMovieCatalog(
        delays = mapOf("Al" to 1_000L, "Alien" to 0L),
        results = mapOf(
            "Al" to listOf(Movie(1, null, "Old", "Old", null, null, null, null, null)),
            "Alien" to listOf(Movie(2, null, "Alien", "Alien", 1979, null, null, null, null)),
        ),
    )
    val controller = MovieSearchController(this, catalog, 0)

    controller.setQuery("Al")
    runCurrent()
    controller.setQuery("Alien")
    advanceUntilIdle()

    assertEquals(listOf(2), controller.state.value.results.map(Movie::tmdbId))
}

@Test
fun `focused movie id is remembered`() = runTest {
    val controller = MovieSearchController(this, RecordingMovieCatalog(), 0)
    controller.setFocusedMovie(603)
    assertEquals(603, controller.state.value.focusedMovieId)
}
```

- [ ] **Step 2: Verify failure**

```bash
./gradlew :app:testDebugUnitTest --tests '*MovieSearchControllerTest*'
```

- [ ] **Step 3: Implement debounce/cancellation**

Keep a single `Job?`. `setQuery()` cancels it, writes the typed query, and clears results for fewer than 2 trimmed characters. For valid input launch `delay(debounceMs)` then `performSearch()`. `searchNow()` cancels debounce and searches immediately. `retry()` searches the latest valid trimmed query. Do not clear `focusedMovieId` when navigating to detail/back.

- [ ] **Step 4: Run tests and commit**

```bash
./gradlew :app:testDebugUnitTest --tests '*MovieSearchControllerTest*'
./gradlew :app:testDebugUnitTest
git add app/src/main/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchUiState.kt app/src/main/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchController.kt app/src/test/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchControllerTest.kt
git commit -m "feat: add debounced movie search state"
```

---

### Task 3: Movie-aware Knaben fallback, dedupe, and sorting

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/search/MovieTorrentSearchRequest.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentSearchProvider.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/search/KnabenTorrentSearchProvider.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/search/FakeTorrentSearchProvider.kt`
- Modify: `app/src/test/java/sk/ziacik/androidstreamplayer/search/KnabenTorrentSearchProviderTest.kt`
- Modify: `app/src/test/java/sk/ziacik/androidstreamplayer/search/FakeTorrentSearchProviderTest.kt`

**Interfaces:**

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

- [ ] **Step 1: Replace old request test with exact fallback/category tests**

For `title="Matrix"`, `originalTitle="The Matrix"`, `year=1999`, enqueue four empty HTTP responses and assert recorded query bodies equal:

```kotlin
listOf("The Matrix 1999", "Matrix 1999", "The Matrix", "Matrix")
```

For `title=originalTitle="The Matrix"`, assert only:

```kotlin
listOf("The Matrix 1999", "The Matrix")
```

For every request assert categories are exactly `listOf(3_000_000)`, `order_by=seeders`, `order_direction=desc`, `hide_unsafe=true`, and `hide_xxx=true`.

- [ ] **Step 2: Add exact dedupe/sort test**

Queue these hits across two fallback responses:

```text
A1: magnet btih:ABC, seeders 20
A2: magnet btih:abc, seeders 40
D : magnet btih:DEF, seeders 100
N : magnet without btih, id="no-hash", seeders 5
```

Assert final IDs are `D`, `A2`, `no-hash` in that order; only one ABC survives.

- [ ] **Step 3: Verify old implementation fails**

```bash
./gradlew :app:testDebugUnitTest --tests '*KnabenTorrentSearchProviderTest*'
```

- [ ] **Step 4: Implement fallback generator and one-query helper**

Use:

```kotlin
internal fun fallbackQueries(movie: MovieTorrentSearchRequest): List<String> = buildList {
    fun addUnique(value: String) {
        val normalized = value.trim().replace(Regex("\\s+"), " ")
        if (normalized.isNotBlank() && none { it.equals(normalized, ignoreCase = true) }) add(normalized)
    }
    movie.year?.let { addUnique("${movie.originalTitle} $it") }
    movie.year?.let { addUnique("${movie.title} $it") }
    addUnique(movie.originalTitle)
    addUnique(movie.title)
}
```

Move the current HTTP/parsing logic to `private suspend fun searchQuery(query: String)`. Its categories array contains only `MOVIES_CATEGORY`.

- [ ] **Step 5: Implement dedupe and final ordering**

```kotlin
private fun infoHash(magnet: String): String? =
    Regex("(?i)[?&]xt=urn:btih:([A-Za-z0-9]+)")
        .find(magnet)
        ?.groupValues
        ?.get(1)
        ?.lowercase()
```

Key by `"hash:$hash"` when present, otherwise `"id:${result.id}"`. If a duplicate key exists, retain the result with the larger `seeders ?: -1`. Sort with:

```kotlin
compareByDescending<TorrentSearchResult> { it.seeders ?: -1 }
    .thenBy { it.title }
```

- [ ] **Step 6: Adapt fake provider to the new interface**

`FakeTorrentSearchProvider.search(movie)` returns the same fake quality options as today but uses `movie.title` as the release prefix. Update its unit test to call the provider with a `MovieTorrentSearchRequest`.

- [ ] **Step 7: Run tests and commit**

Temporarily adapt old `SearchControllerTest.RecordingProvider` to accept `MovieTorrentSearchRequest` solely so the suite compiles; Task 4 deletes that legacy controller/test.

```bash
./gradlew :app:testDebugUnitTest --tests '*KnabenTorrentSearchProviderTest*' --tests '*FakeTorrentSearchProviderTest*'
./gradlew :app:testDebugUnitTest
git add app/src/main/java/sk/ziacik/androidstreamplayer/search app/src/test/java/sk/ziacik/androidstreamplayer/search
git commit -m "feat: search Knaben by movie identity"
```

---

### Task 4: Split torrent discovery and playback controllers

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentSearchUiState.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentSearchController.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/playback/PlaybackUiState.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/playback/PlaybackController.kt`
- Test: `app/src/test/java/sk/ziacik/androidstreamplayer/search/TorrentSearchControllerTest.kt`
- Test: `app/src/test/java/sk/ziacik/androidstreamplayer/playback/PlaybackControllerTest.kt`
- Delete: `app/src/main/java/sk/ziacik/androidstreamplayer/search/SearchController.kt`
- Delete: `app/src/main/java/sk/ziacik/androidstreamplayer/search/SearchUiState.kt`
- Delete: `app/src/test/java/sk/ziacik/androidstreamplayer/search/SearchControllerTest.kt`

**Interfaces:**

```kotlin
data class TorrentSearchUiState(
    val movie: Movie? = null,
    val isSearching: Boolean = false,
    val results: List<TorrentSearchResult> = emptyList(),
    val errorMessage: String? = null,
)

class TorrentSearchController(
    private val scope: CoroutineScope,
    private val catalog: MovieCatalog,
    private val provider: TorrentSearchProvider,
) {
    val state: StateFlow<TorrentSearchUiState>
    fun open(movie: Movie)
    fun retry()
    fun clear()
}

data class PlaybackUiState(
    val selectedResult: TorrentSearchResult? = null,
    val status: String? = null,
)

class PlaybackController(
    private val scope: CoroutineScope,
    private val streamer: TorrentStreamer?,
    private val onStreamReady: (TorrentSource) -> Unit = {},
) {
    val state: StateFlow<PlaybackUiState>
    fun play(result: TorrentSearchResult)
    fun playMagnet(magnet: String)
    fun exit()
}
```

- [ ] **Step 1: Write torrent controller tests**

Exact cases:

1. `externalIds(603)` returns `tt0133093` → provider receives request with that ID.
2. external-ID lookup throws `IOException` → provider still called once with `imdbId=null`, no UI error if provider succeeds.
3. provider throws → state ends with `isSearching=false`, empty results, `errorMessage="Search failed"`.
4. after provider failure, clear the fake failure and call `retry()` → same movie searched again and results published.
5. call `open(movieA)` with delayed provider then `open(movieB)` → final state belongs only to B.

- [ ] **Step 2: Write playback controller tests by moving existing assertions**

Exact cases:

```kotlin
@Test
fun `play prepares selected torrent and reports playing`() = runTest {
    val selected = result("Alien.2160p")
    val source = TorrentSource("torrent://stream/Alien.mkv")
    var prepared: TorrentSearchResult? = null
    var opened: TorrentSource? = null
    val controller = PlaybackController(
        this,
        TorrentStreamer { prepared = it; source },
        onStreamReady = { opened = it },
    )

    controller.play(selected)
    assertEquals("Preparing stream…", controller.state.value.status)
    advanceUntilIdle()

    assertEquals(selected, prepared)
    assertEquals(source, opened)
    assertEquals("Playing", controller.state.value.status)
}

@Test
fun `play magnet creates direct result`() = runTest {
    val controller = PlaybackController(this, TorrentStreamer { TorrentSource("torrent://stream") })
    controller.playMagnet("magnet:?xt=urn:btih:0123456789abcdef")
    advanceUntilIdle()

    assertEquals("direct-magnet", controller.state.value.selectedResult!!.id)
    assertEquals("Magnet", controller.state.value.selectedResult!!.source)
}
```

Also assert streamer failure → `Stream failed`, null streamer → `Streaming unavailable`, callback failure → `Playback failed`, and `exit()` clears result/status.

- [ ] **Step 3: Verify tests fail**

```bash
./gradlew :app:testDebugUnitTest --tests '*TorrentSearchControllerTest*' --tests '*PlaybackControllerTest*'
```

- [ ] **Step 4: Implement `TorrentSearchController`**

`open(movie)` cancels the previous job, publishes loading with the selected movie, then resolves external IDs using `runCatching`. Search with `movie.copy(imdbId = resolvedId)` converted to `MovieTorrentSearchRequest`. External-ID failure does not set an error. Provider failure sets `Search failed`. `retry()` calls `open(state.value.movie!!)`. `clear()` cancels work and resets state.

- [ ] **Step 5: Implement `PlaybackController`**

Move preparation behavior from legacy `SearchController.select()`. Keep current status strings exactly. `playMagnet()` creates the direct result and delegates to `play()`.

- [ ] **Step 6: Delete legacy combined controller and run suite**

Delete `SearchController.kt`, `SearchUiState.kt`, and `SearchControllerTest.kt`. At this intermediate commit, production `SearchScreen.kt` will no longer compile; therefore in the same step replace its controller parameter with a temporary compile-only wrapper is forbidden. Instead defer deleting production `SearchController.kt` until Task 7, but delete the old unit test now. Keep a comment in the plan only—not production code—that Task 7 performs final deletion.

Run tests with the legacy production controller still present:

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

### Task 5: Netflix-style poster search UI

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/MoviePosterCard.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/MovieSearchScreen.kt`
- Test: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/MovieSearchScreenTest.kt`

**Interface:**

```kotlin
@Composable
fun MovieSearchScreen(
    controller: MovieSearchController,
    onMovieSelected: (Movie) -> Unit,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 1: Write failing poster search UI test**

Use a fake `MovieCatalog` returning Matrix and `MovieSearchController(debounceMs=0)`:

```kotlin
@Test
fun movieSearchShowsAndSelectsPoster() {
    var selected: Movie? = null
    composeRule.setContent {
        MovieSearchScreen(controller, onMovieSelected = { selected = it })
    }

    composeRule.onNodeWithTag("movie-search-field").performTextInput("Matrix")
    composeRule.waitUntil(5_000) {
        composeRule.onAllNodesWithTag("movie-603").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("movie-603").performClick()

    assertEquals(603, selected?.tmdbId)
}
```

Add a second test where fake catalog returns empty list; assert `No movies found`. Add a third where fake catalog throws; assert `Couldn’t search movies` and a `Retry` node.

- [ ] **Step 2: Verify instrumentation compilation fails**

```bash
./gradlew :app:assembleAndroidTest
```

- [ ] **Step 3: Implement `MoviePosterCard`**

Use `Card` + Coil `AsyncImage`, 2:3 aspect ratio, `ContentScale.Crop`, 18–20dp rounding. On focus animate scale to `1.05f`, show a strong secondary-color border, and reveal title + year. Add `testTag("movie-${movie.tmdbId}")`. When `posterPath` is null, render a dark placeholder with movie title centered.

The composable accepts `FocusRequester?`, `onFocus`, and `upFocusRequester?` so `MovieSearchScreen` controls navigation without embedding global state in the card.

- [ ] **Step 4: Implement `MovieSearchScreen` grid and focus restoration**

Use `LazyVerticalGrid(GridCells.Fixed(6))` for 1080p TV layout, a `LazyGridState`, and a search `FocusRequester`.

When results exist:

```kotlin
val focusedIndex = state.focusedMovieId?.let { id -> state.results.indexOfFirst { it.tmdbId == id } }
LaunchedEffect(state.results, state.focusedMovieId) {
    if (focusedIndex != null && focusedIndex >= 0) {
        gridState.scrollToItem(focusedIndex)
        posterRequesters.getValue(state.results[focusedIndex].tmdbId).requestFocus()
    }
}
```

If no remembered focus exists, Down from the search field targets the first poster. Every first-row poster has `focusProperties { up = searchRequester }`. Every focused card calls `controller.setFocusedMovie(movie.tmdbId)`.

Initial launch requests search focus only when `focusedMovieId == null`; this prevents Back from detail from stealing focus away from the restored poster.

- [ ] **Step 5: Implement compact states**

Keep the Kino cinematic background. Before results show the larger hero. Once results exist collapse header height and let posters dominate. Loading/error/empty content appears below the search input; never replace the whole screen.

- [ ] **Step 6: Compile/test and commit**

```bash
./gradlew :app:assembleAndroidTest :app:testDebugUnitTest
git add app/src/main/java/sk/ziacik/androidstreamplayer/ui/MoviePosterCard.kt app/src/main/java/sk/ziacik/androidstreamplayer/ui/MovieSearchScreen.kt app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/MovieSearchScreenTest.kt
git commit -m "feat: add TV movie poster search"
```

---

### Task 6: Cinematic detail and torrent result UI

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/TorrentResults.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/MovieDetailScreen.kt`
- Test: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/MovieDetailScreenTest.kt`

**Interface:**

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

- [ ] **Step 1: Write detail UI tests**

Create a controller with fake catalog/provider and assert these exact states in separate tests:

1. title `The Matrix`, year `1999`, overview text visible.
2. while provider is suspended, `Finding versions…` visible while title remains visible.
3. empty provider result → `No versions found`.
4. provider exception → `Couldn’t find versions` and `Retry` visible.
5. result `hit-1` → node tag `torrent-hit-1`; clicking it invokes `onPlay` with ID `hit-1`.

- [ ] **Step 2: Verify instrumentation compilation fails**

```bash
./gradlew :app:assembleAndroidTest
```

- [ ] **Step 3: Implement `TorrentResults`**

Rows show quality badge, full release title, `N seeds`, and formatted GiB. Reuse the existing result-card focus animation but remove `Great/Good/Limited availability`. Add `testTag("torrent-${result.id}")`.

Keep a first-result `FocusRequester`; `LaunchedEffect(results.map { it.id })` requests it only when the non-empty ID list changes. This prevents repeated focus steals during unrelated recompositions.

- [ ] **Step 4: Implement `MovieDetailScreen`**

Use `LaunchedEffect(movie.tmdbId) { torrentController.open(movie) }`. Render backdrop via `tmdbBackdropUrl()` behind dark gradients; render poster, title, year, rating, overview, then torrent section. Add `BackHandler(onBack = onBack)`.

The detail metadata must remain visible for loading, error, empty, and result states.

- [ ] **Step 5: Compile/test and commit**

```bash
./gradlew :app:assembleAndroidTest :app:testDebugUnitTest
git add app/src/main/java/sk/ziacik/androidstreamplayer/ui/TorrentResults.kt app/src/main/java/sk/ziacik/androidstreamplayer/ui/MovieDetailScreen.kt app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/MovieDetailScreenTest.kt
git commit -m "feat: add cinematic movie detail"
```

---

### Task 7: Wire Catalog → Detail → Player and remove old screen

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/KinoApp.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/MainActivity.kt`
- Delete: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/SearchScreen.kt`
- Delete: `app/src/main/java/sk/ziacik/androidstreamplayer/search/SearchController.kt`
- Delete: `app/src/main/java/sk/ziacik/androidstreamplayer/search/SearchUiState.kt`
- Delete: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/SearchScreenTest.kt`
- Test: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/KinoAppFlowTest.kt`

**Interface:**

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

- [ ] **Step 1: Write end-to-end Compose flow test using fakes and a real empty ExoPlayer**

In the test create `ExoPlayer.Builder(context).build()` and release it in `@After`. Fake catalog returns Matrix; fake torrent provider returns `hit-1`; fake streamer returns `TorrentSource("torrent://test")`; playback callback does nothing so state still becomes `Playing`.

Exercise:

```text
input Matrix
→ click movie-603
→ assert The Matrix detail
→ click torrent-hit-1
→ assert Kino player root/test tag from existing player UI
→ invoke player exit/back callback through UI Back behavior
→ assert The Matrix detail again
→ press Back
→ assert movie-603 exists and controller.focusedMovieId == 603
```

If existing `KinoPlayerScreen` lacks a stable root tag, add `testTag("kino-player")` to its outer root only; do not alter its navigation behavior.

- [ ] **Step 2: Verify compilation fails before wiring**

```bash
./gradlew :app:assembleAndroidTest
```

- [ ] **Step 3: Implement `KinoApp`**

Keep `selectedMovie` in `remember` and observe playback state:

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

When playback status is `Preparing stream…` or an error, keep `MovieDetailScreen` visible and overlay/use an inline status surface driven by `PlaybackUiState`. Do not replace detail with a blank screen.

- [ ] **Step 4: Rewire `MainActivity`**

Construct once in `onCreate`:

```kotlin
val movieCatalog = TmdbMovieCatalog(BuildConfig.TMDB_API_KEY)
movieSearchController = MovieSearchController(appScope, movieCatalog)
torrentSearchController = TorrentSearchController(appScope, movieCatalog, KnabenTorrentSearchProvider())
playbackController = PlaybackController(
    scope = appScope,
    streamer = torrentStreamer,
    onStreamReady = { source ->
        playerPort.prepare(source)
        playerPort.play()
    },
)
```

Render `KinoApp`. Preserve fullscreen flags, player pause/release, TorrServer startup/shutdown, and `EXTRA_MAGNET` handling. Replace `startMagnet()` body with `playbackController.playMagnet(magnet)`.

- [ ] **Step 5: Delete the old combined flow**

Delete `SearchScreen.kt`, `SearchController.kt`, `SearchUiState.kt`, and old `SearchScreenTest.kt`.

Verify no old production API remains:

```bash
! grep -R "SearchController\|SearchUiState\|provider.search(query" app/src/main app/src/test app/src/androidTest
```

- [ ] **Step 6: Build all debug artifacts**

```bash
TMDB_API_KEY=ci-placeholder ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleAndroidTest --stacktrace
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/sk/ziacik/androidstreamplayer app/src/androidTest/java/sk/ziacik/androidstreamplayer
git commit -m "feat: wire movie catalog to Kino player"
```

---

### Task 8: TV focus hardening and final branch validation

**Files:**
- Modify: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/MovieSearchScreenTest.kt`
- Modify: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/MovieDetailScreenTest.kt`
- Modify: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/KinoAppFlowTest.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/MovieSearchScreen.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/TorrentResults.kt`

- [ ] **Step 1: Add explicit TV focus tests**

Use `performKeyInput`/focus assertions supported by the Compose version to verify:

```text
movie-search-field initially focused
Down after results → movie-603 focused
Up from movie-603 → movie-search-field focused
open detail and wait for results → torrent-hit-1 focused
Back detail → movie-603 focused again
```

Add `assertIsFocused()` calls after each transition. If direct key synthesis fails on the test runtime, extract a small pure `MovieGridFocusPolicy` with `firstRow(index, columns=6)` and unit-test the row decision while keeping one instrumentation assertion for actual restored poster focus.

- [ ] **Step 2: Verify movie-only provider invariants**

```bash
if grep -R "TV_CATEGORY\|2_000_000" app/src/main/java/sk/ziacik/androidstreamplayer/search; then exit 1; fi
```

Expected: no match.

- [ ] **Step 3: Verify credential safety**

```bash
git grep -nE 'api_key=[A-Za-z0-9_-]{20,}|TMDB_API_KEY[[:space:]]*=[[:space:]]*[A-Za-z0-9_-]{20,}' -- . ':!docs/superpowers/plans/*'
```

Expected: no real credential literal.

- [ ] **Step 4: Run full automated build validation**

```bash
TMDB_API_KEY=ci-placeholder ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleAndroidTest --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Run connected Android TV instrumentation tests**

```bash
TMDB_API_KEY=ci-placeholder ./gradlew :app:connectedDebugAndroidTest --stacktrace
```

Expected: existing Kino player tests plus new catalog/detail/app-flow tests PASS.

- [ ] **Step 6: Smoke-test with a real non-committed TMDB key**

```bash
./gradlew :app:assembleDebug -PtmdbApiKey="$TMDB_API_KEY"
./deploy-debug
```

On TV verify `The Matrix` and `Alien`: correct posters, predictable D-pad grid, cinematic detail, automatic Knaben releases, real seeder/size/quality metadata, playback, then Back Player → Detail → same poster/query.

- [ ] **Step 7: Inspect diff scope**

```bash
git diff --stat master...HEAD
git diff master...HEAD -- app/src/main/java app/src/test app/src/androidTest app/build.gradle.kts gradle/libs.versions.toml .github/workflows/android-ci.yml
```

Reject unrelated TorrServer/player/theme rewrites.

- [ ] **Step 8: Commit focus/test hardening changes**

```bash
git add app/src
git diff --cached --quiet || git commit -m "test: harden movie catalog TV flow"
```

---

## Final Acceptance Checklist

- [ ] Fresh launch shows movie catalog search, not raw torrent rows.
- [ ] Fewer than 2 trimmed characters never call TMDB.
- [ ] Search debounces ~400 ms and old requests cannot overwrite new results.
- [ ] Posters/backdrops load through Coil and missing images are graceful.
- [ ] TMDB ID is retained; IMDb lookup failure does not prevent torrent fallback.
- [ ] Knaben runs every distinct fallback in the specified order using movie-only category.
- [ ] Torrent results merge, dedupe by info-hash, keep the healthier duplicate, and sort by seeders descending.
- [ ] Detail remains visible through torrent loading/error/empty states.
- [ ] Release rows show real seeder counts, size, and quality.
- [ ] Existing TorrServer → Media3 playback still works.
- [ ] External magnet intent bypasses TMDB and still plays.
- [ ] Back Player → Detail → same catalog query/results/scroll/focus.
- [ ] Unit tests pass.
- [ ] Debug and instrumentation APKs compile.
- [ ] Connected TV instrumentation tests pass.
- [ ] No real TMDB credential is committed.
- [ ] `master` remains untouched until deliberate merge.
