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
