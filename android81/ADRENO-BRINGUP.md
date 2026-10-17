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

## Recovery check

The user confirmed a black screen. Windows detected Qualcomm HS-USB Diagnostics
900E instead of ADB. After the user removed power and restarted the phone,
Android boot completed normally with a new boot ID. Both persistent RIL services
and their supporting services were running, both SIMs READY, and both voice
slots IN_SERVICE. SurfaceFlinger still reported SwiftShader and the original
Leo software HWC.

Neither `/sys/fs/pstore` nor `/proc/last_kmsg` exists, and `/proc/filesystems`
does not advertise pstore. The newest tombstone is an older temporary RIL probe
crash, not an Adreno crash. Current-boot dmesg shows GPU initialization but does
not preserve the previous failure. A severe failure during the trial is therefore
supported by observation; its exact kernel fault and the stage reached remain
unknown. Do not repeat the EGL initialization trial on this evidence.

## Adapted retry requested by the user

Read-only inspection then found that stock `a300_pfp.fw` (1156 bytes) and
`a300_pm4.fw` (9220 bytes) were absent from Android's firmware lookup locations.
Stock `init.project.rc` also explicitly selects GPU devfreq `performance` during
early init, while Android was using `msm-adreno-tz`.

A second, user-requested trial supplied those two stock firmware files in
`/tmp/adreno-fw` (device SHA256 matched the source), temporarily pointed the
kernel firmware search path there, and selected stock's `performance` governor.
No clock frequencies were raised. `adreno-trial.sh` restores both settings if
userspace returns, and a reboot drops the temporary configuration. The normal
ueventd firmware fallback `/firmware/image` remains available for modem firmware.
The revised probe fsyncs stage markers to `/data/local/tmp/leo-adreno-trial.log`
before each critical EGL step, so an external power cycle need not erase them.

This retry again caused ADB loss and Windows Qualcomm Diagnostics 900E. A
missing firmware file therefore does not explain the whole failure. The precise
stage must be read after recovery; no further initialization trial should run
until that evidence has been inspected. The installed graphics stack is unchanged.

After the second power-cycle recovery, the persistent log was successfully read
and copied to the PC. Its last markers are `stage=load-egl`, `stage=load-gles`,
`stage=get-display`, and `stage=initialize`. There is no EGL version output or
`stage=choose-config`. Thus the process reached the call to vendor
`eglInitialize` and did not return from it before the observed 900E failure.
Context creation and the pixel readback were never reached. This localizes the
failure but does not identify which kernel ioctl, firmware operation, allocator
interaction or power-management call inside initialization caused it.

The current boot completed normally. Firmware path was restored by normal boot
to `/firmware/image`, governor to `msm-adreno-tz`, and the original SwiftShader
compositor remained active. Both SIMs and voice registrations passed verification.
Further adaptation should first inspect initialization's allocator and KGSL ABI
interactions offline; do not treat another identical pbuffer run as new evidence.

## Guarded investigation without GPU access

Disassembly of the stock binaries establishes this early initialization chain:

`qeglDrvAPI_eglInitialize` -> `egliInitState` -> `gsl_library_open` ->
`ioctl_kgsl_driver_entry` -> device opens and KGSL ioctls.

In `libEGL_adreno.so`, `egliInitState` calls `gsl_library_open` at 0x210a4
and `gsl_device_open` at 0x210c4/0x210d4/0x210e4. In `libgsl.so`,
`gsl_library_open` calls `ioctl_kgsl_driver_entry` at 0x6588. That function
opens `/dev/kgsl-3d0`, optionally the two 2D nodes, and `/dev/ion`.
The Android subdriver mapping occurs later in `qeglDrvAPI_eglInitialize`
at 0xbf54. This orders the operations; it does not locate the previous panic
within them.

`adreno-guard.c` is a process-local diagnostic preload which denies device opens
and **all** ioctls. Its optional fake mode substitutes a regular file only for
KGSL-3D and ION; it never forwards GPU commands. The probe first verifies that
opening a nonexistent self-test device returns the guard's ENODEV, and refuses
EGL execution if that check fails. Static imported-symbol inspection of all
eight staged libraries found device operations through `open`, `fopen`, and
`ioctl`, covered by the guard. This is narrowly scoped diagnostic interposition,
not a security sandbox and not a working GPU compatibility shim.

Two guarded trials completed without a reboot, firmware/governor changes, or
interruption of the installed graphics stack:

- Deny mode: attempted KGSL-3D open was intercepted, then EGL returned 0x3001
  (EGL_NOT_INITIALIZED).
- Fake mode: KGSL-3D and ION were replaced by regular-file descriptors, 2D
  device opens were refused, and these commands were intercepted/refused:

| Request | Qualcomm header name |
| --- | --- |
| 0xc0100940 | IOCTL_KGSL_SYNCSOURCE_CREATE |
| 0xc0100941 | IOCTL_KGSL_SYNCSOURCE_DESTROY |
| 0xc00c0902 | IOCTL_KGSL_DEVICE_GETPROPERTY |

EGL again returned 0x3001. These command encodings match the 32-bit definitions
in the local AOSP `hardware/qcom/msm8996/kernel-headers/linux/msm_kgsl.h`.
Those headers are reference material, not proof of the running kernel ABI.
There is no demonstrated command-number mismatch to patch at this point.
Likewise, these tests do not distinguish a fault in opening the real KGSL node
from one in a subsequent real ioctl.

The kernel config confirms `CONFIG_PSTORE` is unset. The config's default
panic-on-oops value is 0 but the live sysctl is 1; neither was changed. Thus an
oops can become fatal under the running configuration, but no oops text was
recovered and the earlier 900E failure must not be assigned a specific cause.

Build with `build-adreno-probe.sh`; `adreno-guarded-trial.sh deny` or `fake`
runs the guarded probe after staging. Private transcripts were copied to
`output/adreno-guarded.log` and `output/adreno-fake.log`. Radio verification
still passed afterwards. The next useful evidence is a preserved kernel fault
trace or matching kernel KGSL source, rather than another unguarded EGL call.

## Kernel sources obtained

Downloaded the published Nokia Leo v20 source mirror:
https://github.com/bmndc/nokia-leo/tree/leo-v20/kernel/msm-4.9

Pinned repository commit: `949a9f7b2c109f307e72b25fec2dc5cb4d119dec`.
Local WSL checkout: `/root/android81-leo/kernel-leo-v20/kernel/msm-4.9`.
`fetch-leo-kernel.sh` reproduces the sparse checkout and refuses an unexpected
existing origin/revision. The external source and its licenses remain in that
separate checkout rather than being copied into this engineering repository.

The Makefile reports 4.9.240, matching the running release version. This does
not prove that it reproduces the installed `g4f9e270fd77f` kernel build exactly.
The repository describes this branch as HMD's published kernel/B2G release.

Inspection adds a concrete finding: in `drivers/gpu/msm/kgsl.c`,
`kgsl_open` calls `kgsl_open_device` (line 1205). On the first open, that function
calls the device's `init` and `start` methods (lines 1151 and 1155). Thus opening
the real KGSL node already initializes and starts the GPU before userspace's
first ioctl. In `adreno.c`, `adreno_init` reads microcode and initializes power
and IOMMU state. A failure observed during EGL initialization need not be an
ioctl ABI mismatch or an application gralloc failure. The fatal operation is
still not proven without a real kernel fault trace.

Saved the running `/proc/config.gz`, current-boot dmesg, and kallsyms names to
ignored `output/leo-running-*` files. Kallsyms addresses are redacted because
`kptr_restrict=2`; this setting was not changed. The kernel has QCOM memory-dump
and download-mode support, but no pstore, kexec or crash-dump support. Live
download_mode is 1 and subsystem full/mini ramdumps are disabled. These values
were only read, not changed. Entering USB 900E alone does not establish that a
usable dump can be downloaded with the tools currently available.

The old failure log was not recovered. For a future fault, possible approaches
are a live kernel-log capture (which can still lose the final messages when USB
stops) or a separately built diagnostic kernel with a verified persistent-log
memory reservation. No kernel rebuild was flashed and no memory reservation
was guessed or added to the installed boot image.

## Initialization analysis: concrete firmware-path defect found

The running device-tree chip ID is `03 00 04 00`, matching the A304 entry in
`adreno-gpulist.h`. That entry requests `a300_pm4.fw` and `a300_pfp.fw`, the
files staged previously. Their lengths pass `a3xx_microcode_read`'s checks:
PM4 9220 modulo 12 = 4, and PFP 1156 modulo 4 = 0. These are format/length
checks, not proof that the GPU successfully executed the firmware.

The first-open sequence is:

1. `kgsl_open`: runtime PM, device-private allocation.
2. `kgsl_open_device`: clear memstore; call `adreno_init`.
3. `adreno_init`: request microcode; transition to AWARE; initialize IOMMU
   metadata, performance counters and fault detection; return to SLUMBER.
4. `_adreno_start`: power to AWARE, start MMU, program GPU registers, enable
   interrupts, start the command ringbuffer and dispatcher.
5. Only after this does `kgsl_open` create per-process pagetable state and return
   the descriptor to userspace. Userspace's first ioctl is later.

Critically, `_load_firmware` errors reach `KGSL_DRV_FATAL` in
`a3xx_microcode_read`. Despite its logging-like name, `KGSL_LOG_FATAL` in
`kgsl_log.h:45` explicitly calls `BUG()`. A missing firmware file can therefore
cause a kernel fault on this first open. Other fatal candidates include IOMMU
clock-enable retries (`kgsl_iommu.c:973`) and an already-started invariant in
`_adreno_start`; their presence is not evidence they were triggered.

**A real configuration bug was found in our scripts:** both radio setup and
the previous adapted trial used `echo` to set `firmware_class.path`. In this
kernel, `param_set_copystring` (`kernel/params.c:545`) copies the trailing
newline unchanged. `fw_get_filesystem_firmware` joins the entire parameter with
`/filename`, so it tries a directory containing a newline. Additionally,
`param_attr_show` appends a display newline. On the actual phone the old value
ended in two `0a` bytes; setting it with newline-free `printf` reduced that to
one display newline. This directly confirms the malformed parameter, not just
a hypothetical shell issue.

The earlier temporary GPU firmware directory was thus not configured correctly.
That undermines the earlier conclusion that a properly supplied firmware pair
had already been ruled out. The newline plus firmware-load `BUG()` is a concrete
explanation consistent with the observed crash, but not a proven root cause
without the fault trace or a corrected successful trial.

Both scripts now use `printf '%s'`. The installed radio setup script was backed
up, hash-checked and updated atomically by `fix-firmware-path.py`; `/system`
returned read-only. The live firmware path was corrected to `/firmware/image`
without restarting services. Both SIMs/voice registrations passed verification.
No GPU device was opened, no kernel/boot/recovery was changed, and no new
unguarded EGL test was performed during this analysis. A future GPU trial must
verify the exact parameter bytes and capture live kernel output first.

## Next evidence needed

Check the phone's actual state and reconnect ADB. Compare boot ID/uptime and
inspect pstore/last-kmsg, kernel logs and tombstones before another GPU test.
If a new trial is justified, preserve stage output in a private persistent log.
Even successful pbuffer readback will leave Android gralloc/native-buffer
sharing, app linker namespaces, permissions and display composition untested.
The existing graphics bridge uses ashmem buffers; stock GPU support cannot be
assumed compatible with those handles.
