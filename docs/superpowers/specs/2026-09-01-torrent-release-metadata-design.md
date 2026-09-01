# Kino — Torrent Release Metadata Design

## Goal

Make torrent choices easier to scan on Android TV by extracting useful release metadata from torrent titles and presenting it as compact badges.

The user should be able to distinguish releases by video quality, source, HDR format, codec, audio format/channels, language, and year without decoding the raw release name manually.

## Scope

### Included

- Parse structured release metadata from torrent titles.
- Keep parsing independent from `KnabenTorrentSearchProvider` so future providers can reuse it.
- Add a normalized `TorrentReleaseInfo` model to torrent results.
- Parse, when present:
  - resolution: `2160p`, `1080p`, `720p`, `480p`
  - source: `REMUX`, `BluRay`, `WEB-DL`, `WEBRip`, `HDTV`, `DVDRip`
  - video codec: `HEVC`/`H.265`, `H.264`/`AVC`, `AV1`, `MPEG-2`
  - HDR: Dolby Vision, HDR10+, HDR10, HDR
  - audio codec: TrueHD, DTS-HD MA, DTS-HD, DTS, E-AC-3/DD+, AC-3/DD, AAC, FLAC, PCM
  - audio features: Atmos
  - channels: `2.0`, `5.1`, `7.1`
  - bit depth: `10-bit`, `8-bit` when explicitly named
  - language tags when confidently recognizable
  - year when encoded in the release title
  - release group when it can be identified reliably
- Show compact metadata badges in the existing torrent result rows.
- Preserve raw torrent title, size, seeder count, and source/tracker information.
- Unit-test parser behavior with realistic release names and ambiguous inputs.
- Update Compose UI tests for badge rendering.

### Explicitly excluded

- Fetching MediaInfo or probing the actual media file before the user selects a release.
- Adding another remote metadata service just for codec/audio metadata.
- Guaranteeing perfect parsing of arbitrary scene/P2P naming conventions.
- Hiding the raw release title.
- Automatic ranking by parsed quality in this change.

## Why title parsing is the primary source

Knaben already provides the torrent title, size, seeder count, magnet URI, and source information, but codec/HDR/audio details are generally encoded in the release title rather than exposed as normalized API fields.

The current code already parses resolution directly inside `KnabenTorrentSearchProvider`. This design generalizes that behavior and moves it behind a reusable parser boundary.

Media inspection through TorrServer would happen too late for the release-selection UI and would add avoidable torrent metadata/network work for every candidate, so it is not part of the first version.

If a future torrent provider exposes structured release metadata, provider-supplied values may later be merged with parser output without changing the UI model.

## Architecture

Introduce a dedicated parsing unit in the torrent/search domain instead of adding more `inferSomething()` functions to `KnabenTorrentSearchProvider`.

Suggested structure:

```text
search/
├── TorrentSearchResult.kt
├── TorrentSearchProvider.kt
├── TorrentReleaseInfo.kt
├── TorrentReleaseParser.kt
├── KnabenTorrentSearchProvider.kt
└── TorrentSearchController.kt
```

`KnabenTorrentSearchProvider` remains responsible only for obtaining and mapping provider data: ID, title, magnet, size, seeders, and source.

`TorrentReleaseParser` is a pure deterministic parser:

```kotlin
fun interface TorrentReleaseParser {
    fun parse(title: String): TorrentReleaseInfo
}
```

The search orchestration layer enriches provider results after retrieval. This keeps provider adapters transport-focused and makes the parser reusable for Torznab/Torrentio/other providers later.

## Data model

Suggested normalized model:

```kotlin
data class TorrentReleaseInfo(
    val resolution: VideoResolution? = null,
    val source: ReleaseSource? = null,
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

Prefer enums for closed vocabularies such as resolution/source/codec/HDR/audio codec so sorting, filtering, labels, and future preferences do not depend on arbitrary strings.

`TorrentSearchResult` becomes conceptually:

```kotlin
data class TorrentSearchResult(
    val id: String,
    val title: String,
    val magnetUri: String,
    val sizeBytes: Long? = null,
    val seeders: Int? = null,
    val source: String? = null,
    val releaseInfo: TorrentReleaseInfo = TorrentReleaseInfo(),
)
```

The existing top-level `quality` field should be removed once all consumers use `releaseInfo.resolution`.

## Parsing rules

Parsing is case-insensitive and token-aware. Dots, underscores, brackets, parentheses, and repeated whitespace are treated as separators where appropriate, but the original title is never mutated for display.

### Resolution

Recognize at least:

- `2160p`, `4K` → 2160p
- `1080p`
- `720p`
- `480p`

Prefer an explicit numeric resolution over a loose alias when both are present.

### Source

Recognize common forms and normalize aliases:

- `REMUX`
- `BluRay`, `BDRip`, `BRRip`
- `WEB-DL`, `WEBDL`
- `WEBRip`
- `HDTV`
- `DVDRip`

Source parsing must avoid false positives from ordinary title words.

### Video codec

Normalize aliases:

- `HEVC`, `H265`, `H.265`, `x265` → HEVC/H.265
- `AVC`, `H264`, `H.264`, `x264` → H.264/AVC
- `AV1` → AV1

### HDR

Recognize independently because one release may contain multiple HDR formats:

- `DV`, `DoVi`, `Dolby Vision`
- `HDR10+`, `HDR10Plus`
- `HDR10`
- generic `HDR`

Specific formats win over generic duplicate labels. For example `DV HDR10` produces Dolby Vision + HDR10, not a third redundant generic HDR badge.

### Audio

Normalize common aliases and prefer the most specific match:

- `TrueHD`
- `DTS-HD MA`, `DTS-HD.MA`, `DTSHDMA`
- `DTS-HD`
- `DTS`
- `EAC3`, `E-AC-3`, `DD+`, `DDP`
- `AC3`, `AC-3`, `DD`
- `AAC`
- `FLAC`
- `PCM`, `LPCM`

`Atmos` is represented as a feature, not as the codec, so `TrueHD Atmos` becomes two pieces of metadata.

Channel counts such as `5.1` and `7.1` are parsed separately from the codec.

### Year

Parse plausible four-digit movie years from the release title. Use a conservative range suitable for film releases and avoid interpreting values such as resolutions or bitrates as years.

The parsed year describes the torrent release title. It is intentionally separate from the catalog movie year. This allows the UI or later validation logic to spot suspicious results whose encoded year disagrees with the selected movie.

### Languages

Only emit language badges for explicit, confidently recognized release tokens such as `ENG`, `EN`, `CZ`, `CZE`, `SK`, `SVK`, `DE`, `GER`, `FR`, `ITA`, `ESP`, or clear textual equivalents.

Do not guess language from the movie title or release group.

### Release group

Release group parsing is best-effort and low priority. Only expose a trailing group when it matches a conventional release-group position/pattern. An uncertain group is better left null than guessed incorrectly.

## Parsing precedence and false positives

The parser should use ordered rules rather than a bag of independent substring checks.

Examples:

- detect `DTS-HD MA` before generic `DTS`
- detect `E-AC-3`/`DD+` before `AC-3`/`DD`
- detect `HDR10+` before `HDR10` before generic `HDR`
- detect `WEB-DL` before generic `WEB`
- use token boundaries so words containing `DV`, `DD`, `AVC`, etc. do not accidentally become badges

Unknown or ambiguous tokens remain only in the raw release title.

## Torrent result UI

Keep the existing selectable row and strong TV focus treatment.

Recommended hierarchy:

```text
[4K] [REMUX] [DV] [HDR10] [HEVC] [TrueHD] [Atmos] [7.1] [ENG]
Dune.Part.Two.2024.2160p.UHD.BluRay.REMUX.DV.HDR10.HEVC.TrueHD.Atmos.7.1-GROUP
68.4 GiB   154 seeds   1337x
```

Badges should wrap onto another line rather than force the release title or right-side action off-screen.

Badge order should remain stable:

1. resolution
2. source
3. HDR formats
4. video codec
5. audio codec
6. audio features
7. channels
8. languages
9. year, only when useful in the layout

Do not render placeholders such as `AUTO`, `UNKNOWN`, or `N/A` for metadata that was not parsed. Missing metadata simply means no badge.

The raw title remains visible because it contains information the parser may not yet understand.

Size, seed count, and provider/source remain secondary text, not badges.

## Error handling

Parsing must never make torrent search fail.

- Empty or malformed titles return an empty `TorrentReleaseInfo`.
- Unknown tokens are ignored.
- A parser exception must not be able to cancel an otherwise valid search result; the implementation should be pure enough that exceptions are not expected, with tests guarding known edge cases.

## Testing

### Unit tests

Add table-driven tests covering representative names such as:

```text
Dune.Part.Two.2024.2160p.UHD.BluRay.REMUX.DV.HDR10.HEVC.TrueHD.Atmos.7.1-GROUP
Movie.2023.1080p.WEB-DL.DDP5.1.H.264-GROUP
Movie.1999.720p.BluRay.x264.DTS-GROUP
Movie.2025.2160p.WEB-DL.HDR10Plus.AV1.EAC3.5.1-GROUP
```

Also cover:

- mixed separators and casing
- aliases such as `x265`, `H.265`, `DDP`, `DoVi`
- precedence collisions such as `DTS-HD MA` vs `DTS`
- multiple HDR formats
- no metadata at all
- title words that resemble short codec/language tokens
- implausible four-digit numbers that must not become years

### UI tests

Verify that:

- parsed badges render in stable order
- missing fields do not render fake placeholders
- raw release title remains visible
- size/seeds/provider remain visible
- a long badge set does not remove focusability or the play/start state

## Future extensions

The normalized model intentionally leaves room for later features without changing the parser/UI boundary:

- user quality/audio preferences
- sorting or filtering by release characteristics
- highlighting year mismatches against the selected TMDB movie
- provider-supplied structured metadata merged with parser output
- media-file probing after a torrent is selected

These are not part of this implementation.
