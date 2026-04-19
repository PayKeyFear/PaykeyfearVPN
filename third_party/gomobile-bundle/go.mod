module github.com/paykeyfear/gomobile-bundle

go 1.22

// Single-output umbrella module — see bundle.go for the full rationale.
//
// Local replace directives keep the wrappers as sibling directories;
// they have never been published to a Go module proxy.

require (
	github.com/paykeyfear/awg-mobile v0.0.0
	github.com/paykeyfear/hysteria2-mobile v0.0.0
	github.com/paykeyfear/proxylink-android/third_party/vless-mobile v0.0.0
	golang.org/x/mobile v0.0.0-20260209203831-923679eb55af
)

replace (
	github.com/paykeyfear/awg-mobile => ../awg-mobile
	github.com/paykeyfear/hysteria2-mobile => ../hysteria2-mobile
	github.com/paykeyfear/proxylink-android/third_party/vless-mobile => ../vless-mobile
)

// Same gvisor pin the hysteria2 module uses (the unbumped revision
// predates the go1.21 runtime symbol rename and fails to compile on
// the toolchain x/mobile pulls in).
replace gvisor.dev/gvisor => gvisor.dev/gvisor v0.0.0-20231202080848-1f7806d17489
