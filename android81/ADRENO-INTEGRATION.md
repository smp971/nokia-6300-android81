# Android graphics integration, 17 October 2026

This follows the isolated GPU test in [ADRENO-BRINGUP.md](ADRENO-BRINGUP.md).
The goal is an Android compositor using the stock Adreno 304 driver while
preserving the existing RGB565 SPI-panel presentation path.

**Current result:** installed and active. SurfaceFlinger reports Qualcomm Adreno
304 / GLES 3.0. The hardware stack loaded after a full reboot and the user
confirmed the Leo menu/keypad. A subsequent, scoped HWUI fence fix passed an
explicit Android HARDWARE-bitmap allocation/rendering test and application smoke
tests after restarting Java. Both SIMs/voice registrations still pass. This
remains an experimental bring-up, not a general stability certification.

## Buffer and compositor bridge

- Stock `gralloc.msm8909.so`, libmemalloc, libqdMetaData, libqdutils and libqservice
  load against the installed Android 8.1 system libraries.
- The private allocator probe allocates 240x320 RGBA ION buffers with a **288
  pixel stride**. Assuming the old software allocator's 240 stride corrupts rows.
- The probe imports a stock buffer as an EGLImage, attaches it to a GLES FBO,
  clears it on Adreno and reads `64,128,191,255` back through the CPU allocator.
- A forked child duplicates the native-handle FDs, registers the handle through
  the stock mapper and reads the same pixels: `LEO_ION_CROSS_PROCESS_OK`.
- `gralloc-adreno-bridge.c` forwards shared-buffer allocation/import/locking to
  the stock allocator, clears HW_FB usage and retains the verified software
  framebuffer device for the physical panel. Stock perform's varargs ABI is
  retained; this exact stock implementation checks module != NULL but does not
  dereference it. The private probe passed with the bridge as well.
- HWC reads Qualcomm GET_CUSTOM_STRIDE_FROM_HANDLE before converting the RGBA
  GPU-composed target into the physical RGB565 framebuffer. Its original path
  remains compatible with software gralloc, which has no perform callback.

This uses hardware GLES client composition; it does not turn the custom HWC
into a hardware overlay engine. Final panel conversion/copy still runs on CPU.

## Installation and selection

`build-adreno-integration.sh` builds the bridge/probes, HWC and HWUI adjustment.
`prepare-adreno-integration.py` stages additive stock libraries/firmware and
verifies original gralloc/HWC backups. It refuses to overwrite backups with an
already-active bridge. Firmware and vendor binaries are kept local and ignored
by Git; installation hashes/reports live under `output/`.

`adreno-integration-switch.sh activate` stops the Java/graphics services, swaps
only graphics files with atomic replacements, remounts `/system` read-only and
restarts them. SwiftShader originals are kept. A detached watchdog can restore
the original gralloc/HWC/EGL selection if the trial is not accepted. The
system init RC also enables this watchdog for a trial boot. Once validated,
`persist.leo.adreno.validated=1` prevents later ordinary boots from being
downgraded simply because no PC is attached. Explicit activation resets that
validation flag.

The switch installs the rebuilt HWUI library together with gralloc/HWC, so a
fresh reproduction need not pass through the earlier crashing intermediate
state. `install-adreno-hwui-fix.py` upgraded that initial trial in place.

The Oreo EGL loader's exact-name search in this build omits `.so`; merely
installing `libEGL.so` left it selecting the earlier SwiftShader directory entry.
Exact extensionless links `libEGL`, `libGLESv1_CM`, and `libGLESv2` select Adreno
reliably. Normal `/vendor/lib/egl` paths are now present, so the installed driver
uses the **original, unpatched** Qualcomm EGL binary. The temporary path-patched
binary used by the isolated probe is not installed.

`leo-adreno-init.rc` applies the original KaiOS node modes (KGSL 0666, ION 0664),
the newline-free `/vendor/firmware` path and initialization governor before
graphics start. After boot, GPU devfreq returns to `msm-adreno-tz`. Original
firmware and the normal modem firmware mount remain available. No frequencies
are raised beyond the existing hardware tables.

## On-device observations

SurfaceFlinger selected Qualcomm Adreno 304 / GLES 3.0. The Leo app grid and
SystemUI status bar were captured and the user confirmed keypad navigation.
A full reboot also loaded Adreno from installed files, before any `/tmp` staging;
the user confirmed the menu responded. Both SIMs were READY and both voice slots
IN_SERVICE. ADB transport was lost without a Qualcomm 900E fault and returned
after cable reconnection.

The initial framework restart showed ANR dialogs, followed by a reproducible
HWUI crash: `Failed to wait for the fence 0x300c` in hardware-bitmap upload.
The standalone probe reproduced EGL_BAD_PARAMETER for both finite and infinite
`eglClientWaitSyncKHR` waits, despite successful context creation and rendering.
Thus initial rendering success alone was insufficient to call the port stable.

## Scoped HWUI synchronization adaptation

`patch-adreno-hwui-fence.py` changes only the hardware-bitmap upload wait. If
the error is EGL_BAD_PARAMETER, GL_VENDOR contains Qualcomm and
`persist.leo.adreno.hwui_finish=1`, it calls `glFinish()` on the upload's current
GL context and checks GL errors. This completes the upload synchronously before
sharing the buffer; it does not simply discard the fence error. Other errors
retain the original failure behavior. Same-context completion was independently
validated by the GPU-to-CPU ION readback probe.

`install-adreno-hwui-fix.py` backs up the original HWUI library, checks its known
hash and installs the rebuilt library atomically. Rollback also restores that
library and disables the compatibility property. The smoke tests below cover
the affected upload path; sustained stability and speedups need longer testing.

## Final checks and limitations

- `verify-adreno-integration.py --accept` passed with boot completed, unlocked
  user, Leo Shell focused, Adreno renderer, live graphics services and `/system`
  read-only. The accepted boot ID and persistent validation flag were verified.
- The test APK built by `build-hardware-bitmap-probe.sh` requested
  `Bitmap.Config.HARDWARE`, displayed the decoded bitmap and logged both
  `LEO_HWUI_BITMAP_OK config=HARDWARE` and the HWUI glFinish compatibility message.
  It was uninstalled after testing; only its source and selected screenshot remain.
- Calculator opened successfully with hardware rendering; Settings opened
  repeatedly. The post-fix system_server PID remained unchanged during these
  smoke tests. The last fence-related crash predates the fix.
- Settings' two completed cold-process launches were 2439 and 2419 ms. The
  first attempt timed out while a startup ANR dialog was present. This does not
  demonstrate a Settings launch-time speedup over the earlier Canvas-only fix.
- The remaining startup ANR trace shows system_server waiting in
  NetworkPolicyManagerService.systemReady on its init latch. In the source,
  disabled bandwidth control returns from initService without counting that
  latch down, leaving a 30-second wait. This existing network-service issue was
  diagnosed but not changed as part of the GPU integration.
- The synthetic 29 Hz setting and existing hardware-vsync timeout messages are
  not measured application FPS. Physical SPI-panel presentation and CPU RGB565
  conversion remain limits. Do not promise that every app is now fluid.
- No boot/recovery rewrite, userdata format, kernel replacement or overclock
  was involved. Selected real screenshots are in `screenshots/adreno-menu.png`
  and `screenshots/adreno-hardware-bitmap.png`.

## Reverting

With root ADB in init's mount namespace, run
`/system/bin/sh /system/vendor/lib/leo-adreno/switch.sh rollback` through
`/sbin/leo-framework-exec`. This restores original gralloc, HWC and HWUI (if
backed up), removes active Adreno selectors/driver copies and its init RC,
resets device-node modes and restarts graphics/Java services. Additive inactive
libraries and firmware remain as recoverable staging files. Boot, recovery and
userdata formatting are outside these scripts.
