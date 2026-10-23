# Camera port: offline audit and isolated loading

The dump contains `camera.msm8909.so`, `mm-qcamera-daemon`, and the Qualcomm camera
pipeline libraries. Its framework reports Android 6.0.1/API 23; Leo targets API 27.
Android currently reports **zero cameras**. As of 23 October, the private stock
HAL can enumerate **one camera** after isolated sensor bootstrap. It is not yet
registered with Android; camera capture is not functional.

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


## Sensor registered and private HAL count — 23 October 2026

The new bootstrap probe identified the stock sensor candidate table from the
actual ARM binary: `gc2385`, `gc2395`, `bf20a1`, `bf2253L`, `gc030a`.
This is a probe order, not a list of installed cameras. The original five plugins
remain in the marked private directory, with host/device hashes checked.

`camera-bootstrap-plan.c` exports an ioctl wrapper. The default `--plan` forwards
only MEDIA_IOC_DEVICE_INFO and MEDIA_IOC_ENUM_ENTITIES; all five sensor commands
were refused, proving interception before hardware initialization. The optional
`--probe-sensors` additionally permits the audited stock request `0xc0d456ca`
with CFG_SINIT_PROBE (0), without running the daemon or opening a camera.

On the handset, the first two candidates failed ID reads with errno 107. Kernel
messages then explicitly reported **`bf20a1 probe succeeded`**. New nodes appeared:
`v4l-subdev9` (`camera 3-007c`), `video1` (`msm-sensor`) and `media1`.
Later probe calls returning zero are not evidence of three physical sensors:
the driver can accept a request for an already occupied slot.

The original interface disassembly shows cfgtype 2 at the initialization wait.
Related Qualcomm kernel source separates PROBE (0), DONE (1), and WAIT_DONE (2):
[upstream reference, not proof of this exact handset kernel implementation](https://android.googlesource.com/kernel/msm/+/android-7.1.0_r0.2/drivers/media/platform/msm/camera_v2/sensor/msm_sensor_init.c).
This explains a plausible missing handshake in the earlier probe; the old kernel
wait was not instrumented, so its exact cause is still not proven.

`--enumerate-ready` requires the registered sensor and the expected init node,
sends DONE once, and satisfies the subsequent WAIT locally only after DONE
succeeds. No blocking WAIT is forwarded. It exposes the newly created `media1`
inside the private namespace; hiding that node initially produced count zero.
With it visible, both native interface and original HAL returned one:

```text
PROBE_DONE_RETURN=0
PRIVATE_CAMERA_COUNT=1
PRIVATE_HAL_CAMERA_COUNT=1
PROBE_CHILD_EXIT=0
```

The boot ID, system_server and SurfaceFlinger PIDs, and audio-state property were
unchanged; no probe child remained. These checks establish continuity of those
services, not an exhaustive hardware regression test. There was no preview,
capture, call or audio test. Sensor registration is volatile until reboot, and
no boot service was added. Global device permissions and library paths remain
unchanged. This containment is diagnostic, not a boundary for hostile code.

**Remaining boundary:** Android `dumpsys media.camera` still reports zero devices.
Next work is the isolated stock daemon/pipeline and API-23-to-27 provider bridge,
then opening, buffer allocation, preview and JPEG. A private HAL count of one is
not a completed camera port. No stock blobs or raw diagnostic logs are published.

Reproduce on the matching development handset, after the linker sandbox has
staged the shared dependencies:

```bash
bash android81/build-camera-bootstrap-plan.sh
python android81/run-camera-bootstrap-plan.py
# Changes volatile sensor state; do not stack probes or run during a call:
python android81/run-camera-bootstrap-plan.py --probe-sensors
# Only after a sensor has actually registered:
python android81/run-camera-bootstrap-plan.py --enumerate-ready
```

The runner verifies the known serial, boot completion, idle calls, ownership marker,
plugin hashes and absence of an earlier probe/daemon. Child CPU, memory and wall
time are bounded, but an uninterruptible kernel wait can outlive SIGKILL. Inspect
any remaining child before proceeding. The old `--devices` enumeration probe is
not needed for this sequence.
