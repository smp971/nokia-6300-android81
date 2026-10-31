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
