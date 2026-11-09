---
name: leo-shell-change
description: Make and verify scoped changes to Leo Visual on the Nokia 6300 4G. Use for keypad navigation, launcher and settings cleanup, app extraction, persistence migrations, and Leo APK build verification.
---

Read the relevant finding in `android81/LEO-SHELL-AUDIT.md`. Change one user flow or data boundary at a time. Keep existing app IDs, saved category ordering, shortcut IDs, intent extras and preference formats compatible, or supply an explicit migration. Keep visible labels separate from identifiers. Reuse `LeoActivity`, `KeypadDialog`, `KeypadTabs` and `CircularNavigation` where their existing behavior fits.

Validate the real route from Home, Settings, or dedicated Menu. Check D-pad focus, OK, both softkeys, Back, return focus, and empty/loading states on 240 × 320 when relevant. Scan 212 is the dedicated Menu key. Scan 139 is the contextual left softkey. Use actual scan codes for a hardware routing check and identify whether a change belongs in Leo Visual, Leo Keypad, or framework/SystemUI.

See `android81/REBUILD.md` and `android81/POCKET-APPS.md` for build paths. The Windows Pocket builder recompiles only the listed Java classes and reuses a cached class archive. If using it, verify every changed class is rebuilt and compare the signed candidate with the previous APK using `android81/audit-visual-apk.py`. Use the full shell source build when the incremental checkpoint cannot represent the change. Validate on API 27; an APK audit does not validate a ROM or recovery package.

Treat build, test installation and system promotion as separate operations. Preserve a known-good APK and confirm the explicit Nokia target before an authorized installation. Recheck Home, accessibility, notification listener and IME after a package replacement. Avoid unrelated `/system` changes for a UI-only test. Report the APK hash and distinguish the running app from the bundled system base.
