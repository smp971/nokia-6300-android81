# Install Leo on the Nokia 6300 4G

This release targets the Nokia 6300 4G **TA-1286**, development handset `695d`.
Start with root ADB in KaiOS, an existing rooted Leo installation, or a working
root recovery. The firmware ZIPs are for Leo Recovery and its PC installer.
The original recovery's signed OTA menu uses a different package format.

[Private complete release](https://github.com/smp971/nokia-6300-leo-releases/releases/tag/leo-complete-20261116) ·
[Public source](https://github.com/smp971/nokia-6300-android81)

## Choose a package

| Asset | Purpose |
| --- | --- |
| `leo-fresh-0.14.0-dev-20261113-140016.zip` | Hardware-tested internal ROM. This exact ZIP is approved in the native SD installer. |
| `leo-fresh-0.14.0-dev-20261116-all-fixes.zip` | New internal candidate with the rebuilt Leo Visual APK and updated runtime scripts. Verified offline. Install through the PC recovery installer. |
| `leo-sd-storage-20261116.zip` | `LeoAndroid/` with the tested November System and separate empty Data and Cache. |
| `kaios-sd-storage-695d.zip` | `LeoKaiOS/` with original KaiOS System and separate empty Data and Cache. |
| `leo-recovery-sd-radio-20261116.zip` | Recovery with the SD selector, SD radio startup, partition tools and experimental USB MTP. Includes the PC recovery installer. |
| `leo-root-recovery-695d.zip` | Root ADB entry from rooted stock KaiOS. Needed only when the phone lacks root recovery. |
| `kaios-original-restore-695d.zip` | Restore original KaiOS boot and system. |
| `leo-stock-originals-695d.zip` | Verified original boot, system and recovery images required by the clean-install safeguards. |
| `leo-qfil-recovery-695d-20261028.zip` | Emergency QFIL candidate for the recovery partition. Verified offline. |
| `leo-visual-20261116.apk`, `leo-keypad-ime.apk` | Standalone signed app builds. |
| `SHA256SUMS.txt` | Compare each downloaded file before opening or flashing it. |

## 1. Prepare the PC and a backup

Install [Android Platform Tools](https://developer.android.com/tools/releases/platform-tools)
and Python 3. From PowerShell in your download directory:

```powershell
$adb = 'C:/platform-tools/adb.exe'
Get-Content .\SHA256SUMS.txt
Get-FileHash -Algorithm SHA256 .\leo-recovery-sd-radio-20261116.zip
& $adb devices -l
```

Extract the recovery ZIP into `recovery/` and the originals ZIP so you have
`stock-backups/`. Keep a second copy of your own personal files, messages and
other app data. The original partition kit excludes personal userdata. Charge
the phone and use a stable USB connection. For native internal installation,
leave at least **3.3 GiB free in addition to** the files you copy to microSD.
The Leo SD storage set itself occupies about **1.4 GiB** after extraction.

## 2. Install Leo Recovery

If you already have a working root recovery, enter it and go straight to the
second command block. For rooted stock KaiOS with ADB, first extract
`leo-root-recovery-695d.zip` into `entry/` and run:

```powershell
python .\recovery\install-root-recovery.py .\entry\recovery-adbshell.img --preflight --serial 695d --adb $adb
python .\recovery\install-root-recovery.py .\entry\recovery-adbshell.img --install --serial 695d --confirm-target 695d --adb $adb
& $adb -s 695d reboot recovery
```

From root recovery, install the release image. The installer backs up the
current recovery on the PC and verifies the complete readback of p33.

```powershell
python .\recovery\install-root-recovery.py .\recovery\leo-recovery-0.2.img --from-recovery --preflight --serial 695d --adb $adb
python .\recovery\install-root-recovery.py .\recovery\leo-recovery-0.2.img --from-recovery --install --serial 695d --confirm-target 695d --adb $adb --backup-dir .\recovery-backups
& $adb -s 695d reboot recovery
```

The same installer accepts `--leo` instead of `--from-recovery` from the known
internal Leo build. If a reboot command does not work from the running OS, use
the handset's established recovery key combination. Wait for **LEO RECOVERY**.

## 3. Run Leo or KaiOS from the card

Use a card reader to extract the complete storage ZIP into the root of the card.
Keep an existing `LeoAndroid/` or `LeoKaiOS/` directory if it already holds data
you want to retain. The card's root should contain the selected system's folder:

```text
LeoAndroid/               # Or LeoKaiOS/ if you extracted the KaiOS set
  owner
  system.img
  data.img
  cache.img
```

For Leo, you can also copy the approved November ROM ZIP into
`LeoRecovery/packages/` and choose **Boot and power → Boot from SD → Leo →
Prepare card**. This creates the same three images on the handset.

In Recovery choose **Boot and power → Boot from SD → Leo → Boot Leo once**.
Choose **KaiOS → Boot KaiOS once** for the KaiOS set. Recovery validates the
images and consumes the one-time request. Normal reboot starts the system
already installed internally. Each SD system keeps its own Data and Cache.

The tested Leo SD build reports baseband with both RIL services running. SIM 2
initially showed `READY`. Calls, SMS, mobile data and Bluetooth need separate
validation in this SD profile. The November launcher can show a call-log
permission error during the first boot of fresh Data. Allow setup to finish
and repeat **Boot Leo once** using the same folder to retain the saved grants.
The source-built APK includes a guard for that permission race.

## 4. Install Leo internally

**Both clean-install routes erase internal userdata.** Keep the verified
originals and your own backups on the PC. Use Recovery's **Mount partitions**
menu to unmount internal System and Data. Keep microSD mounted for native
staging. A card reader is the straightforward way to copy large assets.
Recovery's **Tools → USB MTP → microSD** is an experimental transfer option.
Select **Stop MTP** after Windows finishes copying to restore ADB.

### Native installer for the tested November ROM

Put the original files on the card at `LeoRecovery/stock-backups/`. Copy the
**unchanged** `leo-fresh-0.14.0-dev-20261113-140016.zip` to
`LeoRecovery/packages/`. Choose **Install → Install from SD → Leo → Check
archive**. When ready choose **Review install → Erase & install**. Wait for
the three verified writes, then choose **Boot and power → Boot internal OS**.

### PC installer for the new candidate

Use the `install-recovery-bundle.py` included in the release's recovery ZIP
or from the public source. Keep the originals on the PC and ensure root ADB
is connected to Leo Recovery:

```powershell
$rom = '.\leo-fresh-0.14.0-dev-20261116-all-fixes.zip'
python .\recovery\install-recovery-bundle.py $rom
python .\recovery\install-recovery-bundle.py $rom --preflight --serial 695d --adb $adb --stock-backups .\stock-backups
python .\recovery\install-recovery-bundle.py $rom --install --serial 695d --confirm-target 695d --adb $adb --wipe-data --stock-backups .\stock-backups
```

The installer writes System, empty Data and Boot in that order, with a full
readback after each. The candidate's clean flash has offline validation. After a
successful install, boot the internal OS and allow the first setup to finish.

## 5. Return to KaiOS or recover p33

For an internal stock return, copy `kaios-original-restore-695d.zip` to
`LeoRecovery/packages/` and keep the verified originals under
`LeoRecovery/stock-backups/`. Choose **Install → Install from SD → KaiOS**.
After verified boot/system restore, use **Tools → Factory reset** to clear
incompatible internal Data and Cache before booting KaiOS. That action erases
internal user data. Recovery remains available for the one-shot SD profiles.

For a handset already in Qualcomm EDL, see the [QFIL p33 procedure](QFIL-RECOVERY.md).
The published QFIL ZIP targets recovery only. It requires the matching eMMC
geometry and an intact GPT. Full EDL restore and flashing this kit on hardware
are outside its validation. The normal from-scratch path is root ADB above.
