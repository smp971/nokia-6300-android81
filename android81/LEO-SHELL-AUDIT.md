# Leo Shell audit · 2026-11-09

Baseline: `8b60a53`, Nokia 6300 4G TA-1286, Android 8.1/API 27. The local
candidate, running APK and on-device system base report SHA-256
`3dd855e10ce94e4222a4dafd6ac9d3692165475683f336ab69f46d80057c7393`.
Leo Visual has 252 Java sources, 48 catalog IDs, and 133 Android components
(108 activities, 12 services, 12 receivers, one provider). All 48 IDs have a
native route to a declared activity. Fourteen are hidden from the main grid.
Some IDs use the same activity with different extras. The 63 Java test sources
include manual trials and historical tests.

## Findings in order of priority

| Priority | Finding and impact | First corrective step |
| --- | --- | --- |
| P1 | **Public Home can execute an internal action.** The [exported Home declaration](visual-shell/AndroidManifest.xml#L48) accepts `open_app` in both [creation](visual-shell/MainActivity.java#L66) and [intent reuse](visual-shell/MainActivity.java#L125). An external app that can start an activity can explicitly target Home with ID 18. The route opens `KeypadLockActivity`, whose [creation writes the lock preference](visual-shell/KeypadLockActivity.java#L42). This is an unauthorized Leo action, with local disruption. Verified by source trace. | Separate internal commands from the public Home entry. Test ordinary Android Home and legitimate shortcuts, plus rejection of an unsolicited lock command. Check the other internal flags at the same boundary. |
| P1 | **An incremental build can ship stale classes.** The [Windows builder](build-pocket-apps-windows.py#L9) pins a cached archive on first run and recompiles a fixed source list. A later edit outside that list can be absent from a successfully signed APK. The DEX auditor checks class coverage, so it cannot catch a stale implementation with the same class name. | Build the app from all tracked shell sources on the Windows path and keep the dependency/class audit. Until then, check every changed class against the compiler inputs. |
| P2 | **General backup omits new Pocket data.** [BackupEngine](visual-shell/BackupEngine.java#L18) snapshots allowlisted preferences, notes, wallpaper, keyboard and calendar. [PocketStore](visual-shell/PocketStore.java) writes its own JSON. A user restoring the general archive after data loss does not get their Pocket subscriptions, stations, study cards, dictionary or expenses back. Some apps offer individual exports. | Add a versioned, opt-in Pocket section and an isolated round-trip test using old archives, current archives and partial failure. Keep SSH credentials and downloaded media outside the settings archive. |
| P2 | **Interrupted restore recovery waits for Backup to be opened.** The [restore journal](visual-shell/BackupEngine.java#L34) covers a write sequence spanning preferences, files and providers. Its sole [recovery entry](visual-shell/BackupActivity.java#L8) runs when Backup opens. After interruption, other apps can read partially restored data before that point. Verified by source trace, with no forced interruption on the daily phone. | Reconcile a pending restore before exposing the affected data. Serialize restore and recovery and test interruption between stores in a fixture. |
| P2 | **Companion transfers can race.** Two workers can pass the same existence check before `renameTo` in [CompanionServer](visual-shell/CompanionServer.java#L13). The last rename can replace the first upload on Linux. The bearer token and file contents also travel over plain HTTP on the LAN. Source review only. | Make destination publication exclusive and test same-name concurrent uploads. Use an authenticated encrypted session before adding screen streaming or remote input. |
| P2 | **The shared header test fails on the active theme.** `UiFoundationTests` asserted the [fixed height](visual-shell/tests/UiFoundationTests.java#L60) and its assertion crashed the instrumentation process. [The current layout](visual-shell/LeoActivity.java#L55) explicitly varies height by palette. | Check the active palette and visible content bounds. Capture assertion failures in the test runner rather than letting them terminate the app process. |
| P3 | **Launcher metadata is spread across parallel structures.** `AppCatalog`, `MainActivity.Screen.open`, `IconArt` and `AppBadge` need coordination. [The old Android-package fallback](visual-shell/MainActivity.java#L378) is unreachable for the 48 validated IDs. Settings has a similar numeric remapping in its [row dispatch](visual-shell/SettingsActivity.java#L22). | Put each stable ID, label, category, destination and extras in one catalog. Migrate launcher routing first, retain persisted IDs, and move Settings separately. |

## Function inventory

**Fresh** means a targeted check in this audit. **Prior** means earlier
validation recorded in the project. **Source** means the code or entry point
was traced. An openable screen does not verify every action on that screen.
Entries marked † are hidden from the grid and have a contextual route or
settings entry. Core feature history is in the [project matrix](../README.md#what-works).

| ID | Function | Evidence and remaining work |
| ---: | --- | --- |
| 0 | Phone | **Fresh** 10 checks for dial, history, stats, focus and draft. **Prior** call audio/SIM 1 and SIM 2 VoLTE. Current carrier registration, calls, DTMF, handover and VoWiFi need separate trials. |
| 1 | Contacts | **Prior** lookup, multi-number edits, favorites and vCard preview. Duplicate merging and import after clean install still need a regression run. |
| 2 | Messages | **Prior** conversation/draft UI, unread state and SIM 2 send/delivery/receive. MMS absent. Repeat carrier tests separately from local fixtures. |
| 3 | Clock | **Fresh** 14 host alarm/timer cases. **Prior** clock tabs, alarms, world time, stopwatch and timer. Physical alarm and snooze audibility separate. |
| 4 | Calculator | **Fresh** 19 correct results and 10 error cases. **Prior** keypad UI. |
| 5 | Gallery | **Prior** local photo browsing. Large image, corrupt image and card-removal handling need a device sweep. |
| 6 | Music | **Prior** library, playlist, formats, visualizer, lyrics and output routing. **Fresh** media dashboard run timed out. Playback on hardware was not retested. |
| 7 | Files † | **Prior** SD/internal browsing, sort, copy/move and export. **Fresh** SD mounted and podcast promotion verified. General file actions were not rerun. |
| 8 | Settings | **Fresh** six Android-controls checks. **Prior** full/simple categories. Wi-Fi/BT/AP/USB state transitions and actual settings edits remain separate. |
| 9 | Android apps | **Prior** external apps and global menu. Compatibility is per app. |
| 10 | Notes | **Prior** pins, lists, drafts, T9 search and TXT export. Interrupted-write and restore cases need a current run. |
| 11 | Calendar | **Prior** local events, recurrence, reminders and ICS. Calendar providers, time zone changes and external sync need separate coverage. |
| 12 | Video | **Source** library → player activity. Test rendering, audio and codec samples on hardware. |
| 13 | Recorder | **Prior** short AAC capture, level meter, pause, rename and SD storage. New quality profiles and interruptions need hardware trials. |
| 14 | Converter | **Source** native route. Check units, locale and bounds. |
| 15 | Downloads † | **Prior** transfer center and local controls. **Fresh** synthetic podcast download completed and promoted to SD. General browser and Bluetooth jobs are separate. |
| 16 | Wallpaper † | **Prior** theme and wallpaper changes. Test large files and persistence after restore. |
| 17 | Favorites † | **Prior** Contacts favorites and Home shortcut. Repeat focus checks on empty/multiple-number contacts. |
| 18 | Keypad lock † | **Prior** wake/unlock. **Source** public Home intent can trigger a private lock route. Test call/alarm priority separately. |
| 19 | Text browser | **Prior** text navigation. Compatibility depends on site and TLS. JavaScript browsing lives in a different runtime. |
| 20 | Quick settings † | **Prior** keypad volume and profile changes. Confirm each radio, audio and USB action independently. |
| 21 | Setup † | **Prior** eleven-step wizard navigation. Clean-image first run and permission grants require a dedicated scenario. |
| 22 | Email | **Prior** IMAP receipt. SMTP, provider authentication and reconnect still need verification. |
| 23 | Terminal | **Prior** `pwd` from keypad and authorized root broker. Shell is line-based. Test ordinary and root sessions separately. |
| 24 | Diagnostics † | **Fresh** boot, APK, IME and grants inspected via ADB. Diagnostic screens and root reports still need a page sweep. |
| 25 | FM | **Prior** tuning, scan, favorites, RDS, headset and speaker. Long sessions and suspend/resume need fresh hardware tests. |
| 26 | Weather | **Prior** live forecast and 7-day detail. Cache, failures and current provider response were not retested. |
| 27 | Backup † | **Source** allowlist, journal and Pocket coverage gap. **Prior** selective restore and provider validation. No restore on the daily phone in this audit. |
| 28 | Help † | **Prior** contextual shortcuts. **Fresh** old shared UI fixture failed a palette-dependent header assertion. |
| 29 | Theme collections † | **Prior** style presets and restoration. Validate each palette at actual 240 × 320 size. |
| 30 | Reader | **Prior** TXT/Markdown/EPUB/PDF, bookmarking and keypad zoom. Verify big/corrupt documents. |
| 31 | Games | **Prior** Snake, Sudoku and 15 Puzzle flows. Long sessions and saved-state cases need coverage. |
| 32 | Connections † | **Prior** status and transfer views. **Fresh** device state sampled through ADB. Live state transitions need a device sweep. |
| 33 | Updates † | **Prior** metadata/hash validation. **Fresh** host guard tests. Complete update/rollback via phone UI remains unverified. |
| 34 | Camera | **Prior** JPEG preview/capture and LED. Video absent. Exposure, resume and long sessions need physical tests. |
| 35 | GPS | **Prior** provider/request lifecycle. A satellite fix remains unverified. |
| 36 | Leo Studio † | **Prior** app workflows in the [premium platform notes](LEO-10-PLATFORM.md). Workspace, automation and Companion need separate lifecycle checks. |
| 37 | Web apps | **Prior** local HTML/JS fixture and isolation checks. WebView GPU limitations and larger apps need a dedicated pass. |
| 38 | News | **Fresh** feed parser, redirect, sanitized text, local fixtures and native page. Real subscriptions not contacted. |
| 39 | Podcasts | **Fresh** enclosure, silent fixture preparation, download and verified SD copy. Long real playback remains separate. |
| 40 | Internet radio | **Fresh** ICY metadata fixture and native page. Real stations/codec combinations not contacted. |
| 41 | SSH/SFTP | **Fresh** loopback fingerprint checks, login, PTY echo and file transfers. External servers and full-screen terminal apps are separate. |
| 42 | Dictionary | **Fresh** starter data, import and normalized search. This dictionary is separate from the keyboard prediction lexicon. |
| 43 | Study | **Fresh** persistent cards, scheduling and stable IDs. Long-term data and restoration remain separate. |
| 44 | ZIP | **Fresh** round trip, CRC, overwrite and traversal guards. Test removable storage faults separately. |
| 45 | Expenses | **Fresh** exact cents, date, currency and safe CSV. Backup integration remains open. |
| 46 | Metronome | **Fresh** generated PCM bar and native page. Physical click audibility not tested. |
| 47 | QR codes | **Fresh** independent Unicode QR decode and native page. Physical scanning by another camera not tested. |

## Shared functions and hardware

| Area | Status |
| --- | --- |
| Home and dedicated Menu | **Prior** category focus, global More and Back from app on this APK. Source audit found the public intent issue above. Validate recents, pointer edge scroll, overlays and accessibility reconnection together after changes. |
| Leo Keypad | **Fresh** Android selects Leo IME and learning exclusions pass. **Prior** Romanian T9, multi-tap, caps, symbols and dictionary. English predictions absent. |
| Notifications and status bar | **Fresh** notification/accessibility configuration present and Android-controls page tests pass. **Prior** grouping and Android panel navigation. Real notification delivery, softkeys and dual-SIM layout remain a separate test. |
| Search, accessibility and personalization | **Prior** local search, Home editor, two SIM labels, simple/full modes, list wrap, focus and high contrast. Long-press paths and per-theme focus need repeatable fixtures. |
| Connectivity | **Prior** Wi-Fi, hotspot Internet, LTE, MTP, Bluetooth HID/A2DP/OPP. **Fresh** external SD mount and scoped test transfer. Bluetooth HFP and simultaneous dual IMS unverified. NFC absent. |
| Audio and haptics | **Fresh** audio bootstrap reports active. **Prior** speaker, earpiece, wired stereo and vibration. The system property alone does not verify audible output. |
| Power, battery, recovery | **Prior** power menu and recovery UI/install. **Fresh** 60 Python tests for project guards and tooling. No real reboot, wipe, restore, or flash performed during this audit. |

## Repeat the checks

- `python -X utf8 android81/audit-leo-shell.py` checks the catalog against the
  native dispatch and manifest. `--json PATH` produces the full 133-component
  inventory with processes, export flags, permissions and declared actions.
- Fresh results: 60 Python host tests, 29 calculator cases, 14 alarm cases,
  72 Pocket device checks with synthetic loopback data, 10 phone navigation
  checks, six Android-controls checks, and the keyboard learning policy.
  `UiFoundationTests` failed as described above. `MediaDashboardTests` did not
  finish within 40 seconds and was still active after the host timeout. The
  timed-out instrumentation was stopped, Home relaunched, and the previous
  accessibility grant restored. Treat playback as inconclusive and investigate
  the runner before using that result to diagnose it.
- The day-to-day path is an audit first, then a narrow refactor with a keypad
  acceptance check. Start at the public Home boundary and catalog, then unify
  the app build, and expand versioned backups before adding more Pocket apps.
  See the repo skills `$leo-shell-audit` and `$leo-shell-change`.
