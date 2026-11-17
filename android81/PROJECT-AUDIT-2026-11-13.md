# Leo project audit · 13 November 2026

The source review used the working tree based on `9f5dd8d`. The Nokia 6300 4G
TA-1286 ran Android 8.1 (API 27) and Leo Visual `0.14.0-dev` with APK SHA-256
`236b4ff8edd42a985b10a0a52d46839fb362e28421bb70c79a4b56f392a6605d`.
The working tree includes changes beyond that Git revision. The 13 November
ROM prerelease predates both the integrated radio and adaptive single-SIM Home.

| Evidence | Result |
| --- | --- |
| Source catalog | 48 stable IDs, all wired to declared native routes. Fourteen are contextual or Settings-only. |
| Manifest | 108 activities, 12 services, 12 receivers, one provider. The source audit checked that declared implementations exist. |
| Host suite | 60 Python tests passed, including app/recovery bundle and partition guards. |
| On-device navigation | 111 reviewed captures in the [screen atlas](../archive/screenshots/atlas-2026-11-13/README.md), including all seven launcher categories, six Settings tabs and app ordering. Captures show screen rendering and navigation. |
| Running state | SIM 2 ready, SIM 1 absent, Leo Keypad selected, Leo accessibility and notification listener configured. |
| Firmware | October clean-install evidence is tied to its exact ZIP. November ZIP passed offline verification. See [packages and validation](GITHUB-PACKAGES.md). |

## Component map

| Component | Entry, service and storage | Verification boundary |
| --- | --- | --- |
| Home and launcher | `AppCatalog` → `MainActivity` → `LeoHome`; editor transforms in `HomeCanvasLayout`, menu order in `appearance`. | Single-SIM Home and separate editor positions were shown on hardware. Two active SIMs were not inserted for this sweep. |
| Menus and focus | `LeoPointerService` routes the dedicated Menu. `LeoActivity`, `KeypadDialog`, `KeypadTabs` and `CircularNavigation` provide shared navigation. | Keypad routes and display were sampled. Third-party apps retain their own focus behavior. |
| Input and Android UI | `ro.leo.keypad` provides the IME. Framework, Settings and SystemUI have separate keypad and dark-theme patches. | Selected IME confirmed. English UI and Romanian predictions are separate features. |
| Telephony | `PhoneActivity` and `LeoInCallService` use Android Telecom; Android providers hold SMS and call history; the Qualcomm stack supplies radio/IMS. | Prior SIM 1 calls and SIM 2 VoLTE/SMS evidence. No carrier calls or messages during this audit. Dual IMS, VoWiFi and handover remain open. |
| Media | `PlaybackService` owns local/Internet audio; `FmService` owns the tuner; the media library reads Android and removable storage. | Earlier LTE MP3/AAC and FM hardware checks. Opening a media screen in the atlas does not exercise a decoder or speaker. |
| Data and import | Android providers, app-private preferences/JSON, `BackupEngine`, `BackupAppData` and verified removable-storage copy helpers. | Reviewed schema/restore entry points. Personal data exports and restore operations excluded from this audit. |
| Connectivity and peripherals | Android services plus Leo radio/audio/camera/connectivity bootstrap; ADB, MTP and SD each have separate paths. | Prior device tests and known limits are linked from the [hardware matrix](../README.md#what-works). Current sweep did not toggle radios or initiate pairing. |
| Recovery and release | Native RGB565 Leo Recovery, stock backup/restore tools, separately pinned ZIPs, signed app and ROM builders. | Host guards passed. November flash, first boot and full stock restoration remain pending. |

## Findings and follow-up

| Priority | Finding | Consequence and acceptance check |
| --- | --- | --- |
| P1 | The exported Home boundary still reads internal action extras in `onCreate` and `onNewIntent`. [Manifest](visual-shell/AndroidManifest.xml) · [Home dispatch](visual-shell/MainActivity.java) | An unsolicited explicit intent can reach internal actions. Keep public HOME behavior and move internal requests behind a private entry. Check permitted Home/keypad actions and rejected external requests with a local test app. Static review only. |
| P2 | `BackupEngine` has a restore journal. `BackupActivity` is the only startup entry that reconciles it. [Backup engine](visual-shell/BackupEngine.java) · [Backup UI](visual-shell/BackupActivity.java) | After interruption, an app may see partially restored data until Backup opens. Use an isolated fixture that interrupts between stores and checks startup reconciliation. |
| P2 | Radio favorites use their own preferences file, while Pocket backup covers the custom station JSON. [Radio](visual-shell/StreamRadioActivity.java) · [Pocket backup](visual-shell/BackupAppData.java) | A restored archive can retain custom stations but lose star selections. Add a versioned favorite section and check old archives and rollback. |
| P2 | Companion still publishes uploads with a separate existence check and rename. Its LAN session uses HTTP. [Companion server](visual-shell/CompanionServer.java) | Concurrent same-name uploads can contend for the destination. Verify exclusive publication with a local concurrent fixture and use encrypted authenticated transport before expanding the feature. Static review only. |
| P3 | App labels, icons and native routing are maintained in several classes. [App catalog](visual-shell/AppCatalog.java) · [Home dispatch](visual-shell/MainActivity.java) | The inventory currently passes. Keep IDs stable while moving destinations and icon metadata into one registry. |

The November 9 finding about stale class selection in the Windows builder is
addressed in the working tree. The builder compiles all top-level Visual Java
sources and the DEX audit preserved all 431 baseline external classes in the
installed candidate. The separate missing-Pocket-store finding is partially
addressed by the selectable v4 backup section. See the older
[48-feature inventory and historical checks](LEO-SHELL-AUDIT.md).

This sweep did not capture every content-dependent view. Recent apps did not
finish loading in the sampled capture. The call example is a local demo, and
the camera capture shows its diagnostics screen. File, gallery, network and
notification views were checked for personal data before publication.

## Repeatable checks

```powershell
python -X utf8 android81/audit-leo-shell.py
python -m unittest discover -s android81/tests
python -X utf8 android81/smoke-leo-keypad.py --serial 695d
```

The smoke test requires the project Nokia running Leo, unlocked and idle.
See [REBUILD](REBUILD.md) for the pinned Android 8.1 source, SDK dependencies
and app builds. See [the flash procedure](FLASH-FROM-STOCK.md) for recovery.

## 16 November follow-up

| Area | Result |
| --- | --- |
| Keypad and focus | Opening Music from the physical keypad and pressing Back restored the same app grid and selected app. Leo saves the last selection by app ID in each category. The keypad smoke passed after re-enabling the already configured accessibility service in Android Settings. |
| Rendering | Three 40-key app-grid trials on the Nokia. Median HWUI frame durations were 8.87, 8.36 and 8.50 ms before, and 6.02, 5.97 and 5.94 ms after. This measures reported frame rendering duration. The Music entry also opens the library directly. |
| Backup | Archive v5 includes starred Internet radio stations. An isolated on-device fixture covered restore, v1 app-data compatibility and retention of a failed journal. The main Leo process attempts pending rollback at startup. |
| SIM and calling | SIM settings show both physical slots and the active subscription's data, voice and IMS state. SIM 2 live IMS voice registration and validated LTE data passed. An offline call demo verified the DTMF UI while a key was held and after release. Carrier DTMF, handover, Bluetooth call audio and VoWiFi require separate trials. |
| USB | The device has a scoped update to the MTP transition service with an idle-call guard and one ADB fallback after a failed MTP request. A simulated request passed the no-op, rollback and active-call cases. Fifteen idle ADB reads passed after installation. MTP transitions and the intermittent disconnect remain open on hardware. |
| Recovery | The November Leo and stock KaiOS archives passed offline verification. The November internal clean install passed complete partition readback and reached Leo. The updated recovery booted both SD profiles. An additional Leo SD boot verified automatic baseband detection and both RIL processes. Full stock return and SD radio service tests remain pending. See the [SD radio evidence](leo-recovery/leo-sd-radio-hardware-validation.json) and the [installation guide](INSTALL-GUIDE.md). |

The Leo Visual APK in the internal app follow-up had SHA-256
`f3278ef6db5fc573fac5a4f14ac62f6e9a708819c2dcdb43622e2950d73996b5`.
