# Leo Android 8.1 · documentation index

Native keypad Android for the Nokia 6300 4G TA-1286. Start with the
[project overview, feature table and current audit](../README.md).

## Current guides — English

| Guide | Contents |
| --- | --- |
| [GitHub packages](GITHUB-PACKAGES.md) | Private release assets, package choice and required fix |
| [Install and restore](FLASH-FROM-STOCK.md) | Rooted KaiOS entry, backup, clean installation and stock return |
| [Leo Recovery](LEO-RECOVERY.md) | Native recovery, SD installer and hardware validation |
| [KaiOS on SD](KAIOS-SD.md) | One-shot SD boot while retaining internal Leo |
| [QPST / QFIL](QFIL-RECOVERY.md) | Recovery-only candidate; EDL flashing untested |
| [Rebuild](REBUILD.md) | Host, pinned AOSP sources, private inputs and app builds |
| [App bundles](RELEASE-APPS.md) | Visual/Keypad APK packaging and updates |
| [Recovery bundles](RECOVERY-BUNDLE.md) | Snapshots and clean-install schemas |
| [Runtime updates](RUNTIME-UPDATE.md) | Capture, verification, install and rollback |
| [UI localization](LOCALIZATION.md) | English/Romanian interface and catalog |
| [Web-app SDK](web-app-sdk/README.md) | Local HTML/JavaScript keypad apps |

## Historical reports — original Romanian

These are dated records. “Untested” or “not implemented” describes that stage;
use the current guides for today's procedure. Old commands may refer to
abandoned experiments or local-only inputs.

- [Initial preparation, 15–16 October](README.preparation-2026-10-16.ro.md)
- [Development log](STATUS.md) and [hardware audit](FUNCTION-AUDIT-2026-10-18.md)
- [Adreno](ADRENO-INTEGRATION.md) and [latency](UI-LATENCY.md)
- [Audio](AUDIO-BRINGUP.md), [headphones](WIRED-HEADSET.md), [FM](FM-INVESTIGATION.md)
- [Radio](RADIO-BRINGUP.md), [IMS](IMS-RUNTIME.md), [SIM 2](IMS-SIM2.md)
- [Camera](CAMERA-RUNTIME.md) and [original camera audit](CAMERA-PORT-AUDIT.md)
- [UI foundation](LEO-UI-FOUNDATION.md) and [keypad IME](KEYPAD-IME.md)
- [0.14](LEO-14-DAILY-EXPERIENCE.md), [0.13](LEO-13-DAILY-TOOLS.md), [0.12](LEO-12-DAILY-CANDIDATE.md)
- [KaiOS 3.1 donor experiment](../kaios31/README.md)

Other dated feature reports remain in Romanian. The retired `shell/` is
historical; the active UI is `visual-shell/`.

## Documentation language

Current entry points and operating/build guides use English. Romanian archives,
source UI strings and captured screens are preserved. Documentation translation
does not change the phone language, firmware payloads or validation evidence.
