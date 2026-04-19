# VLESS integration notes

Unlike AWG and Hysteria2, the Xray-core backend does **not** accept a
TUN file descriptor directly. Adopting the design from v2rayNG and
AmneziaVPN, a VLESS tunnel runs as three cooperating components:

```
┌────────────────────┐    TCP/UDP packets     ┌──────────────────┐
│  Android TUN fd    │ ───────────────────►  │  tun2socks       │
│  (from VpnService) │ ◄─────────────────────│  (bridge)        │
└────────────────────┘                        └──────────────────┘
                                                       │
                                             SOCKS5 on 127.0.0.1:N
                                                       ▼
                                              ┌────────────────┐
                                              │  Xray-core      │
                                              │  (in-process)   │
                                              └────────────────┘
                                                       │
                                         VpnService.protect(fd)
                                                       ▼
                                              Real network egress
```

1. **Xray-core** is started via `vlessmobile.Vlessmobile.StartXray(...)`
   from our own bundled wrapper in `third_party/vless-mobile/`. Given an
   Xray JSON config (our `XrayConfigBuilder` output), it listens for
   SOCKS5 on a loopback port specified in the config.
2. **tun2socks** reads Ethernet frames off the TUN `ParcelFileDescriptor`
   and forwards each TCP/UDP flow to the local SOCKS5 endpoint. Candidate
   libraries: `github.com/xjasonlyu/tun2socks/v2`,
   `github.com/heiher/hev-socks5-tunnel` (C, via NDK), or `go-tun2socks`.
3. **`Protect(fd int64) bool`** — Xray calls back into the JVM via the
   `V2RayVPNServiceSupportsSet` interface so its outbound sockets can be
   wrapped in `VpnService.protect()`. Without this, Xray's traffic would
   loop back through our own tunnel.

## Kotlin surface

The split above maps to two interfaces on the Kotlin side (see
`VlessTunnel.kt`):

```kotlin
interface XrayAdapter {
    fun available(): Boolean
    fun startXray(configJson: String, protector: Protector)
    fun stopXray()
}

interface Tun2SocksBridge {
    fun available(): Boolean
    fun start(tunFd: Int, socksHost: String, socksPort: Int)
    fun stop()
}
```

Real implementations (`NativeXrayAdapter`, `NativeTun2SocksBridge`) sit
in `NativeVlessAdapters.kt` and reflect into `vlessmobile.Vlessmobile`
via `VlessNative`. Both default to noop objects so fresh checkouts (no
`vless.aar`) still build and the JVM unit tests stay deterministic.

## Status

- ✅ `third_party/vless-mobile/` scaffold landed — exposes
  `StartXray`, `StopXray`, `Tun2SocksStart`, `Tun2SocksStop`,
  `SetProtector`, `LastError`, `Version`.
- ✅ Kotlin reflective bridge (`VlessNative` + `NativeXrayAdapter` +
  `NativeTun2SocksBridge`) wired via Hilt (`AppModule`).
- ✅ Go-side Xray boot — `vlessmobile.StartXray` now loads the JSON
  config via `xray-core`'s `serial.LoadJSONConfig` and starts a live
  `core.Instance`. All transport plugins are pulled in via
  `_ "github.com/xtls/xray-core/main/distro/all"`.
- ✅ Go-side tun2socks engine — `Tun2SocksStart` instantiates
  `xjasonlyu/tun2socks/v2` engine pointed at `fd://<tunFd>` and
  `socks5://127.0.0.1:<port>`.
- ✅ Protect hook — `SetProtector` plugs into
  `xraynet.RegisterDialerController` so every outbound socket Xray
  opens is wrapped in `VpnService.protect(fd)` before the TLS
  handshake runs.
- ⬜ `pinSHA256`, `serviceName` for gRPC transport, and Reality
  fingerprints to be validated end-to-end on a device once
  `protocols/vless/libs/vless.aar` is produced by the
  `native-backends.yml` workflow.
- ⬜ VLESS-side stats polling: `VlessTunnel.stats()` currently emits
  zero. Surfacing Xray's `StatsService.QueryStats` through the
  gomobile bridge is tracked separately.

## Why not just expose `tunFd` to Xray?

Because Xray-core has no userspace IP stack — it's a L7 proxy. A TUN
device delivers L3 packets; you need a stack (gVisor/lwip) between the
two. `tun2socks` is that stack. Adding it to Xray upstream has been
proposed and rejected — the v2rayNG+AmneziaVPN combo is the canonical
design.
