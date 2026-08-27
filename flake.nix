{
  description = "Kiki — a tiny e-ink Android launcher";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-24.11";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};
      buildTools = pkgs.fetchurl {
        url = "https://dl.google.com/android/repository/build-tools_r34-linux.zip";
        sha256 = "e858c4b60069d0431051b225d384413b1643e1289b00a4825aed347f25bd510f";
      };
      platform = pkgs.fetchurl {
        url = "https://dl.google.com/android/repository/platform-34-ext7_r03.zip";
        sha256 = "16fdb74c55e59ae3ef52def135aec713508467bd56d7dabcd8c9be31fa8b20f3";
      };
    in {
      packages.${system}.kiki = pkgs.stdenvNoCC.mkDerivation {
        pname = "kiki";
        version = "1.4";
        src = pkgs.lib.cleanSourceWith {
          src = ./android;
          filter = path: type:
            let b = baseNameOf path;
            in b != ".work"
            && b != "dist"
            && !(pkgs.lib.hasSuffix ".keystore" b)
            && !(pkgs.lib.hasSuffix ".apk" b)
            && !(pkgs.lib.hasSuffix ".idsig" b);
        };
        nativeBuildInputs = [ pkgs.jdk17 pkgs.unzip pkgs.python3 ];
        dontConfigure = true;
        dontPatchELF = true;
        dontStrip = true;
        buildPhase = ''
          runHook preBuild
          sdk=$(mktemp -d)
          unzip -q ${buildTools} -d "$sdk"
          unzip -q ${platform} -d "$sdk"
          export ANDROID_SDK="$sdk"
          export ANDROID_BUILD_TOOLS="$sdk/android-14"
          export ANDROID_JAR="$sdk/android-34/android.jar"
          bash ./build.sh "$PWD/out"
          runHook postBuild
        '';
        installPhase = ''
          mkdir -p $out
          cp out/kiki.apk $out/kiki.apk
        '';
        meta = {
          description = "Tiny e-ink Android launcher";
          license = pkgs.lib.licenses.gpl3Plus;
          platforms = [ system ];
        };
      };
      packages.${system}.default = self.packages.${system}.kiki;

      devShells.${system}.default = pkgs.mkShell {
        packages = [ pkgs.jdk17 pkgs.python3 pkgs.unzip pkgs.zip ];
      };
    };
}
