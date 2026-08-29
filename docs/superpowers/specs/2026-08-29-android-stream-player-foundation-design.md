# Android Stream Player — Foundation Design

## Goal

Create the first clean Android TV foundation for `android-stream-player`: a TV-first app where a user can search for a movie, see torrent-like search results, select one, and reach a placeholder playback state. The first version intentionally uses fake search data and does not implement real torrent discovery or torrent streaming yet.

The architecture must make it possible to add real torrent providers and a torrent streaming engine later without rewriting the UI.

## Scope

### Included in the first foundation

- Android TV app scaffold.
- Kotlin + Jetpack Compose.
- Single `:app` module.
- Android SDK 36, minSdk 26, Java 17.
- Gradle Kotlin DSL and version catalog.
- Media3 player abstraction retained for future playback.
- OkHttp available for later network providers.
- TV launcher manifest configuration.
- D-pad friendly search screen.
- Fake torrent search provider.
- Search state/controller.
- Torrent result model with fields needed by future real providers.
- Placeholder torrent streamer interface.
- Placeholder playback state after selecting a result.
- Unit tests for search behavior.
- Compose UI test for search -> results -> selection.
- `deploy-debug` helper similar to `android-tv-player`.

### Explicitly excluded

- Real torrent index scraping/search.
- Torrentio integration.
- BitTorrent protocol implementation.
- Local torrent HTTP proxy/server.
- Actual torrent playback.
- Movie metadata service such as TMDB.
- Movie details page.
- Search history, favorites, accounts, database.
- IPTV channels, EPG, TV Input Framework, channel resolvers.

## Reuse from `android-tv-player`

Keep the parts of the existing project that are useful infrastructure:

- Kotlin/Compose project style.
- Single `:app` module.
- compileSdk/targetSdk 36 and minSdk 26.
- Java 17.
- Compose BOM.
- Media3.
- OkHttp.
- Coroutines.
- JUnit and Compose test setup.
- Android TV manifest characteristics (`leanback`, no touchscreen requirement, landscape launcher activity).
- `deploy-debug` workflow using Gradle + ADB.

Do not copy IPTV-specific code, assets, EPG permissions, TV Input services, channel catalog code, or channel resolvers.

## Package structure

```text
sk.ziacik.androidstreamplayer
├── MainActivity.kt
├── search/
│   ├── TorrentSearchResult.kt
│   ├── TorrentSearchProvider.kt
│   ├── FakeTorrentSearchProvider.kt
│   ├── SearchController.kt
│   └── SearchUiState.kt
├── torrent/
│   ├── TorrentSource.kt
│   └── TorrentStreamer.kt
├── player/
│   ├── PlayerPort.kt
│   └── Media3PlayerPort.kt
└── ui/
    ├── SearchScreen.kt
    └── theme/
```

The structure is intentionally package-based rather than multi-module. Multi-module separation is unnecessary at this stage and can be introduced later if torrent implementation complexity justifies it.

## Core models and interfaces

### `TorrentSearchResult`

Represents one selectable torrent-like search result.

Fields:

- `id: String`
- `title: String`
- `magnetUri: String`
- `quality: String?`
- `sizeBytes: Long?`
- `seeders: Int?`
- `source: String?`

The fake provider may use dummy magnet URIs. The UI must not parse or depend on the magnet URI.

### `TorrentSearchProvider`

```kotlin
interface TorrentSearchProvider {
    suspend fun search(query: String): List<TorrentSearchResult>
}
```

The search UI depends only on this interface. A future Torrentio-compatible or scraper-backed provider can replace the fake implementation.

### `TorrentStreamer`

```kotlin
interface TorrentStreamer {
    suspend fun prepare(result: TorrentSearchResult): TorrentSource
}
```

This is the boundary between torrent acquisition and playback.

### `TorrentSource`

Represents the playable result of torrent preparation. The exact final implementation may eventually contain a local HTTP URL, file-backed URI, or another Media3-compatible source.

The first implementation is a placeholder only.

## Application flow

```text
SearchScreen
    ↓ search query
SearchController
    ↓
TorrentSearchProvider
    ↓
List<TorrentSearchResult>
    ↓
SearchScreen

User selects result
    ↓
TorrentStreamer
    ↓
TorrentSource
    ↓
Media3PlayerPort
```

In the first version, the final torrent-streamer step stops at a placeholder playback state rather than opening a real stream.

## Search state

`SearchUiState` should explicitly represent the screen state rather than infer it from scattered nullable values.

Suggested fields/state:

- current query
- search in progress
- results
- error message
- selected/preparing result

Behavior:

- Empty query does not perform a search.
- Starting a new search clears the previous transient error.
- Successful empty result displays `Nothing found`.
- Provider failure displays a concise error and Retry action.
- Selecting a result displays a `Preparing stream…` state followed by `Streaming not implemented yet` in the fake foundation.

## TV UI

The initial screen should be deliberately simple and usable from a couch.

### Search area

- Large search field near the top.
- Clear focus indication.
- Search can be initiated from the software keyboard action or a dedicated focusable button if needed for reliable TV UX.
- No touch interaction is required.

### Results

Vertically scrollable, D-pad focusable rows.

Each row shows:

- release/result title as the main text
- quality
- formatted file size
- seeder count
- provider/source

Metadata should be visually secondary to the release title.

### Selection

Selecting a row opens the placeholder playback/preparation state. This deliberately tests the navigation/data boundary before the torrent engine exists.

## Player boundary

Keep a small `PlayerPort` abstraction similar in spirit to `android-tv-player`, but do not carry over IPTV-specific playback controller behavior.

`Media3PlayerPort` owns the Media3 player lifecycle and will later receive a playable `TorrentSource`.

For the first foundation, player code may remain minimal because no real media source is produced yet.

## Error handling

- Search provider exceptions are caught in `SearchController` and converted to UI state.
- UI does not catch provider/network exceptions itself.
- Retry repeats the latest non-empty query.
- Torrent preparation failure will eventually use the same explicit state model; the first fake implementation does not require complex recovery.
- Diagnostic logging can use Android `Log` initially; no logging framework is needed.

## Testing

### Unit tests

Test `SearchController` with fake/test providers:

- empty query does not call provider
- successful search publishes results
- empty provider response publishes empty-result state
- provider exception publishes error state
- retry searches the previous query
- selecting a result enters preparation/placeholder state

### UI test

One Compose instrumentation test covers the main happy path:

1. search field receives a movie title
2. search is triggered
3. fake torrent results appear
4. a result receives D-pad/click selection
5. placeholder stream state appears

Tests should target behavior, not exact visual styling.

## Build and repository conventions

Use the same general infrastructure style as `android-tv-player`:

- `settings.gradle.kts`
- root `build.gradle.kts`
- `gradle.properties`
- `gradle/libs.versions.toml`
- Gradle wrapper
- `app/build.gradle.kts`
- `deploy-debug`

Initial namespace/application id:

```text
sk.ziacik.androidstreamplayer
```

Initial app name:

```text
Android Stream Player
```

Branding can be replaced later without coupling it to the search/torrent architecture.

## Future extension path

The intended next implementation steps after this foundation are:

1. Replace `FakeTorrentSearchProvider` with a real provider.
2. Add movie/series metadata lookup only if needed for better search matching.
3. Implement torrent session management behind `TorrentStreamer`.
4. Expose a Media3-compatible stream, likely through a local sequential HTTP source or equivalent torrent-aware data source.
5. Connect `TorrentSource` to `Media3PlayerPort`.
6. Add buffering/progress/peer diagnostics appropriate for TV playback.

None of those steps should require redesigning `SearchScreen` or changing the `TorrentSearchProvider` contract unless real-world provider requirements prove the model insufficient.
