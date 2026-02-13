# anirust-tui

Rust TUI to search anime, pick a series/episode, and play via mpv.

Highlights
- Search via Yummy, enrich metadata via Shikimori, and merge results by IDs.
- Provider auto-registration with `inventory` (`AnimeProvider` + `MetadataProvider`).
- Playback via mpv with resolver support for Kodik/Direct; unsupported players are filtered.

Requirements
- Rust toolchain
- OpenSSL, pkg-config, and a C compiler (or use nix-shell)
- `mpv` installed on the system

Run (recommended)
```sh
nix-shell -p gcc pkg-config openssl --command "cargo run -p anirust -- tui"
```

Other commands
```sh
nix-shell -p gcc pkg-config openssl --command "cargo run -p anirust -- settings"
nix-shell -p gcc pkg-config openssl --command "cargo run -p anirust -- providers"
nix-shell -p gcc pkg-config openssl --command "cargo run -p anirust -- search \"naruto\""
```

Config
- Stored at `~/.config/anirust/config.toml` (XDG).
- Defaults are created automatically on first run.

Tests
```sh
nix-shell -p gcc pkg-config openssl --command "cargo test -p anirust"
```
Integration tests hit network (Yummy/Kodik), so they may be flaky.
