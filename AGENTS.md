# AGENTS.md

This file is for coding agents working in this repository.
Keep changes minimal, follow existing patterns, and update tests when needed.

## Repository Overview
- Workspace root is `/home/ktvsky/coding/anirust-tui`.
- Main crate is `anirust/` (Rust 2021).
- App is a Ratatui TUI with providers, settings, playback, and tests.
- No Cursor or Copilot instructions were found in `.cursor/`, `.cursorrules`, or `.github/`.

## Build, Run, Lint, Test
### Toolchain Notes
- Tests/builds need OpenSSL, pkg-config, and a C compiler.
- Recommended: run commands inside nix-shell to ensure deps.
- Use `-p anirust` in cargo commands (workspace root).

### Build
- `nix-shell -p gcc pkg-config openssl --command "cargo build -p anirust"`
- `nix-shell -p gcc pkg-config openssl --command "cargo build -p anirust --release"`

### Run CLI
- `nix-shell -p gcc pkg-config openssl --command "cargo run -p anirust"`
- `nix-shell -p gcc pkg-config openssl --command "cargo run -p anirust -- settings"`
- `nix-shell -p gcc pkg-config openssl --command "cargo run -p anirust -- providers"`
- `nix-shell -p gcc pkg-config openssl --command "cargo run -p anirust -- tui"`

### Tests (All)
- `nix-shell -p gcc pkg-config openssl --command "cargo test -p anirust"`
- Integration tests hit network (Yummy/Kodik); expect flakiness.
- If a test fails due to network, retry once before changing code.

### Single Test Examples
- Unit test by name: `... cargo test -p anirust ui::app_tests::select_last_watched_episode`
- Specific test module: `... cargo test -p anirust player::tests::player_labels_and_support_flags`
- Integration test file: `... cargo test -p anirust --test yummy_kodik`
- Single integration test: `... cargo test -p anirust --test yummy_kodik yummy_naruto_2x2_has_kodik_links`
- Run only lib tests: `... cargo test -p anirust --lib`

### Format / Lint
- `cargo fmt` (default rustfmt; no repo-specific config).
- `cargo clippy -p anirust --all-targets` (optional, not enforced).
- Prefer fixing warnings in touched modules when feasible.

## Code Style Guidelines
### Imports
- Order: std -> external crates -> crate/super.
- Group imports with braces for related types.
- Keep `use crate::...` paths explicit; prefer `super::` inside modules.
- Add a blank line between logical import groups.

### Formatting
- Use rustfmt defaults (4 spaces, trailing commas).
- Keep match arms concise; pull complex logic into helpers.
- Prefer early returns over deep nesting in UI handlers.

### Naming
- Types/traits/enums: `PascalCase`.
- Functions/vars/modules: `snake_case`.
- Constants: `SCREAMING_SNAKE_CASE`.
- Use domain names (`AnimeId`, `SeriesEntry`, `Episode`) consistently.
- Use explicit names for side-effects: `open_series`, `apply_episode_filter`.

### Types and Data
- `ProviderId` is a newtype; avoid raw `String` for provider IDs.
- Use `Option<T>` for optional fields; avoid sentinel values.
- Keep ordering deterministic using `BTreeMap`/`BTreeSet` when needed.
- Prefer `Vec<T>` for ordered UI lists and provider payloads.
- Normalize user-facing text (trim/empty check) before storing.

### Error Handling
- CLI and filesystem: use `anyhow::Result` with `.context()`/`.with_context()`.
- Providers: return `ProviderResult<T>` with `ProviderStatus`.
- Use `ProviderError { message, source, retryable }` for structured errors.
- Map provider partials via `ProviderResult::partial` and show warnings in UI/CLI.
- Avoid `unwrap()` outside tests; prefer `?` and explicit error messages.
- When mapping reqwest errors, preserve retryable/timeouts.

### Async / Networking
- Providers are `async_trait` and must be `Send + Sync`.
- Use `reqwest` with `rustls-tls` (already configured).
- Prefer small helper methods for decoding/normalizing payloads.
- Keep API base URLs and headers in provider modules.

### UI Architecture
- Keep state in `ui/app.rs` and rendering in `ui/app_view.rs` and `ui/render.rs`.
- Put key handling in `ui/handlers.rs` and flow logic in `ui/handlers_flow.rs`.
- Maintain `View`/`Focus` transitions explicitly.
- When adding UI lists, update both list items and footer hints.
- Update filtering behavior in `app.rs` and view headers in `app_view.rs`.

### Provider/Player Conventions
- Providers register via `inventory` and implement `AnimeProvider`.
- Use `capabilities()` to drive UI decisions (search/series/episodes).
- Episode filtering uses `player::is_supported_kind`.
- Only Kodik/Direct resolvers are supported; others should be filtered.
- When adding a new player, implement `PlayerResolver` and update `player_order`/`player_label`.
- Keep `ResolvedMedia` as URL + headers; pass headers to mpv.

### Search / Unify
- Search is client-sorted for relevance; keep sorting deterministic.
- Unify service merges Shikimori metadata into Yummy results when IDs match.
- Preserve Yummy IDs for series/episodes flow even after unify.

### Settings & Config
- Config path is XDG-based: `~/.config/anirust/config.toml`.
- Call `settings::ensure_config()` before writes.
- Keep defaults in `Settings::default()` and persist via `save()`.
- Per-anime defaults/history are keyed by `shiki:<id>:mal:<id>` when possible.

### Player / Playback
- Use `player::resolve_playback_with_kind` before spawning mpv.
- Keep URL normalization in `player::utils`.
- `player::play_with_kind` is the primary entry for UI.

### Serialization
- Use serde with explicit field renames/aliases for API payloads.
- Avoid failing on missing optional fields; use `Option` and defaults.

### Testing
- Unit tests live in module `tests.rs` files with `#[cfg(test)]`.
- Integration tests live in `anirust/tests/`.
- Network tests should create temp config dirs (see `tests/yummy_kodik.rs`).
- Prefer deterministic tests; avoid relying on specific ordering unless sorted.
- If a test uses time or randomness, bound or seed it.

## Project Layout (Key Files)
- `anirust/src/domain/mod.rs`: core types, IDs, provider result types.
- `anirust/src/providers/`: provider implementations.
- `anirust/src/services/`: unify/search utilities.
- `anirust/src/player/`: playback resolution and mpv spawn.
- `anirust/src/ui/`: TUI state, rendering, handlers.
- `anirust/src/settings/mod.rs`: config and persistence.
- `anirust/tests/`: integration tests.

## Workflow Expectations for Agents
- Read relevant modules before editing; follow existing conventions.
- Keep changes focused and avoid large refactors unless requested.
- Update tests or add new ones when behavior changes.
- Mention any limitations or network dependencies in final notes.
- Do not introduce new dependencies without updating Cargo.toml.

## Notes
- The repo is a simple workspace; use `-p anirust` in cargo commands.
- Integration tests require network access and Yummy/Kodik availability.
- No extra lint rules are configured in the repo.
- If OpenSSL errors appear, ensure pkg-config and gcc are available in the shell.
