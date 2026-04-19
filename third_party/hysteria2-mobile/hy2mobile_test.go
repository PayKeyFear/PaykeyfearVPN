package hy2mobile

import (
	"strings"
	"testing"
)

func TestParseBandwidth(t *testing.T) {
	cases := []struct {
		in   string
		want uint64
	}{
		{"100", 100},
		{"100bps", 100},
		{"5 kbps", 5_000},
		{"50mbps", 50_000_000},
		{"1.5 gbps", 1_500_000_000},
		{"  2tbps ", 2_000_000_000_000},
	}
	for _, tc := range cases {
		t.Run(tc.in, func(t *testing.T) {
			got, err := parseBandwidth(tc.in)
			if err != nil {
				t.Fatalf("parseBandwidth(%q): %v", tc.in, err)
			}
			if got != tc.want {
				t.Fatalf("parseBandwidth(%q) = %d, want %d", tc.in, got, tc.want)
			}
		})
	}
}

func TestParseBandwidth_Errors(t *testing.T) {
	for _, in := range []string{"", "abc", "100 zb", "mbps"} {
		if _, err := parseBandwidth(in); err == nil {
			t.Fatalf("parseBandwidth(%q) returned nil error", in)
		}
	}
}

func TestDecodePinSHA256(t *testing.T) {
	// 32-byte pin in colon-separated and plain forms.
	plain := strings.Repeat("ab", 32)
	colon := strings.Join(strings.SplitAfter(plain, "ab"), ":")
	colon = strings.TrimRight(colon, ":")
	for _, in := range []string{plain, strings.ToUpper(plain), colon} {
		b, err := decodePinSHA256(in)
		if err != nil {
			t.Fatalf("decodePinSHA256(%q): %v", in, err)
		}
		if len(b) != 32 {
			t.Fatalf("len = %d, want 32", len(b))
		}
	}
	if _, err := decodePinSHA256("ab"); err == nil {
		t.Fatal("expected error for short pin")
	}
	if _, err := decodePinSHA256("nothex" + strings.Repeat("ab", 30)); err == nil {
		t.Fatal("expected error for non-hex pin")
	}
}
