# Player Module

Purpose
- Resolve playback URLs and spawn the player.

Expandable
- Yes, add new player resolvers.

How to add a new player
- Add a new `PlayerKind` in `anirust/src/domain/mod.rs`.
- Implement `PlayerResolver` in `anirust/src/player/resolver.rs`.
- Add an entry to `RESOLVERS` in `anirust/src/player/resolver.rs`.
- Add tests in `anirust/src/player/tests.rs`.

Useful functions
- `play_with_kind`
- `resolve_playback_with_kind`
- `is_supported_kind`
- `player_label`
- `player_order`
- `utils::normalize_url`
