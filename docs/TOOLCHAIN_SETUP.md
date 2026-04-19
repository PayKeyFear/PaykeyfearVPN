# Toolchain setup — Windows 11

Everything needed to run `scripts/build-native.sh` on a fresh Windows 11
machine. Most steps also apply to macOS/Linux; this doc is Windows-first
because the current maintainer runs Windows.

## 1. Git Bash

Already installed if you have Git for Windows. `scripts/build-native.sh`
is bash-only — run it from **Git Bash**, not `cmd.exe` or PowerShell.

Verify:
```sh
uname -o   # MSYS or similar
```

## 2. Go 1.22+

Download: https://go.dev/dl/ → `go1.22.<x>.windows-amd64.msi`. Installer
adds `C:\Program Files\Go\bin` to `PATH` automatically.

Verify:
```sh
go version   # go version go1.22.x windows/amd64
```

## 3. Android SDK + NDK

Easiest path: install **Android Studio** (it bundles the SDK and an SDK
Manager). If you don't want Android Studio, install
[cmdline-tools](https://developer.android.com/studio#command-tools) and
run:

```sh
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" \
           "ndk;26.3.11579264"
```

Then set environment variables (User-level, via System Properties →
Environment Variables):

| Variable            | Value                                                   |
|---------------------|---------------------------------------------------------|
| `ANDROID_HOME`      | `C:\Users\<you>\AppData\Local\Android\Sdk`              |
| `ANDROID_NDK_HOME`  | `%ANDROID_HOME%\ndk\26.3.11579264`                      |
| `PATH` (append)     | `%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\cmdline-tools\latest\bin` |

Verify in a **new** Git Bash shell:
```sh
echo "$ANDROID_NDK_HOME"
ls "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt"   # should list windows-x86_64
```

## 4. gomobile + gobind

```sh
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
export PATH="$PATH:$(go env GOPATH)/bin"
gomobile init
```

`gomobile init` needs `$ANDROID_NDK_HOME` set (step 3). It downloads
~200 MB of extra tooling and writes it to `%USERPROFILE%\go\pkg\gomobile`.

Verify:
```sh
gomobile version
```

## 5. Java 17 (Temurin / Microsoft Build)

Gradle 8.10 needs JDK 17. Android Studio ships one; otherwise install
[Microsoft Build of OpenJDK 17](https://learn.microsoft.com/en-us/java/openjdk/download)
and set:

| Variable     | Value                                                |
|--------------|------------------------------------------------------|
| `JAVA_HOME`  | `C:\Program Files\Microsoft\jdk-17.0.<x>-hotspot`    |

Verify:
```sh
java --version   # openjdk 17.x
```

## 6. One-time Gradle wrapper bootstrap

The repo ships `gradlew`/`gradlew.bat` but **not** `gradle-wrapper.jar`
(to avoid committing binaries). Bootstrap it once:

```sh
# From an existing Gradle install (brew/choco/manual)
gradle wrapper --gradle-version 8.10.2
```

After this, `./gradlew` works standalone.

## 7. Verifying the full chain

```sh
./gradlew :app:assembleDebug        # Kotlin + Compose only (fast, no Go)
./scripts/build-native.sh           # AWG + Hysteria2 wrappers (~5–10 min)
./gradlew :app:assembleRelease      # full, picks up the newly-built .aars
```

The first `assembleDebug` should succeed even before `build-native.sh`
runs — the protocol modules fall through to their noop adapters when the
`.aar` files are absent.

## Gotchas

- **`gomobile: command not found`** after install — you forgot to append
  `$(go env GOPATH)/bin` to `PATH`. Reopen the shell after editing env.
- **`fatal: NDK not found`** from `gomobile init` — the NDK path has
  spaces or is symlinked in a way gomobile can't resolve. Use the plain
  `C:\Android\ndk\<version>` layout if Android Studio's default path
  gives you trouble.
- **`go: module github.com/... requires Go 1.22`** — Chocolatey's `go`
  package lags behind. Use the MSI from go.dev.
