# Private packages · 28 October 2026

[Download from GitHub Releases](https://github.com/smp971/nokia-6300-android81/releases/tag/leo-0.14.0-dev-20261028).
This is an **experimental prerelease for Nokia 6300 4G TA-1286 / device 695d**.
The repository and packages remain private. It is not a universal Nokia ROM.

| Asset | Purpose and validation |
| --- | --- |
| `leo-fresh-0.14.0-dev-20261027-232615.zip` | Clean installation through Leo Recovery; system/userdata/boot written and verified, first boot confirmed. Erases internal data. Requires the fix below. |
| `leo-recovery-0.2-private.zip` | Leo Recovery image, ADB installer and documentation. Boot, keys and native SD installation validated. |
| `leo-root-recovery-695d.zip` | Entry from known rooted KaiOS through ADB; recovery-adbshell and installer. Not a signed OTA ZIP. |
| `kaios-original-restore-695d.zip` | Original boot/system for stock return. Hashes verified; complete restoration through the kit remains untested. |
| `leo-qfil-recovery-695d-20261028.zip` | QPST/QFIL candidate: local loader and recovery partition only. Offline checks passed; EDL flashing untested. |
| `leo-first-setup.sh` | Post-install bootstrap fix for accessibility, global menu/pointer, WRITE_SETTINGS and legacy launchers. |
| `SHA256SUMS.txt` | Hashes for the package/text assets; compare before use. |

## Installing Leo and the required fix

The release ROM is **the exact ZIP that was tested**, SHA-256:

```text
5f65e7e7b0c8ce0ce8b24cf5ff9617e3d078a2a76172c4f4df7628063bdd9478
```

The clean-install test exposed missing special access and factory launchers
reappearing after a wipe. These were fixed on the phone. After installing this
ZIP, apply `leo-first-setup.sh` using the
[post-install procedure](FLASH-FROM-STOCK.md#post-install-fix-found-during-testing).
It selects Leo Visual and disables Launcher3/the old Leo Shell.
The old shell APK is still present in that ZIP; its subsequent physical removal
on the phone and in the next builder is documented separately. The tested
archive has not been replaced by an untested rebuild.

Initial setup must select a data SIM and restore network/Wi-Fi preferences.
LTE/IMS after the wipe have not been revalidated. USB/ADB may need a ten-second
physical disconnect/reconnect.

[Recovery installation, backup and limits](LEO-RECOVERY.md) ·
[Rooted KaiOS entry and stock return](FLASH-FROM-STOCK.md).

## QPST / QFIL

The separate kit writes only p33 recovery, with the known eMMC geometry and
intact GPT. Its loader matches the local `nokia-6300-qfil-safe` folder; older
notes describe unstable sessions and read problems. QFIL flashing was not tested
in this stage. The kit does not repair GPT/bootchain or include modem/NV/persist.
See [the QFIL procedure and limitations](QFIL-RECOVERY.md).

## Verifying downloads

Compute SHA-256 and compare with `SHA256SUMS.txt` before extraction:

```powershell
Get-FileHash -Algorithm SHA256 .\leo-recovery-0.2-private.zip
```

Raw images/archives are Release attachments, separate from Git history.
Rebuild sources, builders and tests are available; private blobs and the WSL/AOSP
environment remain required. Existing binary ZIPs keep their original internal
documentation and provenance. Current instructions and the release description
are maintained in English; historical Romanian documents remain archived.
