# Leo Recovery through QPST / QFIL · experimental candidate

Private kit for **Nokia 6300 4G TA-1286 / ADB 695d**, with the original GPT intact.
It can reinstall recovery when Android no longer boots. It does not repair GPT
or the bootloader.

**Offline-checked; QFIL flashing and loader acceptance in EDL are untested.**
The included Leo Recovery image booted on the phone and installed the ROM from
SD. That result does not validate Sahara/Firehose transport or QFIL itself.

## Contents and target

| File | Purpose |
| --- | --- |
| `8k.mbn` | Pinned local ARM32 MSM8909 loader; EDL acceptance unconfirmed |
| `leo-recovery.img` | Leo Recovery 0.2, 33,554,432 bytes |
| `rawprogram0.xml` | One write: recovery, p33, LBA 2488416, 65536 sectors of 512 bytes |
| `patch0.xml` | Empty; no GPT changes |
| `manifest.json`, `SHA256SUMS.txt` | Provenance, geometry, sizes and hashes |

No GPT, bootchain, modem, NV, persist, system, boot or userdata write targets are
included. The kit itself does not wipe data. QFIL does not automatically check
ADB serial 695d: confirm the phone variant and geometry before using it.

The loader matches the SHA-256 of the local `nokia-6300-qfil-safe` copy. That
older kit's notes mention defective reads and unstable sessions, not proof of
successful QFIL flashing. XML does not request automatic readback through this
loader. After programming, separately verify the recovery partition hash through
root ADB. Investigate write errors rather than ignoring them.

## Procedure for a future QFIL validation

1. Extract into a separate folder and verify SHA256SUMS.
2. The phone must expose **Qualcomm 9008 / EDL**. A 900E crash interface does not
   establish readiness for programming. Use the Qualcomm driver appropriate for
   QFIL; the phone's normal Leo-mode driver does not need changing.
3. Select Flat Build, eMMC storage and the included `8k.mbn`. Load only this
   kit's `rawprogram0.xml` and `patch0.xml`.
   **Erase All Before Download must be disabled.**
4. Review the target: **recovery only**. If the loader is rejected or does not
   respond, preserve the log. Possession of the file does not demonstrate
   compatibility; do not substitute a random other-chipset loader.
5. After writing and verification, boot Leo Recovery with the physical key
   combination already used on this development phone. Installing the approved
   Leo ROM from SD is a separate operation that erases data and requires backup
   and confirmation.

The kit does not restore modem/NV. GPT, bootchain or hardware failure requires
separate diagnosis; recovery from every kind of brick is not promised.

## Rebuild on the PC, without a phone

```powershell
python android81/build-qfil-recovery.py
```

The builder verifies GPT CRCs, the four known OS partition locations, the loader
hash and the approved recovery hash. XML contains only recovery and patches are
empty. Existing ZIP output is not overwritten.

The general Flat Build / Browse / Load XML interface is also documented by
[Quectel](https://developer.quectel.com/doc/sbc/Quectel-Pi-H1/en/OS/Debian/system_flash.html).
That page concerns a different UFS board. This kit's eMMC settings and recovery
target come from the verified Nokia GPT, not that board's images.
