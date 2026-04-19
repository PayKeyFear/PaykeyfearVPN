# awgmobile

Gomobile wrapper around
[amnezia-vpn/amneziawg-go](https://github.com/amnezia-vpn/amneziawg-go).
Builds into `protocols/awg/libs/awg.aar`.

## Exported API

```
Start(cfg string, tunFd int32) int32   // handle > 0, or -1 on error
Stop(handle int32)
GetStats(handle int32) string          // "rx=<n>\ntx=<n>\n"
GetVersion() string
LastError() string
```

`cfg` is the amneziawg-go IPC string produced by `AwgConfigRenderer` on
the Kotlin side (e.g. `private_key=...\nendpoint=...\nallowed_ip=...`).

## Build

```sh
export ANDROID_NDK_HOME=/path/to/android-ndk-r26d
./build.sh
```

`amneziawg-go` is GPL-3.0, so shipping this `.aar` forces the Android app
to GPL-3.0 as well. See `docs/NATIVE_BACKENDS.md` for the license rollup.
