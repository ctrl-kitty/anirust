# Domain Module

Purpose
- Core data types shared across CLI, providers, services, UI, and player.

Expandable
- Yes, when a new core concept is needed.

How to expand
- Add new domain types in `anirust/src/domain/mod.rs`.
- Keep types small, explicit, and serializable.
- Use `Option<T>` for optional fields.

Useful types and functions
- `AnimeId`
- `Anime`
- `SeriesEntry`
- `Episode`
- `ProviderId`
- `ProviderResult` and `ProviderError`
- `ProviderStatus`
- `PlayerKind::from_url`
