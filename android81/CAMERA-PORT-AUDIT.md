# Camera port: offline feasibility

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

The kernel exposes `video0` as `msm-config`; that is not proof of a working image
sensor. Next work should load dependencies in an isolated diagnostic, then inspect
the camera module and provider before opening hardware. Keep Android's binder,
utils and graphics libraries intact. No camera HAL or daemon was activated in
this audit. The detailed JSON stays in ignored `output/camera-port-audit.json`.
