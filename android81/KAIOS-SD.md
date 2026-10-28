# KaiOS on SD · Leo in internal storage

Leo Recovery 0.2 includes an early selector and **KaiOS from SD**.
Images were copied to the phone's card and verified. **The first boot reached
the KaiOS menu, confirmed by the user.** Integration remains experimental:
KaiOS services were not exhaustively retested. Root ADB works, but disconnected
after boot and needed a physical USB reconnect.

## Boot architecture

Normal startup still uses the unchanged internal Leo boot partition.
In recovery: **KaiOS from SD → Verify SD images / Boot KaiOS once**.
After confirmation, recovery creates an SD request and reboots into recovery.

Before recovery init, the selector runs as PID 1:

1. Verify the development Nokia and mount the real external card, `179:65`.
2. Consume `LeoKaiOS/boot-once` before attempting boot.
3. Check the marker, sizes and ext4 signatures; recalculate the complete original
   KaiOS system image SHA-256.
4. Attach system/data/cache to loop0/1/2, with a read-only system loop.
5. Make internal boot/system/cache/userdata block devices read-only for this
   boot. Protection resets with the next kernel restart.
6. Move the card mount to `/leo-sd` within the embedded KaiOS root, then execute
   stock init while retaining PID 1. A second init is not launched as an app.

Protected internal devices are boot `179:29`, system `179:30`, cache `259:0`
and userdata `259:7`. Each identity is compared with sysfs before ioctl.

Without a valid request/card, or on failure before KaiOS init is executed, the
selector falls back to recovery. Original recovery init is restored **in RAM**
before execution; its partition image is unchanged. Once KaiOS init takes over,
service/UI failures are no longer selector preparation failures and may require
a reboot.

The recovery kernel is identical to original KaiOS:
`f860d012480be69e03bf57200cef9fbb5d5d2f1dd46e947e1989cd5dc44955fa`.
This kernel has kexec disabled; that mechanism is not used.

## Card layout

| File under `LeoKaiOS/` | Contents |
| --- | --- |
| `system.img` | Original KaiOS system, 800 MiB, verified and read-only |
| `data.img` | Initially empty 512 MiB ext4 image; persistent KaiOS user data |
| `cache.img` | 64 MiB ext4 |
| `owner` | Exact development-device marker |
| `boot-once` | Temporary request, consumed before boot |
| `selector-last.log` | Loop/protection preparation result |

Images need **1,376 MiB**, plus normal working space. Keep the card inserted
while KaiOS runs. System remains unchanged; writable data/cache naturally stop
matching their original hashes after use.

`prepare-kaios-sd.py` prepares images on the PC. `deploy-kaios-sd.py --install
--confirm-target 695d` transfers them compressed in recovery, checks each file
and refuses to overwrite an existing `LeoKaiOS` folder. It neither formats the
card nor writes internal partitions, and does not create a boot request itself.

## Mount isolation

The KaiOS root comes from the verified stock boot image and is embedded in
recovery. The builder redirects system/userdata/cache mounts, including direct
init and charger-mode mounts. Automatic formatting/footer encryption are removed
from fstab; the card is removed from vold-managed volumes. The OTA daemon does
not autostart. Initial configuration requests ADB.

Diagnostic ADB retains the `adbd` service domain and adds only `adbd`/`shell` to
the verified stock policy's permissive bitmap. IDs were read with libsepol from
this exact dump. Vendor AV rules/other domains are preserved; global SELinux is
not disabled. The phone test confirmed UID 0 and global enforcing. Removing the
`su` argument alone had been insufficient.

Modem, SIMs, firmware, persist and NV are shared hardware/services: **radio is
not fully isolated**. Stock KaiOS can change their configuration/state. Personal
data is not copied from Leo.

## Evidence and limits

- Full image transfer and SHA-256 verification passed on the card.
- Native image verification passed without loop attachment.
- Recovery 0.2 booted with working ADB; SD boot/system/recovery backup passed
  full readback hash checks.
- An initial request was skipped because init had additional arguments; selection
  was fixed to use PID 1.
- Loop attachment, three ext4 mounts, SD mount move and read-only guards passed
  a phone probe that restored the guards afterwards.
- KaiOS menu was visually confirmed. The recorded diagnostic build exposed
  Qualcomm USB 9091, root ADB, `b2g=running` and `sys.boot_completed=1`.
- A live mount table confirmed SD-backed loop0/1/2: read-only system, writable
  data/cache. All four protected internal blocks reported read-only.
- Normal reboot returned to Leo, verified visually and through the internal
  Android marker; read-only flags reset.
- ADB dropped after startup. Reconnecting USB restored it without reboot;
  USB stability and KaiOS radio/media functions still need validation.
- Automatic KaiOS screenshot capture failed: screencap found no display and
  direct framebuffer reads were refused. Existing recovery captures are from
  recovery tests, not KaiOS.

Exact image provenance is recorded in `leo-recovery/sd-boot-validation.json`.
On fallback, selector diagnostics are in `/leo-selector.log`.
The recovery-root-only `leo-boot-selector --probe-loops` attaches loops, checks
ext4 mounts and SD movement, then unmounts and restores guards. Mounting writable
data/cache can change their metadata; system/internal filesystems are not written.
See [Leo Recovery](LEO-RECOVERY.md) for the complete recovery procedure.
