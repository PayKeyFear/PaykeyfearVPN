module github.com/paykeyfear/hysteria2-mobile

go 1.22

// gomobile bind needs `golang.org/x/mobile/bind` resolvable through this
// module's own dep graph; otherwise `gobind` fails with
//   "golang.org/x/mobile/bind" is not found; run go get …
require (
	github.com/apernet/hysteria/core/v2 v2.6.0
	github.com/apernet/hysteria/extras/v2 v2.6.0
	github.com/armon/go-socks5 v0.0.0-20160902184237-e75332964ef5
	github.com/xjasonlyu/tun2socks/v2 v2.5.2
	golang.org/x/mobile v0.0.0-20260209203831-923679eb55af
	gopkg.in/yaml.v3 v3.0.1
)

// gvisor's pkg/sync uses go:linkname to pull goready/gopark/semacquire out
// of the Go runtime. The release pulled transitively by tun2socks v2.5.2
// (2023-06-03) predates the Go 1.21 runtime rename and fails to compile
// on go 1.25 with `undefined: goready`. Force a later gvisor snapshot
// (2023-12-02) that adjusted these linknames — same one xray-core pulls
// in the vless module, so no behavioural divergence.
replace gvisor.dev/gvisor => gvisor.dev/gvisor v0.0.0-20231202080848-1f7806d17489
