# Leo performance investigation, 17 October 2026

Measured on the internal Android installation, with both radio services running.
Private captures remain under ignored `output/`; they are not suitable for GitHub.

## Baseline

- `/system` and `/data` use internal eMMC, not the SD card.
- All four CPU cores are online; governor `performance`, maximum 1267200 kHz.
- `ro.config.low_ram=true`, JIT enabled, animation scales already zero.
- Usable RAM 390200 KiB; zRAM 262140 KiB. At idle, about 154248 KiB was
  available. About 160 MiB of swapped pages occupied only about 21 MiB in zRAM;
  the swap-used number alone does not establish active memory thrashing.
- Idle CPU was mostly unused. During application startup, reclaim and CPU load
  increase. An old `dumpsys cpuinfo` sample describes boot, not current load.
- SurfaceFlinger uses Google SwiftShader GLES 2.0 and the CPU framebuffer bridge.
  Its advertised 29 Hz is the synthetic vsync rate, not measured application FPS.
- Settings cold-process launches timed out at 10300 and 10581 ms. HWUI recorded
  five frames, four janky, with two frames in the >=4950 ms histogram bucket.
  A native stack captured the main thread waiting in RenderProxy::postAndWait.
  This identifies a graphics startup bottleneck; it is not proof every delay
  elsewhere has the same cause.

## Changes

Settings now sets application `hardwareAccelerated=false`, using Canvas instead
of app-side HWUI/SwiftShader. SurfaceFlinger still uses SwiftShader to compose
the resulting buffers. `patch-settings-rendering.py` is included in the full
framework build; `build-settings-canvas.sh` builds only Settings. The existing
optional Wi-Fi service fix is retained. The signed APK is installed as a normal
system-app update under `/data/app`, without writing boot or recovery.

The three post-change cold-process launches completed successfully in 3737,
2477 and 2416 ms (`ThisTime`); wait times were 3779, 3002 and 2939 ms.
This is a substantial improvement over the two baseline timeouts, but not a
general FPS measurement. A keypad-down event and screenshot confirmed the
Settings list remained displayed. `gfxinfo` confirmed the software view path.
Both SIMs remained READY and both voice slots IN_SERVICE after these changes.

Leo Shell loads each application label once per application-list refresh.
Sorting and drawing read that cache rather than loading labels repeatedly.
The new shell was built and installed. Two cold-process launches were 2899 and
2446 ms, versus 2296 and 2334 ms before: no startup speedup is demonstrated.
This change removes work from keypad redraws, not the graphics compositor.

Leo Shell was explicitly compiled with `cmd package compile -m speed
ro.leo.shell`; compilation succeeded, but it did not establish a meaningful
startup improvement. No global compiler policy, CPU clocks, swap tuning, or
radio settings were changed.

## Repeating measurements

Run `python android81/measure-performance.py --label settings-canvas --launch
com.android.settings/.Settings --trials 3` from the repository. The script pins
serial 695d, collects properties/mounts/memory/graphics and force-stops only the
selected package before each launch. Filesystem caches remain warm. Compare
Android's `ThisTime` values; do not call this a clean-boot benchmark. An activity
launch result does not prove every screen or animation is responsive.

To undo the rendering experiment, restore Settings' application manifest flag
to `true`, rebuild and install the APK. To undo the shell change, revert its
source change, rebuild and install. Existing user data is retained.
