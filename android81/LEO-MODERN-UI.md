# Leo Modern UI foundation

Leo Modern is a native design layer for the 240 × 320 keypad display. It builds
on the existing Android 8.1 services and Leo navigation rather than adding a
WebView, another compositor or a permanent overlay above other apps.

## Shared app chrome

- Navy surfaces and a teal accent, with a tinted focus surface and white text.
- Compact medium-weight titles, app badges and a stable list-position counter.
- A focus rail and outlined selection, so colour is not the only focus cue.
- A separated softkey bar with an emphasized centre action.
- Shared list/form screens, canvas headers, Home softkeys and theme previews.

`UiChrome.java` owns the reusable chrome and list surfaces. `LeoActivity.java`
applies them to common lists and forms. `Appearance.java` defines the new palette;
theme pack index 3 selects palette 14 and clears stale per-app colour overrides.
Existing theme indices are unchanged. The theme can be selected from **Style →
Complete visual collections → Leo Modern**. Previewing does not apply it.
Applying a collection uses the existing visual checkpoint and restore mechanism.

Layouts retain their content budget: 36 dp shared title, 28 dp softkeys, original
48 dp minimum list rows. Canvas screens retain their existing coordinate space.
No new animation loops, blur, network polling or bitmap dependencies are added.
Accessibility contrast/text size and the previous in-place footer updates remain.

## Actual Android SystemUI

`patch-systemui-modern.py` patches the Oreo source checkout. The normal
`build-framework-keypad.sh` pipeline applies it after the keypad patches.

- The real Android clock moves left, before notification icons.
- Numeric slot labels are hidden as of 31 October to avoid collisions with 4G.
  Android still controls both signal groups, roaming, data type, airplane mode,
  Wi-Fi, Bluetooth and battery indicators.
- System bar and notification panel base colours use the navy palette.
- A diagnostic null guard handles the deliberately absent software navigation
  bar; stock Oreo's SystemUI dump otherwise crashes when accessing its controller.

The status bar remains 18 physical pixels on this device. There is no second
status bar inside Leo. SystemUI colours do not dynamically follow every Leo theme;
other Android apps still choose their own system-bar background/light-icon mode.
System notification and quick-settings layouts retain their Oreo structure.
Custom app content is progressively styled, not completely redesigned by this
foundation. This is not a replacement for the Android application framework.

## Build and deployment

Use the pinned checkout and Java 8 described in [REBUILD.md](REBUILD.md).

```sh
bash android81/build-visual-shell.sh
bash android81/build-framework-keypad.sh --apps-only
```

The second command builds SystemUI, Android Settings and PackageInstaller; only
SystemUI needs installing for this visual change. Keep verified APK backups.
During this development deployment, a SystemUI data update alone retained an old
keyguard service source path. Synchronizing the system base to the same verified
APK and restarting SystemUI activated the new resources and code. System was
returned to read-only. A generic data-only hot update is therefore insufficient
evidence that the new bar is actually running.

The October 28 private ROM ZIP predates this work. It has not been replaced or
relabelled as including Leo Modern. Future ROM assembly must explicitly include
the rebuilt Leo Visual and SystemUI APKs.

## Validation on the development phone

Recorded results and current screenshots are linked from the main README.
The existing UI foundation instrumentation covers focus movement, wraparound,
row recycling, content space, empty states and footer identity. Native SystemUI
must additionally be checked over both Leo and an Android activity, with both
SIM labels and live clock visible. This work does not retest calls, audio, IMS
or battery life, and makes no measured performance improvement claim.

On October 28, both APK builds passed, the UI foundation suite passed **30 checks**
on the final shell, and reapplying the SystemUI patch left all four source hashes
unchanged. Screenshots confirmed both numbered SIM signals, the left clock and
dark icon tint over the light Android Settings screen. The diagnostic dump now
reports `No software navigation bar` and keeps the same SystemUI PID alive.
An earlier diagnostic invocation exposed the stock null dereference; the final
build contains the guard. No full reboot or sustained soak was performed here.

Installed APK SHA-256 values (both system bases synchronized):

```text
LeoVisual 26250b0b2ff8395febedd094e00e3c0c6c10433f8d0b57498206e43a704378c9
SystemUI  dec951c362b61a103a5458531ac8fbeb1100162543cfd560626051c8705293c9
```

## Shared internal pages, 17 November 2026

The settings list layout now supplies the common native page style. Lists use
19 sp text, 44 dp minimum rows, flat dividers and a selected surface with an
accent rail. Headers, tab indicators, option dialogs, form fields and softkeys
share the current palette through `UiChrome`. Notes, Clock, Desk Clock,
Calculator and Android Apps use the `LeoActivity` theme lifecycle.

An explicit `app-theme-<Activity>` preference has priority. A subpage then
inherits its parent application's theme, followed by the global theme.
Personalization pages can inherit through Settings. Existing theme indices and
preference keys keep their meaning.

Returning to an existing page reapplies its palette to lists, fields, tabs and
softkeys in place. Forms retain text, selection and focus. Camera and browser
processes reload appearance preferences at resume. Palette reads during drawing
use the cached preferences.

Build and check the native theme regression suite on the development handset.

```powershell
python android81/build-pocket-apps-windows.py
python android81/build-pocket-tests-windows.py --ui-theme
python android81/framework-command.py --timeout 180 /system/bin/am instrument -w ro.leo.visual.uithemechecks/ro.leo.visual.UiThemeTests
python android81/smoke-leo-keypad.py --serial 695d
```

The regression suite restores appearance preferences after checking inherited
themes, an explicit subpage override, a nested Settings subpage, returning to
an existing list and retaining an unsaved editor. It also draws the shared
selected and unselected surfaces for every registered palette.

On TA-1286 / Android API 27, the final APK passed 72 theme checks and the physical
keypad smoke routes through Home, notifications, the global menu and Settings.
Device screenshots were inspected for Settings subpages, themes, Terminal,
music preview, Clock, Calculator, Notes and Android Apps. The host suite passed
61 tests. The APK audit retained all 431 baseline external dependency classes.

The installed runtime is a data update with SHA-256
`6dbd2c7b27c488d87e0114bde77cf5a03b133ebfff7c0bb678dc0eb3dd0fa11d`.
The bundled system base has SHA-256
`abcb93b6ee1305ededb7e52105a2d5dc232f8c795e67420834e49564cb89f0e6`.

## Clock, Android apps and keypad forms, 17 November 2026

Clock now uses the shared page header and softkeys, with the same three-title
tab window as Settings. Its five pages, alarms and timer preferences keep their
existing identifiers and storage. Android Apps uses native themed list rows;
package discovery and icon loading run on the existing worker. Returning to
the list retains the selected component, including after a reload.

`KeypadDialog` uses native themed rows, two-line option labels, a position
indicator and the common softkeys. OK and numbered shortcuts accept on key
release. Contact-number types and Calendar recurrence/reminder fields use
`KeypadSpinner`, retaining Android Spinner selection and listener behavior.
Form focus moves between editors and selectors with Up and Down. Shared
headings can wrap to two lines and progress indicators follow the palette.

The final source-built APK passed **48 UI polish checks**, **72 theme checks**
and the physical keypad smoke routes on TA-1286 / API 27. The polish suite
covers long labels, canceled and released keys, selector focus, draft retention,
all five Clock tabs, asynchronous app loading and a stale list callback after
reload. It restores Clock preferences and keeps the display awake during
rendering checks. The host suite passed **63 tests**. The DEX audit retained all
431 baseline dependency classes, with no unexpected or duplicate classes.

The running app is a data update with SHA-256
`c6c0b962246feb12100816cf7bf203b440f71bb408dabf5a4857f5513b71d683`.
The handset's bundled Leo Visual system base remains
`abcb93b6ee1305ededb7e52105a2d5dc232f8c795e67420834e49564cb89f0e6`.
Home, accessibility, notification listener and the keypad IME were rechecked
after replacement. Current screenshots are in the main README.

```powershell
python android81/build-pocket-tests-windows.py --ui-polish
python android81/framework-command.py --timeout 180 /system/bin/am instrument -w ro.leo.visual.uipolishchecks/ro.leo.visual.UiPolishTests
```

Private ROM preparation uses the read-only `20261117-210259` device snapshot.
Its overlays contain this Leo Visual APK and the current keypad IME
`bf06791c88c55af099d46ac46edfa260531e0893c9d2041bc2f58255cf53353c`.
Clean assembly preserves the IME and FM runtime from the verified system copy;
older build inputs are fallback files for snapshots that lack those paths.
Building and checking this package is separate from a clean installation,
which erases internal userdata. A clean boot of this candidate still needs
hardware validation.

### Verified private ROM assembly

`leo-fresh-0.14.0-dev-20261117-210259.zip` was built locally from clean source
commit `4dabe542a10af4b335f01948e1d735439a5ad72f`. The schema 2 package is
357,113,426 bytes and is kept under `android81/output/ui-polish-rom/`.
It contains Boot, the updated System and verified empty Data. It is a private
development candidate; the published 16 November release remains separate.

The builder's archive verifier passed. A separate invocation of
`install-recovery-bundle.py` also passed complete archive verification without
connecting to the phone. System and empty Data passed `e2fsck -f -n`. APKs read back from
the final System image match the tested shell, current keypad IME and current
TeleService. The latter has SHA-256
`1f7251b22084526c6254cfb16545f69e0ca9d936a07e4315a8138e679df1d4d3`.

```text
ZIP     3c891db3541f30af94061f046102dee66ab54954849634876d2d15476d3e56e1
System  31c45c1f6b704f56734d53043cc770f0c25f94317dde0ffaf34466ea2ae85bbe
Boot    bb8731b1b7f3dd506e3180c00bbaea67f096a305697c49f5dc070b6738408d2b
Data    cd89c3d37616925b17de315b9373e5a0e95f38d95ee12b9b0d8eb37afc9b9b0d
```

This ROM has not been flashed or booted after a wipe. The development phone
continues to run the tested data APK update. The existing on-device Recovery
catalog pins older archives; this new candidate uses the included PC installer
when a clean installation is explicitly chosen. See [the install guide](INSTALL-GUIDE.md).
