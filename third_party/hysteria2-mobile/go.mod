module github.com/paykeyfear/hysteria2-mobile

go 1.24.0

// gomobile bind needs `golang.org/x/mobile/bind` resolvable through this
// module's own dep graph; otherwise `gobind` fails with
//   "golang.org/x/mobile/bind" is not found; run go get …
require (
	github.com/apernet/hysteria/core/v2 v2.6.0
	github.com/apernet/hysteria/extras/v2 v2.6.0
	github.com/armon/go-socks5 v0.0.0-20160902184237-e75332964ef5
	github.com/xjasonlyu/tun2socks/v2 v2.6.0
	golang.org/x/mobile v0.0.0-20260209203831-923679eb55af
	gopkg.in/yaml.v3 v3.0.1
)

require (
	github.com/ajg/form v1.5.1 // indirect
	github.com/apernet/quic-go v0.48.2-0.20241104191913-cb103fcecfe7 // indirect
	github.com/davecgh/go-spew v1.1.1 // indirect
	github.com/docker/go-units v0.5.0 // indirect
	github.com/go-chi/chi/v5 v5.2.1 // indirect
	github.com/go-chi/cors v1.2.1 // indirect
	github.com/go-chi/render v1.0.3 // indirect
	github.com/go-gost/relay v0.5.0 // indirect
	github.com/go-task/slim-sprig v0.0.0-20230315185526-52ccab3ef572 // indirect
	github.com/google/btree v1.1.3 // indirect
	github.com/google/pprof v0.0.0-20210407192527-94a9f03dee38 // indirect
	github.com/google/shlex v0.0.0-20191202100458-e7afc7fbc510 // indirect
	github.com/google/uuid v1.6.0 // indirect
	github.com/gorilla/schema v1.4.1 // indirect
	github.com/gorilla/websocket v1.5.3 // indirect
	github.com/onsi/ginkgo/v2 v2.9.5 // indirect
	github.com/pmezard/go-difflib v1.0.0 // indirect
	github.com/quic-go/qpack v0.5.1 // indirect
	github.com/stretchr/objx v0.5.2 // indirect
	github.com/stretchr/testify v1.9.0 // indirect
	go.uber.org/atomic v1.11.0 // indirect
	go.uber.org/mock v0.4.0 // indirect
	go.uber.org/multierr v1.11.0 // indirect
	go.uber.org/zap v1.27.0 // indirect
	golang.org/x/crypto v0.48.0 // indirect
	golang.org/x/exp v0.0.0-20240506185415-9bf2ced13842 // indirect
	golang.org/x/mod v0.33.0 // indirect
	golang.org/x/net v0.50.0 // indirect
	golang.org/x/sync v0.19.0 // indirect
	golang.org/x/sys v0.41.0 // indirect
	golang.org/x/text v0.34.0 // indirect
	golang.org/x/time v0.11.0 // indirect
	golang.org/x/tools v0.42.0 // indirect
	golang.zx2c4.com/wintun v0.0.0-20230126152724-0fa3db229ce2 // indirect
	golang.zx2c4.com/wireguard v0.0.0-20250521234502-f333402bd9cb // indirect
	gvisor.dev/gvisor v0.0.0-20250523182742-eede7a881b20 // indirect
)

// gvisor's pkg/sync uses go:linkname to pull goready/gopark/semacquire out
// of the Go runtime. The release pulled transitively by tun2socks v2.5.2
// (2023-06-03) predates the Go 1.21 runtime rename and fails to compile
// on go 1.25 with `undefined: goready`. Force a snapshot that adjusted
// these linknames AND contains pkg/rawfile needed by tun2socks v2.6.0.
replace gvisor.dev/gvisor => gvisor.dev/gvisor v0.0.0-20250523182742-eede7a881b20
