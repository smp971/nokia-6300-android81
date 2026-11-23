# Keypad wake and unlock follow-up

This follows the [validated unplug trial](POWER-WAKE-J2ME.md#battery-trial-2026-11-23).
That trial established suspension; it did not establish that every physical key
could wake the suspended handset. The earlier 0.34-second figure was measured
with USB attached.

## Requested behavior

- The observer requests display wake for the GPIO **star** and the physical
  **left softkey**. Android retains native **Power** handling.
- The observer no longer requests wake for pound or the other matrix keys.
  Pound was checked with the display off on USB and left it off.
- Android's native Back wake policy is unchanged. The right softkey is mapped
  to BACK, so it can still light the display when Android processes that key.
  The observer's allowlist does not override native framework policy.
- With the keypad guard active, waking with the left softkey also starts the
  existing **left softkey → star** unlock sequence. A second left press is not
  required. Star or Power alone still keeps the guard locked.
- The three-second window, wrong-key cancellation and canceled/repeated key
  handling remain. This is an accidental-key guard, not an Android PIN lock;
  security credentials are not bypassed.

## Why the matrix keypad failed in real suspend

The live `/soc/matrix_keypad@0` device-tree node had no `wakeup-source` or legacy
`linux,wakeup` property, and the device had no `power/wakeup` attribute.
The [published Leo kernel driver at the pinned revision](https://github.com/bmndc/nokia-leo/blob/949a9f7b2c109f307e72b25fec2dc5cb4d119dec/kernel/msm-4.9/drivers/input/keyboard/matrix_keypad.c)
reads these properties into `pdata->wakeup`. Its suspend path stops the keypad,
and enables the row wake IRQs only when the device is allowed to wake.

A passive native observer recorded the following physical unplugged probes.
It neither grabs nor injects input, records key codes, holds a wake lock, nor
uses a wake alarm. It blocks when idle and has a non-waking 20-minute deadline.

| Delivered input device | Event-read → LCD-backlight-on |
| --- | ---: |
| GPIO star | 473, 416, 359, 431 ms |
| PMIC pound | 380 ms |
| Matrix keypad | No wake sample delivered during this comparison |

Kernel successful-suspend count increased from 79 to 85 during that comparison.
The owner observed that only star and pound woke the screen. These observations
match the missing matrix wake configuration. The timings begin after delivery
to userspace; they do not measure the earlier physical-press-to-kernel-resume
interval.

## Prepared boot correction

`patch-keypad-wake-boot.py` adds only the empty `wakeup-source` property to the
matrix node in the four relevant appended device trees. The other 34 DTBs stay
byte-identical. A full semantic comparison rejects changes to other properties,
nodes, reserved memory, or DT boot settings. The ARM kernel code, ramdisk,
command line and header addresses remain unchanged. The 32 MiB image's legacy
boot digest is recomputed.

The current boot and recovery were read back and backed up before preparation.
The dedicated installer defaults to **check only** and never reboots. It
independently validates the boot difference, exact handset and partition
identity, live hashes, USB, battery level and idle call state. Install/restore
write only boot, verify readback and check that recovery is unchanged.

| Artifact | SHA-256 |
| --- | --- |
| Backed-up current boot | `bb8731b1b7f3dd506e3180c00bbaea67f096a305697c49f5dc070b6738408d2b` |
| Prepared matrix-wake boot | `434ec1381679d00e1c284897b5411e7c720491bd6ddfa933c7619f22dbbffeb5` |
| Unchanged recovery backup | `a8ca049fa84ee83fe40deaf55932b67dfb09de2f1fe97d4e135fd7c0488b1333` |

**Status:** prepared and checked against the live handset; not flashed or
rebooted yet. Left-softkey wake from real suspend remains pending that step
and a physical unplugged test. A running Android/root ADB connection is needed
for the installer's restore action.

Example preparation, using `fdtput` from dtc in a Linux build environment:

```sh
python3 patch-keypad-wake-boot.py --input output/wake-latency/before-boot.img \
  --output output/wake-latency/boot-keypad-wake.img \
  --expected-sha256 bb8731b1b7f3dd506e3180c00bbaea67f096a305697c49f5dc070b6738408d2b \
  --fdtput /path/to/fdtput
```

The local binaries, backups and full diagnostic output remain under ignored
`output/wake-latency/`; they are not included in the source repository.

## Native observer and shell changes

The two-second wake-request limiter now uses BOOTTIME, which includes suspend.
The previous monotonic clock stopped during suspend and could preserve the
remaining cooldown over a long sleep. PowerManager's wake argument still uses
uptime, as required by Oreo; the two time domains are kept separate.

For a physical left-softkey wake, the native observer publishes one temporary
BOOTTIME stamp. The guard consumes a fresh stamp once to arm only the first
unlock step, with the original remaining three-second timeout. It never changes
`locked` to false. Star must still complete a normal, uncanceled press. The
stamp is nonpersistent, is discarded when unlocked/busy or going back to sleep,
and is not a record of keypad input.

Build the normal helper with `build-keylight.sh`. The optional
`build-keylight.sh --observe` builds the passive timing probe instead.

## Installed and checked before the boot step

- Running **data APK** SHA-256:
  `0ec09bc2fdbe230a12840ee95a40ffeb74657dfa5958dae76d77df75e44225ce`.
- Running native helper SHA-256:
  `57dfa406b4a26794340e0f6a8887cbe4b3d4a5b5258012cb75249f32c71b0cca`.
- Full app build: 292 production sources, unchanged manifest/signer, 2,254 DEX
  classes with all 431 external dependency classes retained.
- **107 host checks pass**, including scoped DT changes, kernel/ramdisk/header
  preservation, digest validation, malformed-input rejection and the
  installer's independent validation of reviewed images.
- All five existing Home/Menu/Settings physical smoke routes pass.
- On USB, through the real built-in input devices: left wake followed by star
  unlocks without another left press; star alone, wrong key and expired sequence
  keep the guard locked; native Power wakes and keeps the guard locked.
- The installed **system-UID** helper successfully published its wake stamp,
  and the two-key sequence worked with that helper. Pound no longer requested
  display wake; GPIO star still did.
- The native helper was installed with a verified previous binary and a timed
  rollback guard, acknowledged after checks. `/system` was returned read-only.
- The original completed battery result remains saved. No new battery trial was
  armed and no kernel sleep-depth parameter, governor or radio policy changed.

The bundled system APK and firmware/recovery archives have not been regenerated.
The boot correction has its own pending installation/test boundary above.
