# Providers Module

Purpose
- Implementations that talk to external anime APIs.

Expandable
- Yes, new providers are expected.

How to add a new provider
- For full anime providers (search/series/episodes): implement `AnimeProvider` in
  `anirust/src/providers/mod.rs`.
- For metadata-only providers (search/enrichment): implement `MetadataProvider` in
  `anirust/src/providers/mod.rs`.
- Return `ProviderResult` from all methods.
- Register via `inventory::submit!` using `ProviderFactory` or
  `MetadataProviderFactory` in `anirust/src/registry/mod.rs`.
- Use `ProviderId` for provider IDs.

Useful types and functions
- `ProviderResult::{ok, partial, not_found, rate_limited, unauthorized, error}`
- `ProviderError::new`, `ProviderError::with_source`
- `ProviderId::new`
- `PlayerKind::from_url`
- `settings::ensure_config`, `Settings::load`
- `utils::{normalized_text, parse_url, map_reqwest_error, normalize_id}`

Useful traits
- `AnimeProvider`
- `MetadataProvider`
