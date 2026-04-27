//go:build android

package vlessmobile

/*
#cgo LDFLAGS: -ldl

#include <dlfcn.h>
#include <stdint.h>

typedef uint32_t (*fdsan_set_level_fn)(uint32_t);

// downgradeAdreno disables fdsan fatal-abort for this process.
//
// Samsung Android 16 + Qualcomm Adreno GPU driver (libgsl.so) has a bug in
// gsl_syncobj_create: it wraps sync-fence fds with unique_fd AND calls raw
// close() on the same fd from a separate code path. When our VPN engine
// (gVisor tun2socks) closes the tun fd on disconnect, the kernel recycles
// that slot for the next GPU sync fence, causing an fdsan SIGABRT on the
// RenderThread at every eglSwapBuffers call. We cannot patch the GPU driver.
//
// Fix: set fdsan to DISABLED (0) so the driver's internal inconsistency is
// silently ignored. WARN_ONCE/WARN_ALWAYS are not used because they fire on
// every eglSwapBuffers (~60/s) and add per-frame overhead that makes the app
// appear laggy.
//
// RTLD_DEFAULT searches already-loaded libraries without dlopen, avoiding
// Android 16 namespace restrictions that block dlopen("libc.so", RTLD_NOLOAD).
static void downgradeAdreno(void) {
    fdsan_set_level_fn fn =
        (fdsan_set_level_fn)dlsym(RTLD_DEFAULT, "android_fdsan_set_error_level");
    if (fn) {
        fn(0); // ANDROID_FDSAN_ERROR_LEVEL_DISABLED
    }
}
*/
import "C"

import "sync"

var fdsanOnce sync.Once

// downgradeAdreno is called lazily on the first SetProtector call (i.e. before
// any VPN activity). Calling CGo from Go's init() deadlocks with gomobile's
// JNI initialization, so we defer it to the first user-facing export.
func downgradeAdreno() {
	fdsanOnce.Do(func() { C.downgradeAdreno() })
}
