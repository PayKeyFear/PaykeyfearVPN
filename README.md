# PaykeyfearVPN

Android VPN client modeled on [AmneziaVPN](https://github.com/amnezia-vpn/amnezia-client),
with first-class support for:

- **AmneziaWG 2.0** (obfuscated WireGuard)
- **VLESS** (Xray-core)
- **Hysteria2**

Accepts connections via raw share-link URIs **or** full config files
(`.conf`, `.vpn` bundles, YAML).

## Status

Early-stage scaffold — all Kotlin glue is in place, plus the protocol
config parsers. The native Go backends (`amneziawg-go`, `Xray-core`,
`hysteria`) are wired through thin adapter interfaces but the `.so`/`.aar`
binaries are **not yet bundled** — the app runs in a "noop" tunnel mode
and all tests pass on the JVM.

## Project layout

```
app/                      Compose UI + Hilt DI + navigation
core/                     Domain models, logging, abstractions
core-config/              Parsers: AWG/WireGuard .conf, VLESS URI,
                            Hysteria2 YAML/URI, AmneziaVPN .vpn bundles
vpn-service/              Android VpnService, foreground notification,
                            TunnelController (protocol-agnostic)
protocols/awg/            Kotlin bridge to awgmobile.aar (gomobile)
protocols/vless/          Kotlin bridge to vless.aar (gomobile)
protocols/hysteria2/      Kotlin bridge to hysteria.aar (gomobile)
third_party/awg-mobile/         Go wrapper → awg.aar
third_party/vless-mobile/       Go wrapper (Xray-core + tun2socks) → vless.aar
third_party/hysteria2-mobile/   Go wrapper → hysteria.aar
```

## Prerequisites

| Tool               | Version           | Notes                                |
|--------------------|-------------------|--------------------------------------|
| JDK                | 17                | Temurin recommended                  |
| Android SDK        | API 35 / build-tools 34.0.0 | `ANDROID_HOME` must be set  |
| Android NDK        | r26+              | Needed for protocol backends         |
| Gradle             | 8.10.2 (via wrapper) | First bootstrap: `gradle wrapper` |
| Kotlin             | 2.0.21 (provided) | Bundled via AGP                      |
| Go (optional, later) | 1.22+           | For building `amneziawg-go`/Xray/Hysteria via gomobile |

### Windows + VSCode quick start

1. Install JDK 17 (e.g. `choco install temurin17`) and Android SDK Command-line Tools.
2. Set `JAVA_HOME` and `ANDROID_HOME` environment variables.
3. Accept SDK licenses: `sdkmanager --licenses`.
4. Fetch the Gradle wrapper JAR one-time:
   ```cmd
   gradle wrapper --gradle-version 8.10.2
   ```
5. Open this folder in VSCode — recommended extensions will be offered
   automatically (see `.vscode/extensions.json`).
6. Build:
   ```cmd
   gradlew.bat assembleDebug
   ```

## Testing

| Command                                  | What it runs                                    |
|------------------------------------------|-------------------------------------------------|
| `./gradlew test`                         | All JVM unit tests (parsers, ViewModels, etc.)  |
| `./gradlew :app:connectedDebugAndroidTest` | Compose smoke tests on a connected device/emulator |
| `./gradlew ktlintCheck detekt :app:lintDebug` | Static analysis                             |

Continuous integration runs on GitHub Actions —
see [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml).

## Config file support

The [`ConfigParserRegistry`](core-config/src/main/kotlin/com/paykeyfear/vpn/config/ConfigParserRegistry.kt)
auto-detects the format based on content:

| Input                                        | Handled by                 |
|----------------------------------------------|----------------------------|
| `vless://...`                                | `VlessUriParser`           |
| `hysteria2://...`                            | `Hysteria2YamlParser`      |
| YAML starting with `server:` + `auth:`       | `Hysteria2YamlParser`      |
| `[Interface] ... [Peer] ...`                 | `AwgConfParser` (also WG)  |
| `vpn://<base64>` or raw JSON with `containers` | `AmneziaBundleParser`    |

Golden-path test fixtures live under
[`core-config/src/test/resources/configs/`](core-config/src/test/resources/configs/).

## Native backends

All three Go backends are built via a single helper script:

```sh
export ANDROID_NDK_HOME=/path/to/ndk
./scripts/build-native.sh            # skips .aars that already exist
./scripts/build-native.sh --force    # rebuilds everything
```

Outputs land in `protocols/*/libs/*.aar`. Until then, `AwgTunnel`, `VlessTunnel`,
and `Hysteria2Tunnel` detect the absence of their native library and run in
no-op mode, so JVM tests and UI development stay green.

For a full Windows toolchain walkthrough (Go, NDK, gomobile, env vars), see
[docs/TOOLCHAIN_SETUP.md](docs/TOOLCHAIN_SETUP.md).

If you don't want to install Go/NDK locally, trigger the
[**Native backends**](.github/workflows/native-backends.yml) workflow on
GitHub Actions (Actions → "Native backends" → Run workflow) — it
produces the three `.aar` artifacts on a clean Ubuntu runner. Download
them and drop into the corresponding `protocols/*/libs/` folders.

VLESS in particular needs more than a plain `.aar` drop — it requires a
tun2socks bridge alongside Xray-core. See
[docs/VLESS_INTEGRATION.md](docs/VLESS_INTEGRATION.md) for the
architecture and the planned adapter rewrite.

## Always-on VPN

PaykeyfearVPN supports Android's system Always-on VPN out of the box:

- The `VpnService` in [`vpn-service/src/main/AndroidManifest.xml`](vpn-service/src/main/AndroidManifest.xml)
  declares the `android.net.VpnService` intent-filter, which is what the
  platform looks for when the user enables Always-on under
  **Settings → Network & internet → VPN → PaykeyfearVPN → Always-on VPN**.
- When the system starts the service itself (no explicit `ACTION_START`
  intent), [`PaykeyfearVpnService.onStartCommand`](vpn-service/src/main/kotlin/com/paykeyfear/vpn/service/PaykeyfearVpnService.kt)
  falls back to `TunnelSettings.selectedConfig()` and brings up the last
  server the user picked from the Servers screen.
- If no server is selected the service stops itself cleanly rather than
  sitting in the "connecting" state — the user has to pick a server once
  before enabling Always-on.
- Block-connections-without-VPN ("lockdown mode") is fully managed by the
  OS; no extra wiring is required on our side. It will only let traffic
  through while our `Builder.establish()` has an active tun descriptor.

## Release builds

Signed release builds use a keystore that is **never** committed.

One-shot setup:

```sh
./scripts/generate-keystore.sh   # creates release.jks + keystore.properties
                                 # + keystore.secrets.txt for GitHub Secrets
```

Then `./gradlew :app:bundleRelease` produces a signed AAB under
`app/build/outputs/bundle/release/`. Delete `keystore.secrets.txt`
(securely!) after copying the four values into GitHub repository
secrets, and back up `release.jks` to a password manager — losing it
means you can never publish an update under this Play Store listing.

CI picks the same values up from the environment:
`PAYKEYFEAR_KEYSTORE_PATH`, `PAYKEYFEAR_KEYSTORE_PASSWORD`,
`PAYKEYFEAR_KEY_ALIAS`, `PAYKEYFEAR_KEY_PASSWORD`. The release workflow at
[`.github/workflows/release.yml`](.github/workflows/release.yml) decodes a
base64-encoded keystore from the `RELEASE_KEYSTORE_BASE64` secret, runs
release unit tests, builds the signed AAB + APK on every tag push
(`v*`), and attaches them to a draft GitHub Release.

If no keystore is configured the release build falls back to the debug
signing config so `./gradlew assembleRelease` still runs locally.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE). The project bundles
`amneziawg-go` (GPL-3.0), which forces the whole work to GPL-3.0 —
the third-party rollup lives in
[docs/NATIVE_BACKENDS.md](docs/NATIVE_BACKENDS.md#license-rollup).

Before publishing any binary, fetch the full license text:

```sh
curl -o LICENSE.gpl-3.0.txt https://www.gnu.org/licenses/gpl-3.0.txt
```

