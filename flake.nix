{
  description = "Kiki — a tiny e-ink Android launcher";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable-small";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };

      android = pkgs.androidenv.composeAndroidPackages {
        buildToolsVersions = [ "34.0.0" ];
        platformVersions = [ "34" ];
        includeEmulator = false;
        includeNDK = false;
        includeCmake = false;
        includeSources = false;
        includeSystemImages = false;
      };

      sdk = "${android.androidsdk}/libexec/android-sdk";
      buildTools = "${sdk}/build-tools/34.0.0";
      androidJar = "${sdk}/platforms/android-34/android.jar";

      kiki = pkgs.stdenvNoCC.mkDerivation {
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
        nativeBuildInputs = [ pkgs.jdk17 pkgs.python3 ];
        dontConfigure = true;
        dontPatchELF = true;
        dontStrip = true;
        buildPhase = ''
          runHook preBuild
          export ANDROID_BUILD_TOOLS="${buildTools}"
          export ANDROID_JAR="${androidJar}"
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
    in {
      packages.${system} = {
        inherit kiki;
        default = kiki;
      };
      devShells.${system} = {
        default = pkgs.mkShell {
          packages = [ pkgs.jdk17 pkgs.python3 android.androidsdk ];
          ANDROID_HOME = sdk;
          ANDROID_SDK_ROOT = sdk;
          ANDROID_BUILD_TOOLS = buildTools;
          ANDROID_JAR = androidJar;
        };
      };
    };
}
