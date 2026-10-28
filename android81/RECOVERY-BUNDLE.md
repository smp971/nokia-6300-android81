# Private OS snapshots and recovery ZIPs

For the current clean-install procedure, start with
[Install Leo and return to stock](FLASH-FROM-STOCK.md). The prepared 0.14 clean
package uses schema 2 (`--fresh`): blank userdata plus apps/runtime integrated
into system. It requires `--wipe-data`, verified stock originals and boot-last
ordering. Native Leo Recovery installation has since passed on hardware;
Python-installer writes and full stock restoration remain untested.

Schema 1 below preserves data and represents an update snapshot. It does not
guarantee a boot after data formatting. The recorded 0.12 build and preflight
results below are historical, not new flash validation.

Installation requires root recovery with a real `recovery` process. An SD
Android diagnostic image is unsuitable even when stored in the recovery
partition. The separate entry kit provides root ADB recovery and a bootstrap
from known rooted KaiOS; it is not a signed stock “Apply update” OTA.

## Source snapshot

With Leo fully booted, no active call, serial `695d` and read-only system:

```powershell
python -X utf8 android81/snapshot-leo-rom.py
```

The Git-ignored `android81/output/rom-snapshots/` receives boot (32 MiB), system
(800 MiB), recovery (32 MiB, backup only), manifest and updated system-app APKs.
It excludes userdata, accounts, SMS and radio partitions. Sizes and SHA-256 are
compared with the phone. Transfer uses ADB sync on block aliases because the
old adbd does not support binary `exec-out` transport.

Keep the original KaiOS backup separately. A Leo snapshot is not a new stock
firmware backup.

## Offline build

Requires Python, WSL `kaios`, `debugfs`/`e2fsck` and the existing static ARM helper
`output/recovery-shell-candidate`. It does not download firmware.

```powershell
python -X utf8 android81/build-recovery-bundle.py android81/output/rom-snapshots/20261027-164004
```

Boot/system are copied into a new directory. Only the system copy is modified:
active APKs are overlaid at factory locations with permissions/SELinux labels,
then extracted and checked again. `e2fsck -f -n` and ZIP verification must pass.
Recovery is excluded from the ROM ZIP. Framework and ART come from the same
source image; JARs are not replaced independently.

The build manifest records hashes, commit/dirty state, tools and
`hardware_flash_validated=false`. A successful build validates the archive,
not a flashed phone. Clean-host and bit-for-bit reproducibility are unvalidated.

## Verify and explicitly install a schema 1 update

Extract only the PC script/README; images stay in the ZIP. `CALE_PACHET.zip`
is a placeholder for the bundle path.

```powershell
python android81/install-recovery-bundle.py CALE_PACHET.zip
python android81/install-recovery-bundle.py CALE_PACHET.zip --preflight --serial 695d --adb D:/ROMS/platform-tools/adb.exe
```

The first command is offline. The second requires root recovery, no running
Android or mounted system, matching device identity and boot/system geometry.
Running Android must be refused without partition writes.

Installation needs an already mounted external SD card, at least 864 MiB free
and equivalent PC temporary space. `--staging-parent /sdcard` must resolve onto
the real `mmcblk1p1` card.

```powershell
python android81/install-recovery-bundle.py CALE_PACHET.zip --install --serial 695d --confirm-target 695d --adb D:/ROMS/platform-tools/adb.exe
```

This overwrites boot/system. It stages both images on SD, checks hashes and
recovery state, writes system then boot and verifies each written image.
Userdata is retained; recovery/modem/NV are not targets. There is no automatic
reboot. Staging remains on SD. Maintain power and keep backups off the phone.

Archives contain proprietary device runtime and remain private; published
release assets are listed in [the package guide](GITHUB-PACKAGES.md).

## Historical 0.12 snapshot

`output/recovery-bundles/leo-recovery-0.12.0-dev-20261027-164004.zip` passed offline
build, APK readback and `e2fsck -f -n`. SHA-256:

```text
3c66dad2f2d1eba59903c053271b0d1366a74d0ebf3befe6cf1779cc0537c707
```

Its manifest records `working_tree_dirty=true` and exact tool hashes; it is not
presented as a build from a clean commit. A later preflight on running Android
returned `Active Android detected` and refused writes. That Python install path
was not hardware-validated. The later native clean-install result is documented
in [Leo Recovery](LEO-RECOVERY.md), not inferred from this older snapshot.
