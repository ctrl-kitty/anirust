{
  description = "anirust TUI";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-25.11";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
        filteredSrc = pkgs.lib.cleanSourceWith {
          src = ./.;
          filter = path: type:
            let
              root = toString ./.;
              pathStr = toString path;
              relPath =
                if pkgs.lib.hasPrefix (root + "/") pathStr
                then pkgs.lib.removePrefix (root + "/") pathStr
                else pathStr;
              allowed =
                relPath == "" ||
                relPath == "Cargo.toml" ||
                relPath == "Cargo.lock" ||
                relPath == "anirust" ||
                pkgs.lib.hasPrefix "anirust/" relPath;
              denied = pkgs.lib.hasPrefix "anirust/tests/" relPath;
            in
            allowed && !denied;
        };
      in
      {
        packages.default = pkgs.rustPlatform.buildRustPackage {
          pname = "anirust";
          version = "0.1.0";
          src = filteredSrc;
          cargoLock = {
            lockFile = ./Cargo.lock;
          };
          nativeBuildInputs = [ pkgs.pkg-config ];
          buildInputs = [ pkgs.openssl ];
          cargoBuildFlags = [ "-p" "anirust" ];
          cargoTestFlags = [ "-p" "anirust" "--lib" ];
        };

        devShells.default = pkgs.mkShell {
          nativeBuildInputs = [
            pkgs.cargo
            pkgs.pkg-config
            pkgs.rustc
            pkgs.rustfmt
          ];
          buildInputs = [ pkgs.openssl ];
        };
      }
    );
}
