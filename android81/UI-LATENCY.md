# Leo UI scheduling latency

Measured and installed on 17 October 2026, Android 8.1 r81, legacy HWC1 path.

## Cause and change

`SurfaceFlinger_hwc1.cpp` constructs `mPrimaryDispSync` but never calls its
`init(hasSyncFramework, dispSyncPresentTimeOffset)`. In this source revision,
`DispSync::init` starts the scheduler thread and initializes fence policy.
The HWC2 implementation already calls it. Without it, the HWC1 EventThreads
fall back to their 1000 ms timeout instead of receiving scheduled vsync events.

`patch-hwc1-dispsync.py` adds that same initialization to HWC1, after config
values are loaded. The installed process now has a `DispSync` thread.
No timeout was shortened and no GPU completion check was removed.

The device also sets `TARGET_RUNNING_WITHOUT_SYNC_FRAMEWORK := true` because
our framebuffer HWC returns no present/retire fences. This selects Android's
supported no-present-fence clock model; GPU acquire fences remain in use.
Configstore alone did not remove the delay: the missing scheduler initialization
was the decisive fix. This configuration is provided by the rebuilt
`android.hardware.configstore@1.0-service`.

## On-device results

Each row used 16 alternating Linux keypad down/up presses through event1.
Times are from the KeyEvent timestamp to entry into Leo Shell's onDraw,
not optical latency or application FPS. Samples include the first press.

| Installed state | Minimum | Median | Maximum |
| --- | ---: | ---: | ---: |
| Original scheduling | 1004.54 ms | 1005.12 ms | 1018.79 ms |
| No-present-fence config alone | 1003.55 ms | 1004.92 ms | 1010.50 ms |
| Config + HWC1 DispSync initialization | 7.92 ms | 17.68 ms | 65.14 ms |

Framebuffer pan ioctl took 31.70–33.31 ms (median 32.84 ms) in the final
trace. It remains a material display-transfer cost. The sampler's optional
next-pan completion field does not identify a particular buffer and must not
be presented as exact key-to-visible latency. A camera/optical test is needed
for that. The synthetic 29 Hz clock is not measured physical FPS.

Settings started successfully twice with stopped application processes:
ThisTime 1195 ms and 516 ms, versus earlier completed 2439/2419 ms trials.
Filesystem and shader caches were not controlled; these are smoke-test timings,
not a controlled general application speedup benchmark.

The menu screenshot was visually checked; Adreno integration verification
passed, `/system` remained read-only and both SIM voice slots were IN_SERVICE.
The final changes were verified after framework/graphics restart, not a new
full-device cold boot. No boot/recovery rewrite or data format was performed.
The separate NetworkPolicy startup wait/ANR is still present.

## Reproduce or roll back

- Build `build-shell.sh`, then install the APK using the shell README procedure.
- Build `build-ui-sync.sh` in WSL kaios. It applies the idempotent framework
  patch and builds the compositor library, configstore service and HWC.
- `python android81/install-ui-sync.py` verifies the pinned device/internal
  system, saves original files and their hashes in ignored output/ui-sync-backup,
  stages verified binaries and restarts graphics/Java. It restores the final
  executable SELinux label after atomic replacement, then remounts read-only.
- `--update` preserves original backups while checking the previous installed
  hashes. `--rollback` restores the saved originals for this change, including
  configstore and libsurfaceflinger, and restarts graphics/Java again.
- Wait for boot and dismiss any existing startup ANR with Wait before measuring.
  Run `python android81/measure-ui-latency.py --label chosen-label` with Leo Shell
  focused. Private logs/reports stay in ignored output/. The sampler enables
  launcher tracing via an intent extra and HWC timing via debug.leo.ui_latency,
  then disables both. Normal use does not log per-input/per-frame timing.

HWC dumps now expose vsync enable/power state, request and callback counts and
last callback time, so a future clock failure can be distinguished from slow
rendering without enabling full tracing. Raw logs may contain device data and
are deliberately excluded from Git.
