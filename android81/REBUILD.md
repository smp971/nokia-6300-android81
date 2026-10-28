# Rebuilding Leo Android 8.1

This repository contains Leo app sources, device configuration, native tools and
porting scripts. `repro/aosp-manifest.xml` pins the development checkout's 590
upstream projects to commit SHAs. `repro/sources.json` and its patches preserve
local AOSP changes. Android sources are fetched from Android Git rather than
being duplicated as an archive here.

**Scope:** a full rebuild from scratch on a clean PC has not been validated.
The running port requires proprietary components from the original dump. Clean
installation of the prepared private ROM has since passed on hardware; see
[installation and its required fix](FLASH-FROM-STOCK.md). That does not validate
arbitrary newly built images. The `repro/` commands do not use ADB or flash.

## Building just the Leo apps

The [versioned app-bundle generator](RELEASE-APPS.md) provides preflight checks,
two APKs, a SHA-256 manifest and an explicit installer. App build scripts accept
`LEO_AOSP_ROOT` and `LEO_JAVA_HOME`. This does not replace AOSP tree preparation
or build a complete ROM.

## Development environment

Recorded builds use x86-64 WSL/Linux, Ubuntu 22.04.5, Git 2.34.1, OpenJDK 8
(1.8.0_504), Python 2.7.18 for old Android tools and Python 3.10.12 for scripts.
Some newer Windows packaging tools use Python 3.11. Do not substitute Java 17
for Java 8 in this tree. Other dependencies include repo, make, GCC/G++, multilib,
rsync, zip/unzip, curl, bison, flex, gperf, bc and AOSP 8.1 host libraries.
The full checkout and intermediates are much larger than this repository.

Existing scripts deliberately default to `/root/android81-leo/src` and
`/usr/lib/jvm/java-8-openjdk-amd64`; use those paths for the first rebuild.
`stage-device.sh` locates device sources relative to this repository. Some
historical installation/diagnostic tools still use development-PC-specific
Windows and WSL paths.

## Fetching sources

Clone the private repository using your GitHub authentication. In WSL/Linux:

```bash
git clone https://github.com/smp971/nokia-6300-android81.git
cd nokia-6300-android81
bash android81/repro/checkout.sh
```

The checkout uses the manifest from your local clone. AOSP remotes are absolute
Android Git URLs. An existing checkout is not automatically resynced or reset.
Check and apply local changes against the exact base:

```bash
python3 android81/repro/apply.py --check-only
python3 android81/repro/apply.py
```

The script checks revisions/hashes, refuses conflicts with local edits and
recognizes patches already applied. `device/nokia/leo` is staged separately from
the canonical sources here.

## Private device inputs

`device/nokia/leo/prebuilt/kernel` is not tracked. Supply the correct TA-1286
kernel from your own boot backup or a rebuilt, validated kernel. An empty
placeholder is not a substitute.

Hardware needs original KaiOS libraries, firmware and modules extracted into
the local project layout. Qualcomm driver source is not available here.
Modem/EFS partitions, user data, private ADB keys and credentials are excluded.
Optional ADB setup uses only your **public** key in
`device/nokia/leo/local/adb_keys`.

`fetch-leo-kernel.sh` references GPL kernel source from `bmndc/nokia-leo`, commit
`949a9f7b2c109f307e72b25fec2dc5cb4d119dec`, directory `kernel/msm-4.9`.
Stock configuration is under `device/nokia/leo/reference-stock/`.
`build-kernel-uhid.sh` documents UHID compilation and the public certificate
extracted by local tools. This does not automatically produce an installable
boot.img.

## Leo applications

With the host and local kernel prepared:

```bash
bash android81/repro/build-apps.sh
```

This prepares host tools and the vCard library, then builds:

- `android81/output/leo-visual.apk`: Home and Leo apps;
- `android81/output/leo-keypad-ime.apk`: physical multi-tap/T9 keyboard.

With AOSP already built, UI changes normally need only:

```bash
bash android81/build-visual-shell.sh
```

The recipe ran successfully in the existing development checkout and rebuilt
both APKs. Separately, ten patches were applied to original upstream files;
all 25 modified files matched the development sources. These results do not
replace validation on a clean host.

`build-visual-shell.sh` automatically includes all Java files in `visual-shell/`.
Signing uses AOSP development keys from the checkout, not the user's private
keys. Keep the signer when updating an installed app while retaining its data.

## Other components

| Component | Recipe |
| --- | --- |
| AOSP Email resources and keypad controls | `build-email-resources.sh` |
| Android keypad in-call interface | `build-call-keypad.sh` |
| ADB renewal during USB mode changes | `build-usb-adbd-renew.sh` |
| Keypad backlight | `build-keylight.sh` |
| Leo WMA decoder | `build-wma-decoder.sh` |
| UHID kernel | `fetch-leo-kernel.sh`, `build-kernel-uhid.sh` |
| Experimental AOSP system | `build-framework-diagnostic.sh` |

`build-six-feature-tests.sh` separately builds device tests, including
UpgradeTests for reordering, shortcuts, T9 and notification/lock behavior.
Production APKs exclude test fixtures and entry points. Instrumentation starts
and stops Leo; verify notification/accessibility reconnection afterwards.
Tests use synthetic contacts/notifications and restore local preferences.

The WMA recipe fetches FFmpeg 4.4.8 and checks its pinned SHA-256. Configuration
and LGPL notice are recorded in [the historical media report](MEDIA-RUNTIME.md)
(Romanian). A hidden WMA binary package is not required.

Full builds also need the old Jack server. `environment.sh` and
`configure-jack-framework.py` describe heap/concurrency; generate local Jack
certificates on the new host. The optional local TLS compatibility file is not
provided as a global security change.

System assembly, hardware ABI checks and installation remain separate steps.
Flashing is not a default build step. Historical reports include abandoned
experiments; start with the current [project overview](../README.md).
