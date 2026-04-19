module github.com/paykeyfear/awg-mobile

go 1.22

// `golang.org/x/mobile/bind` is the Java↔Go bridge gomobile injects into
// every gomobile-bind module — declaring it here makes `go mod tidy`
// resolve a compatible version BEFORE gomobile tries to import it. Without
// this, gobind fails with "no Go package in golang.org/x/mobile/bind".
require (
	github.com/amnezia-vpn/amneziawg-go v0.2.12
	golang.org/x/mobile v0.0.0-20260209203831-923679eb55af
	golang.org/x/sys v0.25.0
)
