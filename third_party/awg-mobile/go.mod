module github.com/paykeyfear/awg-mobile

go 1.24.0

// `golang.org/x/mobile/bind` is the Java↔Go bridge gomobile injects into
// every gomobile-bind module — declaring it here makes `go mod tidy`
// resolve a compatible version BEFORE gomobile tries to import it. Without
// this, gobind fails with "no Go package in golang.org/x/mobile/bind".
require (
	github.com/amnezia-vpn/amneziawg-go v0.2.12
	golang.org/x/mobile v0.0.0-20260209203831-923679eb55af
	golang.org/x/sys v0.41.0
)

require (
	github.com/tevino/abool/v2 v2.1.0 // indirect
	golang.org/x/crypto v0.48.0 // indirect
	golang.org/x/mod v0.33.0 // indirect
	golang.org/x/net v0.50.0 // indirect
	golang.org/x/sync v0.19.0 // indirect
	golang.org/x/tools v0.42.0 // indirect
	golang.zx2c4.com/wintun v0.0.0-20230126152724-0fa3db229ce2 // indirect
)
