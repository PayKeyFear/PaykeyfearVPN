# Bundling native protocol backends

Each `:protocols:*` module ships with a Kotlin-only noop adapter by
default. The real Go-based backends plug in as `.aar` files dropped into
`protocols/<name>/libs/` — the Gradle build picks them up automatically
via `fileTree("libs")` in each module's `build.gradle.kts`.

## Prerequisites

```sh
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
gomobile init
```

Set `$ANDROID_NDK_HOME` to an NDK r26+ install. See
[TOOLCHAIN_SETUP.md](TOOLCHAIN_SETUP.md) for Windows-specific steps.

## One-shot build

```sh
./scripts/build-native.sh          # builds all three, skipping present .aars
./scripts/build-native.sh --force  # rebuild everything
```

The script expects:

| Module              | Wrapper source                     | Output                                  |
|---------------------|------------------------------------|-----------------------------------------|
| `:protocols:awg`    | `third_party/awg-mobile/`          | `protocols/awg/libs/awg.aar`            |
| `:protocols:vless`  | `third_party/vless-mobile/` (Xray + tun2socks) | `protocols/vless/libs/vless.aar`   |
| `:protocols:hysteria2` | `third_party/hysteria2-mobile/`  | `protocols/hysteria2/libs/hysteria.aar` |

## How the Kotlin side picks up each .aar

- **AWG (`:protocols:awg`).** `AwgNative` reflectively looks up
  `awgmobile.Awgmobile` (the class gomobile bind generates from our
  wrapper). No compile-time dependency on the `.aar` — `available` is
  `false` on fresh clones.
- **VLESS (`:protocols:vless`).** `VlessTunnel` splits the work behind two
  interfaces: `XrayAdapter` (the SOCKS5 proxy) and `Tun2SocksBridge` (the
  packet pump). `NativeXrayAdapter` / `NativeTun2SocksBridge` (wired by
  `AppModule`) both reflect into `vlessmobile.Vlessmobile` generated from
  `third_party/vless-mobile/`; if the .aar is missing, `available()` is
  `false` and the tunnel runs in noop mode. See
  [VlessTunnel](../protocols/vless/src/main/kotlin/com/paykeyfear/vpn/protocols/vless/VlessTunnel.kt)
  and `VlessNative.kt`.
- **Hysteria2 (`:protocols:hysteria2`).** Same pattern as VLESS. The
  wrapper `hy2mobile` exposes `Start(yaml, tunFd)` / `Stop(id)` /
  `Stats(id)` — see
  [third_party/hysteria2-mobile/README.md](../third_party/hysteria2-mobile/README.md).

## License rollup

`amneziawg-go` is GPL-3.0. Bundling `awg.aar` forces the whole app to
GPL-3.0 (see [../LICENSE](../LICENSE)).

- Xray-core: MPL-2.0 (file-level copyleft).
- hysteria: MIT.

Anything stricter than GPL-3.0 is incompatible; MPL + MIT are OK.

## Testing with real backends

1. Run a local AWG server (e.g. via the AmneziaVPN self-hosted container).
2. Export a `.conf` and paste into the app's Import screen.
3. Verify the Home tab transitions `Disconnected → Connecting → Connected`.
4. `adb shell dumpsys connectivity | grep -i vpn` should show an active
   interface belonging to `com.paykeyfear.vpn.debug`.

## CI considerations

Go toolchain + NDK adds ~1.5 GB to the runner image. Keep the native
build in a separate GitHub Actions job that caches `~/.gomobile` and
`~/go/pkg/mod`. The existing `android-ci.yml` only builds the
Kotlin/Compose side.

For tagged releases, the
[`assemble-release.yml`](../.github/workflows/assemble-release.yml)
workflow reuses the same toolchain to build all three `.aar`s, drops
them into `protocols/*/libs/`, runs release unit tests, and produces a
signed AAB + APK in a single job. This is what produces the bundles
attached to GitHub Releases — `release.yml` is kept around as a
Kotlin-only fallback for hot-fix builds where the `.aar`s have not
changed and you want to skip the ~25-minute Go compile.

## Internal-track release checklist

For an internal-only release (no Play Store submission yet):

1. Trigger **assemble-release.yml** on `workflow_dispatch` from the
   `main` branch. Set `RELEASE_KEYSTORE_BASE64` etc. as repo secrets,
   or omit them to fall back to the debug signing config.
2. Download the `paykeyfear-<sha>-native` artifact — contains the
   signed `.aab` and `.apk`.
3. `adb install -r app-release.apk` on a test device.
4. Smoke-test each protocol against a known-good server (one AWG, one
   VLESS, one Hysteria2). Watch the **Logs** screen for handshake
   errors; check the foreground notification reflects `↓/↑` traffic.
5. Compare `Settings → About → Version` against the tag.

Steps 4–5 are the only tasks that require human input — everything
else is fully automated.
