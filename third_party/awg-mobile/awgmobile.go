// Package awgmobile is a gomobile-friendly wrapper around
// github.com/amnezia-vpn/amneziawg-go. Mirrors the shape of WireGuard's
// `tunnel/tools/libwg-go/api-android.go`, but uses gomobile bind so the
// Kotlin side gets a generated Java class instead of raw JNI.
//
// Exported API (kept to gomobile-safe types):
//
//	Start(cfg string, tunFd int32) int32       // returns handle (>0), or -1 on error
//	Stop(handle int32)
//	GetStats(handle int32) string              // "rx=<bytes>\ntx=<bytes>\n"
//	GetVersion() string
//	LastError() string
//	SetProtector(p Protector)                  // Android VpnService.protect bridge
package awgmobile

import (
	"fmt"
	"runtime"
	"strings"
	"sync"
	"sync/atomic"

	"github.com/amnezia-vpn/amneziawg-go/conn"
	"github.com/amnezia-vpn/amneziawg-go/device"
	"github.com/amnezia-vpn/amneziawg-go/tun"
	"golang.org/x/sys/unix"
)

// ----------------------------------------------------------------------------
// Public API
// ----------------------------------------------------------------------------

// Protector mirrors android.net.VpnService#protect(int). Every outbound UDP
// socket the AmneziaWG core opens for handshakes / keepalives must be
// wrapped in protect() or the packets loop back into the tunnel.
type Protector interface {
	Protect(fd int32) bool
}

// SetProtector registers the Android protect() bridge. Pass nil to reset.
// Safe to call before or between Start()s; the most recent value wins.
func SetProtector(p Protector) {
	if p == nil {
		protector.Store(protectorHolder{p: noopProtector{}})
		return
	}
	protector.Store(protectorHolder{p: p})
}

func Start(cfg string, tunFd int32) int32 {
	// The Java side calls ParcelFileDescriptor.detachFd() before passing
	// the fd to us, so we are the sole owner. On any failure path Go
	// closes the fd (directly or via dev.Close → tun.Close), so the
	// Kotlin caller must NOT close it again — double-close triggers
	// fdsan SIGABRT when the OS recycles the fd number.
	fd := int(tunFd)
	_ = unix.SetNonblock(fd, true)

	// CreateTUNFromFile runs ioctl(TUNSETIFF) and asks for the interface
	// name — neither works on Android's VpnService fd, which is opened by
	// system_server and exposed to us as a plain packet socket. We must
	// use the unmonitored variant the WireGuard-Android client uses.
	tdev, _, err := tun.CreateUnmonitoredTUNFromFD(fd)
	if err != nil {
		_ = unix.Close(fd)
		setLastError(fmt.Errorf("create tun: %w", err))
		return -1
	}

	// LogLevelVerbose is too chatty for steady-state; Info gives us
	// handshake/keepalive lines that are invaluable for diagnosing
	// "no traffic" issues without drowning logcat.
	logger := device.NewLogger(device.LogLevelVerbose, "[awg] ")
	bind := newProtectedBind(logger)
	dev := device.NewDevice(tdev, bind, logger)

	if err := dev.IpcSet(cfg); err != nil {
		dev.Close()
		setLastError(fmt.Errorf("ipc set: %w", err))
		return -1
	}
	if err := dev.Up(); err != nil {
		dev.Close()
		setLastError(fmt.Errorf("device up: %w", err))
		return -1
	}

	h := &awgHandle{device: dev, bind: bind}
	id := int32(handleCounter.Add(1))
	handles.Store(id, h)
	logger.Verbosef("tunnel up (handle=%d)", id)
	return id
}

// protectedBind wraps *conn.StdNetBind so that every UDP socket the bind
// opens is immediately handed to VpnService.protect(). Without this the
// handshake / keepalive packets go out through our own tun and loop; no
// data can ever flow because the peer never sees a handshake response.
//
// The protect-at-Open hook also covers reconnect: when the device roams
// between networks, amneziawg-go closes and re-opens the bind and we
// protect the fresh sockets before the next handshake is sent.
type protectedBind struct {
	*conn.StdNetBind
	logger *device.Logger
}

func newProtectedBind(logger *device.Logger) *protectedBind {
	inner := conn.NewStdNetBind()
	std, ok := inner.(*conn.StdNetBind)
	if !ok {
		// Should never happen on android (default.go returns StdNetBind);
		// fall back to the naked inner so we at least have *a* bind, and
		// log so the failure is visible instead of mysterious no-traffic.
		logger.Errorf("unexpected bind type %T — protect hook disabled", inner)
		return &protectedBind{StdNetBind: nil, logger: logger}
	}
	return &protectedBind{StdNetBind: std, logger: logger}
}

func (b *protectedBind) Open(port uint16) ([]conn.ReceiveFunc, uint16, error) {
	if b.StdNetBind == nil {
		return nil, 0, fmt.Errorf("protectedBind: inner bind unavailable")
	}
	fns, actualPort, err := b.StdNetBind.Open(port)
	if err != nil {
		return fns, actualPort, err
	}
	b.protectSockets()
	return fns, actualPort, nil
}

func (b *protectedBind) protectSockets() {
	p := currentProtector()
	if fd, err := b.StdNetBind.PeekLookAtSocketFd4(); err == nil && fd >= 0 {
		if !p.Protect(int32(fd)) {
			b.logger.Errorf("VpnService.protect(v4 fd=%d) returned false — AWG traffic will loop into tun", fd)
		} else {
			b.logger.Verbosef("protected v4 fd=%d", fd)
		}
	} else if err != nil {
		b.logger.Errorf("peek v4 fd: %v", err)
	}
	if fd, err := b.StdNetBind.PeekLookAtSocketFd6(); err == nil && fd >= 0 {
		if !p.Protect(int32(fd)) {
			b.logger.Errorf("VpnService.protect(v6 fd=%d) returned false", fd)
		} else {
			b.logger.Verbosef("protected v6 fd=%d", fd)
		}
	} else if err != nil {
		// v6 not being available is normal on v4-only networks; only log
		// if the bind claims to have opened a socket that we can't peek.
		b.logger.Verbosef("peek v6 fd: %v", err)
	}
}

func Stop(handle int32) {
	v, ok := handles.LoadAndDelete(handle)
	if !ok {
		return
	}
	v.(*awgHandle).device.Close()
}

func GetStats(h int32) string {
	v, ok := handles.Load(h)
	if !ok {
		return ""
	}
	dev := v.(*awgHandle).device

	raw, err := dev.IpcGet()
	if err != nil {
		return ""
	}
	var rx, tx uint64
	for _, line := range strings.Split(raw, "\n") {
		switch {
		case strings.HasPrefix(line, "rx_bytes="):
			fmt.Sscanf(line[len("rx_bytes="):], "%d", &rx)
		case strings.HasPrefix(line, "tx_bytes="):
			fmt.Sscanf(line[len("tx_bytes="):], "%d", &tx)
		}
	}
	return fmt.Sprintf("rx=%d\ntx=%d\n", rx, tx)
}

func GetVersion() string {
	return fmt.Sprintf("amneziawg-go (go %s) awgmobile/1", runtime.Version())
}

func LastError() string {
	v := lastErr.Load()
	if v == nil {
		return ""
	}
	return v.(string)
}

// ----------------------------------------------------------------------------
// Internals
// ----------------------------------------------------------------------------

var (
	handles       sync.Map
	handleCounter atomic.Int32
	lastErr       atomic.Value // string
	protector     atomic.Value // protectorHolder
)

func init() {
	protector.Store(protectorHolder{p: noopProtector{}})
}

type awgHandle struct {
	device *device.Device
	bind   conn.Bind
}

type noopProtector struct{}

func (noopProtector) Protect(int32) bool { return true }

// protectorHolder wraps the Protector interface in a concrete struct so
// atomic.Value sees the same dynamic type on every Store (atomic.Value
// panics with "store of inconsistently typed value" otherwise).
type protectorHolder struct{ p Protector }

func currentProtector() Protector {
	v := protector.Load()
	if v == nil {
		return noopProtector{}
	}
	return v.(protectorHolder).p
}

func setLastError(err error) { lastErr.Store(err.Error()) }
