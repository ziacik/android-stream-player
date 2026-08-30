# Kino — Movie Catalog Search Design

## Goal

Replace the current torrent-first search screen with a movie-first Android TV experience inspired by streaming catalog apps.

A user searches for a movie, browses TMDB poster results with the remote, opens a cinematic movie detail screen, then sees matching torrent releases and chooses one for playback.

The user must not need to configure metadata or torrent providers.

## Scope

### Included

- Movies only. TV series, seasons, and episodes are out of scope.
- TMDB-backed movie search.
- Poster-grid search results optimized for Android TV D-pad navigation.
- Movie detail screen with backdrop, poster, title, year, rating, and overview.
- TMDB and IMDb identifiers stored in the movie model.
- Automatic torrent discovery after opening a movie detail.
- Existing Knaben provider retained as the built-in zero-configuration torrent source.
- Movie-aware fallback queries for Knaben.
- Torrent result deduplication.
- Existing TorrServer playback flow retained.
- Back navigation from detail to catalog with search query/results/focus restored.
- Loading, empty, and failure states for both catalog and torrent discovery.
- Unit and Compose UI coverage for the new flow.

### Explicitly excluded

- TV shows or episodic content.
- User-configured Torznab/Jackett/Prowlarr.
- Accounts, favorites, watchlists, profiles, recommendations, or search history persistence.
- Multiple catalog providers.
- Replacing TorrServer or the existing player.
- Automatic playback without the user selecting a torrent release.

## User flow

```text
MovieSearchScreen
    ↓ type movie title
TMDB movie search
    ↓
Poster grid
    ↓ OK on poster
MovieDetailScreen
    ↓ automatic torrent discovery
KnabenTorrentSearchProvider
    ↓
Available releases
    ↓ OK on release
TorrServer
    ↓
KinoPlayerScreen
```

Back from `MovieDetailScreen` restores the same catalog query, results, scroll position, and focus to the movie that was selected. If that movie is no longer present because the catalog state was replaced, focus falls back to the first visible result.

## Architecture

Keep the app single-module, but split catalog discovery from torrent discovery. The current `SearchController` should not grow into a controller that owns unrelated catalog, detail, torrent, and playback concerns.

Suggested package structure:

```text
sk.ziacik.androidstreamplayer
├── catalog/
│   ├── Movie.kt
│   ├── MovieCatalog.kt
│   ├── TmdbMovieCatalog.kt
│   ├── TmdbHttpTransport.kt
│   ├── MovieSearchController.kt
│   └── MovieSearchUiState.kt
├── search/
│   ├── TorrentSearchResult.kt
│   ├── TorrentSearchProvider.kt
│   ├── MovieTorrentSearchRequest.kt
│   ├── KnabenTorrentSearchProvider.kt
│   └── TorrentSearchController.kt
├── torrent/
├── player/
└── ui/
    ├── MovieSearchScreen.kt
    ├── MovieDetailScreen.kt
    ├── TorrentResults.kt
    └── KinoPlayerScreen.kt
```

Exact names may vary during implementation, but catalog metadata and torrent discovery must remain separate boundaries.

## Movie model

`Movie` represents a catalog identity rather than a torrent release.

Suggested fields:

```kotlin
data class Movie(
    val tmdbId: Int,
    val imdbId: String?,
    val title: String,
    val originalTitle: String,
    val releaseYear: Int?,
    val overview: String?,
    val voteAverage: Double?,
    val posterPath: String?,
    val backdropPath: String?,
)
```

TMDB ID is the primary catalog identity. IMDb ID is retained for future torrent providers that support ID-based lookup.

## TMDB integration

Use TMDB API v3.

Movie text search uses:

```text
GET /3/search/movie
```

Search must set `include_adult=false` and use an app-defined UI language, initially English unless the existing app localization later dictates otherwise.

Movie search responses already provide the fields needed for the grid and most of the detail screen. IMDb ID is obtained through:

```text
GET /3/movie/{movie_id}/external_ids
```

IMDb ID does not need to block initial poster rendering. It can be fetched when the movie is selected or as part of detail loading.

Poster/backdrop URLs are built from TMDB image paths. The implementation may use the documented `image.tmdb.org` image base URL with appropriate fixed sizes for the current TV UI instead of introducing a configuration request on every launch.

Use an Android image-loading library with memory/disk caching for posters and backdrops. Coil Compose is the preferred small dependency.

## TMDB credential

The end user must never enter a TMDB credential.

The TMDB API credential is embedded into the APK through `BuildConfig` at build time. The source value should come from a Gradle property/environment/CI secret rather than be committed as plaintext to the public repository.

Example boundary:

```kotlin
BuildConfig.TMDB_API_KEY
```

A missing credential should fail clearly at development/build time rather than produce a mysterious empty catalog at runtime.

This keeps the installed app zero-configuration while avoiding an unnecessary plaintext credential in Git history.

## Catalog search behavior

### Input

- Search begins after at least 2 non-whitespace characters.
- Debounce approximately 400 ms after typing stops.
- A new query cancels or supersedes the previous request.
- Clearing the query clears catalog results and returns to the landing state.
- The software keyboard Search action may trigger immediately rather than wait for debounce.

### Poster grid

The catalog screen is TV-first rather than a desktop form.

- Large portrait posters arranged in a D-pad-friendly grid.
- Poster aspect ratio approximately 2:3.
- Focused poster scales up modestly and gets a clearly visible focus treatment.
- Focused item exposes title and release year without cluttering every poster with permanent text.
- Missing posters use a deliberate placeholder instead of a broken image.
- `OK` opens the selected movie detail.
- Search input remains reachable with D-pad Up from the first grid row.
- D-pad Down from the search field enters the result grid.

Search results should visually dominate the screen once present. The initial hero/header may collapse to a compact header while browsing results.

## Movie detail screen

The detail screen should feel cinematic but remain functional on a TV.

Layout:

- Large backdrop covering the upper/background region with a readability gradient.
- Poster on the left or leading side.
- Title, year, rating, and overview as primary metadata.
- Torrent availability section below/alongside the movie metadata depending on available vertical space.

Opening the detail starts torrent discovery automatically; no separate `Search torrents` action is required.

While torrent discovery is loading there is no artificial focus jump. When the first non-empty release list arrives, focus moves exactly once to the first torrent result. Subsequent state updates must not steal focus from the user's current selection.

Back returns to the catalog result previously selected.

## Torrent search contract

Torrent discovery should accept movie identity, not an already formatted free-text query.

Suggested request model:

```kotlin
data class MovieTorrentSearchRequest(
    val tmdbId: Int,
    val imdbId: String?,
    val title: String,
    val originalTitle: String,
    val year: Int?,
)
```

Suggested provider boundary:

```kotlin
interface TorrentSearchProvider {
    suspend fun search(movie: MovieTorrentSearchRequest): List<TorrentSearchResult>
}
```

This intentionally carries TMDB/IMDb IDs even though the first built-in Knaben implementation will mainly use title/year queries. Future providers can use the IDs without changing the catalog or detail UI.

Direct magnet handling is not a catalog concern. If external magnet intents are retained, they should enter the torrent/playback flow through a dedicated path rather than masquerading as a movie catalog query.

## Knaben fallback strategy

Knaben remains the zero-configuration built-in provider.

For a selected movie, issue movie-only searches in this order, skipping duplicate query strings:

1. `originalTitle + year`
2. `title + year`
3. `originalTitle`
4. `title`

If title and original title are identical, do not repeat the same query.

Do not stop after the first non-empty fallback. Merge useful results from the fallback queries, because alternative release naming can appear across different title forms.

Deduplicate merged results primarily by BitTorrent info-hash extracted from the magnet URI. If no usable hash is available, fall back to a stable normalized result identity.

Sort the final list primarily by seeder count descending. Quality and size remain visible metadata but do not override obviously healthier availability by default.

Knaben search should use the movie category only for this flow; TV category is no longer appropriate for movie catalog selection.

## Torrent result UX

The torrent section is a technical choice underneath the movie, not the main product identity.

Each release should expose at least:

- parsed quality (`2160p`, `1080p`, etc.)
- release title
- real seeder count
- file size

Prefer real seeder numbers over labels such as `Great availability`.

Focused rows/cards must have a strong TV focus state. Selecting one enters the existing preparation/playback path.

While torrent discovery is running, keep the movie detail visible and show a compact loading state in the release area. Do not replace the whole detail screen with a spinner.

If no torrents are found, keep the detail visible and show a concise `No versions found` state.

If Knaben fails, show an inline retry action for torrent discovery.

## Screen/state ownership

A small top-level app coordinator may own which high-level screen is active:

```text
Catalog
Detail(movie)
Player(movie, torrent)
```

Navigation Compose is not required for this iteration. The app only has a small linear flow, so explicit Compose state/navigation callbacks are sufficient and avoid introducing unnecessary framework complexity.

Suggested responsibilities:

### `MovieSearchController`

Owns:

- catalog query
- debounce/search jobs
- catalog loading/error/results
- selected/focused movie identity needed for restoration

Does not know about torrents or playback.

### `TorrentSearchController`

Owns:

- currently selected movie identity
- torrent discovery state
- retry
- release list

Does not own catalog query or poster search.

### Playback coordinator/current existing boundary

Owns selected torrent preparation and player transition. Reuse existing `TorrentStreamer`, `TorrServerTorrentStreamer`, `Media3PlayerPort`, and `KinoPlayerScreen` behavior where possible.

## Error handling

### Catalog

- TMDB network/API failure -> inline search error with Retry.
- Invalid/malformed response -> treated as catalog failure, not app crash.
- Missing poster/backdrop/overview -> valid movie with graceful missing metadata.
- Empty result -> explicit no-movies-found state.

### Detail / torrent discovery

- External-ID lookup failure must not prevent torrent searching by title/year.
- Knaben failure -> inline error and Retry within movie detail.
- Empty torrent results -> explicit empty state.
- Playback failure retains the existing friendly player/preparation behavior and allows returning to the same movie detail.

## Testing

### Catalog unit tests

- query shorter than minimum does not search
- debounce issues a search after the delay
- newer query supersedes older search result
- successful TMDB response maps to `Movie`
- missing optional TMDB metadata is accepted
- API/network failure produces error state
- clearing query resets catalog results

### Torrent unit tests

- movie-aware fallback queries are generated in the defined order
- identical title/original title does not produce duplicate searches
- fallback results are merged
- duplicate info-hashes collapse to one result
- final results sort by seeders descending
- IMDb/TMDB IDs survive through the request model even though Knaben ignores them initially
- external-ID lookup failure still allows title/year torrent discovery

### Compose UI tests

Happy path:

1. enter movie query
2. catalog posters appear
3. select a poster
4. movie detail appears
5. torrent releases appear
6. select a torrent
7. playback preparation/player transition occurs
8. Back from detail restores the catalog context

Also test D-pad focus movement between search field, poster grid, and torrent results where Compose testing APIs make the behavior reliable.

## Migration from current screen

The current `SearchScreen` and `SearchController` combine torrent search and playback selection. They should be decomposed rather than incrementally expanded.

Existing pieces to preserve where useful:

- `TorrentSearchResult`
- Knaben HTTP transport/parsing
- quality inference
- TorrServer integration
- player implementation and custom TV OSD
- cinematic Kino theme/assets

Pieces to replace or substantially reshape:

- torrent-first `SearchScreen`
- free-text `TorrentSearchProvider.search(query)` contract
- `SearchController` as the owner of both searching and playback state
- current vertical torrent-result list as the landing experience

## Delivery branch

All work for this feature lives on:

```text
feature/movie-catalog-search
```

`master` is not modified until the feature is reviewed and deliberately merged.

## Success criteria

The feature is complete when a fresh app launch allows the following with only the TV remote and no user configuration:

1. type part of a movie title
2. see recognizable TMDB poster suggestions
3. select the intended movie
4. see a cinematic movie detail
5. automatically receive relevant torrent releases for that movie
6. choose a release
7. reach the existing Kino player
8. return to the same catalog context with Back

The experience should feel like browsing a movie app whose playback happens to be torrent-backed, rather than a torrent search utility with movie styling.