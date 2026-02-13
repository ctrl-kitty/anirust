# anirust-tui

Rust TUI to search anime, pick a series/episode, and play via mpv.

Cool features
- Unified results: search via Yummy, enrich with Shikimori, and merge by IDs.
- Preferred dub setting for playback defaults.
- Remembers your last watched episode for quick resume.
- Provider auto-registration via `inventory` (`AnimeProvider` + `MetadataProvider`).
- Playback via mpv with resolver support for Kodik/Direct; unsupported players are filtered.

Requirements
- Rust toolchain
- OpenSSL, pkg-config, and a C compiler (or use nix-shell)
- `mpv` installed on the system

Installation (Rust)
```sh
git clone https://github.com/ctrl-kitty/anirust.git
cd anirust
cargo build -p anirust --release
```
The binary is at `target/release/anirust`.

Install variant (Rust)
```sh
git clone https://github.com/ctrl-kitty/anirust.git
cd anirust
cargo install --path anirust
```
The binary is at `~/.cargo/bin/anirust`.

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

NixOS (flakes)
Add the input:
```nix
inputs = {
  nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  anirust.url = "github:ctrl-kitty/anirust";
};
```
Install the package:
```nix
outputs = { self, nixpkgs, anirust, ... }:
{
  nixosConfigurations.my-host = nixpkgs.lib.nixosSystem {
    system = "x86_64-linux";
    modules = [
      ({ pkgs, ... }: {
        nixpkgs.overlays = [
          (final: prev: {
            anirust = anirust.packages.${final.stdenv.hostPlatform.system}.default;
          })
        ];
        environment.systemPackages = [ pkgs.anirust ];
      })
    ];
  };
};
```

Config
- Stored at `~/.config/anirust/config.toml` (XDG).
- Defaults are created automatically on first run.

Tests
```sh
nix-shell -p gcc pkg-config openssl --command "cargo test -p anirust"
```
Integration tests hit network (Yummy/Kodik), so they may be flaky.

Issues and contributions
We welcome issues for new features, improvements, and bug reports. If you want something new, open an issue with a short description and any references.

Code of Conduct
Be respectful and inclusive in all issues, PRs, and discussions. Harassment or discrimination is not tolerated.
