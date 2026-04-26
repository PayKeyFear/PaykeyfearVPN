module github.com/paykeyfear/gomobile-bundle

go 1.26

// Single-output umbrella module — see bundle.go for the full rationale.
//
// Local replace directives keep the wrappers as sibling directories;
// they have never been published to a Go module proxy.

require (
	github.com/paykeyfear/awg-mobile v0.0.0
	github.com/paykeyfear/hysteria2-mobile v0.0.0
	github.com/paykeyfear/proxylink-android/third_party/vless-mobile v0.0.0
)

require (
	github.com/ajg/form v1.5.1 // indirect
	github.com/amnezia-vpn/amneziawg-go v0.2.17 // indirect
	github.com/andybalholm/brotli v1.1.0 // indirect
	github.com/apernet/hysteria/core/v2 v2.6.0 // indirect
	github.com/apernet/hysteria/extras/v2 v2.6.0 // indirect
	github.com/apernet/quic-go v0.59.1-0.20260330051153-c402ee641eb6 // indirect
	github.com/armon/go-socks5 v0.0.0-20160902184237-e75332964ef5 // indirect
	github.com/cloudflare/circl v1.6.3 // indirect
	github.com/davecgh/go-spew v1.1.1 // indirect
	github.com/docker/go-units v0.5.0 // indirect
	github.com/ghodss/yaml v1.0.1-0.20220118164431-d8423dcdf344 // indirect
	github.com/go-chi/chi/v5 v5.2.1 // indirect
	github.com/go-chi/cors v1.2.1 // indirect
	github.com/go-chi/render v1.0.3 // indirect
	github.com/go-gost/relay v0.5.0 // indirect
	github.com/google/btree v1.1.3 // indirect
	github.com/google/shlex v0.0.0-20191202100458-e7afc7fbc510 // indirect
	github.com/google/uuid v1.6.0 // indirect
	github.com/gorilla/schema v1.4.1 // indirect
	github.com/gorilla/websocket v1.5.3 // indirect
	github.com/juju/ratelimit v1.0.2 // indirect
	github.com/klauspost/compress v1.17.9 // indirect
	github.com/klauspost/cpuid/v2 v2.3.0 // indirect
	github.com/miekg/dns v1.1.72 // indirect
	github.com/pelletier/go-toml v1.9.5 // indirect
	github.com/pires/go-proxyproto v0.11.0 // indirect
	github.com/pmezard/go-difflib v1.0.0 // indirect
	github.com/quic-go/qpack v0.6.0 // indirect
	github.com/refraction-networking/utls v1.8.3-0.20260301010127-aa6edf4b11af // indirect
	github.com/sagernet/sing v0.5.1 // indirect
	github.com/sagernet/sing-shadowsocks v0.2.7 // indirect
	github.com/stretchr/objx v0.5.2 // indirect
	github.com/stretchr/testify v1.11.1 // indirect
	github.com/vishvananda/netlink v1.3.1 // indirect
	github.com/vishvananda/netns v0.0.5 // indirect
	github.com/xjasonlyu/tun2socks/v2 v2.6.0 // indirect
	github.com/xtls/reality v0.0.0-20260322125925-9234c772ba8f // indirect
	github.com/xtls/xray-core v1.260327.0 // indirect
	go.uber.org/atomic v1.11.0 // indirect
	go.uber.org/multierr v1.11.0 // indirect
	go.uber.org/zap v1.27.0 // indirect
	go4.org/netipx v0.0.0-20231129151722-fdeea329fbba // indirect
	golang.org/x/crypto v0.49.0 // indirect
	golang.org/x/exp v0.0.0-20240531132922-fd00a4e0eefc // indirect
	golang.org/x/mobile v0.0.0-20260209203831-923679eb55af // indirect
	golang.org/x/mod v0.33.0 // indirect
	golang.org/x/net v0.52.0 // indirect
	golang.org/x/sync v0.20.0 // indirect
	golang.org/x/sys v0.42.0 // indirect
	golang.org/x/text v0.35.0 // indirect
	golang.org/x/time v0.12.0 // indirect
	golang.org/x/tools v0.42.0 // indirect
	golang.zx2c4.com/wintun v0.0.0-20230126152724-0fa3db229ce2 // indirect
	golang.zx2c4.com/wireguard v0.0.0-20250521234502-f333402bd9cb // indirect
	google.golang.org/genproto/googleapis/rpc v0.0.0-20251202230838-ff82c1b0f217 // indirect
	google.golang.org/grpc v1.79.3 // indirect
	google.golang.org/protobuf v1.36.11 // indirect
	gopkg.in/yaml.v2 v2.4.0 // indirect
	gopkg.in/yaml.v3 v3.0.1 // indirect
	gvisor.dev/gvisor v0.0.0-20260122175437-89a5d21be8f0 // indirect
	lukechampine.com/blake3 v1.4.1 // indirect
)

replace (
	github.com/paykeyfear/awg-mobile => ../awg-mobile
	github.com/paykeyfear/hysteria2-mobile => ../hysteria2-mobile
	github.com/paykeyfear/proxylink-android/third_party/vless-mobile => ../vless-mobile
)

// vless-mobile pulls tun2socks v2.6.0 which imports gvisor.dev/gvisor/pkg/rawfile.
// That package was added after the v20231202 snapshot, so pin the same revision
// vless-mobile uses — it also contains the go:linkname fixes hysteria2 needed.
replace gvisor.dev/gvisor => gvisor.dev/gvisor v0.0.0-20250523182742-eede7a881b20
