# Architecture

## Module graph

```
          ┌──────────────────────────┐
          │          :app            │
          │  (Compose UI, Hilt DI)   │
          └───────┬─────────┬────────┘
                  │         │
         ┌────────▼──┐   ┌──▼───────────────┐
         │ :vpn-     │   │ :core-config     │
         │ service   │   │ (parsers)        │
         │ (VpnSvc)  │   └──────┬───────────┘
         └─┬─┬───┬───┘          │
           │ │   │               │
 ┌─────────▼┐│┌──▼─────┐  ┌─────▼─────┐
 │:protocols│││protocols│  │  :core   │
 │   :awg   │││ :vless  │  │ (models) │
 └────┬─────┘│└────┬────┘  └──────────┘
      │      │     │
      │      │     │
      └──────▼─────▼───── all depend on :core
```

## Why this split

- **`:core`** — pure Kotlin (Android library but no Android APIs used).
  Cheap to test; shared domain types so nothing else has to reinvent
  `ConnectionConfig` / `TunnelState`.
- **`:core-config`** — parsers live separately so that config handling can
  evolve (new formats, CLI import from shell) without dragging in UI or
  VpnService concerns.
- **`:vpn-service`** — owns Android's `VpnService`. The `TunnelController`
  is the only thing that knows the mapping `Protocol → VpnTunnel`, so the
  service itself is trivial and testable on JVM through a fake controller.
- **`:protocols:*`** — one module per Go backend. Each has its own
  `jniLibs/` so the native prebuilts of one protocol don't bloat the APK
  size or build time of the others.
- **`:app`** — thin Compose UI + Hilt wiring. No business logic beyond
  view-state mapping; everything else delegates down the graph.

## Config import flow

```
User pastes text / picks file
           │
           ▼
ImportViewModel.onImportClicked
           │
           ▼
ConfigParserRegistry.parse(source)
           │
           ├── AmneziaBundleParser   (vpn:// or bundle JSON)
           ├── AwgConfParser         ([Interface]+[Peer])
           ├── VlessUriParser        (vless://)
           └── Hysteria2YamlParser   (hysteria2:// or yaml)
           │
           ▼
ConnectionConfig.(Awg|Vless|Hysteria2)
           │
           ▼
persisted to Room ➜ rendered in Servers screen
```

The Amnezia bundle parser dispatches back into the single-protocol
parsers to avoid duplicating logic.

## Tunnel lifecycle

```
UI "Connect" ── startService(START, configJson) ──► PaykeyfearVpnService
                                                         │
                                                         │ VpnService.Builder.establish
                                                         │ ──► ParcelFileDescriptor (tunFd)
                                                         ▼
                                                  TunnelController.start(cfg, tunFd)
                                                         │
                                       Protocol ─┬── AwgTunnel  (libawg.so)
                                                 ├── VlessTunnel (libXray.aar)
                                                 └── Hysteria2Tunnel (libhysteria.aar)
                                                         │
                                                 StateFlow<TunnelState>
                                                         ▼
                                                 Home UI updates
```

The `VpnTunnel` interface is the single seam between Kotlin and the Go
backends. Each adapter receives the raw tun file descriptor and is free to
hand it to the Go side as it sees fit (e.g. `amneziawg-go` expects
`turnOnWithSocket(fd)`).
