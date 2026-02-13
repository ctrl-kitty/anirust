# Roadmap: anirust TUI

Goals
- Build a Rust TUI app (anirust) to search anime, select a series or episode, and launch playback in mpv.
- Support multiple providers with automatic discovery (no manual integration).
- Unify anime across providers using Shikimori or MyAnimeList IDs.
- Provide settings for preferred provider and preferred dubbing or subtitles.
- Keep architecture simple, modular, and ready to expand (e.g., anime list sync).

Milestone 0 - Repo Bootstrap
- Create Rust workspace and binary crate anirust.
- Add core dependencies: tokio, reqwest, serde, serde_json, toml, ratatui, crossterm, thiserror, anyhow, async-trait, inventory, url, clap.
- Establish module layout:
  - domain (core models, ids, errors)
  - providers (Shikimori, Yummy)
  - registry (provider auto-registration)
  - services (search, unify, selection)
  - settings
  - ui
  - player
  - tests (core only)

Status: done.

Milestone 1 - Core Domain and Result Model
- Define AnimeId with optional Shikimori and MAL IDs.
- Define Anime, SeriesEntry, Episode, VoiceVariant, SubtitleVariant.
- Define ProviderStatus and ProviderResult<T>:
  - status: Ok | Partial | NotFound | RateLimited | Unauthorized | Error
  - data: Option<T>
  - error: structured error info (message, source, retryable).
- Define ProviderId and ProviderCapabilities (search, series list, episodes).

Status: done. (Includes PlayerKind on Episode.)

Milestone 2 - Provider Registry
- Create AnimeProvider trait:
  - search(query) -> ProviderResult<Vec<Anime>>
  - series(anime_id) -> ProviderResult<Vec<SeriesEntry>>
  - episodes(series_id) -> ProviderResult<Vec<Episode>>
- Implement compile-time discovery using the inventory crate.
- Add a provider registry loader used by services and UI.

Status: done.

Milestone 3 - Settings
- Create config file at ~/.config/anirust/config.toml.
- Settings fields:
  - preferred_provider (default: yummy)
  - yummy.app_token (required for API)
  - yummy.lang (ru default)
  - audio.preferred_dubbings (ordered list)
  - audio.preferred_subtitles (ordered list)
  - player.command (default: mpv)
  - player.args (default: ["--force-window=yes"])
- Add anirust settings subcommand to print location and create defaults if missing.

Status: done. (App token optional; per-anime defaults/history persisted.)

Milestone 4 - Shikimori Provider (Metadata)
- Implement GraphQL client for https://shiki.one/api/graphql.
- Map anime search results into Anime with AnimeId.
- Keep Shikimori as metadata source for unification and canonical titles.

Status: in progress. (Search mapping implemented; no unification yet.)

Milestone 5 - Yummy Provider (Streaming)
- Implement API client for https://api.yani.tv with X-Application header.
- Search: GET /anime?q=... -> map results with remote_ids (shiki or mal).
- Series list: GET /anime/{url} -> use viewing_order.
- Episodes: GET /anime/{id}/videos -> map iframe_url, data.dubbing, number.
- Expose available dubbings as VoiceVariant.

Status: done. (Search/series/episodes wired.)

Milestone 6 - Unification Service
- Combine results from providers by AnimeId:
  - Shikimori data = canonical metadata.
  - Yummy data = playback and episodes.
- If Yummy lacks IDs, resolve via Shikimori search by title (fuzzy or normalized).
- Provide merged list for UI.

Status: not started.

Milestone 7 - TUI Flow
- Build state machine:
  1. Search input
  2. Results list
  3. Series list (viewing order)
  4. Episode list (with dubbing variants)
- Provide navigation and filtering; display provider or source badges.
- Allow dubbing preference from settings; allow manual override per title.

Status: done. (Includes episode search filter, player selection, and auto-select last watched.)

Milestone 8 - Player Integration
- Spawn mpv with selected episode iframe_url.
- If iframe_url is not directly playable, allow fallback to a future resolver.

Status: done. (Kodik resolver + player trait; unsupported players filtered.)

Milestone 9 - Tests (Core Only)
- Unification logic (ID merge, dedupe).
- Settings parse, validate, defaults.
- Provider registry discovery.
- Dubbing preference selection.
- Player command builder.

Status: in progress. (Integration tests for Yummy/Kodik and playback; settings + selection tests added.)

Milestone 10 - Docs and Polish
- Add README with install and run instructions and provider setup.
- Document required tokens and API limits.
- Add example config and troubleshooting.

Status: in progress. (development.md updated; README pending.)

Deferred or Future
- Shikimori or MAL user lists sync.
- Provider-specific cookies or auth for locked content.
- Episode progress tracking.
- Full text search, fuzzy matching, and caching.
