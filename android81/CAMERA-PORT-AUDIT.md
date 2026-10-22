# Camera port: offline audit and isolated loading

The dump contains `camera.msm8909.so`, `mm-qcamera-daemon`, and the Qualcomm camera
pipeline libraries. Its framework reports Android 6.0.1/API 23; Leo targets API 27.
Android currently reports **zero cameras**. Camera capture is not functional.

`inspect-camera-port.py` inspected 23 original camera/vendor binaries and compared
their required symbols with 16 Android 8.1 platform libraries. The offline audit
found no missing declared shared libraries and no unresolved global imports in
that combined symbol set. It does not load code on the phone or publish blobs.

This makes an isolated port probe worthwhile, but does **not** establish ABI
compatibility. Structure layouts, symbol versions, plugin `dlopen` dependencies,
sensor tuning/calibration, daemon device permissions, kernel ioctls and the
camera-provider adapter still need runtime checks. Global symbol availability
does not prove each library's actual linker scope will resolve identically.

## Device linker probe — 19 October 2026

`build-camera-linker-probe.sh` and `run-camera-linker-probe.py` built and ran a
separate ARM process against the private stock libraries. `dlopen(RTLD_NOW)`
succeeded and the exported module reported:

```text
SANDBOX uid=9999 devices=hidden network=isolated
DLOPEN_OK module=camera name=QCamera Module module_api=0x100 hal_api=0x100
PROBE_CHILD_EXIT=0
```

The child used separate mount/network namespaces, a replacement `/dev` containing
only null/zero/urandom, dropped supplementary groups and root identity, and had
memory/CPU/time limits. No camera enumeration, open, capture or daemon method was
called. This is a diagnostic containment measure, not a security boundary for
arbitrary hostile code. A denied `vold.decrypt` property read was logged but did
not prevent loading. The phone's boot ID and audio state were unchanged.

Original libraries remain in a marked private diagnostic directory under
`/data/local/tmp`, outside Android's system library search paths. No platform
library was replaced. Binaries and detailed results stay in ignored `output/`.

The kernel exposes `video0` as `msm-config`; that is not proof of a working image
sensor. Next work is to inspect daemon initialization, sensor dependencies and
the provider adapter before opening hardware. Keep Android's binder, utils and
graphics libraries intact. Capture and camera registration remain unimplemented.

## Sensor initialization probe — 20 October 2026

The kernel exposes CSI PHY, two CSID nodes, VFE, ISPIF, flash, actuator and
`msm_sensor_init`; `/dev/video0` is still `msm-config`, not an enumerated sensor.
The original nodes were root-only. No qcamera daemon socket was present.

The new enumeration probe first hid all hardware: HAL loading and enumeration
completed, reporting **CAMERA_COUNT=0**. A separate child then received only
13 camera character nodes recreated inside its private mount namespace, owned
by camera UID 1006. No platform device permissions were changed; binder, ion,
GPU, audio and modem nodes and external daemon sockets remained hidden.

With those nodes visible, initialization did not return. A child was observed
in kernel wait `msm_sensor_init_subdev_ioctl`. The parent watchdog requested
SIGKILL after 15 seconds but could not immediately reap the kernel-blocked
child. The host call timed out at 20 seconds. Later inspection found no remaining
probe process. Boot ID, SurfaceFlinger/system_server PIDs and audio state were
unchanged. No camera open, preview or capture method was called.

This narrows the problem to sensor initialization and its missing daemon/probe
handshake; it does not prove the exact failing dependency or that changing
permissions alone will fix it. The runner refuses to stack another probe while
an earlier process remains. Further hardware probing needs a kernel wait/timeout
review and a controlled stock daemon/sensor-library initialization path first.
Do not install this diagnostic HAL globally or claim that capture works.


## Bootstrap inventory and keypad app — 22 October 2026

`inspect-camera-bootstrap.py` reads the original files only. It found the strings
`module_sensor_init` / `server_process_module_sensor_init` in `mm-qcamera-daemon`,
and `module_sensor_init`, `sensor_init_probe`, `sensor_init_session` in
`libmmcamera2_sensor_modules.so`. The stock `qcamerasvr` service belongs to
`late_start`, runs as camera, and has camera/system/inet/input/graphics groups.
There are 82 vendor libraries with the `libmmcamera` prefix; this is **not a count
of physical sensors**. No hardware has been identified by this inventory.

These markers suggest reviewing the daemon-to-sensor-module bootstrap before
retrying enumeration. Strings do not prove call order, the exact blocked ioctl,
or that starting the daemon would fix the previous kernel wait. No daemon,
probe or private HAL was started in this stage. The private report stays in
ignored `output/camera-bootstrap-audit.json`.

The new Camera Leo activity queries the public legacy camera count and shows
unavailability when Android exposes none. A permission-gated preview and JPEG
capture path is implemented for a future registered camera. It does not load or
install vendor libraries. Its preview, orientation and capture remain untested
on the handset; USB/ADB was unavailable during this work.
