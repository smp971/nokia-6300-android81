# UHID kernel candidate: offline preparation

The MX Master 3 Mac is bonded, but Android's HID host is disconnected. The live
4.9.240-perf-g4f9e270fd77f kernel has `CONFIG_UHID` disabled and no `/dev/uhid`.
Oreo's `system/bt/btif/co/bta_hh_co.cc` opens this device to deliver HID reports.
The existing `/dev/uinput` is not a drop-in replacement for that interface.

The live config also enables `CONFIG_MODULE_SIG_FORCE=y` and
`CONFIG_MODVERSIONS=y`. An independently signed UHID module cannot simply be
added to the original kernel. The candidate therefore builds UHID into the
kernel, with original module signature enforcement retained.

## Compatibility approach

Use the published Leo v20 source pinned at
`949a9f7b2c109f307e72b25fec2dc5cb4d119dec`, the extracted original config and
the existing Android GCC 4.8 ARM toolchain. Preserve the original release string
explicitly, rather than using the public source repository's different Git ID.
Matching release strings alone do not prove compatibility.

`prepare-uhid-certificates.py` recovers public X.509 certificates from the
decompressed original kernel into ignored local output. It does not recover a
private signing key. Offline OpenSSL CMS verification passes for all 16 nonempty
original module files with these public certificates, including WLAN and audio.
The candidate embeds those original certificates alongside its newly generated
local build certificate. This lets it retain trust for original signed modules;
newly signed modules would still not load into the original kernel.

The normalized candidate config changes only:

- `CONFIG_UHID`: disabled to built in.
- `CONFIG_LOCALVERSION_AUTO`: disabled; the original release suffix is supplied
  explicitly as `LOCALVERSION`.
- `CONFIG_SYSTEM_TRUSTED_KEYS`: recovered original public certificates.

## Reproduce without touching a device

1. Fetch sources with `fetch-leo-kernel.sh` if not already present.
2. Run `python android81/prepare-uhid-certificates.py` on the host (cryptography).
3. Run `python android81/verify-kernel-uhid.py --signatures-only`
   (pyelftools, cryptography, WSL kaios/OpenSSL).
4. Run `build-kernel-uhid.sh` in WSL. The separate output tree is
   `/root/android81-leo/kernel-uhid-build`; binaries/config/reports are copied to
   ignored `android81/output/kernel-uhid/`.
5. After successful compilation, run `python android81/verify-kernel-uhid.py`.
   This compares every original module's imported symbol CRCs with the rebuilt
   `Module.symvers`, verifies the original module signatures, checks the release
   string, and checks certificates/UHID/enforcement in the candidate image.
   Imports supplied by other retained original modules are checked against their
   real exported CRCs; audio drivers depend on each other, not only on the kernel.

## Result: 19 October 2026

The full offline `zImage`, modules and device-tree build completed with GCC 4.8.
All 16 original modules passed signature and imported-symbol CRC checks against
the new kernel plus retained original module providers. The compressed candidate
contains both recovered original certificates, the exact expected release,
built-in UHID and enforced module signatures. The embedded configuration has
only the three intended differences listed above. Build artifacts and detailed
reports remain under ignored `android81/output/kernel-uhid/`.

## First device trial: 19 October 2026

`package-uhid-boot.py` combines the candidate zImage with all 38 appended original
DTBs, retaining the live boot image's exact compressed ramdisk, header addresses,
command line and partition size. It updates the kernel size and boot SHA-1 ID.
`flash-uhid-boot.py` verifies serial, partition identity/size, idle call state,
fresh boot backup, staged image and readback hashes. It also saves the current
recovery read-only and confirms its hash is unchanged. It never formats data or
writes recovery, and does not reboot automatically.

The authorized flash readback matched and Android booted the rebuilt kernel.
ADB and the Leo launcher ran; `/dev/uhid` appeared in Android's device namespace
as `uhid:uhid`, mode 0660. The original signed WLAN module loaded and WCNSS
reached ONLINE. Both SIMs reported READY and the first radio reported LTE;
Internet connectivity was not revalidated in this trial. Bluetooth reached ON.

A manually started audio regression trial loaded the initial Qualcomm audio
modules, then the device entered Qualcomm diagnostics 900E. The exact failure
point still requires the persisted audio trace/crash log after a battery power
cycle. The audio setup disarms its automatic loading before inserting modules;
the failed trial did not reach its success/rearm step. Do not rerun audio setup
until the failure is understood. Static module CRC/signature compatibility does
not establish runtime safety.

## Recovery and mouse result

After a battery power cycle, the rebuilt kernel and Leo launcher booted again.
Audio autoload remained 0, and no audio trial was repeated. The persisted trace
shows successful return from every module insertion, including the final
`audio_machine_int_msm8909`; the crash occurred after that point, before the
setup reached active. With `CONFIG_PSTORE` disabled and no last_kmsg, the final
panic could not be recovered after battery removal. Its root cause is unresolved.

Connectivity was started manually and Bluetooth enabled. The bonded MX Master
3 Mac connected with HID state 2 and created an input device under virtual UHID
(`046d:b023`, handlers keyboard/mouse/event). A screenshot showed the cursor.
An eight-second physical input capture recorded 231 relative-motion/wheel events,
including 21 wheel events. No button events were captured in that interval, so
individual click actions were not independently measured. The user confirmed
that the mouse works. `verify-uhid-runtime.py` checks the running kernel, device
permissions, UHID input registration and HID connection without changing state.

This establishes Bluetooth mouse operation with the rebuilt kernel. It does not
resolve the audio crash or establish automatic connectivity activation at boot.
A later read-only check in the same boot found HID disconnected and the input
device absent, while Bluetooth remained ON. Whether the mouse was switched to
another channel, slept, or lost its connection was not established; sustained
connection and reconnection behavior still need testing. The verification script
reports the current disconnected state rather than reusing earlier success.

These are offline prerequisite checks. They do not establish bootability or
working graphics, audio, modem, Wi-Fi or mouse input. No boot image is packaged
or flashed by these scripts. A later device trial must preserve the current
working boot image and recovery access, retain its exact ramdisk/header/device
tree requirements, and validate the existing hardware functions after boot.

Private build keys, original binaries, certificates and diagnostic artifacts
remain in local ignored output; commit only the scripts and documentation.
