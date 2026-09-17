{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  name = "anirust-android-dev-shell";

  buildInputs = with pkgs; [
    jdk21
  ];

  shellHook = ''
    export ANDROID_HOME="''${ANDROID_HOME:-$HOME/Android/Sdk}"
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
    export JAVA_HOME="${pkgs.jdk21}"
    export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
    echo "=================================================="
    echo "  Anirust Android Build Environment (nix-shell)   "
    echo "  Java: $(java -version 2>&1 | head -n 1)"
    echo "  Android SDK: $ANDROID_HOME"
    echo "=================================================="
  '';
}
