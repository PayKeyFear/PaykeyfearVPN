// Package vlessmobile is the gomobile-friendly bundle that ships Xray-core
// and a tun2socks pump inside a single .aar. The Kotlin side sees a
// generated Java class `vlessmobile.Vlessmobile` with only primitive /
// string signatures — any richer type (net.Conn, channels, maps) is kept
// inside this package.
//
// Architecture
// ------------
//
//	VpnService (Android)
//	  ├─ tunFd ──────────────────┐
//	  └─ Protector (protect(fd)) │
//	                             ▼
//	  Vlessmobile.StartXray(json)  ──► Xray-core, SOCKS5 inbound @ 127.0.0.1:port
//	  Vlessmobile.Tun2SocksStart() ──► pumps tunFd ↔ 127.0.0.1:port over SOCKS5
//
// Only Xray-core's outbound sockets need VpnService.protect() — the
// tun2socks engine only ever dials 127.0.0.1, which is exempt from the
// VPN route by construction. We install the protect hook via
// `transport/internet.RegisterDialerController` at init time.
package vlessmobile

import (
	"context"
	"fmt"
	"runtime"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"

	xraycore "github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/infra/conf/serial"
	xraynet "github.com/xtls/xray-core/transport/internet"

	"github.com/xjasonlyu/tun2socks/v2/engine"

	// Side-effect import — registers all Xray inbound/outbound/transport
	// handlers (VLESS, Reality, WS, gRPC, TCP, TLS, etc.) with the core.
	// Without this, `core.New` returns "unknown config type" for anything
	// beyond freedom/blackhole.
	_ "github.com/xtls/xray-core/main/distro/all"
)

// Protector mirrors android.net.VpnService#protect(int). Xray's outbound
// dialer must invoke this for every socket it opens; otherwise the socket
// is routed back into the TUN and loops. gomobile can expose Go interfaces
// to Java, so the Kotlin side passes in an implementation.
type Protector interface {
	Protect(fd int32) bool
}

// SetProtector registers the Android protect() callback. Safe to call
// multiple times; the most recent value wins. Must be called before
// StartXray when running on Android.
func SetProtector(p Protector) {
	if p == nil {
		protector.Store(protectorHolder{p: noopProtector{}})
		return
	}
	protector.Store(protectorHolder{p: p})
}

// StartXray boots Xray-core with the given JSON config. The config is
// expected to contain exactly one SOCKS5 inbound on 127.0.0.1 — the port
// chosen by the caller (Kotlin XrayConfigBuilder) and re-used by
// Tun2SocksStart. Returns a handle (>0) or -1 on error.
func StartXray(configJson string) int32 {
	cfg, err := serial.LoadJSONConfig(strings.NewReader(configJson))
	if err != nil {
		setLastError(fmt.Errorf("parse xray config: %w", err))
		return -1
	}
	ctx, cancel := context.WithCancel(context.Background())
	server, err := xraycore.New(cfg)
	if err != nil {
		cancel()
		setLastError(fmt.Errorf("xray new: %w", err))
		return -1
	}
	if err := server.Start(); err != nil {
		cancel()
		setLastError(fmt.Errorf("xray start: %w", err))
		return -1
	}

	id := xrayCounter.Add(1)
	h := &xrayHandle{
		id:     id,
		ctx:    ctx,
		cancel: cancel,
		core:   server,
	}
	xrayHandles.Store(id, h)
	return id
}

// StopXray releases all resources associated with the handle. No-op if the
// handle is unknown.
func StopXray(handle int32) {
	v, ok := xrayHandles.LoadAndDelete(handle)
	if !ok {
		return
	}
	h := v.(*xrayHandle)
	if h.core != nil {
		_ = h.core.Close()
	}
	if h.cancel != nil {
		h.cancel()
	}
}

// Tun2SocksStart pumps packets between a Linux TUN file descriptor and a
// local SOCKS5 endpoint. socksHost is typically "127.0.0.1".
//
// Returns a handle (>0) or -1 on error. The handle is independent of the
// StartXray handle — caller should track both.
func Tun2SocksStart(tunFd int32, socksHost string, socksPort int32) int32 {
	if socksHost == "" {
		setLastError(fmt.Errorf("socks host is empty"))
		return -1
	}
	// tun2socks only exposes one engine instance via package-level state,
	// so we serialize start/stop across handles. A second concurrent
	// StartXray+Tun2SocksStart pair is an invariant violation at the
	// Kotlin layer, not something we try to support here.
	engineMu.Lock()
	defer engineMu.Unlock()
	if engineRunning {
		setLastError(fmt.Errorf("tun2socks engine already running"))
		return -1
	}

	key := &engine.Key{
		Proxy:    fmt.Sprintf("socks5://%s:%d", socksHost, socksPort),
		Device:   fmt.Sprintf("fd://%d", tunFd),
		LogLevel: "error",
		MTU:      1500,
	}
	engine.Insert(key)
	// xjasonlyu/tun2socks's engine.Start()/engine.Stop() do not return
	// errors — they log-and-exit on misconfig. Panics from inside are
	// caught by the gomobile-injected recovery; we keep engineRunning
	// tracking to guarantee at-most-one concurrent tun2socks instance.
	engine.Start()
	engineRunning = true

	id := t2sCounter.Add(1)
	h := &t2sHandle{id: id, tunFd: tunFd}
	t2sHandles.Store(id, h)
	return id
}

// Tun2SocksStop stops the packet pump for the given handle. No-op if unknown.
func Tun2SocksStop(handle int32) {
	_, ok := t2sHandles.LoadAndDelete(handle)
	if !ok {
		return
	}
	engineMu.Lock()
	defer engineMu.Unlock()
	if !engineRunning {
		return
	}
	engine.Stop()
	engineRunning = false
}

// LastError returns the most recent Start error message, or empty string.
// Stays available even after the handle that produced the error is gone.
func LastError() string {
	v := lastErr.Load()
	if v == nil {
		return ""
	}
	return v.(string)
}

// Version returns a human-readable build stamp for About screens.
func Version() string {
	return fmt.Sprintf("vless-mobile (xray-core bundled, tun2socks bundled, go %s)", runtime.Version())
}

// ----------------------------------------------------------------------------
// Internals
// ----------------------------------------------------------------------------

var (
	xrayHandles sync.Map // int32 -> *xrayHandle
	xrayCounter atomic.Int32

	t2sHandles sync.Map // int32 -> *t2sHandle
	t2sCounter atomic.Int32

	engineMu      sync.Mutex
	engineRunning bool

	protector atomic.Value // protectorHolder
	lastErr   atomic.Value // string
)

func init() {
	protector.Store(protectorHolder{p: noopProtector{}})
	// Register a dialer controller so every outbound socket Xray opens
	// passes through VpnService.protect(). Runs once per process. The
	// controller is invoked synchronously during connect, so grabbing the
	// current protector via an atomic load is safe.
	xraynet.RegisterDialerController(func(network, address string, c syscall.RawConn) error {
		p := currentProtector()
		var protectErr error
		if err := c.Control(func(fd uintptr) {
			if !p.Protect(int32(fd)) {
				protectErr = fmt.Errorf("VpnService.protect(%d) returned false", fd)
			}
		}); err != nil {
			return err
		}
		return protectErr
	})
}

type xrayHandle struct {
	id     int32
	ctx    context.Context
	cancel context.CancelFunc
	core   *xraycore.Instance
}

type t2sHandle struct {
	id    int32
	tunFd int32
}

type noopProtector struct{}

func (noopProtector) Protect(fd int32) bool { return true }

// protectorHolder wraps the Protector interface in a concrete struct so
// atomic.Value sees the same dynamic type on every Store (atomic.Value
// panics with "store of inconsistently typed value" otherwise).
type protectorHolder struct{ p Protector }

func setLastError(err error) {
	if err == nil {
		return
	}
	lastErr.Store(err.Error())
}

func currentProtector() Protector {
	v := protector.Load()
	if v == nil {
		return noopProtector{}
	}
	return v.(protectorHolder).p
}
