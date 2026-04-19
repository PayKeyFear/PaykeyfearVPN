# hy2mobile

Gomobile-friendly wrapper around
[apernet/hysteria](https://github.com/apernet/hysteria). Produces
`protocols/hysteria2/libs/hysteria.aar` consumed by the
`:protocols:hysteria2` module.

## Exported API

```
SetProtector(p Protector)            // VpnService.protect bridge
Start(yamlConfig string, tunFd int32) string   // handle; "" on err
Stop(handleID string)
Stats(handleID string) string                  // {"rx":N,"tx":N}
LastError() string
Version() string
```

`yamlConfig` matches the output of the Kotlin-side
`Hysteria2ConfigRenderer` (subset of the upstream client YAML:
`server`, `auth`, `tls{sni,insecure,pinSHA256}`, `obfs{type,salamander}`,
`bandwidth{up,down}`).

## Runtime architecture

Identical to vless-mobile — the Hysteria2 client is wrapped in a local
SOCKS5 inbound and the TUN packets are pumped through it by the bundled
`xjasonlyu/tun2socks` engine:

```
Android TUN fd
      │
      ▼
  tun2socks engine  ──►  127.0.0.1:<random>  (in-process SOCKS5)
                                 │
                                 ▼
                         hyclient.Client.TCP(addr)
                                 │
                         VpnService.protect()
                                 │
                                 ▼
                          real network egress (QUIC)
```

`SetProtector` must be called by the Kotlin layer before `Start` so the
QUIC UDP socket is wrapped in `VpnService.protect(fd)`. pinSHA256 is
enforced via `crypto/tls.VerifyPeerCertificate` over the SPKI of the
leaf certificate; bandwidth YAML strings (`"100 mbps"`, `"1.5 gbps"`) are
parsed into `BandwidthConfig.MaxTx/MaxRx`.

## Building

```sh
export ANDROID_NDK_HOME=/path/to/android-ndk-r26d
./build.sh
```

The resulting `hysteria.aar` lands in
`protocols/hysteria2/libs/hysteria.aar` and is picked up by the
`:protocols:hysteria2` Gradle module through
`fileTree(dir = "libs", include = listOf("*.aar"))`.

## Known limitations

- `Stats()` currently returns `{"rx":0,"tx":0}` because the upstream
  tun2socks engine doesn't export counters. Vendoring a fork to surface
  them is tracked separately and does not block an internal-track
  release.
- The Kotlin side multiplexes only a single Hysteria2 tunnel at a time;
  the underlying tun2socks engine holds package-global state and
  rejects concurrent `Start` calls.
