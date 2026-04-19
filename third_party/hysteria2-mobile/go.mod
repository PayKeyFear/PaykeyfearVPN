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
