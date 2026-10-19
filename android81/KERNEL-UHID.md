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

This candidate has not been packaged into a device boot image or run on the phone.
Mouse connection and input remain unverified.

These are offline prerequisite checks. They do not establish bootability or
working graphics, audio, modem, Wi-Fi or mouse input. No boot image is packaged
or flashed by these scripts. A later device trial must preserve the current
working boot image and recovery access, retain its exact ramdisk/header/device
tree requirements, and validate the existing hardware functions after boot.

Private build keys, original binaries, certificates and diagnostic artifacts
remain in local ignored output; commit only the scripts and documentation.
