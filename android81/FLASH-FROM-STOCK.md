# KaiOS → Leo · clean installation and stock return

For the **Nokia 6300 4G TA-1286 development phone, serial `695d`**, using the
geometry verified in this project. Private packages include proprietary runtime
from this device's backups. **Native Leo Recovery clean installation passed on
28 October 2026:** system, new userdata and boot were written and fully checked
by SHA-256. Full KaiOS return and writes through the Python installer below
remain untested.

## What “from scratch” means here

The starting point is working KaiOS **with root and ADB already obtained**.
This procedure does not obtain root on a locked stock phone, unlock a bootloader
or repair a phone unable to enter recovery. Files are verified for this device;
the installers refuse another serial.

The Leo ZIP supports a PC Python installer through root recovery and the native
approved-package menu in Leo Recovery. It is not signed for stock “Apply update”.
Boot/system plus new userdata provide the apps/services integrated in system;
initial settings are generated at first boot. Personal data is not included.
The published ZIP needs the [post-install fix](#post-install-fix-found-during-testing).

## Prepared files

Under Git-ignored `android81/output/`:

| File / directory | Purpose |
| --- | --- |
| `recovery-bundles/final/leo-fresh-0.14.0-dev-20261027-232615.zip` | Boot, adapted system and new blank ext4 userdata |
| `recovery-entry-kit/leo-root-recovery-695d.zip` | Root ADB recovery and entry installer from KaiOS |
| `recovery-entry-kit/kaios-original-restore-695d.zip` | Original KaiOS boot/system for stock return |
| `recovery-entry-kit/stock-backups/` | Original boot/system/recovery, SHA-256 verified |
| `recovery-entry-kit/checksums.json` | Entry/restore package hashes |

The entry kit's recovery matches `recovery-adbshell.img` saved under
`Desktop/good 6300 files`, SHA-256
`2e4dee03305c5b617d5b0d51c064af350c817f3dc93f669ec302bdd97adf6fdc`.
An older SD diagnostic recovery image can start Android; that does not meet the
installer's recovery requirements. A real `recovery` process, root ADB and
unmounted targets are required.

## 1. PC backups and checks

Keep originals and personal-data backups **off the phone**. Clean installation
erases internal contacts, messages, accounts, installed user apps and files.
External SD is staging storage: leave at least **3.3 GiB free** and do not format
it. PC temporary storage needs equivalent space.

`backup-kaios-internal.py` can read boot/system through ADB sync while the phone
is still rooted stock KaiOS. Running it on Leo does not produce a KaiOS backup.
The prepared kit retains this phone's already-verified originals.

Run in PowerShell from the repository root:

```powershell
$adbLeo = 'D:/ROMS/platform-tools/adb.exe'
$freshLeo = 'android81/output/recovery-bundles/final/leo-fresh-0.14.0-dev-20261027-232615.zip'
$stockLeo = 'android81/output/recovery-entry-kit/stock-backups'
python android81/install-recovery-bundle.py $freshLeo
```

This is local verification only: ZIP layout, entry hashes/sizes and boot/ext4
headers. Installation additionally requires the three exact originals in
`$stockLeo`.

## 2. Enter root recovery from KaiOS

Extract `leo-root-recovery-695d.zip` on the PC. `CALE` in the examples means the
path to that extracted image. Check it:

```powershell
python android81/install-root-recovery.py CALE/recovery-adbshell.img
python android81/install-root-recovery.py CALE/recovery-adbshell.img --preflight --serial 695d --adb $adbLeo
```

Preflight checks root, serial/geometry and stock boot/system hashes. It refuses
an already-running Leo port. After those checks, **the next command writes only
recovery**, backing it up to the PC and verifying before/after writing:

```powershell
python android81/install-root-recovery.py CALE/recovery-adbshell.img --install --serial 695d --confirm-target 695d --adb $adbLeo
& $adbLeo -s 695d reboot recovery
```

The installer does not reboot or automatically restore original recovery.
Reuse the correct installed root recovery rather than rewriting it unnecessarily.
Confirm `uid=0` through ADB and a running `recovery` process.

## 3. Prepare SD in recovery

System/userdata must be unmounted in both recovery and PID 1 namespaces. Mount
the external card separately from `mmcblk1p1` (`179:65`). Inspect first:

```powershell
& $adbLeo -s 695d shell '/system/bin/busybox cat /proc/1/mountinfo'
& $adbLeo -s 695d shell '/system/bin/busybox cat /sys/class/block/mmcblk1p1/uevent'
```

If recovery mounted system/data, unmount them through its menu or `busybox umount`
and recheck both mountinfo lists. Keep the staging card mounted. For an already
identified FAT32 card, `busybox mkdir -p /sdcard` and
`busybox mount -t vfat /dev/block/mmcblk1p1 /sdcard` can mount it if needed.
Inspect differing nodes/filesystems rather than formatting the card for the test.

## 4. Preflight and clean installation

```powershell
python android81/install-recovery-bundle.py $freshLeo --preflight --serial 695d --adb $adbLeo
```

Preflight writes no partitions. It refuses active Android, missing recovery,
wrong geometry or mounted system/userdata. It may create a temporary helper and
block aliases under `/tmp` and `/dev`.

**The following command erases all internal user data. This Python write path
has not been executed; the hardware test used the native recovery menu:**

```powershell
python android81/install-recovery-bundle.py $freshLeo --install --serial 695d --confirm-target 695d --wipe-data --stock-backups $stockLeo --staging-parent /sdcard --adb $adbLeo
```

Order: check originals → stage all images on SD → verify staging SHA-256 →
recheck mounts → system → blank userdata → **boot last**. Each target is read
back completely and verified. Errors stop the process; retain recovery/staging
for diagnosis. Multi-partition installation is not atomic and never auto-reboots.

Modem, modemst/NV, persist, GPT and recovery are not targets. Radio identity and
calibration remain on the phone. After complete verification:

```powershell
& $adbLeo -s 695d reboot
```

First boot may take longer for ART compilation. The native test confirmed boot
completion, default Leo Home/IME/dialer, permissions/new-UID root authorization,
audio card and both SIMs registered. Audio listening, FM and LTE/IMS were not
repeated after this installation. Select the data SIM and configure network
preferences after the wipe.

## 5. Return to KaiOS

Enter root recovery with no active Android or mounted system, then verify and
install the original package:

```powershell
$restoreLeo = 'android81/output/recovery-entry-kit/kaios-original-restore-695d.zip'
python android81/install-recovery-bundle.py $restoreLeo
python android81/install-recovery-bundle.py $restoreLeo --preflight --serial 695d --adb $adbLeo
python android81/install-recovery-bundle.py $restoreLeo --install --serial 695d --confirm-target 695d --staging-parent /sdcard --adb $adbLeo
```

This restores original system/boot while retaining recovery access.
**Run “wipe data/factory reset” through stock KaiOS recovery tools before normal
stock startup.** Android data is not KaiOS data. Restore personal content from
its separate backup. Original `recovery-device.img` stays on the PC; restoring
that partition is separate, after KaiOS boot has been confirmed.
Full stock return through this kit remains untested.

## Rebuild on the development PC

Requires the verified private snapshot, WSL `kaios`, AOSP/SDK/JDK/compilers,
`debugfs`, `e2fsck`, `make_ext4fs` and static ARM BusyBox. Git sources alone do
not replace private blobs. `ALTA_CONSTRUCTIE` below means a different output/build
folder.

```powershell
wsl -d kaios -- bash /mnt/d/ROMS/nokia-6300-ta1286-dump/android81/build-terminal-root.sh
wsl -d kaios -- bash /mnt/d/ROMS/nokia-6300-ta1286-dump/android81/build-fm-diagnostic.sh
wsl -d kaios -- bash /mnt/d/ROMS/nokia-6300-ta1286-dump/android81/build-visual-shell.sh
python android81/build-recovery-bundle.py android81/output/rom-snapshots/20261027-232615 --fresh --output android81/output/recovery-bundles/ALTA_CONSTRUCTIE
python android81/build-recovery-entry-kit.py
```

Existing archives are not overwritten. The final command needs an unpackaged
kit output or old archives preserved elsewhere. Builders do not access the phone.
The builder repairs only bitmap padding in the blank userdata image generated
by the old formatter, then requires clean `e2fsck -f -n` for both filesystems.

## `/data` → `system` audit

| Required component | Prepared ROM location |
| --- | --- |
| Leo Visual and updated system apps | Factory `system/app` and `system/priv-app` |
| Leo keyboard, previously user-installed | `system/app/LeoKeypad/LeoKeypad.apk` |
| FM tools, Qualcomm utility, QMI library, trace and audio.jar | `system/vendor/leo-fm/` |
| Root broker / client | `system/bin/leo-terminal-root`, `system/bin/leo-su` |
| Home, IME, permissions and root authorization bootstrap | `system/etc/leo-first-setup.sh` |
| One-time initial audio arming | `system/etc/leo-clean-audio-seed.sh`, before preload |
| Required hardware properties | Explicit `system/build.prop` defaults, later user overrides |

Generated UID authorization, first-boot markers, FM locks/logs, ART/cache and user
data stay under data. Accounts, passwords, messages, databases and calibration
are not moved into system. Test/instrumentation APKs are excluded. The manifest
audits every added file/hash. The clean ROM's Leo APK was rebuilt for the system
FM location and differs from the earlier pre-clean runtime pair.

## Package verification results

Clean archive: **351,257,972 bytes** (about 335 MiB), SHA-256:

```text
5f65e7e7b0c8ce0ce8b24cf5ff9617e3d078a2a76172c4f4df7628063bdd9478
```

- Initial preparation passed 29 host tests covering archives, targets, wipe
  confirmation, backups, boot-last ordering and rejection of non-stock bootstrap.
- `e2fsck -f -n` returned 0 for system/userdata; blank userdata contains only
  `lost+found`, with no copied personal data.
- Fifteen runtime/config files were integrated and checked by extraction/hash,
  in addition to the active snapshot APKs.
- Live preflight on running Android returned `Active Android detected`, retaining
  boot ID without partition writes or reboot.
- Native installation later wrote and verified system → userdata → boot.
- Stock return still needs validation.

The candidate manifest records its base commit, `working_tree_dirty=true` and
tool hashes. It is not represented as a bit-for-bit rebuild from a clean checkout.

## Native recovery installation test

Before erasing data on 695d, existing userdata was backed up to SD and PC; both
copies matched the unmounted partition hash. **Install Leo → Install package →
Confirm installation** checked stock originals/archive, extracted all images,
then wrote and read back system/userdata/boot. The log confirms all three hashes.
Recovery, radio/NV, persist and card content were not install targets.

An initial attempt refused writes because the stock recovery SELinux domain
could not inspect PID 1 mounts/create block aliases. The corrected image keeps
global SELinux enforcing and relaxes only recovery in the exact pinned policy;
target checks were not bypassed.

![Native installation complete, all images verified](screenshots/leo-recovery-install-complete.png)

First boot generated `audio-v1`/`ui-v1`, Home/dialer/IME and permission setup.
Both SIMs voice-registered; default data subscription remained `-1`, preferred
network modes `0/0`. Data/LTE setup and IMS validation remain separate. ADB
returned after USB was disconnected for ten seconds and reconnected.

<a id="corecție-post-instalare-identificată-în-probă"></a>

## Post-install fix found during testing

The approved ZIP contains the original bootstrap: after wipe it did not enable
Leo accessibility, and Launcher3/old Leo Shell reappeared from system.
The phone and `leo-first-setup.sh` were corrected with `access-v2`: global
menu/pointer, WRITE_SETTINGS, notifications/DND and preserved third-party
accessibility services. Legacy launchers are disabled only after Home is set to
Leo. Global menu over Android Settings and idempotent re-execution passed.

**The hash-pinned ZIP has not been silently replaced.** Reinstalling that exact
old ZIP still requires the fixed bootstrap. The next ROM build will include it
and need a rebuilt recovery catalog; it has not already passed a flash test.

With internal Leo running and root ADB, preserve the old setup script on PC,
then apply the corrected source:

```powershell
& D:/ROMS/platform-tools/adb.exe -s 695d push android81/leo-first-setup.sh /tmp/leo-first-setup-v2.sh
python android81/framework-command.py --timeout 20 /system/bin/sh -c 'set -e; mount -o remount,rw /system; trap "mount -o remount,ro /system" EXIT; cp /tmp/leo-first-setup-v2.sh /system/etc/leo-first-setup.sh.new; chown 0:0 /system/etc/leo-first-setup.sh.new; chmod 644 /system/etc/leo-first-setup.sh.new; restorecon /system/etc/leo-first-setup.sh.new; mv /system/etc/leo-first-setup.sh.new /system/etc/leo-first-setup.sh; restorecon /system/etc/leo-first-setup.sh; mount -o remount,ro /system; trap - EXIT; sh /system/etc/leo-first-setup.sh'
```

At the user's request, the old Leo Shell was subsequently **removed from
`/system/app/LeoShell` and uninstalled for user 0**, after PC APK backup/hash
verification. Launcher3 stays in system, disabled. The clean builder now removes
only the exact known legacy APK; an unknown hash stops the build. This does not
rewrite the already-published ZIP.

![Global menu over Android Settings after the fix](screenshots/leo-fresh-install-global-menu.png)
