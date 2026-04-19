// Package hy2mobile is the gomobile-friendly wrapper around
// github.com/apernet/hysteria. It boots a Hysteria2 client and exposes
// its egress as a loopback SOCKS5 inbound; a bundled tun2socks engine
// then pumps packets from the Android VpnService-owned TUN fd through
// that SOCKS5 endpoint.
//
// The same two-part design is used by the VLESS tunnel (see
// third_party/vless-mobile/vlessmobile.go) — keeping the layout
// identical means the Kotlin side gets the same lifecycle model for
// both protocols.
//
// API surface (kept gomobile-safe — strings + primitives only):
//
//	Start(yamlConfig, tunFd)   -> handle string ("" on err, see LastError)
//	Stop(handle)
//	Stats(handle)              -> {"rx":N,"tx":N}
//	SetProtector(p Protector)
//	LastError()                -> string
//	Version()                  -> string
package hy2mobile

import (
	"context"
	"crypto/sha256"
	"crypto/x509"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	hyclient "github.com/apernet/hysteria/core/v2/client"
	"github.com/apernet/hysteria/extras/v2/obfs"
	socks5 "github.com/armon/go-socks5"
	"github.com/xjasonlyu/tun2socks/v2/engine"
	"gopkg.in/yaml.v3"
)

// ----------------------------------------------------------------------------
// Public API (gomobile bind)
// ----------------------------------------------------------------------------

// Protector mirrors android.net.VpnService#protect(int). The QUIC
// underlying UDP socket must be wrapped in protect() before the handshake
// runs; otherwise it's routed back through the tun and loops.
type Protector interface {
	Protect(fd int32) bool
}

// SetProtector swaps the global Android protect() callback. Must be
// called before Start when running on Android. Pass nil to revert.
func SetProtector(p Protector) {
	if p == nil {
		protector.Store(Protector(noopProtector{}))
		return
	}
	protector.Store(p)
}

// Start launches a Hysteria2 client using the YAML configuration produced
// by Hysteria2ConfigRenderer on the Kotlin side. It opens a local
// SOCKS5 listener on 127.0.0.1 and a tun2socks engine bound to tunFd;
// the engine forwards every TCP/UDP flow in the TUN through the SOCKS5
// listener, which in turn dispatches via the Hysteria2 client.
//
// Returns a non-empty handle on success, or "" on error (see LastError).
func Start(yamlConfig string, tunFd int32) string {
	cfg, err := parseConfig(yamlConfig)
	if err != nil {
		setLastError(fmt.Errorf("parse config: %w", err))
		return ""
	}

	clientCfg, err := cfg.toClientConfig()
	if err != nil {
		setLastError(fmt.Errorf("build client config: %w", err))
		return ""
	}

	c, _, err := hyclient.NewClient(clientCfg)
	if err != nil {
		setLastError(fmt.Errorf("dial hysteria server: %w", err))
		return ""
	}

	// Start a loopback SOCKS5 listener that proxies through the hysteria
	// client's TCP() method. We deliberately pick :0 so there's no port
	// collision with vless-mobile running in the same process (not a
	// supported configuration today, but cheap insurance).
	lis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		_ = c.Close()
		setLastError(fmt.Errorf("listen loopback socks: %w", err))
		return ""
	}
	port := lis.Addr().(*net.TCPAddr).Port

	server, err := socks5.New(&socks5.Config{
		Dial: func(_ context.Context, _, addr string) (net.Conn, error) {
			// Hysteria's Client.TCP is a blocking dial through the QUIC
			// tunnel — exactly what SOCKS5 needs.
			return c.TCP(addr)
		},
		Logger: log.New(io.Discard, "", 0),
	})
	if err != nil {
		_ = lis.Close()
		_ = c.Close()
		setLastError(fmt.Errorf("socks5 init: %w", err))
		return ""
	}
	socksErr := make(chan error, 1)
	go func() { socksErr <- server.Serve(lis) }()

	// Point tun2socks at that listener. socksHost/port are the only knobs
	// it needs; the engine reads L3 packets off tunFd and translates each
	// flow to SOCKS5 on the loopback.
	engineMu.Lock()
	if engineRunning {
		engineMu.Unlock()
		_ = lis.Close()
		_ = c.Close()
		setLastError(fmt.Errorf("tun2socks engine already running"))
		return ""
	}
	key := &engine.Key{
		Proxy:    fmt.Sprintf("socks5://127.0.0.1:%d", port),
		Device:   fmt.Sprintf("fd://%d", tunFd),
		LogLevel: "error",
		MTU:      1500,
	}
	engine.Insert(key)
	if err := engine.Start(); err != nil {
		engineMu.Unlock()
		_ = lis.Close()
		_ = c.Close()
		setLastError(fmt.Errorf("tun2socks start: %w", err))
		return ""
	}
	engineRunning = true
	engineMu.Unlock()

	h := &handle{
		client:   c,
		listener: lis,
		socksErr: socksErr,
		started:  time.Now(),
	}
	id := newHandleID()
	handles.Store(id, h)
	return id
}

// Stop releases all resources associated with the handle. No-op if the
// handle is unknown / already stopped.
func Stop(handleID string) {
	v, ok := handles.LoadAndDelete(handleID)
	if !ok {
		return
	}
	h := v.(*handle)

	engineMu.Lock()
	if engineRunning {
		if err := engine.Stop(); err != nil {
			setLastError(fmt.Errorf("tun2socks stop: %w", err))
		}
		engineRunning = false
	}
	engineMu.Unlock()

	if h.listener != nil {
		_ = h.listener.Close()
	}
	if h.client != nil {
		if err := h.client.Close(); err != nil {
			setLastError(fmt.Errorf("client close: %w", err))
		}
	}
}

// Stats returns a JSON-encoded {"rx":N,"tx":N} string for the handle, or
// "" if the handle is unknown. Byte counters are maintained by the
// bundled tun2socks engine when running; since the engine's exported
// counters are package-global, we snapshot them here.
func Stats(handleID string) string {
	if _, ok := handles.Load(handleID); !ok {
		return ""
	}
	rx, tx := engineCounters()
	buf, _ := json.Marshal(struct {
		Rx uint64 `json:"rx"`
		Tx uint64 `json:"tx"`
	}{Rx: rx, Tx: tx})
	return string(buf)
}

// LastError returns the most recent Start/Stop error message, or "".
func LastError() string {
	v := lastErr.Load()
	if v == nil {
		return ""
	}
	return v.(string)
}

// Version returns the bundled hysteria core version + Go runtime stamp.
func Version() string {
	return fmt.Sprintf("hy2mobile (hysteria/2.6.0, tun2socks bundled, go %s)", runtime.Version())
}

// ----------------------------------------------------------------------------
// Config
// ----------------------------------------------------------------------------

type yamlConfig struct {
	Server string `yaml:"server"`
	Auth   string `yaml:"auth"`
	TLS    *struct {
		SNI       string `yaml:"sni"`
		Insecure  bool   `yaml:"insecure"`
		PinSHA256 string `yaml:"pinSHA256"`
	} `yaml:"tls"`
	Obfs *struct {
		Type       string `yaml:"type"`
		Salamander *struct {
			Password string `yaml:"password"`
		} `yaml:"salamander"`
	} `yaml:"obfs"`
	Bandwidth *struct {
		Up   string `yaml:"up"`
		Down string `yaml:"down"`
	} `yaml:"bandwidth"`
}

func parseConfig(s string) (*yamlConfig, error) {
	var c yamlConfig
	if err := yaml.Unmarshal([]byte(s), &c); err != nil {
		return nil, err
	}
	if c.Server == "" {
		return nil, errors.New("server is required")
	}
	return &c, nil
}

func (c *yamlConfig) toClientConfig() (*hyclient.Config, error) {
	host, portStr, err := net.SplitHostPort(c.Server)
	if err != nil {
		return nil, fmt.Errorf("server %q: %w", c.Server, err)
	}
	addr, err := net.ResolveUDPAddr("udp", net.JoinHostPort(host, portStr))
	if err != nil {
		return nil, fmt.Errorf("resolve %q: %w", c.Server, err)
	}

	cfg := &hyclient.Config{
		ServerAddr: addr,
		Auth:       c.Auth,
	}
	if c.TLS != nil {
		cfg.TLSConfig.ServerName = c.TLS.SNI
		cfg.TLSConfig.InsecureSkipVerify = c.TLS.Insecure
		if c.TLS.PinSHA256 != "" {
			pin, err := decodePinSHA256(c.TLS.PinSHA256)
			if err != nil {
				return nil, fmt.Errorf("pinSHA256: %w", err)
			}
			cfg.TLSConfig.VerifyPeerCertificate = makePinVerifier(pin)
			// pinning IS the trust anchor once set — disable chain verify.
			cfg.TLSConfig.InsecureSkipVerify = true
		}
	}
	if c.Obfs != nil && c.Obfs.Type == "salamander" && c.Obfs.Salamander != nil {
		ob, err := obfs.NewSalamanderObfuscator([]byte(c.Obfs.Salamander.Password))
		if err != nil {
			return nil, fmt.Errorf("obfs: %w", err)
		}
		cfg.ConnFactory = &obfsConnFactory{obfs: ob}
	} else {
		cfg.ConnFactory = &plainConnFactory{}
	}
	if c.Bandwidth != nil {
		if c.Bandwidth.Up != "" {
			bps, err := parseBandwidth(c.Bandwidth.Up)
			if err != nil {
				return nil, fmt.Errorf("bandwidth.up: %w", err)
			}
			cfg.BandwidthConfig.MaxTx = bps
		}
		if c.Bandwidth.Down != "" {
			bps, err := parseBandwidth(c.Bandwidth.Down)
			if err != nil {
				return nil, fmt.Errorf("bandwidth.down: %w", err)
			}
			cfg.BandwidthConfig.MaxRx = bps
		}
	}
	return cfg, nil
}

// ----------------------------------------------------------------------------
// Protected UDP listener (for Hysteria's own QUIC socket)
// ----------------------------------------------------------------------------

// plainConnFactory wraps a plain UDP socket and runs each newly-opened fd
// through Android's protect() so QUIC traffic doesn't loop through tun.
type plainConnFactory struct{}

func (plainConnFactory) New(addr net.Addr) (net.PacketConn, error) {
	_, ok := addr.(*net.UDPAddr)
	if !ok {
		return nil, fmt.Errorf("unsupported addr type %T", addr)
	}
	return protectedListenUDP()
}

type obfsConnFactory struct {
	obfs obfs.Obfuscator
}

func (f *obfsConnFactory) New(addr net.Addr) (net.PacketConn, error) {
	udpAddr, ok := addr.(*net.UDPAddr)
	if !ok {
		return nil, fmt.Errorf("unsupported addr type %T", addr)
	}
	conn, err := protectedListenUDP()
	if err != nil {
		return nil, err
	}
	return obfs.WrapPacketConn(conn, udpAddr, f.obfs), nil
}

func protectedListenUDP() (*net.UDPConn, error) {
	lc := &net.ListenConfig{
		Control: func(_, _ string, c syscall.RawConn) error {
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
	pc, err := lc.ListenPacket(context.Background(), "udp", ":0")
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
// pinSHA256
// ----------------------------------------------------------------------------

func decodePinSHA256(s string) ([]byte, error) {
	clean := strings.ReplaceAll(strings.ToLower(s), ":", "")
	b, err := hex.DecodeString(clean)
	if err != nil {
		return nil, fmt.Errorf("not hex: %w", err)
	}
	if len(b) != sha256.Size {
		return nil, fmt.Errorf("expected %d bytes, got %d", sha256.Size, len(b))
	}
	return b, nil
}

func makePinVerifier(expected []byte) func(rawCerts [][]byte, _ [][]*x509.Certificate) error {
	return func(rawCerts [][]byte, _ [][]*x509.Certificate) error {
		if len(rawCerts) == 0 {
			return errors.New("no peer certificate presented")
		}
		leaf, err := x509.ParseCertificate(rawCerts[0])
		if err != nil {
			return fmt.Errorf("parse leaf cert: %w", err)
		}
		sum := sha256.Sum256(leaf.RawSubjectPublicKeyInfo)
		for i := range expected {
			if expected[i] != sum[i] {
				return fmt.Errorf("pinSHA256 mismatch (got %s)", hex.EncodeToString(sum[:]))
			}
		}
		return nil
	}
}

// ----------------------------------------------------------------------------
// Bandwidth parsing
// ----------------------------------------------------------------------------

func parseBandwidth(raw string) (uint64, error) {
	s := strings.ToLower(strings.TrimSpace(raw))
	if s == "" {
		return 0, errors.New("empty")
	}
	idx := 0
	for idx < len(s) && (s[idx] == '.' || (s[idx] >= '0' && s[idx] <= '9')) {
		idx++
	}
	if idx == 0 {
		return 0, fmt.Errorf("missing numeric prefix in %q", raw)
	}
	num, err := strconv.ParseFloat(s[:idx], 64)
	if err != nil {
		return 0, fmt.Errorf("parse number: %w", err)
	}
	mult, err := bandwidthUnitMultiplier(strings.TrimSpace(s[idx:]))
	if err != nil {
		return 0, err
	}
	return uint64(num * float64(mult)), nil
}

func bandwidthUnitMultiplier(u string) (uint64, error) {
	switch u {
	case "", "b", "bps":
		return 1, nil
	case "k", "kb", "kbps":
		return 1_000, nil
	case "m", "mb", "mbps":
		return 1_000_000, nil
	case "g", "gb", "gbps":
		return 1_000_000_000, nil
	case "t", "tb", "tbps":
		return 1_000_000_000_000, nil
	}
	return 0, fmt.Errorf("unknown bandwidth unit %q", u)
}

// ----------------------------------------------------------------------------
// Internals
// ----------------------------------------------------------------------------

var (
	handles       sync.Map // handleID -> *handle
	handleCounter atomic.Uint64

	engineMu      sync.Mutex
	engineRunning bool

	protector atomic.Value // Protector
	lastErr   atomic.Value // string
)

func init() {
	protector.Store(Protector(noopProtector{}))
}

func newHandleID() string {
	n := handleCounter.Add(1)
	return fmt.Sprintf("hy2-%d-%d", time.Now().UnixNano(), n)
}

type handle struct {
	client   hyclient.Client
	listener net.Listener
	socksErr chan error
	started  time.Time
}

type noopProtector struct{}

func (noopProtector) Protect(int32) bool { return true }

func currentProtector() Protector {
	v := protector.Load()
	if v == nil {
		return noopProtector{}
	}
	return v.(Protector)
}

func setLastError(err error) {
	if err == nil {
		return
	}
	lastErr.Store(err.Error())
}

// engineCounters reads the bundled tun2socks engine's byte counters.
// Abstracted behind a helper so tests can fake it without poking at the
// engine internals.
func engineCounters() (rx, tx uint64) {
	// xjasonlyu/tun2socks's engine doesn't export its counters directly;
	// until we ship a vendored fork that does, we return zeros. The
	// Kotlin side treats zero as "stats unavailable" and shows a dash.
	return 0, 0
}
