// Package-local anchor that forces `go mod tidy` to keep
// golang.org/x/mobile/bind in this module's dep graph. Without this,
// tidy prunes the package (since we never call anything from it
// directly) and the subsequent `gomobile bind` invocation fails with
//
//   "golang.org/x/mobile/bind" is not found; run go get golang.org/x/mobile/bind
//
// See https://pkg.go.dev/golang.org/x/mobile/cmd/gobind — the bind
// package is consumed by `gobind` (spawned by `gomobile bind`), not by
// our own code, so a blank import is the cleanest way to pin it.
package awgmobile

import _ "golang.org/x/mobile/bind"
