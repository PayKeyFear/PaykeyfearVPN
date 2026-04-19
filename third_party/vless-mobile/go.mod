module github.com/paykeyfear/proxylink-android/third_party/vless-mobile

go 1.22

// Bundled into one gomobile module so the resulting vless.aar ships Xray +
// tun2socks in lockstep. Pin to tags known to build with NDK r26d.
//
// gomobile bind needs `golang.org/x/mobile/bind` in this module's own dep
// graph; otherwise `gobind` aborts with
//   "golang.org/x/mobile/bind" is not found; run go get …
//
// Other versions left loose with // indirect are resolved by `go mod tidy`
// on the CI runner; commit the resulting go.sum.
require (
	github.com/xjasonlyu/tun2socks/v2 v2.5.2
	github.com/xtls/xray-core v1.8.23
	golang.org/x/mobile v0.0.0-20240916200251-e3142cf09c44
)
