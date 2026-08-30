# Kino Movie Catalog Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the torrent-first landing screen with a zero-configuration TMDB movie catalog where users browse posters, open a cinematic detail, choose a Knaben torrent release, and play it through the existing TorrServer/Media3 player.

**Architecture:** Keep the app single-module. `MovieCatalog` owns TMDB metadata; `MovieSearchController` owns catalog query/results/focus; `TorrentSearchController` owns movie-to-release discovery; `PlaybackController` owns torrent preparation/player transition. `KinoApp` composes Catalog → Detail → Player while catalog state survives detail/player navigation.

**Tech Stack:** Kotlin 2.3.21, Android SDK 36/minSdk 26, Compose BOM 2026.06.00, OkHttp 5.1.0, Coroutines 1.10.2, Media3 1.11.0, Coil 3.4.0, org.json, JUnit 4, Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-08-30-movie-catalog-search-design.md`

## Global Constraints

- Movies only; no TV series/seasons/episodes.
- End users configure nothing.
- TMDB search: `/3/search/movie`, `include_adult=false`, `language=en-US`.
- IMDb lookup: `/3/movie/{movie_id}/external_ids`.
- Real TMDB credential is never committed; release build gets it through `BuildConfig.TMDB_API_KEY`.
- Debug/test CI may use `ci-placeholder`; no automated test may contact TMDB.
- Use Coil 3.4.0; do not upgrade project Kotlin as part of this feature.
- Knaben remains the only built-in torrent provider.
- Fallback order: `originalTitle + year`, `title + year`, `originalTitle`, `title`; run every distinct query.
- Knaben uses only movie category `3_000_000`.
- Merge results, dedupe by normalized BTIH when available, retain the duplicate with more seeders, sort seeders descending.
- Preserve TorrServer, Media3, and Kino player OSD behavior.
- Back: Player → same Detail → same catalog query/results/scroll/focused poster.
- Initial catalog focus is search. Down enters posters; Up from first poster row returns to search.
- First torrent result gets focus once when a non-empty result set arrives.
- Keep every intermediate commit buildable/testable.
- Work only on `feature/movie-catalog-search` until deliberate merge.

---

### Task 1: Add TMDB model, client, image dependency, and build key

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `.github/workflows/android-ci.yml`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/Movie.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/MovieCatalog.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/TmdbMovieCatalog.kt`
- Test: `app/src/test/java/sk/ziacik/androidstreamplayer/catalog/TmdbMovieCatalogTest.kt`

**Produces:**

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

- [ ] **Step 1: Write failing TMDB tests**

Test search request path/query parameters, mapping of Matrix (`id=603`, year 1999), missing optional fields → null, external IDs → `tt0133093`, and non-2xx → `IOException`. Use an injected recording `TmdbHttpTransport`; never real network.

Concrete search assertion:

```kotlin
assertEquals("/3/search/movie", request.url.encodedPath)
assertEquals("Matrix", request.url.queryParameter("query"))
assertEquals("false", request.url.queryParameter("include_adult"))
assertEquals("en-US", request.url.queryParameter("language"))
assertEquals("test-key", request.url.queryParameter("api_key"))
```

- [ ] **Step 2: Verify failure**

```bash
./gradlew :app:testDebugUnitTest --tests '*TmdbMovieCatalogTest*'
```

- [ ] **Step 3: Add Coil 3.4.0**

`gradle/libs.versions.toml`:

```toml
coil = "3.4.0"
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-okhttp = { module = "io.coil-kt.coil3:coil-network-okhttp", version.ref = "coil" }
```

`app/build.gradle.kts` dependencies:

```kotlin
implementation(libs.coil.compose)
implementation(libs.coil.network.okhttp)
```

- [ ] **Step 4: Wire TMDB key through BuildConfig**

```kotlin
val tmdbApiKey = providers.gradleProperty("tmdbApiKey")
    .orElse(providers.environmentVariable("TMDB_API_KEY"))
    .orElse("")
```

Inside Android config:

```kotlin
defaultConfig {
    buildConfigField("String", "TMDB_API_KEY", "\"${tmdbApiKey.get()}\"")
}

buildFeatures {
    compose = true
    buildConfig = true
}
```

Release guard:

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

- [ ] **Step 5: Update CI**

Add branch `feature/movie-catalog-search` and job env:

```yaml
env:
  TMDB_API_KEY: ci-placeholder
```

- [ ] **Step 6: Implement `TmdbMovieCatalog`**

Use OkHttp GET + `api_key`. Parse search `results`; map `release_date.take(4).toIntOrNull()`. Missing overview/rating/poster/backdrop is valid. Add:

```kotlin
fun tmdbPosterUrl(path: String?): String? =
    path?.takeIf(String::isNotBlank)?.let { "https://image.tmdb.org/t/p/w500$it" }

fun tmdbBackdropUrl(path: String?): String? =
    path?.takeIf(String::isNotBlank)?.let { "https://image.tmdb.org/t/p/w1280$it" }
```

- [ ] **Step 7: Test and commit**

```bash
./gradlew :app:testDebugUnitTest --tests '*TmdbMovieCatalogTest*'
./gradlew :app:testDebugUnitTest
git add gradle/libs.versions.toml app/build.gradle.kts .github/workflows/android-ci.yml app/src/main/java/sk/ziacik/androidstreamplayer/catalog app/src/test/java/sk/ziacik/androidstreamplayer/catalog
git commit -m "feat: add TMDB movie catalog"
```

---

### Task 2: Add debounced catalog search state

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchUiState.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchController.kt`
- Test: `app/src/test/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchControllerTest.kt`

**Produces:**

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

- [ ] **Step 1: Write failing tests**

Exact cases:

1. `"A"` → no catalog call and empty results.
2. `"Al"` → no call at 399 ms; one call at 400 ms.
3. clearing query after successful result → query/results/error/loading reset.
4. catalog exception → `errorMessage="Search failed"`; clear fake failure + `retry()` → same query called again and error cleared.
5. slow `"Al"` followed by immediate `"Alien"` → final results only from Alien.
6. `setFocusedMovie(603)` → state keeps 603 across subsequent navigation with unchanged query.

Use `runTest`, `advanceTimeBy`, `runCurrent`, and `advanceUntilIdle`.

- [ ] **Step 2: Verify failure**

```bash
./gradlew :app:testDebugUnitTest --tests '*MovieSearchControllerTest*'
```

- [ ] **Step 3: Implement controller**

Keep one `Job?`. Every `setQuery()` cancels it. Preserve typed text, trim only for length/search. Fewer than 2 characters clears results/loading. Valid query launches `delay(debounceMs)` then search. `searchNow()` cancels debounce and immediately searches. `retry()` repeats latest valid normalized query. Cancellation must prevent stale result publication.

- [ ] **Step 4: Test and commit**

```bash
./gradlew :app:testDebugUnitTest --tests '*MovieSearchControllerTest*'
./gradlew :app:testDebugUnitTest
git add app/src/main/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchUiState.kt app/src/main/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchController.kt app/src/test/java/sk/ziacik/androidstreamplayer/catalog/MovieSearchControllerTest.kt
git commit -m "feat: add debounced movie search state"
```

---

### Task 3: Make Knaben movie-aware without breaking the legacy screen

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/search/MovieTorrentSearchRequest.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentSearchProvider.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/search/KnabenTorrentSearchProvider.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/search/FakeTorrentSearchProvider.kt`
- Modify: `app/src/test/java/sk/ziacik/androidstreamplayer/search/KnabenTorrentSearchProviderTest.kt`
- Modify: `app/src/test/java/sk/ziacik/androidstreamplayer/search/FakeTorrentSearchProviderTest.kt`
- Modify: `app/src/test/java/sk/ziacik/androidstreamplayer/search/SearchControllerTest.kt`

**Produces:**

```kotlin
data class MovieTorrentSearchRequest(
    val tmdbId: Int,
    val imdbId: String?,
    val title: String,
    val originalTitle: String,
    val year: Int?,
)
```

Use this temporary migration interface so the old `SearchController` still compiles until Task 7:

```kotlin
fun interface TorrentSearchProvider {
    suspend fun search(movie: MovieTorrentSearchRequest): List<TorrentSearchResult>

    @Deprecated("Legacy free-text search; remove with old SearchController")
    suspend fun search(query: String): List<TorrentSearchResult> = search(
        MovieTorrentSearchRequest(
            tmdbId = 0,
            imdbId = null,
            title = query,
            originalTitle = query,
            year = null,
        ),
    )
}
```

- [ ] **Step 1: Write fallback/category tests**

For Matrix translated/original titles differing, assert requests exactly:

```kotlin
listOf("The Matrix 1999", "Matrix 1999", "The Matrix", "Matrix")
```

For identical title/original title, assert only:

```kotlin
listOf("The Matrix 1999", "The Matrix")
```

Every body must have only category `3_000_000`, seeders descending, unsafe/xxx hidden.

- [ ] **Step 2: Write dedupe/sort test**

Across fallback responses return:

```text
A1: btih ABC, 20 seeders
A2: btih abc, 40 seeders
D : btih DEF, 100 seeders
N : no usable btih, id no-hash, 5 seeders
```

Expected final IDs: `D`, `A2`, `no-hash`.

- [ ] **Step 3: Verify failure**

```bash
./gradlew :app:testDebugUnitTest --tests '*KnabenTorrentSearchProviderTest*'
```

- [ ] **Step 4: Implement fallback generator**

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

Move existing one-query HTTP/parsing code into `searchQuery(query)` and change categories to movie-only.

- [ ] **Step 5: Implement dedupe/order**

```kotlin
private fun infoHash(magnet: String): String? =
    Regex("(?i)[?&]xt=urn:btih:([A-Za-z0-9]+)")
        .find(magnet)?.groupValues?.get(1)?.lowercase()
```

Use key `hash:<hash>` else `id:<id>`. For duplicate key retain higher `seeders ?: -1`. Sort seeders descending, then title.

- [ ] **Step 6: Adapt fakes and legacy test**

`FakeTorrentSearchProvider` implements `search(movie)` and uses `movie.title`. Change `SearchControllerTest.RecordingProvider` to implement `search(movie)` and record `movie.title`; the old controller calls the deprecated free-text default, so its existing assertions continue to pass.

- [ ] **Step 7: Test and commit**

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
git add app/src/main/java/sk/ziacik/androidstreamplayer/search app/src/test/java/sk/ziacik/androidstreamplayer/search
git commit -m "feat: search Knaben by movie identity"
```

---

### Task 4: Split torrent discovery and playback state

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentSearchUiState.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentSearchController.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/playback/PlaybackUiState.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/playback/PlaybackController.kt`
- Test: `app/src/test/java/sk/ziacik/androidstreamplayer/search/TorrentSearchControllerTest.kt`
- Test: `app/src/test/java/sk/ziacik/androidstreamplayer/playback/PlaybackControllerTest.kt`

**Produces:**

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

1. external IDs returns `tt0133093` → provider gets it.
2. external-ID request throws → provider still runs once with `imdbId=null`, no error when provider succeeds.
3. provider throws → `Search failed`, no loading.
4. retry after clearing fake failure searches the same movie and publishes results.
5. delayed movie A then movie B → final state only B.

- [ ] **Step 2: Write playback tests**

Move semantics from current `SearchControllerTest`: selected result immediately enters `Preparing stream…`; successful streamer + callback → `Playing`; streamer failure → `Stream failed`; null streamer → `Streaming unavailable`; callback failure → `Playback failed`; `exit()` clears result/status. Add direct magnet test asserting ID `direct-magnet` and source `Magnet`.

- [ ] **Step 3: Verify failure**

```bash
./gradlew :app:testDebugUnitTest --tests '*TorrentSearchControllerTest*' --tests '*PlaybackControllerTest*'
```

- [ ] **Step 4: Implement `TorrentSearchController`**

`open(movie)` cancels prior work, publishes loading, resolves IMDb using `runCatching`, creates `MovieTorrentSearchRequest`, then calls provider. IMDb failure is non-fatal. Provider failure sets `Search failed`. `retry()` repeats current movie. `clear()` cancels and resets.

- [ ] **Step 5: Implement `PlaybackController`**

Move preparation logic without changing user-visible status strings. `playMagnet()` creates the direct `TorrentSearchResult` and delegates to `play()`.

- [ ] **Step 6: Test and commit**

Keep legacy `SearchController`/`SearchScreen` for now so the app remains buildable; Task 7 removes them.

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
git add app/src/main/java/sk/ziacik/androidstreamplayer/search app/src/main/java/sk/ziacik/androidstreamplayer/playback app/src/test/java/sk/ziacik/androidstreamplayer/search app/src/test/java/sk/ziacik/androidstreamplayer/playback
git commit -m "refactor: split torrent search from playback"
```

---

### Task 5: Build Netflix-style poster catalog UI

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/MoviePosterCard.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/MovieSearchScreen.kt`
- Test: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/MovieSearchScreenTest.kt`

**Produces:**

```kotlin
@Composable
fun MovieSearchScreen(
    controller: MovieSearchController,
    onMovieSelected: (Movie) -> Unit,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 1: Write failing UI tests**

With fake catalog + `debounceMs=0`:

1. type `Matrix` → `movie-603` appears → click → callback gets TMDB 603.
2. empty response → `No movies found`.
3. exception → `Couldn’t search movies` + `Retry`.

- [ ] **Step 2: Verify instrumentation compilation fails**

```bash
./gradlew :app:assembleAndroidTest
```

- [ ] **Step 3: Implement poster card**

Use Coil `AsyncImage`, 2:3 ratio, rounded Card, focused scale `1.05f`, strong focus border, title/year shown on focus, placeholder title when no poster. Add `testTag("movie-${movie.tmdbId}")`. Accept focus requester/navigation parameters from parent.

- [ ] **Step 4: Implement grid and deterministic focus**

Use `LazyVerticalGrid(GridCells.Fixed(6))`, `LazyGridState`, search `FocusRequester`, and per-movie requesters.

On first launch (`focusedMovieId == null`) focus search. When returning and remembered movie exists:

```kotlin
val index = state.results.indexOfFirst { it.tmdbId == state.focusedMovieId }
if (index >= 0) {
    gridState.scrollToItem(index)
    posterRequesters.getValue(state.results[index].tmdbId).requestFocus()
}
```

Down from search targets first result. First-row posters set Up → search. On poster focus call `controller.setFocusedMovie(id)`.

- [ ] **Step 5: Implement visual states**

Keep cinematic Kino background. Large landing hero before results; compact header once grid exists. Loading/error/empty are inline under search and never replace the screen.

- [ ] **Step 6: Build and commit**

```bash
./gradlew :app:assembleAndroidTest :app:testDebugUnitTest
git add app/src/main/java/sk/ziacik/androidstreamplayer/ui/MoviePosterCard.kt app/src/main/java/sk/ziacik/androidstreamplayer/ui/MovieSearchScreen.kt app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/MovieSearchScreenTest.kt
git commit -m "feat: add TV movie poster search"
```

---

### Task 6: Build cinematic movie detail + torrent rows

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/TorrentResults.kt`
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/MovieDetailScreen.kt`
- Test: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/MovieDetailScreenTest.kt`

**Produces:**

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

- [ ] **Step 1: Write failing detail tests**

Separate tests assert:

1. title/year/overview remain visible.
2. suspended provider → `Finding versions…` while title remains visible.
3. empty provider → `No versions found`.
4. provider error → `Couldn’t find versions` + Retry.
5. result ID `hit-1` renders tag `torrent-hit-1`; click calls `onPlay(hit-1)`.

- [ ] **Step 2: Verify compilation fails**

```bash
./gradlew :app:assembleAndroidTest
```

- [ ] **Step 3: Implement torrent rows**

Show quality badge, release title, real `N seeds`, formatted GiB, and `PLAY` focus accent. Add `testTag("torrent-${result.id}")`. Remove old availability labels. Request first-result focus in `LaunchedEffect(results.map { it.id })` only when non-empty IDs change.

- [ ] **Step 4: Implement detail**

`LaunchedEffect(movie.tmdbId) { torrentController.open(movie) }`. Render backdrop with dark gradients, poster, title, year, rating, overview, release section. Use `BackHandler(onBack = onBack)`. Metadata stays visible in loading/error/empty/result states.

- [ ] **Step 5: Build and commit**

```bash
./gradlew :app:assembleAndroidTest :app:testDebugUnitTest
git add app/src/main/java/sk/ziacik/androidstreamplayer/ui/TorrentResults.kt app/src/main/java/sk/ziacik/androidstreamplayer/ui/MovieDetailScreen.kt app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/MovieDetailScreenTest.kt
git commit -m "feat: add cinematic movie detail"
```

---

### Task 7: Wire Catalog → Detail → Player and remove legacy flow

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/KinoApp.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/MainActivity.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/search/TorrentSearchProvider.kt`
- Delete: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/SearchScreen.kt`
- Delete: `app/src/main/java/sk/ziacik/androidstreamplayer/search/SearchController.kt`
- Delete: `app/src/main/java/sk/ziacik/androidstreamplayer/search/SearchUiState.kt`
- Delete: `app/src/test/java/sk/ziacik/androidstreamplayer/search/SearchControllerTest.kt`
- Delete: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/SearchScreenTest.kt`
- Test: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/KinoAppFlowTest.kt`

**Produces:**

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

- [ ] **Step 1: Write app-flow test**

Use fake catalog/provider/streamer and an empty real `ExoPlayer` released after test. Flow:

```text
Matrix input
→ movie-603
→ detail The Matrix
→ torrent-hit-1
→ playback state Playing / kino-player root
→ exit player
→ The Matrix detail again
→ Back
→ movie-603 exists and MovieSearchController.focusedMovieId == 603
```

If `KinoPlayerScreen` has no stable root test tag, add only `testTag("kino-player")` to its outer root.

- [ ] **Step 2: Verify pre-wiring compile state**

```bash
./gradlew :app:assembleAndroidTest
```

The new flow test must fail because `KinoApp` is absent; existing app still builds.

- [ ] **Step 3: Implement `KinoApp`**

Keep selected movie in `remember`; observe playback state:

```kotlin
when {
    playbackState.status == "Playing" -> KinoPlayerScreen(...)
    selectedMovie != null -> MovieDetailScreen(...)
    else -> MovieSearchScreen(...)
}
```

Playback exit clears only `PlaybackController`; selected movie remains, so detail returns. Detail Back clears `TorrentSearchController` and selected movie, leaving `MovieSearchController` untouched. For `Preparing stream…`/playback error keep detail visible and display an inline/overlay status surface.

- [ ] **Step 4: Rewire `MainActivity`**

Create `TmdbMovieCatalog(BuildConfig.TMDB_API_KEY)`, `MovieSearchController`, `TorrentSearchController`, `PlaybackController`. Pass player into `KinoApp`. Preserve existing lifecycle/TorrServer code. `startMagnet(magnet)` becomes `playbackController.playMagnet(magnet)`; magnets never touch TMDB.

- [ ] **Step 5: Remove migration compatibility and legacy files**

Delete old screen/controller/state/tests. Change `TorrentSearchProvider` to final form:

```kotlin
fun interface TorrentSearchProvider {
    suspend fun search(movie: MovieTorrentSearchRequest): List<TorrentSearchResult>
}
```

Verify old symbols are absent without matching new class names:

```bash
! grep -R -w "SearchController" app/src/main app/src/test app/src/androidTest
! grep -R -w "SearchUiState" app/src/main app/src/test app/src/androidTest
! grep -R "search(query: String)" app/src/main/java/sk/ziacik/androidstreamplayer/search
```

- [ ] **Step 6: Full debug build**

```bash
TMDB_API_KEY=ci-placeholder ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleAndroidTest --stacktrace
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/sk/ziacik/androidstreamplayer app/src/test app/src/androidTest
git commit -m "feat: wire movie catalog to Kino player"
```

---

### Task 8: TV navigation hardening and final validation

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/MovieSearchScreen.kt`
- Modify: `app/src/main/java/sk/ziacik/androidstreamplayer/ui/TorrentResults.kt`
- Modify: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/MovieSearchScreenTest.kt`
- Modify: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/MovieDetailScreenTest.kt`
- Modify: `app/src/androidTest/java/sk/ziacik/androidstreamplayer/ui/KinoAppFlowTest.kt`

- [ ] **Step 1: Add focus assertions**

Using Compose key input/focus APIs, assert:

```text
movie-search-field initially focused
Down with results → movie-603 focused
Up from movie-603 → search focused
open detail + results → torrent-hit-1 focused
Back detail → movie-603 focused
```

If TV key synthesis is unreliable in the runner, add a pure `MovieGridFocusPolicy.isFirstRow(index, columns=6)` unit helper and test it, while keeping instrumentation coverage for actual Back focus restoration.

- [ ] **Step 2: Verify provider invariants**

```bash
if grep -R "TV_CATEGORY\|2_000_000" app/src/main/java/sk/ziacik/androidstreamplayer/search; then exit 1; fi
```

- [ ] **Step 3: Verify credential safety**

```bash
git grep -nE 'api_key=[A-Za-z0-9_-]{20,}|TMDB_API_KEY[[:space:]]*=[[:space:]]*[A-Za-z0-9_-]{20,}' -- . ':!docs/superpowers/plans/*'
```

Expected: no real credential literal.

- [ ] **Step 4: Run full automated validation**

```bash
TMDB_API_KEY=ci-placeholder ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleAndroidTest --stacktrace
```

- [ ] **Step 5: Run connected TV tests**

```bash
TMDB_API_KEY=ci-placeholder ./gradlew :app:connectedDebugAndroidTest --stacktrace
```

Expected: old Kino player tests + new catalog/detail/app flow all PASS.

- [ ] **Step 6: Smoke-test with real non-committed key**

```bash
./gradlew :app:assembleDebug -PtmdbApiKey="$TMDB_API_KEY"
./deploy-debug
```

On TV verify `The Matrix` and `Alien`: posters, D-pad grid, detail, automatic Knaben results, real seeds/size/quality, playback, Back Player → Detail → same poster/query.

- [ ] **Step 7: Inspect scope**

```bash
git diff --stat master...HEAD
git diff master...HEAD -- app/src/main/java app/src/test app/src/androidTest app/build.gradle.kts gradle/libs.versions.toml .github/workflows/android-ci.yml
```

No unrelated TorrServer/player/theme rewrites.

- [ ] **Step 8: Commit hardening changes if present**

```bash
git add app/src
git diff --cached --quiet || git commit -m "test: harden movie catalog TV flow"
```

---

## Final Acceptance Checklist

- [ ] Fresh launch shows TMDB movie catalog search, not torrent rows.
- [ ] <2 trimmed characters never call TMDB.
- [ ] Search debounces ~400 ms; stale requests cannot overwrite current results.
- [ ] Posters/backdrops use Coil; missing images degrade gracefully.
- [ ] TMDB ID retained; IMDb lookup failure still falls back to title/year search.
- [ ] Knaben uses every distinct fallback in specified order and movie-only category.
- [ ] Results dedupe by BTIH, healthier duplicate wins, final order is seeders descending.
- [ ] Detail remains visible in loading/error/empty states.
- [ ] Release rows show real seed count, size, quality.
- [ ] Existing TorrServer → Media3 playback works.
- [ ] External magnet intent bypasses TMDB and works.
- [ ] Back Player → Detail → same catalog query/results/scroll/focus.
- [ ] Unit tests pass.
- [ ] Debug/instrumentation APKs compile.
- [ ] Connected TV instrumentation tests pass.
- [ ] No real TMDB key committed.
- [ ] `master` untouched until deliberate merge.
