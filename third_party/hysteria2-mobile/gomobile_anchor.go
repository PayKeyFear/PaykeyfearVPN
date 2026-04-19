// Package-local anchor that forces `go mod tidy` to keep
// golang.org/x/mobile/bind in this module's dep graph. See
// awg-mobile/gomobile_anchor.go for the full rationale.
package hy2mobile

import _ "golang.org/x/mobile/bind"
