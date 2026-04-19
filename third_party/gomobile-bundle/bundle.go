// Package paykeyfearnative is the gomobile-friendly umbrella package
// that re-exports symbols from the three per-protocol wrappers (awg,
// vless, hysteria2) as a single Java class
// `paykeyfearnative.Paykeyfearnative`.
//
// Why a single bundle?
// --------------------
// `gomobile bind` bakes a copy of the Go runtime support classes
// (go.Seq, go.Universe, go.error, mobile.*) AND a single libgojni.so
// into every .aar it produces. Shipping three separate .aars in the
// same Android app fails at the AGP `checkDuplicateClasses` +
// `mergeNativeLibs` steps with collisions we can't resolve by
// pickFirst (each libgojni.so contains DIFFERENT Go code, so picking
// one would silently drop the other two protocols).
//
// Bundling everything into a single Go module gives gomobile a single
// compilation unit → one .aar → one libgojni.so → no merge conflicts.
// The generated Java surface keeps the three protocol namespaces
// separate via the `Awg*`, `Vless*`, `Hy2*` function prefixes.
package paykeyfearnative

import (
	awg "github.com/paykeyfear/awg-mobile"
	hy2 "github.com/paykeyfear/hysteria2-mobile"
	vless "github.com/paykeyfear/proxylink-android/third_party/vless-mobile"
)

// Protector mirrors android.net.VpnService#protect(int). Every outbound
// UDP socket opened by any wrapped protocol must pass through Protect()
// or its traffic is routed back into the tunnel and loops.
type Protector interface {
	Protect(fd int32) bool
}

// Adapters between this umbrella's Protector type and each underlying
// wrapper's identical-but-distinct Protector type. Go's structural
// interface satisfaction handles the conversion implicitly, but each
// wrapper exposes its own Protector type in its public API so we wrap
// once and dispatch.
type protectorAdapter struct{ p Protector }

func (a protectorAdapter) Protect(fd int32) bool { return a.p.Protect(fd) }

// SetProtector fans the Android protect() bridge out to every wrapper.
// Pass nil to reset all three to noop.
func SetProtector(p Protector) {
	if p == nil {
		awg.SetProtector(nil)
		hy2.SetProtector(nil)
		vless.SetProtector(nil)
		return
	}
	a := protectorAdapter{p: p}
	awg.SetProtector(a)
	hy2.SetProtector(a)
	vless.SetProtector(a)
}

// ----------------------------------------------------------------------------
// AmneziaWG
// ----------------------------------------------------------------------------

func AwgStart(cfg string, tunFd int32) int32 { return awg.Start(cfg, tunFd) }
func AwgStop(handle int32)                   { awg.Stop(handle) }
func AwgStats(handle int32) string           { return awg.GetStats(handle) }
func AwgLastError() string                   { return awg.LastError() }
func AwgVersion() string                     { return awg.GetVersion() }

// ----------------------------------------------------------------------------
// VLESS (Xray + tun2socks)
// ----------------------------------------------------------------------------

func VlessStartXray(configJson string) int32 { return vless.StartXray(configJson) }
func VlessStopXray(handle int32)             { vless.StopXray(handle) }
func VlessTun2SocksStart(tunFd int32, socksHost string, socksPort int32) int32 {
	return vless.Tun2SocksStart(tunFd, socksHost, socksPort)
}
func VlessTun2SocksStop(handle int32) { vless.Tun2SocksStop(handle) }
func VlessLastError() string          { return vless.LastError() }
func VlessVersion() string            { return vless.Version() }

// ----------------------------------------------------------------------------
// Hysteria2
// ----------------------------------------------------------------------------

func Hy2Start(yamlConfig string, tunFd int32) string { return hy2.Start(yamlConfig, tunFd) }
func Hy2Stop(handle string)                          { hy2.Stop(handle) }
func Hy2Stats(handle string) string                  { return hy2.Stats(handle) }
func Hy2LastError() string                           { return hy2.LastError() }
func Hy2Version() string                             { return hy2.Version() }
