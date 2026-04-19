# vless-mobile

Gomobile wrapper that bundles [Xray-core](https://github.com/XTLS/Xray-core)
and [xjasonlyu/tun2socks](https://github.com/xjasonlyu/tun2socks) into one
`vless.aar`. The Kotlin side talks to Xray via SOCKS5 on localhost; the
tun2socks pump bridges the Android TUN fd to that SOCKS5 port.

## Why one module?

Xray and tun2socks run in the same process and share the Android
`VpnService.protect(fd)` callback. Shipping them as two separate gomobile
bindings would duplicate the Go runtime inside the app and make the
Protector plumbing twice as awkward. One `.aar` keeps the ABI small and
the lifecycle local to Go.

## Exported API (see `vlessmobile.go`)

```
StartXray(configJson string) int32           // returns handle (>0) or -1
StopXray(handle int32)
Tun2SocksStart(tunFd int32, host string, port int32) int32
Tun2SocksStop(handle int32)
SetProtector(p Protector)                    // Protector interface { Protect(fd int32) bool }
LastError() string
Version() string
```

## Build

```
./build.sh   # or invoke from scripts/build-native.sh
```

Requires Go 1.22+, gomobile, and `ANDROID_NDK_HOME` pointing at NDK r26+.

## Status

Scaffold only: handles are tracked but the Xray `core.Server` boot and the
tun2socks engine loop are TODOs marked in `vlessmobile.go`. Kotlin
integration uses the same symbol names so the reflective bridge
(`XrayAdapterImpl`, `Tun2SocksBridgeImpl`) lands before the Go wiring is
complete.
