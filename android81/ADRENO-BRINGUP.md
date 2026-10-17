# Stock Adreno investigation, 17 October 2026

## Confirmed

The running kernel exposes `/dev/kgsl-3d0` and `/dev/ion`.
`/sys/class/kgsl/kgsl-3d0/gpu_model` reports `Adreno304v1`; advertised
frequencies are 456000000, 307200000 and 200000000 Hz. This establishes that
the kernel exposes the GPU interface, not that Android UI acceleration works.

Eight stock vendor libraries were copied from the dump to the temporary
`/tmp/adreno-probe-libs` directory. They are the five files from
`analysis/system/vendor/lib/egl` and `libgsl.so`, `libadreno_utils.so`, and
`libsc-a3xx.so`. With that private directory on LD_LIBRARY_PATH, the existing
library-only loader successfully loaded `libEGL_adreno.so`,
`libGLESv2_adreno.so` and `eglsubAndroid.so` against Android 8.1 libraries.
No stock libc, libui, gralloc or system EGL dispatcher was installed.

## Incomplete rendering trial

`adreno-probe.c` dynamically loads the vendor EGL/GLES entry points directly,
attempts a 16x16 pbuffer/context, clears a known color and checks pixel readback.
It has a 20-second alarm and never requests an on-screen surface. This bounds
normal userspace waits; it cannot recover a kernel GPU-driver hang.

The first invocation returned without probe output; immediately afterwards the
phone was absent from both ADB and Windows' USB device inventory. There is no
evidence establishing whether execution reached EGL initialization, whether a
GPU/kernel fault occurred, or whether the USB connection failed independently.
The probe must not be called successful, and the vendor driver must not replace
SwiftShader on this evidence. Stage prints were added to the source afterwards
and the revised executable was build-checked, but has not been run on the phone.

The installed compositor, graphics drivers, boot and recovery were untouched.
The temporary driver directory does not alter the normal graphics selection.

## Next evidence needed

Check the phone's actual state and reconnect ADB. Compare boot ID/uptime and
inspect pstore/last-kmsg, kernel logs and tombstones before another GPU test.
If a new trial is justified, preserve stage output in a private persistent log.
Even successful pbuffer readback will leave Android gralloc/native-buffer
sharing, app linker namespaces, permissions and display composition untested.
The existing graphics bridge uses ashmem buffers; stock GPU support cannot be
assumed compatible with those handles.
