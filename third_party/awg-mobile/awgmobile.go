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
	"context"
	"fmt"
	"net"
	"runtime"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"

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
	// the fd to us, so we are the sole owner. No dup needed; on any
	// failure path we close it ourselves.
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

	logger := device.NewLogger(device.LogLevelError, "[awg] ")
	bind := conn.NewDefaultBind()
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

	// Wireguard-android pattern: once the bind has opened its sockets,
	// peek their fds and route them through VpnService.protect() so the
	// AWG handshake/keepalive UDP packets bypass our own tun (otherwise
	// they loop and no traffic flows at all).
	protectBindSockets(bind)

	h := &awgHandle{device: dev, bind: bind}
	id := int32(handleCounter.Add(1))
	handles.Store(id, h)
	return id
}

func protectBindSockets(bind conn.Bind) {
	pb, ok := bind.(conn.PeekLookAtSocketFd)
	if !ok {
		return
	}
	p := currentProtector()
	if fd, err := pb.PeekLookAtSocketFd4(); err == nil && fd >= 0 {
		_ = p.Protect(int32(fd))
	}
	if fd, err := pb.PeekLookAtSocketFd6(); err == nil && fd >= 0 {
		_ = p.Protect(int32(fd))
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
// Protected UDP listener (exported for the future custom Bind integration)
// ----------------------------------------------------------------------------

// protectedListenUDP opens a UDP socket whose fd is passed through the
// currently-installed Protector before any packets are sent. Used by the
// (future) protected Bind wrapper. Lower-case so gomobile bind doesn't
// try to expose the *net.UDPConn return type to Java.
//
// NOTE: amneziawg-go's device.NewDevice takes a conn.Bind but
// conn.StdNetBind does not expose its sockets, so we cannot retro-fit
// protect() onto the default bind. A follow-up change will replace
// NewDefaultBind with a protected wrapper. Until then, SetProtector only
// influences sockets opened via this helper (currently none during
// Start — the default bind still owns them) and the upstream race
// documented in AwgTunnel.kt:21 remains present on first connect of a
// freshly booted device.
func protectedListenUDP(network, addr string) (*net.UDPConn, error) {
	lc := &net.ListenConfig{
		Control: func(_ string, _ string, c syscall.RawConn) error {
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
		},
	}
	pc, err := lc.ListenPacket(context.Background(), network, addr)
	if err != nil {
		return nil, err
	}
	uc, ok := pc.(*net.UDPConn)
	if !ok {
		_ = pc.Close()
		return nil, fmt.Errorf("expected *net.UDPConn, got %T", pc)
	}
	return uc, nil
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
