# Development Notes: anirust

## Current State
- TUI MVP: search -> results -> series -> dubbing -> save default -> episodes, with filter (`f` or `/`) and mpv playback.
- Results are client-sorted for better relevance, and series auto-selects the matching Yummy ID.
- Episodes auto-focus the last watched entry and skip the dubbing chooser when a saved default exists.
- Providers: Yummy is fully wired (search/series/episodes), Shikimori search is stubbed with GraphQL mapping.
- Playback: Kodik iframe URLs are resolved into playable m3u8 URLs, with HTTP headers passed to mpv.
- Players: `PlayerKind` enum + `PlayerResolver` trait; TUI supports switching player (`p`).
- Settings: config lives under XDG config; defaults generated on first run; per-anime defaults and history saved.
- Tests: integration tests validate Kodik availability and m3u8 playability, plus unit tests for selection and history.

## Concepts
- Domain model: `AnimeId` ties together Shikimori/MAL/Yummy IDs; `Anime`, `SeriesEntry`, `Episode`, `VoiceVariant` describe the search/selection flow.
- Provider results: `ProviderStatus` and `ProviderResult<T>` wrap data + structured errors.
- Provider discovery: providers register via the inventory crate and are loaded into a registry.
- TUI state machine: explicit `View` (search/series/dubbing/save/player/episodes) plus `Focus` (input/list/filter).
- Playback resolution: `player::resolve_playback_with_kind` returns `ResolvedMedia` (URL + headers) before mpv spawn.

## Important Traits and Interfaces
- `AnimeProvider` (`anirust/src/providers/mod.rs`)
  - `search(query) -> ProviderResult<Vec<Anime>>`
  - `series(anime_id) -> ProviderResult<Vec<SeriesEntry>>`
  - `episodes(series_id) -> ProviderResult<Vec<Episode>>`
  - `capabilities()` drives UI decisions.
- `ProviderFactory` (inventory) enables compile-time discovery for the registry.

## Paths and Responsibilities
- `Cargo.toml`, `anirust/Cargo.toml` - workspace + deps.
- `anirust/src/main.rs` - CLI entry; default launches TUI.
- `anirust/src/domain/mod.rs` - core types: IDs, models, provider result types.
- `anirust/src/providers/mod.rs` - `AnimeProvider` trait.
- `anirust/src/providers/yummy.rs` - Yummy API client (search/series/episodes).
- `anirust/src/providers/shikimori.rs` - Shikimori GraphQL search mapping.
- `anirust/src/registry/mod.rs` - provider registry and inventory loader.
- `anirust/src/settings/mod.rs` - config load/save, defaults, per-anime history.
- `anirust/src/player/mod.rs` - playback resolver + mpv spawn.
- `anirust/src/ui/mod.rs` - TUI runtime wiring.
- `anirust/src/ui/app.rs` - state model + list rendering helpers.
- `anirust/src/ui/handlers.rs` - key handling and flow logic.
- `anirust/src/ui/render.rs` - draw function for Ratatui.
- `anirust/src/ui/playback.rs` - suspend/resume terminal + playback.
- `anirust/src/ui/input.rs` - input editing helpers.
- `anirust/src/ui/selection.rs` - list selection helpers.
- `anirust/tests/yummy_kodik.rs` - naruto + 2x2 + kodik integration test.
- `anirust/tests/kodik_playback.rs` - validates m3u8 is playable.

## Known Limitations
- Only Kodik/Direct are resolved to a playable URL. Other iframe players are unsupported and filtered.
- Integration tests depend on Yummy/Kodik availability and network access.

## Planned Changes (Design Notes)
- Add Alloha resolver and optional per-anime preferred player in settings.
- Add provider switching when multiple streaming providers are available.
